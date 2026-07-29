package com.github.jankoran90.showlyfin.core.db.repository

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.dao.CtvWatchedDao
import com.github.jankoran90.showlyfin.core.db.entity.CtvWatchedEntity
import com.github.jankoran90.showlyfin.core.db.sync.CtvWatchedSyncableDao
import com.github.jankoran90.showlyfin.core.db.sync.SyncEngine
import com.github.jankoran90.showlyfin.core.domain.resume.CtvWatchedStore
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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * VLTAVA (SHW-110) — implementace [CtvWatchedStore] nad SUBSTRATE Room + delta sync (`ctv_watched`,
 * doména `ctv-watched`). Vzor: `DirectResumeStore` (pozice direct epizod).
 *
 * Co se tím opravilo: dokoukané díly ČT byly v jednom lokálním `SharedPreferences` bez profilu →
 * (a) telefon a TV o sobě nevěděly, (b) **dětský profil viděl dokoukané dospělého**. Nově per profil
 * (`profileUuid`) a cross-device. Označení je optimistické do [watched] + `dirty=1` do Room; push
 * řeší [syncNow] (změna profilu, start, otevření karty pořadu) — ať sync není chatty.
 *
 * Legacy prefs `ctv_watched` se jednorázově naseedují do PRVNÍHO profilu, který se přihlásí
 * (`dirty=1` → propagace na server). Nevíme, komu původně patřily; ponechat je jen lokálně by
 * znamenalo tichou ztrátu fajfek, které si user naklikal.
 */
@Singleton
class CtvWatchedStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: CtvWatchedDao,
    private val syncableDao: CtvWatchedSyncableDao,
    private val syncEngine: SyncEngine,
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) : CtvWatchedStore {

    private val legacyPrefs = context.getSharedPreferences("ctv_watched", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _watched = MutableStateFlow<Set<String>>(emptySet())
    override val watched: StateFlow<Set<String>> = _watched.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeUuidFlow = profileRepository.activeProfile
        .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
        .distinctUntilChanged()

    init {
        // Zrcadli Room (aktivní profil) do _watched — přepne se sám při změně profilu.
        scope.launch {
            activeUuidFlow.flatMapLatest { key ->
                if (key == null) flowOf(emptyList()) else dao.observe(key)
            }.collect { rows -> _watched.value = rows.map { it.mediaKey }.toSet() }
        }
        // Po přiřazení profilu: jednorázová migrace lokálních prefs → Room + pull ze serveru.
        scope.launch {
            activeUuidFlow.collect { key ->
                if (key != null) {
                    migrateFromPrefsIfNeeded(key)
                    syncNow(key)
                }
            }
        }
    }

    override fun isWatched(key: String): Boolean = key.isNotBlank() && key in _watched.value

    override fun markWatched(key: String) {
        if (key.isBlank() || key in _watched.value) return
        _watched.update { it + key }
        val profileKey = activeKey() ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            dao.upsert(
                CtvWatchedEntity(
                    profileKey = profileKey, mediaKey = key, watchedAt = now,
                    updatedAt = now, dirty = 1, deleted = 0,
                ),
            )
            syncNow(profileKey)
        }
    }

    override fun clear(key: String) {
        if (key !in _watched.value) return
        _watched.update { it - key }
        val profileKey = activeKey() ?: return
        scope.launch {
            dao.markDeleted(profileKey, key, System.currentTimeMillis())
            syncNow(profileKey)
        }
    }

    override fun syncNow() {
        syncNow(activeKey())
    }

    private fun syncNow(key: String?) {
        val k = key ?: return
        scope.launch {
            val base = baseUrl()
            if (base.isBlank()) return@launch
            syncMutex.withLock {
                runCatching { syncEngine.sync(DOMAIN, syncableDao, k, base, cookie()) }
                    .onFailure { Timber.w(it, "[SUBSTRATE] sync dokoukaných ČT dílů selhal") }
            }
        }
    }

    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    /** Jednorázově naseeduj fajfky ze starého prefs setu `ctv_watched` (dirty=1 → propagace na server). */
    private suspend fun migrateFromPrefsIfNeeded(profileKey: String) {
        if (appPrefs.getBoolean(KEY_MIGRATED, false)) return
        val legacy = legacyPrefs.getStringSet(LEGACY_KEY, emptySet()).orEmpty()
        if (legacy.isNotEmpty()) {
            val existing = dao.getAll(profileKey).map { it.mediaKey }.toSet()
            val now = System.currentTimeMillis()
            val seed = legacy.filter { it.isNotBlank() && it !in existing }.map { key ->
                CtvWatchedEntity(
                    profileKey = profileKey, mediaKey = key, watchedAt = now,
                    updatedAt = now, dirty = 1, deleted = 0,
                )
            }
            if (seed.isNotEmpty()) {
                dao.upsertAll(seed)
                Timber.i("[SUBSTRATE] migrace ctv_watched→Room: %d dílů (profil %s)", seed.size, profileKey)
            }
        }
        appPrefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    private companion object {
        const val DOMAIN = "ctv-watched"
        const val LEGACY_KEY = "ctv_watched_keys"
        const val KEY_MIGRATED = "substrate_ctv_watched_migrated"
    }
}
