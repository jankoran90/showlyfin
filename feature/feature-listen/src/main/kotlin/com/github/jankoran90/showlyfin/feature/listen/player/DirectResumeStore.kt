package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.dao.PlaybackStateDao
import com.github.jankoran90.showlyfin.core.db.entity.PlaybackStateEntity
import com.github.jankoran90.showlyfin.core.db.sync.PlaybackStateSyncableDao
import com.github.jankoran90.showlyfin.core.db.sync.SyncEngine
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * LEVER (SHW-61) L2b — paměť pozice přehrávání pro DIRECT epizody (RSS / YouTube / ČT).
 *
 * EXCISE (SHW-103) Fáze B: přepsáno na SUBSTRATE Room + delta sync (`playback_state`, doména
 * `playback-state`) → pozice je **cross-device** per profil (`slovo-main` u appky Slovo), ne jen na
 * telefonu. Veřejné API ([marks]/[get]/[save]/[clear]) beze změny — přehrávač/seznamy nesahám.
 *
 * ABS podcasty/audioknihy mají pozici na ABS serveru (cross-device zadarmo); tento store řeší JEN direct
 * epizody. Klíč = stabilní `mediaId` (`yt:<id>` / `rss:<id>` / `ctv:<id>`). Zápis pozice = optimistický +
 * `dirty=1`; push na server až na lifecycle ([syncNow]) — pozice se ukládá často, sync ať není chatty.
 */
@Singleton
class DirectResumeStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: PlaybackStateDao,
    private val syncableDao: PlaybackStateSyncableDao,
    private val syncEngine: SyncEngine,
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    /** Pozice + (známá) délka v ms pro jednu direct epizodu. [updatedAt] = čas posledního zápisu (epoch ms). */
    data class Mark(val posMs: Long, val durMs: Long, val updatedAt: Long = 0L)

    private val legacyPrefs = context.getSharedPreferences("direct_resume", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _marks = MutableStateFlow<Map<String, Mark>>(emptyMap())
    /** mediaId → [Mark]; reaktivní (seznamy ukáží progres/„Pokračovat" bez pollingu). */
    val marks: StateFlow<Map<String, Mark>> = _marks.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeUuidFlow = profileRepository.activeProfile
        .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
        .distinctUntilChanged()

    init {
        // Zrcadli Room (aktivní profil) do _marks — přepne se sám při změně profilu.
        scope.launch {
            activeUuidFlow.flatMapLatest { key ->
                if (key == null) flowOf(emptyList())
                else dao.observe(key)
            }.collect { rows ->
                _marks.value = rows.associate { it.mediaKey to Mark(it.posMs, it.durMs, it.updatedAt) }
            }
        }
        // Po přiřazení profilu: jednorázová migrace lokálních prefs → Room + pull ze serveru (cross-device).
        scope.launch {
            activeUuidFlow.collect { key ->
                if (key != null) {
                    migrateFromPrefsIfNeeded(key)
                    syncNow(key)
                }
            }
        }
    }

    fun get(mediaId: String): Mark? = _marks.value[mediaId]

    /**
     * Ulož pozici. Blízko konce ([FINISH_TAIL_MS]) = dohrané → [clear]. Pod [MIN_RESUME_MS] neukládáme.
     * Optimisticky do [_marks] (snappy UI + [get]) + `dirty=1` do Room; server push řeší [syncNow] na lifecycle.
     */
    fun save(mediaId: String, posMs: Long, durMs: Long) {
        if (mediaId.isBlank()) return
        if (durMs > 0 && posMs >= durMs - FINISH_TAIL_MS) { clear(mediaId); return }
        if (posMs < MIN_RESUME_MS) return
        val cur = _marks.value[mediaId]
        if (cur != null && cur.posMs == posMs && cur.durMs == durMs) return
        val now = System.currentTimeMillis()
        _marks.update { it + (mediaId to Mark(posMs, durMs, now)) }
        val key = activeKey() ?: return
        scope.launch {
            dao.upsert(
                PlaybackStateEntity(
                    profileKey = key, mediaKey = mediaId, posMs = posMs, durMs = durMs,
                    updatedAt = now, dirty = 1, deleted = 0,
                ),
            )
        }
    }

    fun clear(mediaId: String) {
        if (mediaId.isBlank() || !_marks.value.containsKey(mediaId)) return
        _marks.update { it - mediaId }
        val key = activeKey() ?: return
        scope.launch { dao.markDeleted(key, mediaId, System.currentTimeMillis()) }
    }

    /** Push dirty pozic + pull ze serveru (lifecycle: příchod appky do popředí / pauza přehrávání). */
    fun syncNow(key: String? = activeKey()) {
        val k = key ?: return
        scope.launch {
            val base = baseUrl(); val cookie = cookie()
            if (base.isBlank()) return@launch
            syncMutex.withLock {
                runCatching { syncEngine.sync(DOMAIN, syncableDao, k, base, cookie) }
                    .onFailure { Timber.w(it, "[SUBSTRATE] sync pozic selhal") }
            }
        }
    }

    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    /** Jednorázově naseeduj pozice ze starého prefs blobu `direct_resume` (dirty=1 → propagace na server). */
    private suspend fun migrateFromPrefsIfNeeded(key: String) {
        if (appPrefs.getBoolean(KEY_MIGRATED, false)) return
        val json = legacyPrefs.getString(LEGACY_KEY, "").orEmpty()
        if (json.isNotBlank()) {
            runCatching {
                val obj = JSONObject(json)
                val existing = dao.getAll(key).map { it.mediaKey }.toSet()
                val seed = buildList {
                    obj.keys().forEach { id ->
                        if (id !in existing) {
                            val o = obj.getJSONObject(id)
                            add(
                                PlaybackStateEntity(
                                    profileKey = key, mediaKey = id,
                                    posMs = o.optLong("p", 0L), durMs = o.optLong("d", 0L),
                                    updatedAt = o.optLong("u", System.currentTimeMillis()),
                                    dirty = 1, deleted = 0,
                                ),
                            )
                        }
                    }
                }
                if (seed.isNotEmpty()) {
                    dao.upsertAll(seed)
                    Timber.i("[SUBSTRATE] migrace direct_resume→Room: %d pozic (profil %s)", seed.size, key)
                }
            }.onFailure { Timber.w(it, "[SUBSTRATE] parse legacy direct_resume") }
        }
        appPrefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    companion object {
        const val MIN_RESUME_MS = 5_000L
        const val FINISH_TAIL_MS = 15_000L
        private const val DOMAIN = "playback-state"
        private const val LEGACY_KEY = "marks"
        private const val KEY_MIGRATED = "substrate_playback_migrated"
    }
}
