package com.github.jankoran90.showlyfin.feature.listen.stats

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.sync.SyncEngine
import com.github.jankoran90.showlyfin.core.db.sync.SyncableDao
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AMPHORA (SHW-124) F8 / CH2 — statistiky poslechu aktivního profilu: lokálně (SharedPreferences,
 * JSON per profil) a delta sync se serverem (doména `listen-stats`, [SyncEngine] jako záložky).
 *
 * Počítá se jen opravdu přehraný čas: přehrávač volá [tick] při každém obnovení stavu (~2× za
 * sekundu); dokud hraje, přičítá se uplynulý čas na hodinách (ne × rychlost, „ušetřeno rychlostí“
 * zvlášť). Mezisoučet se zapíše nejvýš jednou za [FLUSH_MS] a při každé pauze / změně položky, na
 * server odejde při pauze a nejvýš jednou za [SYNC_MS] během hraní.
 */
@Singleton
class ListenStatsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncEngine: SyncEngine,
    private val profileRepository: ProfileRepository,
    private val gson: Gson,
    private val absPrefs: AbsPreferences,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private data class Row(
        val stat: ListenStat,
        val dirty: Boolean = false,
        val version: Long = 0L,
    )

    private val prefs = context.getSharedPreferences("slovo_listen_stats", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val syncMutex = Mutex()
    private val cache = HashMap<String, MutableList<Row>>()
    private val rowsType = object : TypeToken<List<Row>>() {}.type
    private val device: String by lazy { absPrefs.deviceId.take(DEVICE_ID_LEN) }

    private val _stats = MutableStateFlow<List<ListenStat>>(emptyList())
    /** Všechny řádky aktivního profilu (ze všech jeho zařízení). */
    val stats: StateFlow<List<ListenStat>> = _stats.asStateFlow()

    // ── Tik z přehrávače (hlavní vlákno) ──
    private var lastTickAt = 0L
    private var wasPlaying = false
    private var pendingItem: ListenItem? = null
    /** Profil, kterému patří mezisoučet (po přepnutí profilu se dopíše ještě tomu starému). */
    private var pendingProfile: String = LOCAL
    private var pendingMs = 0L
    private var pendingSavedMs = 0.0
    private var lastFlushAt = 0L
    private var lastSyncAt = 0L
    private var lastFinishedUnit: String? = null

    /**
     * Volá přehrávač při každém obnovení stavu. [item] = co je načtené (null = nic), [playing] = opravdu
     * hraje (ne buffering / pauza), [speed] = rychlost přehrávání.
     */
    fun tick(item: ListenItem?, playing: Boolean, speed: Float) {
        val now = SystemClock.elapsedRealtime()
        val delta = if (lastTickAt == 0L) 0L else (now - lastTickAt).coerceIn(0L, MAX_GAP_MS)
        lastTickAt = now
        if (!ListenStatsPrefs.enabled(context)) {
            wasPlaying = false
            pendingItem = null; pendingMs = 0L; pendingSavedMs = 0.0
            return
        }
        val profile = activeKey() ?: LOCAL
        if (pendingItem != null && (item?.key != pendingItem?.key || profile != pendingProfile)) flush(sync = false)
        pendingProfile = profile
        if (playing && item != null) {
            // Jen úsek mezi dvěma tiky, kdy hrálo celou dobu (start po pauze se počítá až od dalšího tiku).
            if (wasPlaying) {
                pendingMs += delta
                pendingSavedMs += delta * (speed - 1f).toDouble()
            }
            pendingItem = item
            wasPlaying = true
            if (now - lastFlushAt >= FLUSH_MS) {
                flush(sync = now - lastSyncAt >= SYNC_MS)
            }
        } else {
            if (wasPlaying) flush(sync = true)
            wasPlaying = false
        }
    }

    /** Díl / kniha dohrána (přehrávač na konci). Stejná položka se započítá jen jednou za sebou. */
    fun markFinished(item: ListenItem) {
        if (!ListenStatsPrefs.enabled(context) || item.unitId == lastFinishedUnit) return
        lastFinishedUnit = item.unitId
        if (pendingItem?.key == item.key) flush(sync = false)
        val day = LocalDate.now().toString()
        mutate { rows -> upsert(rows, day, item) { it.copy(finished = it.finished + 1) } }
    }

    private fun flush(sync: Boolean) {
        val item = pendingItem
        val ms = pendingMs
        val saved = pendingSavedMs
        pendingMs = 0L
        pendingSavedMs = 0.0
        lastFlushAt = SystemClock.elapsedRealtime()
        if (sync) lastSyncAt = lastFlushAt
        if (item == null || ms < MIN_FLUSH_MS) {
            // Pod sekundu se nic nezapíše, ale zbytek se neztratí (přičte se k dalšímu zápisu).
            pendingMs = ms; pendingSavedMs = saved
            return
        }
        val day = LocalDate.now().toString()
        val sec = ms / 1000L
        val savedSec = (saved / 1000.0).toLong()
        // Zbytek pod sekundu zůstává v mezisoučtu (jinak by se při častých pauzách ztrácel).
        pendingMs = ms - sec * 1000L
        mutate(sync, pendingProfile) { rows ->
            upsert(rows, day, item) { it.copy(seconds = it.seconds + sec, savedSec = it.savedSec + savedSec) }
        }
    }

    private fun upsert(rows: MutableList<Row>, day: String, item: ListenItem, change: (ListenStat) -> ListenStat) {
        val id = "$day|$device|${item.key}"
        val i = rows.indexOfFirst { it.stat.id == id }
        val base = rows.getOrNull(i)?.stat ?: ListenStat(
            id = id, day = day, device = device, key = item.key, kind = item.kind,
            title = item.title, coverUrl = item.coverUrl,
        )
        val next = change(base).copy(
            title = item.title.ifBlank { base.title },
            coverUrl = item.coverUrl ?: base.coverUrl,
            updatedAt = System.currentTimeMillis(),
        )
        val row = Row(next, dirty = true, version = rows.getOrNull(i)?.version ?: 0L)
        if (i >= 0) rows[i] = row else rows += row
    }

    /** Odešle rozpracované a stáhne řádky z jiných zařízení profilu. */
    fun syncNow(key: String? = activeKey()) {
        val k = key?.takeIf { it != LOCAL } ?: return
        scope.launch {
            val base = appPrefs.getString("uploader_base_url", "").orEmpty()
            val cookie = appPrefs.getString("uploader_session_cookie", "").orEmpty()
            if (base.isBlank()) return@launch
            syncMutex.withLock {
                runCatching { syncEngine.sync(DOMAIN, syncAdapter, k, base, cookie) }
                    .onFailure { Timber.w(it, "[STATISTIKY] sync selhal") }
            }
        }
    }

    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    /** Změny jednou frontou v pořadí volání (vzor [com.github.jankoran90.showlyfin.feature.listen.bookmarks.BookmarkStore]). */
    private val ops = Channel<Triple<String, Boolean, (MutableList<Row>) -> Unit>>(Channel.UNLIMITED)

    private fun mutate(sync: Boolean = false, key: String = activeKey() ?: LOCAL, block: (MutableList<Row>) -> Unit) {
        ops.trySend(Triple(key, sync, block))
    }

    private suspend fun applyOps() {
        for ((key, sync, block) in ops) {
            mutex.withLock {
                val rows = load(key)
                block(rows)
                save(key, rows)
                publish(key)
            }
            if (sync) syncNow(key)
        }
    }

    private fun load(key: String): MutableList<Row> = cache.getOrPut(key) {
        val json = prefs.getString("rows:$key", null)
        runCatching { gson.fromJson<List<Row>>(json, rowsType)?.toMutableList() }
            .onFailure { Timber.w(it, "[STATISTIKY] čtení selhalo") }
            .getOrNull()
            ?.filter { it.stat != null && sanitized(it.stat) != null }
            ?.toMutableList()
            ?: mutableListOf()
    }

    private fun save(key: String, rows: MutableList<Row>) {
        // Telefon drží jen posledních ~13 měsíců (server má všechno); rozdělané řádky zůstávají.
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS).toString()
        rows.removeAll { !it.dirty && it.stat.day < cutoff }
        prefs.edit().putString("rows:$key", gson.toJson(rows)).apply()
    }

    private fun publish(key: String) {
        if ((activeKey() ?: LOCAL) != key) return
        _stats.value = load(key).map { it.stat }
    }

    /** Adaptér pro [SyncEngine]: identita řádku = [ListenStat.id]. */
    private val syncAdapter = object : SyncableDao {
        private val pushed = HashMap<String, Long>()

        override suspend fun dirtyRows(profileKey: String): List<DeltaRow> = mutex.withLock {
            pushed.clear()
            load(profileKey).filter { it.dirty }.onEach { pushed[it.stat.id] = it.stat.updatedAt }.map { r ->
                DeltaRow(rowId = r.stat.id, payload = gson.toJsonTree(r.stat), updatedAt = r.stat.updatedAt, version = r.version, deleted = 0)
            }
        }

        override suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>) = mutex.withLock {
            val local = load(profileKey)
            for (row in rows) {
                if (row.deleted == 1) continue // statistiky se nemažou
                val i = local.indexOfFirst { it.stat.id == row.rowId }
                val cur = local.getOrNull(i)
                if (cur != null && cur.dirty && cur.stat.updatedAt >= row.updatedAt) continue
                val stat = row.payload?.takeUnless { it.isJsonNull }
                    ?.let { p -> runCatching { gson.fromJson(p, ListenStat::class.java) }.getOrNull() }
                    ?.let(::sanitized)
                    ?: continue
                val next = Row(stat.copy(updatedAt = row.updatedAt), dirty = false, version = row.version)
                if (i >= 0) local[i] = next else local += next
            }
            save(profileKey, local)
            publish(profileKey)
        }

        override suspend fun clearDirty(profileKey: String, versions: Map<String, Long>) = mutex.withLock {
            val local = load(profileKey)
            for (i in local.indices) {
                val v = versions[local[i].stat.id] ?: continue
                // Přičteno během odesílání → zůstává dirty, odejde příště.
                if (pushed[local[i].stat.id] != local[i].stat.updatedAt) continue
                local[i] = local[i].copy(dirty = false, version = v)
            }
            save(profileKey, local)
        }
    }

    // init AŽ po všech vlastnostech (ops, syncAdapter) — korutiny na IO je čtou hned po startu.
    init {
        scope.launch { applyOps() }
        scope.launch {
            profileRepository.activeProfile
                .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
                .distinctUntilChanged()
                .collect { key ->
                    mutex.withLock { publish(key ?: LOCAL) }
                    if (key != null) syncNow(key)
                }
        }
    }

    /** Gson obchází Kotlin null-safety: payload z jiné verze appky může pole vynechat. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitized(s: ListenStat): ListenStat? {
        if (s.id == null || s.id.isBlank() || s.day == null || s.day.length != DAY_LEN || s.key == null || s.key.isBlank()) return null
        return s.copy(
            device = s.device ?: "",
            kind = s.kind ?: ListenStat.KIND_EPISODE,
            title = s.title ?: "",
            seconds = s.seconds.coerceAtLeast(0L),
        )
    }

    companion object {
        private const val DOMAIN = "listen-stats"
        private const val LOCAL = "local"
        private const val DEVICE_ID_LEN = 8
        private const val DAY_LEN = 10
        /** Mezera mezi tiky delší než tohle (uspaný proces) se nepočítá celá. */
        private const val MAX_GAP_MS = 30_000L
        private const val FLUSH_MS = 60_000L
        private const val SYNC_MS = 10 * 60_000L
        private const val MIN_FLUSH_MS = 1_000L
        private const val KEEP_DAYS = 400L
    }
}
