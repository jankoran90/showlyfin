package com.github.jankoran90.showlyfin.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.github.jankoran90.showlyfin.ui.tv.ui.TvJellyfinDetailScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvJellyfinItemsScreen
import com.github.jankoran90.showlyfin.ui.tv.ui.TvDrawerEntry
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
    val homeState by viewModel.state.collectAsStateWithLifecycle()

    var moveMode by remember { mutableStateOf(false) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    fun exitMove() {
        moveMode = false
        movingKey = null
    }

    LaunchedEffect(Unit) {
        viewModel.playEvents.collect { event ->
            currentDestination = TvDestination.Playback(event.itemId, event.positionMs)
        }
    }

    // Back: navigace v rámci appky místo ukončení (Playback řeší vlastní BackHandler)
    BackHandler(
        enabled = currentDestination !is TvDestination.Home &&
            currentDestination !is TvDestination.Playback,
    ) {
        if (moveMode) {
            exitMove()
            return@BackHandler
        }
        currentDestination = when (val d = currentDestination) {
            is TvDestination.JellyfinLibrary -> d.parent
            is TvDestination.JellyfinDetail -> d.parent
            is TvDestination.Detail -> TvDestination.Discover
            is TvDestination.Setup -> TvDestination.Settings
            else -> TvDestination.Home
        }
    }

    // --- Data-driven postranní menu: pořadí položek (fixní + připnuté), řaditelné ---
    val pinned = homeState.pinnedLibraries
    val pinnedIds = pinned.map { it.id }.toSet()
    val pinKeys = pinned.map { "pin:${it.id}" }
    val defaultOrder = listOf("home", "discover", "watchlist", "library") + pinKeys +
        listOf("movies", "series", "settings")
    val order = if (homeState.drawerOrder.isEmpty()) {
        defaultOrder
    } else {
        homeState.drawerOrder.filter { it in defaultOrder } +
            defaultOrder.filterNot { it in homeState.drawerOrder }
    }

    // Highlight: detail/knihovna mapujeme na původní sekci
    val drawerSelected: TvDestination = when (val d = currentDestination) {
        is TvDestination.JellyfinDetail -> d.parent
        is TvDestination.Detail -> TvDestination.Discover
        else -> d
    }

    val entries = order.mapNotNull { key ->
        when {
            key == "home" -> TvDrawerEntry(key, "Domů", Icons.Default.Home, drawerSelected is TvDestination.Home) {
                viewModel.setFilter(null)
                currentDestination = TvDestination.Home
            }
            key == "discover" -> TvDrawerEntry(key, "Discover", Icons.Default.Search, drawerSelected is TvDestination.Discover) {
                currentDestination = TvDestination.Discover
            }
            key == "watchlist" -> TvDrawerEntry(key, "Watchlist", Icons.Default.Bookmark, drawerSelected is TvDestination.Watchlist) {
                currentDestination = TvDestination.Watchlist
            }
            key == "library" -> TvDrawerEntry(
                key, "Knihovna", Icons.Default.VideoLibrary,
                drawerSelected is TvDestination.JellyfinBrowse ||
                    (drawerSelected is TvDestination.JellyfinLibrary && drawerSelected.libraryId !in pinnedIds),
            ) { currentDestination = TvDestination.JellyfinBrowse }
            key == "movies" -> TvDrawerEntry(
                key, "Filmy", Icons.Default.Movie,
                drawerSelected is TvDestination.HomeFiltered && drawerSelected.mediaType == BaseItemKind.MOVIE,
            ) {
                viewModel.setFilter(BaseItemKind.MOVIE)
                currentDestination = TvDestination.HomeFiltered(BaseItemKind.MOVIE)
            }
            key == "series" -> TvDrawerEntry(
                key, "Seriály", Icons.Default.Tv,
                drawerSelected is TvDestination.HomeFiltered && drawerSelected.mediaType == BaseItemKind.SERIES,
            ) {
                viewModel.setFilter(BaseItemKind.SERIES)
                currentDestination = TvDestination.HomeFiltered(BaseItemKind.SERIES)
            }
            key == "settings" -> TvDrawerEntry(key, "Nastavení", Icons.Default.Settings, drawerSelected is TvDestination.Settings) {
                currentDestination = TvDestination.Settings
            }
            key.startsWith("pin:") -> pinned.firstOrNull { "pin:${it.id}" == key }?.let { lib ->
                TvDrawerEntry(
                    key, lib.name, Icons.Default.Star,
                    drawerSelected is TvDestination.JellyfinLibrary && drawerSelected.libraryId == lib.id,
                ) {
                    currentDestination = TvDestination.JellyfinLibrary(
                        libraryId = lib.id,
                        libraryName = lib.name,
                        collectionType = lib.collectionType,
                        parent = TvDestination.Home,
                    )
                }
            }
            else -> null
        }
    }

    ShowlyfinTvTheme {
        when (val dest = currentDestination) {
            is TvDestination.Setup -> TvServerSetupScreen(
                onDone = {
                    viewModel.reload()
                    currentDestination = TvDestination.Home
                },
                modifier = Modifier.fillMaxSize(),
            )
            is TvDestination.Playback -> PlaybackScreen(
                itemId = dest.itemId,
                positionMs = dest.positionMs,
                onBack = { currentDestination = TvDestination.Home },
            )
            else -> TvNavDrawer(
                entries = entries,
                moveMode = moveMode,
                movingKey = movingKey,
                onToggleMove = { key ->
                    if (movingKey == key) {
                        exitMove()
                    } else {
                        moveMode = true
                        movingKey = key
                    }
                },
                onMove = { key, up ->
                    val idx = order.indexOf(key)
                    val target = if (up) idx - 1 else idx + 1
                    if (idx >= 0 && target in order.indices) {
                        val newOrder = order.toMutableList().apply { add(target, removeAt(idx)) }
                        viewModel.setDrawerOrder(newOrder)
                    }
                },
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
                        onItemClick = { mediaItem -> currentDestination = TvDestination.Detail(mediaItem) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    is TvDestination.Watchlist -> TvWatchlistScreen(
                        onItemClick = { mediaItem -> currentDestination = TvDestination.Detail(mediaItem) },
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
                    is TvDestination.JellyfinLibrary -> TvJellyfinItemsScreen(
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
                        onPlay = { itemId -> currentDestination = TvDestination.JellyfinDetail(itemId, parent = dest) },
                        onBack = { currentDestination = dest.parent },
                        modifier = Modifier.fillMaxSize(),
                    )
                    is TvDestination.JellyfinDetail -> TvJellyfinDetailScreen(
                        itemId = dest.itemId,
                        onPlay = { itemId -> currentDestination = TvDestination.Playback(itemId) },
                        onBack = { currentDestination = dest.parent },
                        modifier = Modifier.fillMaxSize(),
                    )
                    is TvDestination.Detail -> TvDetailScreen(
                        item = dest.item,
                        onPlayJellyfin = { itemId -> currentDestination = TvDestination.Playback(itemId) },
                        onBack = { currentDestination = TvDestination.Discover },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> TvHomeScreen(
                        onItemClick = { itemId -> currentDestination = TvDestination.JellyfinDetail(itemId, parent = dest) },
                        onOpenSetup = { currentDestination = TvDestination.Setup },
                        onOpenLibrary = { libId, name, ct ->
                            currentDestination = TvDestination.JellyfinLibrary(
                                libraryId = libId,
                                libraryName = name,
                                collectionType = ct,
                                parent = dest,
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF07071A)),
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}
