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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * COMPASS C2 (SHW-44) — kategorie Oblíbených (dle zadání usera; WRITER = scénáristé doplněn C3).
 *
 * SEZONA f3k (2026-08-03) — [WANT_MOVIE] a [WANT_SHOW] = **lokální „Chci vidět"** pro profily, které
 * nemají (a mít nebudou) Trakt, tedy dětské. User 2026-08-02 13:27: *„Jestli zvládneš udělat spíš CHCI
 * VIDĚT pro dětský profil, aby fungoval MIMO TRAKT, ale jako PŘÍKAZ pro to najít zdroj… a oblíbené budou
 * fungovat jako doposud — opravdu jako pro milované věci."*
 * 🔴 **Dva druhy, ne jeden s příznakem:** PK tabulky je `(profileKey, kind, refId)` a tmdb id filmu
 * a seriálu se PŘEKRÝVAJÍ — tmdb 30984 je seriál Bleach i film „Dissection" (přesně tahle záměna už
 * jednou usera stála cizí kartu ve Filmotéce). Oddělený `kind` je to, co identity drží od sebe.
 * Skladují se ve stejné tabulce jako Oblíbené, takže dědí hotový per-profil sync i tombstony.
 */
enum class FavoriteKind {
    MOVIE, ACTOR, DIRECTOR, WRITER, PRODUCER, COMPOSER, COMPANY, WANT_MOVIE, WANT_SHOW,
    /**
     * RAMPA (SHW-121) — fronta „K přehrání" (user 2026-08-28: *„presne taková aktualni nebo budouci
     * fronta prehrani"*). TŘETÍ seznam vedle Oblíbených a „Chci vidět", ruční a čistě náš — na Trakt
     * se NEPOSÍLÁ. Veze se stejnou tabulkou jako Oblíbené, takže dědí hotový per-profil sync
     * (telefon ↔ TV ↔ web) i tombstony; server `kind` nevaliduje, takže nepotřeboval zásah.
     * 🔴 Opět DVA druhy, ne jeden s příznakem — tmdb id filmu a seriálu se překrývají (viz komentář
     * u [WANT_MOVIE] výše: tmdb 30984 je seriál Bleach i film „Dissection").
     */
    QUEUE_MOVIE, QUEUE_SHOW,
}

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
    private val profileRepository: com.github.jankoran90.showlyfin.core.data.ProfileRepository,
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
        // 🔴 2026-08-26 — stejný vzor jako WorkingSourceStore (viz jeho komentář u initu): jednorázový
        // sync jen při startu procesu appky NEreagoval na přepnutí profilu. Fix = reaktivní sync na
        // KAŽDOU reálnou změnu aktivního profilu.
        scope.launch {
            profileRepository.activeProfile
                .map { it?.id }
                .distinctUntilChanged()
                .collect { id -> if (id != null) syncFromServer() }
        }
    }

    // ── server přístup (stejné prefs klíče jako RealDebrid/Settings ViewModel) ──
    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty()
    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    private fun load(): List<FavoriteItem> {
        val raw = prefs.getString(storeKey, null) ?: return emptyList()
        val parsed = runCatching {
            gson.fromJson<List<FavoriteItem>>(raw, object : TypeToken<List<FavoriteItem>>() {}.type)
        }.onFailure { Timber.w(it, "[COMPASS] favorites parse failed") }.getOrNull() ?: emptyList()
        // Vyčisti i STARÝ blob z disku — na instalacích odsud psaných PŘED SUBSTRATE F2b (2026-08-28)
        // mohou ležet zamrzlé WANT_*/QUEUE_* položky z doby, kdy tenhle store byl jejich jediný domov.
        return parsed.stripNewStoreKinds()
    }

    /**
     * SUBSTRATE (SHW-99) rozštěpilo doménu „favorites" na dvě NEZÁVISLÁ úložiště nad stejnými
     * serverovými řádky: [FavoriteKind.WANT_MOVIE]/[WANT_SHOW]/[QUEUE_MOVIE]/[QUEUE_SHOW] teď píše a
     * čte výhradně `core.db.repository.FavoritesRepository` (Room, tombstone-aware delta sync) —
     * tenhle starší store (prostý SharedPrefs blob + full-list PUT bez náhrobků) o smazáních udělaných
     * tou cestou vůbec neví. Když ho cokoli zavolá (`refresh()` z `FilmyMainActivity.onCreate`,
     * OblibeniViewModel, TvFavoritesViewModel…), jeho stará UNION logika smazaný titul znovu přimíchá
     * do lokálu a `pushNow` ho i s čerstvým razítkem vrátí na server — tak se titul „sám" vracel do
     * „Chci vidět"/fronty „K přehrání" (user 2026-08-28, Happy Hour + Survival Family, potvrzeno živě
     * v serverových logách: DB řádek smazán, o 28 s později PUSH z tohohle store ho oživil zpátky).
     * Fix: tenhle store ty čtyři druhy vůbec nedrží ani nepushuje — na KAŽDÉM zápisu je odfiltruje.
     */
    private fun List<FavoriteItem>.stripNewStoreKinds(): List<FavoriteItem> = filterNot {
        it.kind == FavoriteKind.WANT_MOVIE || it.kind == FavoriteKind.WANT_SHOW ||
            it.kind == FavoriteKind.QUEUE_MOVIE || it.kind == FavoriteKind.QUEUE_SHOW
    }

    private fun persist(list: List<FavoriteItem>) {
        val filtered = list.stripNewStoreKinds()
        _items.value = filtered
        prefs.edit().putString(storeKey, gson.toJson(filtered)).apply()
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
                // 🔴 `merged` pro PUSH se NESMÍ prosívat skrz [stripNewStoreKinds] — `pushNow` jde na
                // full-blob PUT (`_sub.full_replace`), který cokoli CHYBĚJÍCÍ ve snapshotu tombstonuje.
                // Kdyby `merged` neneslo WANT_*/QUEUE_* položky stažené ze serveru, tenhle PUSH by je
                // aktivně SMAZAL na serveru — mnohem horší regrese, než kterou opravujeme. `persist()`
                // filtruje jen to, co si tenhle store nechá LOKÁLNĚ; server dostává vždy plný snapshot.
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
