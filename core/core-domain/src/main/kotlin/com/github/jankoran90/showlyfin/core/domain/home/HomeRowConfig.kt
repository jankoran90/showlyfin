package com.github.jankoran90.showlyfin.core.domain.home

import kotlinx.serialization.Serializable

/**
 * TENFOOT — TV DOMOV REDESIGN (Kodi Arctic Fuse styl). Konfigurace JEDNÉ řady na domově.
 *
 * Uživatelská personalizace (NE admin restrikce — ta je v [core.data.ProfileConfig]). Ukládá
 * [HomeLayoutStore] jako JSON seznam. Schéma je záměrně ploché + volný [params] map → forward-compat:
 * nový parametr v OTA nerozbije starý uložený layout (`ignoreUnknownKeys`), neznámá enum hodnota
 * spadne na default (`coerceInputValues`).
 */
@Serializable
data class HomeRowConfig(
    /** Stabilní id (default řady = pevné, vlastní = "custom_<n>"). Klíč pro reorder/merge. */
    val id: String,
    val source: HomeRowSourceType = HomeRowSourceType.DISCOVER,
    /** Titulek nad řadou (uživatel může přejmenovat). Prázdné = použij [HomeRowSourceType.label]/params. */
    val title: String = "",
    val cardStyle: HomeCardStyle = HomeCardStyle.POSTER,
    val sort: HomeRowSort = HomeRowSort.DEFAULT,
    /** Kolik položek načíst (strop, výkon). NENÍ počet zobrazených karet — viz [displayLimit]. */
    val limit: Int = 30,
    /**
     * FOYER (SHW-107, user 2026-07-26): kolik karet řada REÁLNĚ UKÁŽE na domově; zbytek je za dlaždicí
     * „Zobrazit vše" ([showAll]). Oddělené od [limit] (strop načtení) schválně — drill potřebuje celý
     * načtený seznam, řada jen prvních pár. Přidané pole → staré uložené layouty dostanou default 5.
     */
    val displayLimit: Int = 5,
    /**
     * FOYER (SHW-107): dlaždice „Zobrazit vše" / „Celá filmotéka" na konci řady → drill do plné mřížky
     * (nebo do odpovídající sekce, viz UI). Kreslí se jen když má řada víc položek, než ukazuje.
     */
    val showAll: Boolean = true,
    val enabled: Boolean = true,
    /**
     * Popisek pod/na kartě. Immersive Netflix styl skrývá popisky (řídí editor / globální přepínač).
     * Default true = parita se stávajícím chováním starých uložených layoutů (přidané pole, `encodeDefaults`).
     */
    val showTitles: Boolean = true,
    /**
     * KOLO2 (M): immersive hlavička (název/rok/popis fokusované karty nahoře, Netflix styl) PER ŘADA.
     * Default false = vypnuto (jen řady, které to mají explicitně zapnuté — z výroby první řada). Přidané
     * pole → staré uložené layouty dostanou false (`encodeDefaults`, `ignoreUnknownKeys`).
     */
    val immersiveHeader: Boolean = false,
    /** Volné parametry zdroje: viz [HomeRowParams] (tab/filter/watchlistKind/hideWatched/genre/libraryId/collectionId). */
    val params: Map<String, String> = emptyMap(),
) {
    /** Řešený titulek řady: uživatelský přepis, jinak štítek zdroje (knihovní/kolekční řady si titulek nesou v [title]). */
    fun resolvedTitle(): String = title.ifBlank { source.label }
}

/** Odkud řada bere obsah. Zdroj, který daný [HomeRowSort] neumí, řazení ignoruje. */
@Serializable
enum class HomeRowSourceType(val label: String) {
    /** Jellyfin serverové rozkoukané položky (getResumeItems). */
    CONTINUE_WATCHING("Pokračovat ve sledování"),
    /** Jellyfin další nezhlédnuté epizody napříč seriály (getNextUp bez seriesId). */
    NEXT_UP("Další díly"),
    /** Jellyfin sloučené Pokračovat + Další díly (resume ∪ nextUp, řazeno dle posledního přehrání). */
    CONTINUE_WATCHING_COMBINED("Pokračovat + Další díly"),
    /** Trakt kategorie (viz [HomeRowParams.TAB] + [HomeRowParams.FILTER]). */
    DISCOVER("Objevovat (Trakt)"),
    /** Oblíbené z per-profil [data.uploader.FavoritesStore] (TOTÉŽ co telefonní „Oblíbené", parita). */
    FAVORITES("Oblíbené"),

    /**
     * FOYER (SHW-107, user 2026-07-26): NEDÁVNO PŘIDANÉ z celé Filmotéky — tatáž báze, jakou ukazuje sekce
     * Filmotéka (Jellyfin knihovna ∪ zapamatované zdroje ∪ Trakt „Chci vidět" ∪ Oblíbené, dedup + věkový
     * gate), seřazená podle data přidání. NEPLÉST s [RECENTLY_ADDED] = jen `getLatestMedia` JEDNÉ Jellyfin
     * knihovny. Dlaždice „Zobrazit vše" u téhle řady vede rovnou do sekce Filmotéka („Celá filmotéka").
     */
    FILMOTEKA_RECENT("Filmotéka — nedávno přidané"),
    /**
     * NOVÝ zdroj: tituly se zapamatovaným zdrojem přehrávání z [data.uploader.WorkingSourceStore]
     * („uloženo k přehrání"). Klik = přímé přehrání bez hledání zdroje. Samostatná sekce místo cpaní do oblíbených.
     */
    SAVED_FOR_PLAYBACK("Uloženo k přehrání"),

    /** COUCH T1: Trakt watchlist (uložené k zhlédnutí). Volitelně [HomeRowParams.WATCHLIST_KIND]. OAuth. */
    TRAKT_WATCHLIST("Watchlist (Trakt)"),
    /** COUCH T1: Trakt historie sledování (watched). Volitelně [HomeRowParams.WATCHLIST_KIND]. OAuth. */
    TRAKT_HISTORY("Zhlédnuto (Trakt)"),
    /** COUCH T1: konkrétní Trakt seznam uživatele (viz [HomeRowParams.LIST_ID] = trakt id listu). OAuth. */
    TRAKT_LIST("Trakt seznam"),
    /**
     * COUCH T2: sloučená „Doporučeno" z couchmonkey — všechny userovy Trakt listy s „couchmonkey" v názvu,
     * sjednocené + dedup, mínus viděné ∪ hodnocené ∪ watchlist. Self-contained (bez params). OAuth.
     */
    COUCHMONKEY_RECOMMENDATIONS("Doporučeno"),

    /**
     * COUCH (SHW-88): play-count vážená doporučení „na míru dle sledování" — z nejvíc přehrávaných
     * titulů (Trakt `plays`) → TMDB recommendations, vážené počtem přehrání, mínus co už mám. OAuth.
     */
    WEIGHTED_RECOMMENDATIONS("Na míru (podle sledování)"),

    /**
     * AUTEUR (SHW-91): kurátorský mozek „Pro tebe" — vkus (Trakt watched+ratings+watchlist ∪ Favorites)
     * pošle na backend `/curator/recommend` (LLM → resolve na TMDB). Prázdné/nedostupné → fallback na
     * [WEIGHTED_RECOMMENDATIONS]. OAuth (potřebuje Trakt vkus).
     */
    BRAIN_FOR_YOU("Pro tebe (kurátor)"),

    /** JEDNA konkrétní Jellyfin knihovna (viz [HomeRowParams.LIBRARY_ID]). První-třídní řada:
     *  vlastní enabled/pořadí/styl per knihovna. Seed-once z profilu (viz [HomeLayoutStore.syncLibraries]). */
    JELLYFIN_LIBRARY("Jellyfin knihovna"),
    /** „Nejnovější v <knihovna>" — Jellyfin getLatestMedia pro konkrétní knihovnu (viz [HomeRowParams.LIBRARY_ID]). */
    RECENTLY_ADDED("Nejnovější v knihovně"),
    /** Dlaždice knihoven (navigace do knihovny), yellyfin LibraryTiles vzor. */
    LIBRARY_TILES("Dlaždice knihoven"),
    /** Libovolná Jellyfin kolekce / playlist (viz [HomeRowParams.COLLECTION_ID], getItems ByParent). */
    COLLECTION("Kolekce"),
    /** Dlaždice žánrů konkrétní knihovny (viz [HomeRowParams.LIBRARY_ID]). Yellyfin vzor — 2. vlna editoru. */
    GENRES("Žánry"),
    /** Dlaždice studií konkrétní knihovny (viz [HomeRowParams.LIBRARY_ID]). Yellyfin vzor — 2. vlna editoru. */
    STUDIOS("Studia"),

    /**
     * DEPRECATED meta zdroj (OTA ≤293): expandoval na jednu řadu per Jellyfin knihovna se SDÍLENÝM
     * stylem → nahodile landscape + žádná per-knihovna správa. Ponechán JEN kvůli forward-compat decode
     * starých uložených layoutů; z [HomeLayoutStore.DEFAULT_ROWS] vyřazen a při loadu se nahradí
     * seedem [JELLYFIN_LIBRARY] řad. Nová verze ho už nikdy negeneruje.
     */
    JELLYFIN_LIBRARIES("Jellyfin knihovny"),
}

/** Styl karet v řadě. Oddělené od `feature.jellyfin.ViewMode` (jeho detail-list se do railu nehodí). */
@Serializable
enum class HomeCardStyle(val label: String) {
    /** Plakát 2:3 s názvem pod kartou. */
    POSTER("Plakát"),
    /** Čistý plakát 2:3, název jen ve scrimu (vzdušnější mřížka). */
    COVER("Jen obal"),
    /** Fanart 16:9 se scrimem + progress (Netflix/Kodi styl). */
    LANDSCAPE("Fanart"),
    /** Řádek: malý plakát vlevo + název/rok/ČSFD vpravo (kompaktní seznam). */
    LIST("Seznam"),
    /** Fanart 16:9 + název/rok/popis vedle (širší karta s textem). */
    FANART_DETAIL("Fanart + popis"),
}

/** Řazení řady. Ne každý zdroj umí každé (DEFAULT = pořadí z API/serveru). */
@Serializable
enum class HomeRowSort(val label: String) {
    DEFAULT("Výchozí"),
    RECENT("Nejnovější"),
    RATING("Hodnocení"),
    YEAR_DESC("Rok (nejnovější)"),
    ALPHA("Abecedně"),
    RANDOM("Náhodně"),
}

/** Klíče do [HomeRowConfig.params]. Stringové → forward-compat bez schema breaku. */
object HomeRowParams {
    /** DISCOVER: "movies" | "shows". */
    const val TAB = "tab"
    /** DISCOVER: "trending" | "popular" | "anticipated" | "recommended". */
    const val FILTER = "filter"
    /** TRAKT_WATCHLIST / TRAKT_HISTORY: "movies" | "shows" | "all". */
    const val WATCHLIST_KIND = "watchlistKind"
    /** TRAKT_LIST: trakt id konkrétního seznamu (Long jako string). */
    const val LIST_ID = "listId"
    /** Bool ("true"): skryj zhlédnuté položky. */
    const val HIDE_WATCHED = "hideWatched"
    /** Volitelný žánrový filtr (klientský). */
    const val GENRE = "genre"
    /** JELLYFIN_LIBRARY / RECENTLY_ADDED / GENRES / STUDIOS: id konkrétní Jellyfin knihovny (userView). */
    const val LIBRARY_ID = "libraryId"
    /** JELLYFIN_LIBRARY: collectionType knihovny ("movies"/"tvshows"/…) — určuje default styl karty. */
    const val COLLECTION_TYPE = "collectionType"
    /** COLLECTION: id konkrétní Jellyfin kolekce/playlistu (BoxSet/Playlist parentId). */
    const val COLLECTION_ID = "collectionId"

    fun Map<String, String>.boolParam(key: String): Boolean = this[key] == "true"
}
