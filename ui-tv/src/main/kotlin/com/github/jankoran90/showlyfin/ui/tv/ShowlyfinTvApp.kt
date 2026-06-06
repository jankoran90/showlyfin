package com.github.jankoran90.showlyfin.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ProfileGateViewModel
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.ui.tv.setup.TvProfilePickerScreen
import com.github.jankoran90.showlyfin.ui.tv.setup.TvServerSetupScreen
import com.github.jankoran90.showlyfin.ui.tv.theme.ShowlyfinTvTheme
import com.github.jankoran90.showlyfin.ui.tv.ui.TvDetailScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvDiscoverScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvHomeScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvJellyfinBrowseScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvJellyfinItemsScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvNavDrawer
import com.github.jankoran90.showlyfin.ui.tv.ui.TvSettingsScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvWatchlistScreen
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShowlyfinTvApp(
    viewModel: TvHomeViewModel = hiltViewModel(),
) {
    val gateViewModel: ProfileGateViewModel = hiltViewModel()
    val gateState by gateViewModel.state.collectAsStateWithLifecycle()

    if (gateState.isLoading) {
        Box(Modifier.fillMaxSize().background(Color(0xFF07071A)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (gateState.isAddingProfile || gateState.profiles.isEmpty()) {
        ShowlyfinTvTheme {
            TvServerSetupScreen(
                onDone = {
                    gateViewModel.cancelAddProfile()
                    viewModel.reload()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    if (gateState.activeProfile == null) {
        ShowlyfinTvTheme {
            TvProfilePickerScreen(
                profiles = gateState.profiles,
                onProfileSelected = {
                    gateViewModel.selectProfile(it)
                    viewModel.reload()
                },
                onAddProfile = { gateViewModel.startAddProfile() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    var currentDestination by remember { mutableStateOf<TvDestination>(TvDestination.Home) }

    LaunchedEffect(Unit) {
        viewModel.playEvents.collect { event ->
            currentDestination = TvDestination.Playback(event.itemId, event.positionMs)
        }
    }

    ShowlyfinTvTheme {
        when (val dest = currentDestination) {
            is TvDestination.Home, is TvDestination.HomeFiltered, is TvDestination.Settings,
            is TvDestination.Discover, is TvDestination.Watchlist, is TvDestination.JellyfinBrowse -> {
                TvNavDrawer(
                    selected = dest,
                    onNavigateHome = {
                        viewModel.setFilter(null)
                        currentDestination = TvDestination.Home
                    },
                    onOpenDiscover = { currentDestination = TvDestination.Discover },
                    onOpenWatchlist = { currentDestination = TvDestination.Watchlist },
                    onOpenJellyfin = { currentDestination = TvDestination.JellyfinBrowse },
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
                        is TvDestination.Discover -> TvDiscoverScreen(
                            onItemClick = { mediaItem ->
                                currentDestination = TvDestination.Detail(mediaItem)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        is TvDestination.Watchlist -> TvWatchlistScreen(
                            onItemClick = { mediaItem ->
                                currentDestination = TvDestination.Detail(mediaItem)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        is TvDestination.JellyfinBrowse -> TvJellyfinBrowseScreen(
                            onLibraryClick = { lib ->
                                currentDestination = TvDestination.JellyfinLibrary(
                                    libraryId = lib.id,
                                    libraryName = lib.name,
                                    collectionType = lib.collectionType,
                                )
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
                                .background(Color(0xFF07071A)),
                            viewModel = viewModel,
                        )
                    }
                }
            }
            is TvDestination.JellyfinLibrary -> {
                TvJellyfinItemsScreen(
                    libraryId = dest.libraryId,
                    libraryName = dest.libraryName,
                    collectionType = dest.collectionType,
                    parentItemType = dest.parentItemType,
                    onDrillIn = { item ->
                        currentDestination = TvDestination.JellyfinLibrary(
                            libraryId = item.id,
                            libraryName = item.name,
                            parentItemType = "BOX_SET",
                            parent = dest,
                        )
                    },
                    onPlay = { itemId -> currentDestination = TvDestination.Playback(itemId) },
                    onBack = { currentDestination = dest.parent },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is TvDestination.Detail -> {
                TvDetailScreen(
                    item = dest.item,
                    onPlayJellyfin = { itemId ->
                        currentDestination = TvDestination.Playback(itemId)
                    },
                    onBack = { currentDestination = TvDestination.Discover },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is TvDestination.Setup -> {
                TvServerSetupScreen(
                    onDone = {
                        viewModel.reload()
                        currentDestination = TvDestination.Home
                    },
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
