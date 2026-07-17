package com.github.jankoran90.showlyfin.core.db.repository

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.db.dao.FavoriteDao
import com.github.jankoran90.showlyfin.core.db.entity.FavoriteEntity
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Obálka serverového JSONu `{"favorites":[…]}` (endpoint `/api/profiles/{key}/favorites`). */
private data class FavoritesEnvelope(val favorites: List<FavoriteItem> = emptyList())

/**
 * SUBSTRATE (SHW-99) F1 — repozitář domény OBLÍBENÉ. **Room = jediný zdroj pravdy pro UI.**
 *
 * - [observe] = reaktivní [Flow] z Room (přepíná se dle AKTIVNÍHO profilu; UI se překreslí okamžitě).
 * - [add]/[remove] = optimistický zápis do Room (dirty=1, updatedAt=now; remove = **tombstone**) →
 *   Flow se aktualizuje HNED → na pozadí [sync] pushne na server.
 * - [sync] = pull `GET` + **UNION + tombstone-aware + LWW** merge + push celého blobu `PUT`
 *   (endpointy už existují; delta/verze až ve F2). Nikdy neztratit lokál kvůli prázdnému serveru.
 * - Profil-klíč = **profileUuid** (F0 nález: `jellyfin_user_id` kolidoval u sdílených JF účtů).
 * - Migrace: jednorázový import ze starého prefs blobu `compass_favorites` (flag [KEY_MIGRATED]).
 *
 * Běží PARALELNĚ se starým [com.github.jankoran90.showlyfin.data.uploader.FavoritesStore] — UI přepne
 * parent agent zvlášť (F1 = jen foundation, bez rewiringu srdíčka/sekce Oblíbené).
 */
@Singleton
class FavoritesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: FavoriteDao,
    private val profileRepository: ProfileRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val gson: Gson,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val prefs = context.getSharedPreferences("compass_favorites", Context.MODE_PRIVATE)
    private val legacyStoreKey = "favorites"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    /**
     * Synchronně čitelný snapshot oblíbených AKTIVNÍHO profilu (API-parita se starým `FavoritesStore`).
     * Plněn z Room [observe] Flow → `.value` čtou konzumenti (CuratorLoader, isFavorite/toggle).
     */
    private val _items = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val items: StateFlow<List<FavoriteItem>> = _items.asStateFlow()

    init {
        // Zrcadli reaktivní seznam aktivního profilu do synchronně čitelného StateFlow (parita s Store.items).
        scope.launch { observe().collect { _items.value = it } }
        // Samo-start: po prvním přiřazení aktivního profilu proveď migraci + background sync (cross-device).
        scope.launch {
            profileRepository.activeProfile
                .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
                .distinctUntilChanged()
                .collect { key ->
                    if (key != null) {
                        migrateFromPrefsIfNeeded(key)
                        sync(key)
                    }
                }
        }
    }

    /** API-parita se starým `FavoritesStore`: vynuť pull ze serveru pro aktivní profil (UI screen open). */
    fun refresh() {
        scope.launch { sync() }
    }

    /** Aktivní per-profil klíč (= profileUuid). null = žádný aktivní profil (nepřihlášeno). */
    private fun activeKey(): String? =
        profileRepository.activeProfile.value?.profileUuid?.takeIf { it.isNotBlank() }

    // ── reaktivní čtení (UI) ─────────────────────────────────────────────────
    /** Reaktivní seznam oblíbených AKTIVNÍHO profilu. Přepne se sám při změně profilu. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<FavoriteItem>> =
        profileRepository.activeProfile
            .map { it?.profileUuid?.takeIf { uuid -> uuid.isNotBlank() } }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                if (key == null) flowOf(emptyList())
                else dao.observe(key).map { rows -> rows.map { it.toItem() } }
            }

    fun observe(profileKey: String): Flow<List<FavoriteItem>> =
        dao.observe(profileKey).map { rows -> rows.map { it.toItem() } }

    /** Synchronní dotaz (API-parita se Store) — čte zrcadlený [items] snapshot aktivního profilu. */
    fun isFavorite(kind: FavoriteKind, id: Long): Boolean =
        _items.value.any { it.kind == kind && it.id == id }

    // ── optimistický zápis ───────────────────────────────────────────────────
    /** Přidá do oblíbených AKTIVNÍHO profilu (optimisticky) → Flow hned → background push. */
    fun add(item: FavoriteItem) {
        if (item.id <= 0L) return
        val key = activeKey() ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            dao.upsert(
                FavoriteEntity(
                    profileKey = key,
                    kind = item.kind.name,
                    refId = item.id,
                    name = item.name,
                    imageUrl = item.imageUrl,
                    year = item.year,
                    addedAt = if (item.addedAtMs > 0L) item.addedAtMs else now,
                    updatedAt = now,
                    dirty = 1,
                    deleted = 0,
                ),
            )
            Timber.i("[SUBSTRATE] +oblíbené %s #%d %s", item.kind, item.id, item.name)
            sync(key)
        }
    }

    /** Odebere (tombstone) z oblíbených AKTIVNÍHO profilu → Flow hned → background push. */
    fun remove(kind: FavoriteKind, id: Long) {
        val key = activeKey() ?: return
        scope.launch {
            dao.markDeleted(key, kind.name, id, System.currentTimeMillis())
            Timber.i("[SUBSTRATE] -oblíbené %s #%d", kind, id)
            sync(key)
        }
    }

    /** @return true = po přepnutí je v oblíbených, false = odebráno. */
    fun toggle(item: FavoriteItem): Boolean =
        if (isFavorite(item.kind, item.id)) {
            remove(item.kind, item.id); false
        } else {
            add(item); true
        }

    // ── sync broker (pull + merge + push) ────────────────────────────────────
    /**
     * Pull server → UNION+tombstone+LWW merge do Room → push celého živého snapshotu (F1 full-blob).
     * Offline/404 → nesahat na lokál (ochrana proti ztrátě dat).
     */
    suspend fun sync(key: String = activeKey().orEmpty()) {
        if (key.isBlank()) return
        val base = baseUrl(); val cookie = cookie()
        if (base.isBlank()) return  // nepřihlášeno k serveru → jen lokál
        syncMutex.withLock {
            runCatching {
                val serverItems = parseServer(uploaderDs.getProfileFavorites(base, cookie, key))
                if (serverItems != null) mergeServer(key, serverItems)

                // Push: dirty lokál NEBO server prázdný/neúplný (UNION safety — server dorovnat).
                val all = dao.getAll(key)
                val liveSnapshot = all.filter { it.deleted == 0 }.map { it.toItem() }
                val hasDirty = all.any { it.dirty == 1 }
                val serverKeys = serverItems?.map { it.kind to it.id }?.toSet() ?: emptySet()
                val liveKeys = liveSnapshot.map { it.kind to it.id }.toSet()
                if (hasDirty || serverItems == null || serverKeys != liveKeys) {
                    pushNow(key, base, cookie, liveSnapshot)
                    dao.clearDirty(key, System.currentTimeMillis())
                }
            }.onFailure { Timber.w(it, "[SUBSTRATE] sync oblíbených selhal") }
        }
    }

    /**
     * UNION + tombstone-aware + LWW. Full-blob server nemá per-položku verzi → LWW děláme jen kde to jde
     * (server [FavoriteItem.addedAtMs] vs lokál [FavoriteEntity.updatedAt]); jinak konzervativně chráníme
     * lokál (nikdy neztratit) a dirty tombstones (pending removal) vyhrávají nad serverem.
     */
    private suspend fun mergeServer(key: String, server: List<FavoriteItem>) {
        val localByKey = dao.getAll(key).associateBy { it.kind to it.refId }
        val toUpsert = mutableListOf<FavoriteEntity>()
        for (s in server) {
            val local = localByKey[s.kind.name to s.id]
            when {
                local == null -> {
                    // Server má, lokál ne → adoptuj čistě.
                    toUpsert += s.toEntity(key, dirty = 0, deleted = 0)
                }
                local.deleted == 1 && local.dirty == 1 -> {
                    // Pending removal (tombstone čeká na push) → NECHAT smazané (push ho pak odebere).
                }
                local.deleted == 1 && local.dirty == 0 -> {
                    // Čistý tombstone: vzkřísit jen když je server prokazatelně novější (LWW).
                    if (s.addedAtMs > local.updatedAt) {
                        toUpsert += s.toEntity(key, dirty = 0, deleted = 0)
                    }
                }
                else -> {
                    // Živý lokál: obnov metadata ze serveru jen když není dirty (neztratit rozdělanou změnu).
                    if (local.dirty == 0) {
                        toUpsert += local.copy(
                            name = s.name.ifBlank { local.name },
                            imageUrl = s.imageUrl ?: local.imageUrl,
                            year = s.year ?: local.year,
                        )
                    }
                }
            }
        }
        if (toUpsert.isNotEmpty()) dao.upsertAll(toUpsert)
    }

    // ── migrace prefs → Room ─────────────────────────────────────────────────
    /** Jednorázově naseeduj oblíbené ze starého prefs blobu `compass_favorites` (dirty=0 → UNION se serverem). */
    private suspend fun migrateFromPrefsIfNeeded(key: String) {
        if (appPrefs.getBoolean(KEY_MIGRATED, false)) return
        val legacy = loadLegacyPrefs()
        if (legacy.isNotEmpty()) {
            val existing = dao.getAll(key).associateBy { it.kind to it.refId }
            val seed = legacy
                .filter { it.id > 0L && !existing.containsKey(it.kind.name to it.id) }
                .map { it.toEntity(key, dirty = 0, deleted = 0) }
            if (seed.isNotEmpty()) {
                dao.upsertAll(seed)
                Timber.i("[SUBSTRATE] migrace prefs→Room: naseedováno %d oblíbených (profil %s)", seed.size, key)
            }
        }
        appPrefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun loadLegacyPrefs(): List<FavoriteItem> {
        val raw = prefs.getString(legacyStoreKey, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<FavoriteItem>::class.java)?.toList() ?: emptyList()
        }.onFailure { Timber.w(it, "[SUBSTRATE] parse legacy compass_favorites") }.getOrNull() ?: emptyList()
    }

    // ── server přístup (stejné prefs klíče jako ostatní storů) ───────────────
    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    private fun parseServer(raw: String?): List<FavoriteItem>? {
        if (raw == null) return null
        return runCatching { gson.fromJson(raw, FavoritesEnvelope::class.java)?.favorites ?: emptyList() }
            .onFailure { Timber.w(it, "[SUBSTRATE] parse server favorites") }.getOrNull()
    }

    private suspend fun pushNow(key: String, base: String, cookie: String, list: List<FavoriteItem>) {
        runCatching {
            uploaderDs.putProfileFavorites(base, cookie, key, gson.toJson(FavoritesEnvelope(list)))
        }.onFailure { Timber.w(it, "[SUBSTRATE] push oblíbených selhal") }
    }

    // ── mapování entity ↔ wire model ─────────────────────────────────────────
    private fun FavoriteEntity.toItem(): FavoriteItem = FavoriteItem(
        kind = runCatching { FavoriteKind.valueOf(kind) }.getOrDefault(FavoriteKind.MOVIE),
        id = refId,
        name = name,
        imageUrl = imageUrl,
        year = year,
        addedAtMs = addedAt,
    )

    private fun FavoriteItem.toEntity(key: String, dirty: Int, deleted: Int): FavoriteEntity = FavoriteEntity(
        profileKey = key,
        kind = kind.name,
        refId = id,
        name = name,
        imageUrl = imageUrl,
        year = year,
        addedAt = addedAtMs,
        updatedAt = addedAtMs,
        dirty = dirty,
        deleted = deleted,
    )

    private companion object {
        /** Flag jednorázové migrace prefs→Room (per-device, v traktPreferences). */
        const val KEY_MIGRATED = "substrate_favorites_migrated"
    }
}
