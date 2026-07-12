package com.github.jankoran90.showlyfin.ui.tv.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.ui.tv.TvDestination
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeScreen

/**
 * TENFOOT (SHW-87) — ruční stavová navigace TV shellu (stejné paradigma jako telefonní
 * `ShowlyfinPhoneApp`: back stack + `when`, ne Navigation-Compose). BACK popuje stack; na kořeni
 * (Home) systémový BACK propadne (ukončí appku).
 *
 * Fáze 1 smyčka: Home → Detail (reuse phone `DetailScreen` — jeho výběr zdroje je díky
 * `AdaptivePickerScaffold`/vc278 už D-pad-adaptivní) → Player (`PlaybackScreen`, TV D-pad hotový).
 */
@Composable
fun TvNavigator() {
    val backStack = remember { mutableStateListOf<TvDestination>(TvDestination.Home) }
    val current = backStack.last()

    fun navigate(dest: TvDestination) { backStack.add(dest) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { back() }

    when (val dest = current) {
        TvDestination.Home -> TvHomeScreen(
            onOpenDetail = { item -> navigate(TvDestination.Detail(item)) },
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
