package com.github.jankoran90.showlyfin.ui.tv.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.EpisodePickerScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinDetailScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.ui.tv.TvDestination
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeScreen
import com.github.jankoran90.showlyfin.ui.tv.jellyfin.TvJellyfinBrowserScreen
import com.github.jankoran90.showlyfin.ui.tv.search.TvSearchScreen
import com.github.jankoran90.showlyfin.ui.tv.settings.TvSettingsScreen
import com.github.jankoran90.showlyfin.ui.tv.watchlist.TvWatchlistScreen

/**
 * TENFOOT (SHW-87) — ruční stavová navigace TV shellu (stejné paradigma jako telefonní
 * `ShowlyfinPhoneApp`: back stack + `when`, ne Navigation-Compose). BACK popuje stack; na kořeni
 * (Home) systémový BACK propadne (ukončí appku).
 *
 * Fáze 1 smyčka: Home → Detail (reuse phone `DetailScreen` — jeho výběr zdroje je díky
 * `AdaptivePickerScaffold`/vc278 už D-pad-adaptivní) → Player (`PlaybackScreen`, TV D-pad hotový).
 */
@Composable
fun TvNavigator(navVm: TvNavViewModel = viewModel()) {
    val current = navVm.current

    fun navigate(dest: TvDestination) = navVm.navigate(dest)
    fun back() = navVm.back()

    BackHandler(enabled = navVm.canGoBack) { back() }

    when (val dest = current) {
        TvDestination.Home -> TvHomeScreen(
            onOpenDetail = { item -> navigate(TvDestination.Detail(item)) },
            onOpenLibrary = { id, name, collectionType ->
                navigate(TvDestination.LibraryItems(id, name, collectionType))
            },
            onOpenSearch = { navigate(TvDestination.Search) },
            onOpenSettings = { navigate(TvDestination.Settings) },
            onOpenWatchlist = { navigate(TvDestination.Watchlist) },
        )

        TvDestination.Search -> TvSearchScreen(
            onOpenDetail = { item -> navigate(TvDestination.Detail(item)) },
            onBack = { back() },
        )

        TvDestination.Settings -> TvSettingsScreen(onBack = { back() })

        TvDestination.Watchlist -> TvWatchlistScreen(
            onOpenDetail = { item -> navigate(TvDestination.Detail(item)) },
            onBack = { back() },
        )

        is TvDestination.LibraryItems -> TvJellyfinBrowserScreen(
            libraryId = dest.libraryId,
            libraryName = dest.libraryName,
            collectionType = dest.collectionType,
            parentItemType = dest.parentItemType,
            // Bohatý film s tmdbId → nativní TV karta obsahu (fanart hero, sdílená s doporučovačem).
            onOpenRich = { item -> navigate(TvDestination.Detail(item)) },
            // Bez tmdb / seriál → Jellyfin detail (reuse telefonní obrazovky).
            onOpenJellyfinDetail = { itemId -> navigate(TvDestination.JellyfinDetail(itemId)) },
            // Složka/BOX_SET → zanoření (nová mřížka, BACK popuje).
            onDrillIn = { itemId, itemName, itemType ->
                navigate(
                    TvDestination.LibraryItems(
                        libraryId = itemId,
                        libraryName = itemName,
                        collectionType = null,
                        parentItemType = itemType,
                    ),
                )
            },
        )

        is TvDestination.JellyfinDetail -> JellyfinDetailScreen(
            itemId = dest.itemId,
            onBack = { back() },
            onPlay = { itemId -> navigate(TvDestination.Player(itemId = itemId)) },
            onOpenEpisodes = { seriesId, name -> navigate(TvDestination.EpisodePicker(seriesId, name)) },
            onCollectionPartClick = { part ->
                part.jellyfinId?.let { navigate(TvDestination.JellyfinDetail(it)) }
            },
        )

        is TvDestination.EpisodePicker -> EpisodePickerScreen(
            seriesId = dest.seriesId,
            seriesName = dest.seriesName,
            onBack = { back() },
            onPlayEpisode = { epId -> navigate(TvDestination.Player(itemId = epId)) },
        )

        is TvDestination.Detail -> DetailScreen(
            item = dest.item,
            onBack = { back() },
            // Fáze 1: po výběru zdroje / přehrání z knihovny navazujeme na TV přehrávač.
            onPlayStreamUrl = { url, title, subtitleQuery ->
                navigate(
                    TvDestination.Player(
                        externalUrl = url,
                        externalTitle = title,
                        subtitleQuery = subtitleQuery,
                    ),
                )
            },
            onPlayJellyfin = { jellyfinId -> navigate(TvDestination.Player(itemId = jellyfinId)) },
        )

        is TvDestination.Player -> PlaybackScreen(
            itemId = dest.itemId ?: "",
            externalUrl = dest.externalUrl,
            externalTitle = dest.externalTitle,
            subtitleQuery = dest.subtitleQuery,
            externalPosterUrl = dest.externalPosterUrl,
            onBack = { back() },
        )
    }
}
