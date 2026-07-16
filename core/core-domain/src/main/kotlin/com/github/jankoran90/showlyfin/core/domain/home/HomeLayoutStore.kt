package com.github.jankoran90.showlyfin.core.domain.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TENFOOT — TV DOMOV REDESIGN. Uživatelské rozvržení domova: seznam [HomeRowConfig] řad + [SidebarEntry]
 * sidebar. Dedikovaný prefs soubor (`tv_home_layout`) mimo `traktPreferences` → izolace + snadný reset,
 * nepodléhá odhlášení Traktu. Vzor: [core.domain.resume.VideoResumeStore] (kotlinx JSON → String,
 * reaktivní [StateFlow], tolerantní `load`).
 *
 * **Forward-compat merge:** uložené (validní) řady zůstávají v uživatelově pořadí, nové default řady
 * z novější OTA se doplní na konec. „Skrýt" = `enabled=false` (řada zůstává, aby se dala vrátit);
 * hard [removeRow] jen pro vlastní (custom_*) řady. Neznámá enum hodnota → default (`coerceInputValues`);
 * poškozená jednotlivá řada se přeskočí (per-element decode), zbytek přežije.
 */
@Singleton
class HomeLayoutStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tv_home_layout", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * COUCH per-profil — domov (řady/sidebar/immersive/seen) je klíčovaný per AKTIVNÍ profil. Klíč nese
     * prefix `p<id>_`; při chybějícím per-profil klíči padá read na GLOBÁLNÍ klíč (bezešvá migrace — dospělý
     * zdědí stávající layout, deti začne od stejného defaultu). core-domain nesmí vidět ProfileRepository
     * (obrácená závislost), proto profil přepíná [switchProfile] volané z TvHomeViewModel (feature vrstva).
     */
    private var activeId: Long? = null

    private fun keyFor(base: String): String = activeId?.let { "p${it}_$base" } ?: base

    /** Přepni na layout daného profilu — přenačte všechny toky. Idempotentní (stejný profil = no-op). */
    fun switchProfile(id: Long?) {
        if (id == activeId && switched) return
        activeId = id
        switched = true
        _rows.value = loadRows()
        _sidebar.value = loadSidebar()
        _immersiveBackground.value = prefs.getBoolean(keyFor(KEY_IMMERSIVE), prefs.getBoolean(KEY_IMMERSIVE, true))
        _immersiveHeader.value = prefs.getBoolean(keyFor(KEY_IMMERSIVE_HEADER), prefs.getBoolean(KEY_IMMERSIVE_HEADER, true))
        _immersiveHeaderLines.value = prefs.getInt(keyFor(KEY_IMMERSIVE_HEADER_LINES), prefs.getInt(KEY_IMMERSIVE_HEADER_LINES, 0))
    }
    private var switched = false

    private val _rows = MutableStateFlow(loadRows())
    /** Všechny řady (i vypnuté) v pořadí; konzument si vyfiltruje `enabled`. */
    val rows: StateFlow<List<HomeRowConfig>> = _rows.asStateFlow()

    private val _sidebar = MutableStateFlow(loadSidebar())
    val sidebar: StateFlow<List<SidebarEntry>> = _sidebar.asStateFlow()

    private val _immersiveBackground = MutableStateFlow(prefs.getBoolean(KEY_IMMERSIVE, true))
    /** Netflix-like immersive pozadí (fokusovaná karta řídí fanart) na Domů/Objevovat/Knihovna. */
    val immersiveBackground: StateFlow<Boolean> = _immersiveBackground.asStateFlow()

    fun setImmersiveBackground(enabled: Boolean) {
        _immersiveBackground.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_IMMERSIVE), enabled).apply()
    }

    private val _immersiveHeader = MutableStateFlow(prefs.getBoolean(KEY_IMMERSIVE_HEADER, true))
    /** OTA 299: immersive hlavička nahoře (název + rok + popis fokusované karty = „netflix styl") — oddělená
     * od pozadí, aby šla zapnout/vypnout zvlášť. */
    val immersiveHeader: StateFlow<Boolean> = _immersiveHeader.asStateFlow()

    fun setImmersiveHeader(enabled: Boolean) {
        _immersiveHeader.value = enabled
        prefs.edit().putBoolean(keyFor(KEY_IMMERSIVE_HEADER), enabled).apply()
    }

    private val _immersiveHeaderLines = MutableStateFlow(prefs.getInt(KEY_IMMERSIVE_HEADER_LINES, 0))
    /** CONVERGE (SHW-97): počet řádků popisu v immersive hlavičce. 0 = AUTO (dopočítá se z dostupné výšky,
     * ať se nic neuřízne pod obsahem ani při jiné velikosti UI/písma); 1..N = pevný počet řádků. */
    val immersiveHeaderLines: StateFlow<Int> = _immersiveHeaderLines.asStateFlow()

    fun setImmersiveHeaderLines(lines: Int) {
        _immersiveHeaderLines.value = lines
        prefs.edit().putInt(keyFor(KEY_IMMERSIVE_HEADER_LINES), lines).apply()
    }

    // ── Řady ──────────────────────────────────────────────────────────────────

    /** Posun řady o jedno místo (nahoru = dřív). No-op na kraji. */
    fun move(id: String, up: Boolean) {
        _rows.update { list ->
            // Přesun mezi VIDITELNÝMI (zapnutými) řadami — cíl je sousední ZAPNUTÁ řada, ne libovolná
            // sousední v plném seznamu (jinak by se řada „prohodila" se skrytou = uživateli se nic nezmění).
            val enabled = list.filter { it.enabled }
            val ei = enabled.indexOfFirst { it.id == id }
            if (ei < 0) return@update list
            val ej = if (up) ei - 1 else ei + 1
            if (ej < 0 || ej >= enabled.size) return@update list
            val i = list.indexOfFirst { it.id == enabled[ei].id }
            val j = list.indexOfFirst { it.id == enabled[ej].id }
            if (i < 0 || j < 0) return@update list
            list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        }
        persistRows()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _rows.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        persistRows()
    }

    /** Přepiš konfiguraci řady (styl/řazení/filtr/titulek). */
    fun updateRow(config: HomeRowConfig) {
        _rows.update { list -> list.map { if (it.id == config.id) config else it } }
        persistRows()
    }

    /** Přidej vlastní řadu na konec (id musí být unikátní). */
    fun addRow(config: HomeRowConfig) {
        _rows.update { list -> if (list.any { it.id == config.id }) list else list + config }
        persistRows()
    }

    /**
     * Hromadně přidej řady (import z Jellyfin serveru). Řady s již existujícím id se PŘESKOČÍ (uživatelovo
     * nastavení má přednost), nové se doplní na konec v pořadí importu. Idempotentní — opakovaný import nepřidá duplikáty.
     */
    fun addRows(configs: List<HomeRowConfig>) {
        if (configs.isEmpty()) return
        _rows.update { list ->
            val existing = list.map { it.id }.toMutableSet()
            list + configs.filter { existing.add(it.id) }
        }
        persistRows()
    }

    /** Odeber řadu. Default řadu to jen skryje sémanticky ne — vrátí se při dalším merge; pro
     *  default používej [setEnabled]. Míněno pro vlastní `custom_*` řady. */
    fun removeRow(id: String) {
        _rows.update { list -> list.filterNot { it.id == id } }
        persistRows()
    }

    /** Obnovit výchozí sadu řad. Vyčistí i „seen" knihovny → při dalším [syncLibraries] se naseedují znovu. */
    fun resetRows() {
        _rows.value = DEFAULT_ROWS
        prefs.edit().remove(keyFor(KEY_SEEN_LIBS)).apply()
        persistRows()
    }

    /**
     * Seed-once per Jellyfin knihovna. Pro každou knihovnu, kterou jsme ještě NEVIDĚLI, přidá první-třídní
     * [HomeRowSourceType.JELLYFIN_LIBRARY] řadu (enabled, default styl dle collectionType) a označí ji jako
     * viděnou. Existující řady zachovají uživatelovo nastavení (styl/pořadí/enabled); knihovny, které user
     * skryl nebo smazal, se NEVRACÍ (jsou v „seen"). Voláno z UI po načtení seznamu knihoven profilu.
     */
    fun syncLibraries(libraries: List<LibrarySummary>) {
        if (libraries.isEmpty()) return
        val seen = prefs.getStringSet(keyFor(KEY_SEEN_LIBS), emptySet()).orEmpty()
        val existingLibIds = _rows.value.mapNotNull { it.params[HomeRowParams.LIBRARY_ID] }.toSet()
        val toAdd = libraries
            .filter { it.id !in seen && it.id !in existingLibIds }
            .map { lib ->
                HomeRowConfig(
                    id = "lib_${lib.id}",
                    source = HomeRowSourceType.JELLYFIN_LIBRARY,
                    title = lib.name,
                    cardStyle = defaultLibraryStyle(lib.collectionType),
                    params = mapOf(
                        HomeRowParams.LIBRARY_ID to lib.id,
                        HomeRowParams.COLLECTION_TYPE to (lib.collectionType ?: ""),
                    ),
                )
            }
        // Vždy označ VŠECHNY aktuální knihovny jako viděné (i ty už přítomné) → idempotentní.
        prefs.edit().putStringSet(keyFor(KEY_SEEN_LIBS), seen + libraries.map { it.id }).apply()
        if (toAdd.isEmpty()) return
        _rows.update { list -> list + toAdd }
        persistRows()
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    fun moveSidebar(item: String, up: Boolean) {
        _sidebar.update { list ->
            val i = list.indexOfFirst { it.item == item }
            if (i < 0) return@update list
            val j = if (up) i - 1 else i + 1
            if (j < 0 || j >= list.size) return@update list
            list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        }
        persistSidebar()
    }

    fun setSidebarEnabled(item: String, enabled: Boolean) {
        _sidebar.update { list -> list.map { if (it.item == item) it.copy(enabled = enabled) else it } }
        persistSidebar()
    }

    // ── Perzistence ─────────────────────────────────────────────────────────────

    private fun persistRows() {
        prefs.edit().putString(keyFor(KEY_ROWS), json.encodeToString(_rows.value)).apply()
    }

    private fun persistSidebar() {
        prefs.edit().putString(keyFor(KEY_SIDEBAR), json.encodeToString(_sidebar.value)).apply()
    }

    private fun loadRows(): List<HomeRowConfig> {
        // Per-profil klíč; fallback na GLOBÁLNÍ (bezešvá migrace stávajícího layoutu na první profil).
        val raw = prefs.getString(keyFor(KEY_ROWS), null) ?: prefs.getString(KEY_ROWS, null)
        val stored = decodeList(raw) { el ->
            json.decodeFromJsonElement<HomeRowConfig>(el)
        }
            // Migrace ≤293: starý meta zdroj zahoď — nahradí ho seed JELLYFIN_LIBRARY řad ([syncLibraries]).
            .filterNot { it.source == HomeRowSourceType.JELLYFIN_LIBRARIES }
            // WEATHER (user 2026-07-16): odstraň Trakt DOPORUČENÍ/OBJEVOVÁNÍ řady i ze STARÝCH uložených
            // layoutů (rozbité migrací Traktu na V3 + dětem nevhodné; user chce jen NAŠE + watchlist/historii).
            .filterNot { it.id in DEPRECATED_ROW_IDS }
        if (stored.isEmpty()) return DEFAULT_ROWS
        // Merge: uložené v pořadí + nové default řady (podle id) na konec.
        val storedIds = stored.map { it.id }.toSet()
        return stored + DEFAULT_ROWS.filter { it.id !in storedIds }
    }

    private fun loadSidebar(): List<SidebarEntry> {
        val raw = prefs.getString(keyFor(KEY_SIDEBAR), null) ?: prefs.getString(KEY_SIDEBAR, null)
        val stored = decodeList(raw) { el ->
            json.decodeFromJsonElement<SidebarEntry>(el)
        }.filter { SidebarItem.fromName(it.item) != null }
        if (stored.isEmpty()) return SidebarItem.DEFAULT
        val storedItems = stored.map { it.item }.toSet()
        return stored + SidebarItem.DEFAULT.filter { it.item !in storedItems }
    }

    /** Per-element tolerantní decode: poškozená položka se přeskočí, ne celý seznam. */
    private fun <T> decodeList(raw: String?, decode: (kotlinx.serialization.json.JsonElement) -> T): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { runCatching { decode(it) }.getOrNull() }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val KEY_ROWS = "rows_json"
        private const val KEY_SIDEBAR = "sidebar_json"
        private const val KEY_IMMERSIVE = "immersive_bg"
        private const val KEY_IMMERSIVE_HEADER = "immersive_header"
        private const val KEY_IMMERSIVE_HEADER_LINES = "immersive_header_lines"
        private const val KEY_SEEN_LIBS = "seen_library_ids"

        // WEATHER (user 2026-07-16): Trakt doporučovací/objevovací řady vyřazené z domova — strippnou se
        // i z uložených layoutů ([loadRows]). Trakt je migrací na V3 rozbil (401) + ukazovaly dětem
        // nevhodný obsah. Zůstává jen NÁŠ kurátor (brain_for_you) + Chci vidět + Historie.
        private val DEPRECATED_ROW_IDS = setOf(
            "couchmonkey_reco", "trakt_reco_movies", "trakt_reco_shows",
            "weighted_reco", "trending_movies", "popular_shows",
        )

        /** Default styl karty pro řadu knihovny dle collectionType. Konzistentní = plakát (žádné
         *  nahodilé landscape jako v ≤293); user přepíše v editoru. */
        fun defaultLibraryStyle(collectionType: String?): HomeCardStyle = HomeCardStyle.POSTER

        /** Výchozí domov: vzdušná Kodi-like sada. Obsah hned nahoře (Pokračovat), pak Trakt.
         *  JF knihovny se přidávají dynamicky per knihovna ([syncLibraries]) — ne natvrdo zde. */
        val DEFAULT_ROWS: List<HomeRowConfig> = listOf(
            HomeRowConfig(
                id = "continue",
                source = HomeRowSourceType.CONTINUE_WATCHING,
                title = "Pokračovat ve sledování",
                cardStyle = HomeCardStyle.LANDSCAPE,
                // KOLO2 (M): z výroby jen první řada má immersive hlavičku zapnutou.
                immersiveHeader = true,
            ),
            HomeRowConfig(
                id = "next_up",
                source = HomeRowSourceType.NEXT_UP,
                title = "Další díly",
                cardStyle = HomeCardStyle.LANDSCAPE,
            ),
            // AUTEUR (SHW-91): kurátorský mozek „Pro tebe" (LLM z vkusu Trakt+Favorites → TMDB) = NAŠE
            // doporučení. Zapnuto z výroby; merge v loadRows doplní i stávajícím uživatelům (nový id → append).
            HomeRowConfig(
                id = "brain_for_you",
                source = HomeRowSourceType.BRAIN_FOR_YOU,
                title = "Pro tebe (kurátor)",
                cardStyle = HomeCardStyle.POSTER,
            ),
            // WEATHER (user 2026-07-16): z Traktu na Domů si necháváme JEN watchlist („Chci vidět") a historii.
            // VŠECHNA Trakt DOPORUČENÍ/OBJEVOVÁNÍ pryč — jednak je Trakt migrací na V3 rozbil (401), jednak
            // ukazovala dětem nevhodný obsah (Trendy: horory) a user chce jen NAŠE (kurátor). Odstraněné řady
            // (couchmonkey_reco/trakt_reco_movies/trakt_reco_shows/weighted_reco/trending_movies/popular_shows)
            // se navíc strippnou i ze STARÝCH uložených layoutů — viz DEPRECATED_ROW_IDS ve [loadRows].
            HomeRowConfig(
                id = "trakt_watchlist",
                source = HomeRowSourceType.TRAKT_WATCHLIST,
                title = "Chci vidět",
                cardStyle = HomeCardStyle.POSTER,
                params = mapOf(HomeRowParams.WATCHLIST_KIND to "all"),
            ),
            HomeRowConfig(
                id = "trakt_history",
                source = HomeRowSourceType.TRAKT_HISTORY,
                title = "Historie",
                cardStyle = HomeCardStyle.POSTER,
                params = mapOf(HomeRowParams.WATCHLIST_KIND to "all"),
            ),
            HomeRowConfig(
                id = "favorites",
                source = HomeRowSourceType.FAVORITES,
                title = "Oblíbené",
                cardStyle = HomeCardStyle.POSTER,
            ),
            // Zapamatované zdroje — když prázdné, render řadu vynechá (buildList jen neprázdné).
            HomeRowConfig(
                id = "saved_for_playback",
                source = HomeRowSourceType.SAVED_FOR_PLAYBACK,
                title = "Uloženo k přehrání",
                cardStyle = HomeCardStyle.POSTER,
            ),
        )
    }
}

/** Lehký souhrn Jellyfin knihovny pro [HomeLayoutStore.syncLibraries] (bez závislosti na feature vrstvě). */
data class LibrarySummary(
    val id: String,
    val name: String,
    val collectionType: String?,
)
