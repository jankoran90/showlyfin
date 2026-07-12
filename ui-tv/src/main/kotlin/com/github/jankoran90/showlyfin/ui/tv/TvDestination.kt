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
}
