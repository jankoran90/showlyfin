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
import com.github.jankoran90.showlyfin.ui.tv.ui.TvNavDrawer

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
            is TvDestination.Home -> {
                TvNavDrawer(
                    onNavigateHome = { currentDestination = TvDestination.Home },
                ) {
                    TvHomeScreen(
                        onItemClick = { itemId ->
                            currentDestination = TvDestination.Playback(itemId)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0D0D1A)),
                        viewModel = viewModel,
                    )
                }
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
