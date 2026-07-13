package com.github.jankoran90.showlyfin.ui.tv.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.EpisodePickerScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.ui.tv.TvDestination
import com.github.jankoran90.showlyfin.ui.tv.TvShell
import com.github.jankoran90.showlyfin.ui.tv.jellyfin.TvJellyfinBrowserScreen
import com.github.jankoran90.showlyfin.ui.tv.jellyfin.TvJellyfinDetailRoute
import com.github.jankoran90.showlyfin.ui.tv.search.TvSearchScreen
import com.github.jankoran90.showlyfin.ui.tv.settings.TvSettingsScreen
import com.github.jankoran90.showlyfin.ui.tv.watchlist.TvWatchlistScreen

/**
 * TENFOOT (SHW-87) — ruční stavová navigace TV shellu (stejné paradigma jako telefonní
 * `ShowlyfinPhoneApp`: back stack + `when`, ne Navigation-Compose). BACK popuje stack; na kořeni
 * (Home) systémový BACK propadne (ukončí appku).
 *
 * Smyčka: Home → Detail (nativní immersive `TvDetailScreen`; výběr zdroje D-pad-adaptivní přes
 * `AdaptivePickerScaffold`) → Player (`PlaybackScreen`, TV D-pad hotový).
 */
@Composable
fun TvNavigator(navVm: TvNavViewModel = viewModel()) {
    val current = navVm.current

    fun navigate(dest: TvDestination) = navVm.navigate(dest)
    fun back() = navVm.back()

    // TENFOOT KOLO2 (N5): proklik karty v sekci detailu (kolekce / od režiséra / studia / tvorba osoby).
    // Sdílené immersive Detailem i sjednoceným Jellyfin routem. tmdbId → nativní immersive detail (stub);
    // jinak jellyfinId → JellyfinDetail (resolver dořeší i správný typ SHOW/MOVIE a immersive vzhled).
    fun openCollectionPart(part: CollectionPart) {
        val tmdb = part.tmdbId
        val jfId = part.jellyfinId
        when {
            tmdb != null -> navigate(
                TvDestination.Detail(
                    MediaItem(
                        traktId = 0L,
                        tmdbId = tmdb,
                        imdbId = null,
                        title = part.title,
                        year = part.year?.toIntOrNull(),
                        overview = null,
                        rating = null,
                        genres = null,
                        type = MediaType.MOVIE,
                    ),
                ),
            )
            jfId != null -> navigate(TvDestination.JellyfinDetail(jfId))
        }
    }

    BackHandler(enabled = navVm.canGoBack) { back() }

    when (val dest = current) {
        TvDestination.Home -> TvShell(
            section = navVm.section,
            onSelectSection = { navVm.selectSection(it) },
            onOpenSearch = { navigate(TvDestination.Search) },
            onOpenDetail = { item -> navigate(TvDestination.Detail(item)) },
            onOpenJellyfinDetail = { itemId -> navigate(TvDestination.JellyfinDetail(itemId)) },
            onOpenLibrary = { id, name, collectionType ->
                navigate(TvDestination.LibraryItems(id, name, collectionType))
            },
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
            // Položka s tmdbId (film i seriál) → nativní TV immersive detail (fanart hero, sdílená s doporučovačem).
            onOpenRich = { item -> navigate(TvDestination.Detail(item)) },
            // Bez tmdbId → resolver z jellyfinId (imdb→immersive, jinak fallback telefonní JellyfinDetail).
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

        // TENFOOT KOLO2 (N3): Jellyfin obsah → nativní immersive detail (resolver z jellyfinId dohledá
        // meta a deleguje na DetailScreen; telefonní JellyfinDetailScreen jen fallback bez tmdb/imdb).
        is TvDestination.JellyfinDetail -> TvJellyfinDetailRoute(
            itemId = dest.itemId,
            onBack = { back() },
            onCollectionPartClick = { part -> openCollectionPart(part) },
            onPlayJellyfin = { itemId -> navigate(TvDestination.Player(itemId = itemId)) },
            onPlayStreamUrl = { url, title, subtitleQuery ->
                navigate(
                    TvDestination.Player(
                        externalUrl = url,
                        externalTitle = title,
                        subtitleQuery = subtitleQuery,
                    ),
                )
            },
            onOpenEpisodes = { seriesId, name -> navigate(TvDestination.EpisodePicker(seriesId, name)) },
            onOpenJellyfinDetail = { itemId -> navigate(TvDestination.JellyfinDetail(itemId)) },
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
            // TENFOOT KOLO2 (N5): karty v sekcích detailu → tmdbId má přednost (nativní immersive detail),
            // jinak jellyfinId přes resolver. Sdílená logika s Jellyfin routem (openCollectionPart).
            onCollectionPartClick = { part -> openCollectionPart(part) },
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
