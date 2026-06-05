package com.github.jankoran90.showlyfin.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvHomeScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvJellyfinSetupScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvNavDrawer
import com.github.jankoran90.showlyfin.ui.tv.ui.TvSettingsScreen
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShowlyfinTvApp(
    viewModel: TvHomeViewModel = hiltViewModel(),
) {
    var currentDestination by remember { mutableStateOf<TvDestination>(TvDestination.Home) }

    LaunchedEffect(Unit) {
        viewModel.playEvents.collect { event ->
            currentDestination = TvDestination.Playback(event.itemId, event.positionMs)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        when (val dest = currentDestination) {
            is TvDestination.Home, is TvDestination.HomeFiltered, is TvDestination.Settings -> {
                TvNavDrawer(
                    selected = dest,
                    onNavigateHome = {
                        viewModel.setFilter(null)
                        currentDestination = TvDestination.Home
                    },
                    onFilterMovies = {
                        viewModel.setFilter(BaseItemKind.MOVIE)
                        currentDestination = TvDestination.HomeFiltered(BaseItemKind.MOVIE)
                    },
                    onFilterSeries = {
                        viewModel.setFilter(BaseItemKind.SERIES)
                        currentDestination = TvDestination.HomeFiltered(BaseItemKind.SERIES)
                    },
                    onOpenSettings = { currentDestination = TvDestination.Settings },
                ) {
                    when (dest) {
                        is TvDestination.Settings -> TvSettingsScreen(
                            onChangeServer = { currentDestination = TvDestination.Setup },
                            onBack = { currentDestination = TvDestination.Home },
                            onDisconnected = {
                                viewModel.reload()
                                currentDestination = TvDestination.Home
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> TvHomeScreen(
                            onItemClick = { itemId ->
                                currentDestination = TvDestination.Playback(itemId)
                            },
                            onOpenSetup = { currentDestination = TvDestination.Setup },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D0D1A)),
                            viewModel = viewModel,
                        )
                    }
                }
            }
            is TvDestination.Setup -> {
                TvJellyfinSetupScreen(
                    onConnected = {
                        viewModel.reload()
                        currentDestination = TvDestination.Home
                    },
                    onBack = { currentDestination = TvDestination.Home },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is TvDestination.Playback -> {
                PlaybackScreen(
                    itemId = dest.itemId,
                    positionMs = dest.positionMs,
                    onBack = { currentDestination = TvDestination.Home },
                )
            }
        }
    }
}
