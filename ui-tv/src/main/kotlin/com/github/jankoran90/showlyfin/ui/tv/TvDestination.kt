package com.github.jankoran90.showlyfin.ui.tv

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery

/**
 * TENFOOT (SHW-87) — ruční stavová navigace TV shellu (paritní s telefonním `Destination`, NE
 * Navigation-Compose). Malý sealed interface; back stack drží [com.github.jankoran90.showlyfin.ui.tv.nav.TvNavigator].
 *
 * Smyčka „najdi → vyber zdroj → přehraj": Home → Detail → Player. Home/Search/Settings/Watchlist i
 * Detail jsou nativní TV obrazovky; telefonní obrazovky přežívají už jen jako fallbacky bez tmdb/imdb.
 */
sealed interface TvDestination {

    /** Domovská „Sleduj" mřížka (feature-discover). Kořen stacku. */
    data object Home : TvDestination

    /** Nativní TV Hledání (sdílí telefonní `SearchViewModel`, výsledky = plakátová mřížka). */
    data object Search : TvDestination

    /** Nativní TV Nastavení (10-foot bloky, D-pad steppery nad sdílenými prefs VM). */
    data object Settings : TvDestination

    /** Oblíbené (Trakt watchlist, plakátová mřížka nad sdíleným `WatchlistViewModel`). */
    data object Watchlist : TvDestination

    /** Karta obsahu — nativní immersive `TvDetailScreen` (fanart hero). */
    data class Detail(val item: MediaItem) : TvDestination

    /**
     * Přehrávač (reuse `feature-playback/PlaybackScreen`, který už umí TV D-pad beze změny).
     * Mapuje callbacky detailu: `onPlayStreamUrl` → externalUrl, `onPlayJellyfin` → itemId.
     */
    data class Player(
        val itemId: String? = null,
        val externalUrl: String? = null,
        val externalTitle: String = "",
        val subtitleQuery: SubtitleQuery? = null,
        val externalPosterUrl: String? = null,
    ) : TvDestination

    /**
     * Mřížka položek Jellyfin knihovny. Zanoření (složka/BOX_SET) = další `LibraryItems`
     * na stacku s `parentItemType`; BACK popuje (drží [com.github.jankoran90.showlyfin.ui.tv.nav.TvNavigator]).
     */
    data class LibraryItems(
        val libraryId: String,
        val libraryName: String,
        val collectionType: String? = null,
        val parentItemType: String? = null,
    ) : TvDestination

    /** Jellyfin detail z jellyfinId — resolver dohledá meta → nativní immersive; telefonní `JellyfinDetailScreen` jen fallback bez tmdb/imdb. */
    data class JellyfinDetail(val itemId: String) : TvDestination

    /** Výběr epizod seriálu — legacy fallback (reuse telefonní `EpisodePickerScreen`; seriál s tmdb jde immersit). */
    data class EpisodePicker(val seriesId: String, val seriesName: String) : TvDestination
}
