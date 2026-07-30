package com.github.jankoran90.showlyfin.core.db.repository

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.dao.PlaybackStateDao
import com.github.jankoran90.showlyfin.core.db.entity.PlaybackStateEntity
import com.github.jankoran90.showlyfin.core.db.sync.PlaybackStateSyncableDao
import com.github.jankoran90.showlyfin.core.db.sync.SyncEngine
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
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
 * Implementace [VideoResumeStore] nad SUBSTRATE Room + delta sync (`playback_state`, doména
 * `playback-state`). Vzor: `DirectResumeStore` (pozice direct audio epizod) a [CtvWatchedStoreImpl].
 *
 * Co se tím opravilo (2026-07-30): pozice videa byly v jednom lokálním `SharedPreferences` bez profilu →
 * telefon a TV o sobě nevěděly a dětský profil dostal rozkoukané dospělého. Nově per profil
 * (`profileUuid`) a cross-device. Tabulku SDÍLÍ s audiem záměrně — klíč je společný (`ctv:<idec>` hraje
 * jednou jako video, jindy jako audio), takže „poslední vyhrává" je žádoucí chování, ne kolize.
 *
 * Zápis pozice je optimistický do [marks] + `dirty=1` do Room; push na server řeší [syncNow] na
 * lifecycle (odchod z přehrávače, změna profilu, start) — pozice se ukládá po sekundách, sync ať není chatty.
 *
 * Legacy prefs `video_resume` se jednorázově naseedují do PRVNÍHO profilu, který se přihlásí
 * (`dirty=1` → propagace na server). Nevíme, komu původně patřily; zahodit je by znamenalo tichou
 * ztrátu rozkoukaných filmů.
 */
@Singleton
class VideoResumeStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: PlaybackStateDao,
    private val syncableDao: PlaybackStateSyncableDao,
    private val syncEngine: SyncEngine,
    private val profileRepository: ProfileRepository,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) : VideoResumeStore {

    private val legacyPrefs = context.getSharedPreferences("video_resume", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _marks = MutableStateFlow<Map<String, VideoResumeStore.Mark>>(emptyMap())
    override val marks: StateFlow<Map<String, VideoResumeStore.Mark>> = _marks.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeUuidFlow = profileRepository.activeProfile
        .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
        .distinctUntilChanged()

    init {
        // Zrcadli Room (aktivní profil) do _marks — přepne se sám při změně profilu.
        scope.launch {
            activeUuidFlow.flatMapLatest { key ->
                if (key == null) flowOf(emptyList()) else dao.observe(key)
            }.collect { rows ->
                _marks.value = rows.associate {
                    it.mediaKey to VideoResumeStore.Mark(it.posMs, it.durMs, it.updatedAt)
                }
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

    override fun get(key: String): VideoResumeStore.Mark? = _marks.value[key]

    override fun save(key: String, posMs: Long, durMs: Long) {
        if (key.isBlank()) return
        if (durMs > 0 && posMs >= durMs - VideoResumeStore.FINISH_TAIL_MS) { clear(key); return }
        if (posMs < VideoResumeStore.MIN_RESUME_MS) return
        val cur = _marks.value[key]
        if (cur != null && cur.posMs == posMs && cur.durMs == durMs) return
        val now = System.currentTimeMillis()
        _marks.update { it + (key to VideoResumeStore.Mark(posMs, durMs, now)) }
        val profileKey = activeKey() ?: return
        scope.launch {
            dao.upsert(
                PlaybackStateEntity(
                    profileKey = profileKey, mediaKey = key, posMs = posMs, durMs = durMs,
                    updatedAt = now, dirty = 1, deleted = 0,
                ),
            )
        }
    }

    override fun clear(key: String) {
        if (key.isBlank() || !_marks.value.containsKey(key)) return
        _marks.update { it - key }
        val profileKey = activeKey() ?: return
        scope.launch { dao.markDeleted(profileKey, key, System.currentTimeMillis()) }
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
                    .onFailure { Timber.w(it, "[SUBSTRATE] sync pozic videa selhal") }
            }
        }
    }

    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    /** Jednorázově naseeduj pozice ze starého prefs blobu `video_resume` (dirty=1 → propagace na server). */
    private suspend fun migrateFromPrefsIfNeeded(profileKey: String) {
        if (appPrefs.getBoolean(KEY_MIGRATED, false)) return
        val json = legacyPrefs.getString(LEGACY_KEY, "").orEmpty()
        if (json.isNotBlank()) {
            runCatching {
                val obj = JSONObject(json)
                val existing = dao.getAll(profileKey).map { it.mediaKey }.toSet()
                val now = System.currentTimeMillis()
                val seed = buildList {
                    obj.keys().forEach { id ->
                        if (id !in existing) {
                            val o = obj.getJSONObject(id)
                            add(
                                PlaybackStateEntity(
                                    profileKey = profileKey, mediaKey = id,
                                    posMs = o.optLong("p", 0L), durMs = o.optLong("d", 0L),
                                    updatedAt = now, dirty = 1, deleted = 0,
                                ),
                            )
                        }
                    }
                }
                if (seed.isNotEmpty()) {
                    dao.upsertAll(seed)
                    Timber.i("[SUBSTRATE] migrace video_resume→Room: %d pozic (profil %s)", seed.size, profileKey)
                }
            }.onFailure { Timber.w(it, "[SUBSTRATE] parse legacy video_resume") }
        }
        appPrefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    private companion object {
        const val DOMAIN = "playback-state"
        const val LEGACY_KEY = "marks"
        const val KEY_MIGRATED = "substrate_video_resume_migrated"
    }
}
