package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.dao.SavedShowDao
import com.github.jankoran90.showlyfin.core.db.entity.SavedShowEntity
import com.github.jankoran90.showlyfin.core.db.sync.SavedShowSyncableDao
import com.github.jankoran90.showlyfin.core.db.sync.SyncEngine
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AGORA F3 — „Moje oblíbené" podcasty/kanály (srdíčka) = OSOBNÍ záložky, na rozdíl od SDÍLENÉHO serverového
 * store zdrojů ([com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository] = „Přidáno", vidí rodina).
 *
 * EXCISE (SHW-103) Fáze B: přepsáno na SUBSTRATE Room + delta sync (`saved_show`, doména `saved-shows`) →
 * srdíčka jsou **cross-device** per profil (`slovo-main` u appky Slovo). Veřejné API ([favorites]/[isFavorite]/
 * [refsSnapshot]/[toggle]) beze změny. Ukládá CELOU kartu ([SourceSearchResult]) → „Oblíbené" render i offline.
 */
@Singleton
class FavoriteSourcesStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: SavedShowDao,
    private val syncableDao: SavedShowSyncableDao,
    private val syncEngine: SyncEngine,
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val legacyPrefs = context.getSharedPreferences("podcast_favorites", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _favorites = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    /** Oblíbené karty (nejnovější srdíčko první). Reaktivní → karty hned ukáží plné/prázdné srdce. */
    val favorites: StateFlow<List<SourceSearchResult>> = _favorites.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeUuidFlow = profileRepository.activeProfile
        .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
        .distinctUntilChanged()

    init {
        scope.launch {
            activeUuidFlow.flatMapLatest { key ->
                if (key == null) flowOf(emptyList())
                else dao.observe(key)
            }.collect { rows ->
                _favorites.value = rows.map { it.toCard() }
            }
        }
        scope.launch {
            activeUuidFlow.collect { key ->
                if (key != null) {
                    migrateFromPrefsIfNeeded(key)
                    syncNow(key)
                }
            }
        }
    }

    /** Stabilní klíč karty (shoda s „addedRefs" v PodcastDiscoveryViewModel). */
    private fun key(type: String, ref: String) = "$type:$ref"

    fun refsSnapshot(): Set<String> = _favorites.value.map { key(it.type, it.ref) }.toSet()

    fun isFavorite(type: String, ref: String): Boolean =
        _favorites.value.any { it.type == type && it.ref == ref }

    /** Přepne srdíčko (přidá nebo odebere). Optimisticky → Room (dirty) → push na server. */
    fun toggle(card: SourceSearchResult) {
        val k = key(card.type, card.ref)
        val exists = _favorites.value.any { key(it.type, it.ref) == k }
        // Optimistický UI update (nejnovější první).
        _favorites.value =
            if (exists) _favorites.value.filterNot { key(it.type, it.ref) == k }
            else listOf(card) + _favorites.value
        val profileKey = activeKey() ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            if (exists) {
                dao.markDeleted(profileKey, card.type, card.ref, now)
            } else {
                dao.upsert(card.toEntity(profileKey, addedAt = now, updatedAt = now, dirty = 1))
            }
            syncNow(profileKey)
        }
    }

    /** Push dirty srdíček + pull ze serveru. */
    fun syncNow(key: String? = activeKey()) {
        val k = key ?: return
        scope.launch {
            val base = baseUrl(); val cookie = cookie()
            if (base.isBlank()) return@launch
            syncMutex.withLock {
                runCatching { syncEngine.sync(DOMAIN, syncableDao, k, base, cookie) }
                    .onFailure { Timber.w(it, "[SUBSTRATE] sync oblíbených pořadů selhal") }
            }
        }
    }

    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    /** Jednorázově naseeduj srdíčka ze starého prefs blobu `podcast_favorites` (dirty=1 → propagace na server). */
    private suspend fun migrateFromPrefsIfNeeded(key: String) {
        if (appPrefs.getBoolean(KEY_MIGRATED, false)) return
        val json = legacyPrefs.getString(LEGACY_KEY, "").orEmpty()
        if (json.isNotBlank()) {
            runCatching {
                val arr = JSONArray(json)
                val existing = dao.getAll(key).map { it.type to it.ref }.toSet()
                val now = System.currentTimeMillis()
                val seed = buildList {
                    // Nejstarší srdíčko dostane nejnižší addedAt (JSON je nejnovější první → jdi odzadu).
                    for (i in arr.length() - 1 downTo 0) {
                        val o = arr.getJSONObject(i)
                        val type = o.optString("type"); val ref = o.optString("ref")
                        if (type.isBlank() || ref.isBlank() || (type to ref) in existing) continue
                        add(
                            SavedShowEntity(
                                profileKey = key, type = type, ref = ref,
                                title = o.optString("title"),
                                subtitle = o.optString("subtitle").ifBlank { null },
                                thumbnail = o.optString("thumbnail").ifBlank { null },
                                summary = o.optString("summary").ifBlank { null },
                                episodeCount = if (o.has("episode_count")) o.optInt("episode_count") else null,
                                category = o.optString("category").ifBlank { null },
                                addedAt = now + size, updatedAt = now + size, dirty = 1, deleted = 0,
                            ),
                        )
                    }
                }
                if (seed.isNotEmpty()) {
                    dao.upsertAll(seed)
                    Timber.i("[SUBSTRATE] migrace podcast_favorites→Room: %d srdíček (profil %s)", seed.size, key)
                }
            }.onFailure { Timber.w(it, "[SUBSTRATE] parse legacy podcast_favorites") }
        }
        appPrefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    private fun SavedShowEntity.toCard(): SourceSearchResult = SourceSearchResult(
        type = type, ref = ref, title = title, subtitle = subtitle,
        thumbnail = thumbnail, summary = summary, episodeCount = episodeCount, category = category,
    )

    private fun SourceSearchResult.toEntity(profileKey: String, addedAt: Long, updatedAt: Long, dirty: Int): SavedShowEntity =
        SavedShowEntity(
            profileKey = profileKey, type = type, ref = ref, title = title, subtitle = subtitle,
            thumbnail = thumbnail, summary = summary, episodeCount = episodeCount, category = category,
            addedAt = addedAt, updatedAt = updatedAt, dirty = dirty, deleted = 0,
        )

    companion object {
        private const val DOMAIN = "saved-shows"
        private const val LEGACY_KEY = "favorites"
        private const val KEY_MIGRATED = "substrate_saved_shows_migrated"
    }
}
