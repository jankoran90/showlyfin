package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** COMPASS C2 (SHW-44) — kategorie Oblíbených (dle zadání usera; WRITER = scénáristé doplněn C3). */
enum class FavoriteKind { MOVIE, ACTOR, DIRECTOR, WRITER, PRODUCER, COMPOSER, COMPANY }

/**
 * COMPASS C2 (SHW-44) — jedna položka v Oblíbených. [id] = tmdbId (film / osoba / vydavatelství),
 * [imageUrl] = plná TMDB URL (poster filmu / profil osoby / logo studia), [year] jen u filmu.
 */
data class FavoriteItem(
    val kind: FavoriteKind = FavoriteKind.MOVIE,
    val id: Long = 0L,
    val name: String = "",
    val imageUrl: String? = null,
    val year: Int? = null,
    val addedAtMs: Long = 0L,
)

/** Obálka serverového JSONu `{"favorites":[…]}` (endpoint /api/profiles/{key}/favorites). */
private data class FavoritesEnvelope(val favorites: List<FavoriteItem> = emptyList())

/**
 * COMPASS C2 (SHW-44) + PER-PROFIL SYNC (DINGO) — úložiště Oblíbených.
 *
 * Dřív jen lokální [SharedPreferences] `compass_favorites` (per-ZAŘÍZENÍ, bez syncu) → na jiném
 * zařízení pod stejným profilem prázdno. Teď **per-profil na serveru** (klíč = `jellyfin_user_id`),
 * lokální prefs slouží jako **offline cache** + instant render. Server je zdroj pravdy:
 *  - [refresh]/init dotáhne seznam profilu ze serveru a nahradí lokál (i při přepnutí profilu).
 *  - [add]/[remove] zapíšou lokálně hned (reaktivní UI) a pushnou celý snapshot na server.
 *  - **Jednorázová migrace:** při prvním běhu nové verze se stávající lokální oblíbené nahrají na
 *    aktuální profil (flag [KEY_MIGRATED]), ať o ně user nepřijde.
 *
 * Reaktivní [items] (StateFlow) → obrazovka se aktualizuje okamžitě (detail, ENSEMBLE, hledání).
 */
@Singleton
class FavoritesStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val prefs = context.getSharedPreferences("compass_favorites", Context.MODE_PRIVATE)
    private val storeKey = "favorites"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<FavoriteItem>> = _items.asStateFlow()

    /**
     * Profil, pro který [_items] naposledy odpovídá serveru — PERSISTOVANÝ (přežije restart), aby
     * šlo po startu poznat „stejný profil" (→ union, chraň lokál) vs „přepnutý profil" (→ adoptuj
     * server, izolace). @Volatile field to nezvládl: po restartu null → záměna přepnutí za migraci.
     */
    private var lastSyncedProfile: String?
        get() = appPrefs.getString(KEY_LAST_PROFILE, null)
        set(v) { appPrefs.edit().putString(KEY_LAST_PROFILE, v).apply() }

    init {
        scope.launch { syncFromServer() }
    }

    // ── server přístup (stejné prefs klíče jako RealDebrid/Settings ViewModel) ──
    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty()
    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    private fun load(): List<FavoriteItem> {
        val raw = prefs.getString(storeKey, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<FavoriteItem>>(raw, object : TypeToken<List<FavoriteItem>>() {}.type)
        }.onFailure { Timber.w(it, "[COMPASS] favorites parse failed") }.getOrNull() ?: emptyList()
    }

    private fun persist(list: List<FavoriteItem>) {
        _items.value = list
        prefs.edit().putString(storeKey, gson.toJson(list)).apply()
    }

    /** Dotáhni oblíbené aktuálního profilu ze serveru; server = zdroj pravdy. Volá se z UI (screen open). */
    fun refresh() {
        scope.launch { syncFromServer() }
    }

    /**
     * BEZPEČNÝ sync (oprava ztráty dat 2026-07-07). Pravidla:
     *  - **Stejný profil / první běh (migrace):** UNION(lokál, server) → nikdy neztratíme lokální
     *    oblíbené kvůli (dočasně) prázdnému serveru. Když jsme přidali lokál-only → pushneme nahoru.
     *  - **Přepnutí profilu** (persistovaný [lastSyncedProfile] ≠ aktuální): adoptuj server 1:1
     *    (i prázdný) — lokál patří JINÉMU profilu, nesmí se přelít (izolace).
     *  - Offline / 404 → nesahat na lokál.
     */
    private suspend fun syncFromServer() {
        val key = profileKey(); val base = baseUrl(); val cookie = cookie()
        if (key.isBlank() || base.isBlank()) return  // nepřihlášeno → jen lokál
        runCatching {
            val server = parseServer(uploaderDs.getProfileFavorites(base, cookie, key))
                ?: return  // offline / 404 → nesahat na lokál
            val prev = lastSyncedProfile
            if (prev != null && prev != key) {
                // Přepnutí profilu → server je pravda pro NOVÝ profil (lokál patří starému).
                persist(server)
            } else {
                // Stejný profil / první běh → UNION (lokál první = přednost při shodě klíče).
                val merged = (_items.value + server).distinctBy { it.kind to it.id }
                persist(merged)
                if (merged.size != server.size) pushNow(key, base, cookie, merged) // seedni/dorovnej server
            }
            lastSyncedProfile = key
        }.onFailure { Timber.w(it, "[COMPASS] sync oblíbených selhal") }
    }

    private fun parseServer(raw: String?): List<FavoriteItem>? {
        if (raw == null) return null
        return runCatching { gson.fromJson(raw, FavoritesEnvelope::class.java)?.favorites ?: emptyList() }
            .onFailure { Timber.w(it, "[COMPASS] parse server favorites") }.getOrNull()
    }

    private suspend fun pushNow(key: String, base: String, cookie: String, list: List<FavoriteItem>) {
        runCatching {
            uploaderDs.putProfileFavorites(base, cookie, key, gson.toJson(FavoritesEnvelope(list)))
        }.onFailure { Timber.w(it, "[COMPASS] push oblíbených selhal") }
    }

    /** Po lokální změně pošli celý snapshot na server (fire-and-forget). */
    private fun pushToServer() {
        val key = profileKey(); val base = baseUrl(); val cookie = cookie()
        if (key.isBlank() || base.isBlank()) return
        val snapshot = _items.value
        scope.launch { pushNow(key, base, cookie, snapshot) }
    }

    fun isFavorite(kind: FavoriteKind, id: Long): Boolean =
        _items.value.any { it.kind == kind && it.id == id }

    fun add(item: FavoriteItem) {
        if (item.id <= 0L) return
        if (isFavorite(item.kind, item.id)) return
        persist(_items.value + item.copy(addedAtMs = System.currentTimeMillis()))
        Timber.i("[COMPASS] +oblíbené %s #%d %s", item.kind, item.id, item.name)
        pushToServer()
    }

    fun remove(kind: FavoriteKind, id: Long) {
        persist(_items.value.filterNot { it.kind == kind && it.id == id })
        pushToServer()
    }

    /** @return true = po přepnutí je v oblíbených, false = odebráno. */
    fun toggle(item: FavoriteItem): Boolean =
        if (isFavorite(item.kind, item.id)) {
            remove(item.kind, item.id); false
        } else {
            add(item); true
        }

    private companion object {
        /** Persistovaný poslední synchronizovaný profil (detekce přepnutí; per-device v trakt_prefs). */
        const val KEY_LAST_PROFILE = "compass_favorites_last_profile"
    }
}
