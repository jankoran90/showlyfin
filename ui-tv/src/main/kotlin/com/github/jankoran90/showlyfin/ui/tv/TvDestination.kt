package com.github.jankoran90.showlyfin.ui.tv

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery

/**
 * TENFOOT (SHW-87) — ruční stavová navigace TV shellu (paritní s telefonním `Destination`, NE
 * Navigation-Compose). Malý sealed interface; back stack drží [com.github.jankoran90.showlyfin.ui.tv.nav.TvNavigator].
 *
 * Fáze 1 = smyčka „najdi → vyber zdroj → přehraj": Home → Detail (dočasně reuse phone DetailScreen)
 * → Player. Detail/Jellyfin-native + Search/Settings přibývají ve fázích 2–3.
 */
sealed interface TvDestination {

    /** Domovská „Sleduj" mřížka (feature-discover). Kořen stacku. */
    data object Home : TvDestination

    /** Fáze 3 — nativní TV Hledání (sdílí telefonní `SearchViewModel`, výsledky = plakátová mřížka). */
    data object Search : TvDestination

    /** Fáze 3 — nativní TV Nastavení (10-foot bloky, D-pad steppery nad sdílenými prefs VM). */
    data object Settings : TvDestination

    /** Karta obsahu. Fáze 1 reuse telefonní `DetailScreen`, Fáze 2 nativní `TvDetailScreen`. */
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
     * Fáze 2 — mřížka položek Jellyfin knihovny. Zanoření (složka/BOX_SET) = další `LibraryItems`
     * na stacku s `parentItemType`; BACK popuje (drží [com.github.jankoran90.showlyfin.ui.tv.nav.TvNavigator]).
     */
    data class LibraryItems(
        val libraryId: String,
        val libraryName: String,
        val collectionType: String? = null,
        val parentItemType: String? = null,
    ) : TvDestination

    /** Fáze 2 — Jellyfin detail (reuse telefonní `JellyfinDetailScreen`; nativní TV detail = Fáze 3). */
    data class JellyfinDetail(val itemId: String) : TvDestination

    /** Fáze 2 — výběr epizod seriálu (reuse telefonní `EpisodePickerScreen`). */
    data class EpisodePicker(val seriesId: String, val seriesName: String) : TvDestination
}
