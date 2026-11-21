package com.github.jankoran90.showlyfin.feature.listen.bookmarks

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.sync.SyncEngine
import com.github.jankoran90.showlyfin.core.db.sync.SyncableDao
import com.github.jankoran90.showlyfin.data.uploader.model.DeltaRow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AMPHORA (SHW-124) F5 / PR2 — záložky aktivního profilu: lokálně (SharedPreferences, JSON per profil)
 * a delta sync se serverem (doména `bookmarks`, [SyncEngine] jako oblíbené pořady). Bez Room → žádná
 * migrace sdílené databáze s Filmy.
 *
 * Zápis je optimistický (hned v [bookmarks]), na server odejde v příštím [syncNow] (po každé změně,
 * při startu a přepnutí profilu). Konflikty řeší server Last-Write-Wins podle `updatedAt`; smazání =
 * tombstone (řádek zůstává s `deleted`, ať se nevrátí z jiného telefonu).
 */
@Singleton
class BookmarkStore @Inject constructor(
    @ApplicationContext context: Context,
    private val syncEngine: SyncEngine,
    private val profileRepository: ProfileRepository,
    private val gson: Gson,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    /** Uložený řádek = záložka + stav syncu. */
    private data class Row(
        val bm: Bookmark,
        val dirty: Boolean = false,
        val deleted: Boolean = false,
        val version: Long = 0L,
    )

    private val prefs = context.getSharedPreferences("slovo_bookmarks", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val syncMutex = Mutex()
    private val cache = HashMap<String, MutableList<Row>>()
    private val rowsType = object : TypeToken<List<Row>>() {}.type

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    /** Záložky aktivního profilu (bez smazaných), nejnovější první. */
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    init {
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

    /** Záložky jedné položky (kniha / díl) seřazené podle času v nahrávce. */
    fun forItem(itemKey: String): List<Bookmark> =
        _bookmarks.value.filter { it.itemKey == itemKey }.sortedBy { it.posMs }

    /** Nová záložka; vrátí ji (s vygenerovaným id). */
    fun add(
        kind: String, itemId: String, episodeId: String?, title: String, subtitle: String?, coverUrl: String?,
        posMs: Long, durationMs: Long, chapter: String?, url: String?, note: String = "",
    ): Bookmark {
        val now = System.currentTimeMillis()
        val bm = Bookmark(
            id = UUID.randomUUID().toString(), kind = kind, itemId = itemId, episodeId = episodeId,
            title = title, subtitle = subtitle, coverUrl = coverUrl, posMs = posMs.coerceAtLeast(0L),
            durationMs = durationMs, chapter = chapter, note = note.trim(), url = url,
            createdAt = now, updatedAt = now,
        )
        _bookmarks.value = listOf(bm) + _bookmarks.value  // hned v UI
        mutate { rows -> rows += Row(bm, dirty = true) }
        return bm
    }

    fun updateNote(id: String, note: String) = mutate { rows ->
        val i = rows.indexOfFirst { it.bm.id == id && !it.deleted }
        if (i >= 0) rows[i] = rows[i].copy(bm = rows[i].bm.copy(note = note.trim(), updatedAt = System.currentTimeMillis()), dirty = true)
    }

    fun delete(id: String) = mutate { rows ->
        val i = rows.indexOfFirst { it.bm.id == id }
        if (i >= 0) rows[i] = rows[i].copy(bm = rows[i].bm.copy(updatedAt = System.currentTimeMillis()), dirty = true, deleted = true)
    }

    /** Odešle rozpracované a stáhne změny z jiných zařízení. */
    fun syncNow(key: String? = activeKey()) {
        val k = key?.takeIf { it != LOCAL } ?: return
        scope.launch {
            val base = appPrefs.getString("uploader_base_url", "").orEmpty()
            val cookie = appPrefs.getString("uploader_session_cookie", "").orEmpty()
            if (base.isBlank()) return@launch
            syncMutex.withLock {
                runCatching { syncEngine.sync(DOMAIN, syncAdapter, k, base, cookie) }
                    .onFailure { Timber.w(it, "[ZALOZKY] sync selhal") }
            }
        }
    }

    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    private fun mutate(block: (MutableList<Row>) -> Unit) {
        val key = activeKey() ?: LOCAL
        scope.launch {
            mutex.withLock {
                val rows = load(key)
                block(rows)
                save(key, rows)
                publish(key)
            }
            syncNow(key)
        }
    }

    private fun load(key: String): MutableList<Row> = cache.getOrPut(key) {
        val json = prefs.getString("rows:$key", null)
        runCatching { gson.fromJson<List<Row>>(json, rowsType)?.toMutableList() }
            .onFailure { Timber.w(it, "[ZALOZKY] čtení selhalo") }
            .getOrNull() ?: mutableListOf()
    }

    private fun save(key: String, rows: List<Row>) {
        prefs.edit().putString("rows:$key", gson.toJson(rows)).apply()
    }

    /** Zveřejní záložky profilu [key], jen když je pořád aktivní. */
    private fun publish(key: String) {
        if ((activeKey() ?: LOCAL) != key) return
        _bookmarks.value = load(key).filter { !it.deleted }.map { it.bm }.sortedByDescending { it.createdAt }
    }

    /** Adaptér pro [SyncEngine]: identita řádku = id záložky. */
    private val syncAdapter = object : SyncableDao {
        /** id → updatedAt odeslané verze; po ACK se smaže dirty JEN když se řádek mezitím nezměnil. */
        private val pushed = HashMap<String, Long>()

        override suspend fun dirtyRows(profileKey: String): List<DeltaRow> = mutex.withLock {
            pushed.clear()
            load(profileKey).filter { it.dirty }.onEach { pushed[it.bm.id] = it.bm.updatedAt }.map { r ->
                DeltaRow(
                    rowId = r.bm.id, payload = gson.toJsonTree(r.bm), updatedAt = r.bm.updatedAt,
                    version = r.version, deleted = if (r.deleted) 1 else 0,
                )
            }
        }

        override suspend fun applyServerRows(profileKey: String, rows: List<DeltaRow>) = mutex.withLock {
            val local = load(profileKey)
            for (row in rows) {
                val i = local.indexOfFirst { it.bm.id == row.rowId }
                val cur = local.getOrNull(i)
                // Rozdělaná lokální změna stejně nová nebo novější → nech ji, odejde příštím pushem.
                if (cur != null && cur.dirty && cur.bm.updatedAt >= row.updatedAt) continue
                val bm = row.payload?.takeUnless { it.isJsonNull }
                    ?.let { p -> runCatching { gson.fromJson(p, Bookmark::class.java) }.getOrNull() }
                    ?.let(::sanitized)
                    ?: cur?.bm
                    ?: continue
                val next = Row(bm.copy(updatedAt = row.updatedAt), dirty = false, deleted = row.deleted == 1, version = row.version)
                if (i >= 0) local[i] = next else local += next
            }
            save(profileKey, local)
            publish(profileKey)
        }

        override suspend fun clearDirty(profileKey: String, versions: Map<String, Long>) = mutex.withLock {
            val local = load(profileKey)
            for (i in local.indices) {
                val v = versions[local[i].bm.id] ?: continue
                // Změněno během odesílání (nová poznámka, smazání) → zůstává dirty, odejde příště.
                if (pushed[local[i].bm.id] != local[i].bm.updatedAt) continue
                local[i] = local[i].copy(dirty = false, version = v)
            }
            save(profileKey, local)
        }
    }

    /** Gson obchází Kotlin null-safety: payload z jiné verze appky může pole vynechat. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitized(b: Bookmark): Bookmark? {
        if (b.id == null || b.id.isBlank() || b.itemId == null || b.itemId.isBlank() || b.kind == null) return null
        return b.copy(title = b.title ?: "", note = b.note ?: "")
    }

    companion object {
        private const val DOMAIN = "bookmarks"
        /** Bez profilu: jen v telefonu, na server nic. */
        private const val LOCAL = "local"
    }
}
