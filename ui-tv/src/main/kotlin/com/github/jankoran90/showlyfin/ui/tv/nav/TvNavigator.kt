package com.github.jankoran90.showlyfin.ui.tv.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    // CONVERGE (SHW-97): sidebar jako PŘEKRYV nad detailem — D-pad doleva z akcí detailu ho zobrazí (detail
    // zůstane vzadu), výběr sekce skočí do shellu, doprava/Back/scrim schová. Nahrazuje dřívější přímý skok do
    // Nastavení. Reset při jakékoli navigaci/zpět, ať nezůstane viset nad jinou destinací.
    var sidebarOverlay by remember { mutableStateOf(false) }

    fun navigate(dest: TvDestination) { sidebarOverlay = false; navVm.navigate(dest) }
    fun back() { sidebarOverlay = false; navVm.back() }
    fun openSidebarOverlay() { sidebarOverlay = true }
    fun selectSectionAndHome(section: TvSection) { navVm.selectSection(section); navVm.goHome(); sidebarOverlay = false }

    // BESPOKE F4: notifikace „nová doporučení" (`showlyfin://foryou`) → přepni na sekci „Pro tebe".
    val openForYouSignal by com.github.jankoran90.showlyfin.core.ui.ListenNavSignal.openForYou.collectAsStateWithLifecycle()
    LaunchedEffect(openForYouSignal) {
        if (openForYouSignal > 0) selectSectionAndHome(TvSection.FOR_YOU)
    }

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

    // FILMYCAST — přijímač castu telefon→TV. Pollí serverovou frontu příkazů (jen v popředí); při čekajícím
    // příkazu skočí rovnou do přehrávače s už resolvnutou URL (telefon poslal přehratelný zdroj + titulky/plakát).
    com.github.jankoran90.showlyfin.ui.tv.cast.TvCastReceiver(
        onCast = { cmd ->
            navigate(
                TvDestination.Player(
                    externalUrl = cmd.sourceUrl,
                    externalTitle = cmd.title,
                    subtitleQuery = cmd.subtitleQuery,
                    externalPosterUrl = cmd.posterUrl,
                ),
            )
        },
    )

    BackHandler(enabled = navVm.canGoBack) { back() }

    when (val dest = current) {
        TvDestination.Home -> TvShell(
            section = navVm.section,
            onSelectSection = { navVm.selectSection(it) },
            onOpenSearch = { navigate(TvDestination.Search) },
            onOpenDetail = { item -> navigate(TvDestination.Detail(item)) },
            // LAPIDARY S4b: klik na kartu řady „Uloženo k přehrání" → detail v režimu one-click (autoplay).
            onOpenDetailPlay = { item -> navigate(TvDestination.Detail(item, autoplay = true)) },
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
        is TvDestination.JellyfinDetail -> Box(Modifier.fillMaxSize()) {
            TvJellyfinDetailRoute(
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
            // CONVERGE (SHW-97): doleva z detailu → vysuň sidebar overlay (ne přímý skok do Nastavení).
            onOpenSettings = { openSidebarOverlay() },
            )
            if (sidebarOverlay) {
                TvSidebarOverlay(
                    activeSection = navVm.section,
                    onSelectSection = { selectSectionAndHome(it) },
                    onOpenSearch = { sidebarOverlay = false; navigate(TvDestination.Search) },
                    onDismiss = { sidebarOverlay = false },
                )
            }
        }

        is TvDestination.EpisodePicker -> EpisodePickerScreen(
            seriesId = dest.seriesId,
            seriesName = dest.seriesName,
            onBack = { back() },
            onPlayEpisode = { epId -> navigate(TvDestination.Player(itemId = epId)) },
        )

        is TvDestination.Detail -> Box(Modifier.fillMaxSize()) {
            DetailScreen(
            item = dest.item,
            onBack = { back() },
            // CONVERGE (SHW-97) — D-pad doleva od nejlevější akce v detailu → vysuň sidebar overlay (výběr
            // sekce/Nastavení nad detailem), místo dřívějšího přímého skoku do Nastavení.
            onOpenSettings = { openSidebarOverlay() },
            // LAPIDARY S4b: one-click z řady „Uloženo k přehrání" → přehrát zapamatovaný zdroj rovnou.
            autoplayRemembered = dest.autoplay,
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
            if (sidebarOverlay) {
                TvSidebarOverlay(
                    activeSection = navVm.section,
                    onSelectSection = { selectSectionAndHome(it) },
                    onOpenSearch = { sidebarOverlay = false; navigate(TvDestination.Search) },
                    onDismiss = { sidebarOverlay = false },
                )
            }
        }

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
