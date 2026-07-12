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
    /** Titulek nad řadou (uživatel může přejmenovat). */
    val title: String = "",
    val cardStyle: HomeCardStyle = HomeCardStyle.POSTER,
    val sort: HomeRowSort = HomeRowSort.DEFAULT,
    /** Kolik položek načíst (strop, výkon). */
    val limit: Int = 30,
    val enabled: Boolean = true,
    /** Volné parametry zdroje: viz [HomeRowParams] (tab/filter/watchlistKind/hideWatched/genre/libraryId). */
    val params: Map<String, String> = emptyMap(),
)

/** Odkud řada bere obsah. Zdroj, který daný [HomeRowSort] neumí, řazení ignoruje. */
@Serializable
enum class HomeRowSourceType(val label: String) {
    /** Jellyfin serverové rozkoukané položky (getResumeItems). */
    CONTINUE_WATCHING("Pokračovat ve sledování"),
    /** Jellyfin další nezhlédnuté epizody napříč seriály (getNextUp bez seriesId). */
    NEXT_UP("Další díly"),
    /** Trakt kategorie (viz [HomeRowParams.TAB] + [HomeRowParams.FILTER]). */
    DISCOVER("Objevovat (Trakt)"),
    /** Oblíbené z per-profil [data.uploader.FavoritesStore] (TOTÉŽ co telefonní „Oblíbené", parita). */
    FAVORITES("Oblíbené"),
    /** Meta: expanduje na jednu řadu per Jellyfin knihovna (reuse LibraryRowsViewModel). */
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
    /** WATCHLIST: "movies" | "shows" | "all". */
    const val WATCHLIST_KIND = "watchlistKind"
    /** Bool ("true"): skryj zhlédnuté položky. */
    const val HIDE_WATCHED = "hideWatched"
    /** Volitelný žánrový filtr (klientský). */
    const val GENRE = "genre"

    fun Map<String, String>.boolParam(key: String): Boolean = this[key] == "true"
}
