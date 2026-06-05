package com.github.jankoran90.showlyfin.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.discover.ui.DiscoverScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinBrowserScreen
import com.github.jankoran90.showlyfin.feature.watchlist.ui.WatchlistScreen

private sealed interface Destination {
    data object Discover : Destination
    data object Watchlist : Destination
    data object Jellyfin : Destination
    data object Settings : Destination
    data class Detail(val item: MediaItem) : Destination
}

@Composable
fun ShowlyfinPhoneApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var currentDestination by remember { mutableStateOf<Destination>(Destination.Discover) }
        var bottomTab by remember { mutableStateOf<Destination>(Destination.Discover) }

        BackHandler(enabled = currentDestination is Destination.Detail) {
            currentDestination = bottomTab
        }

        val isDetail = currentDestination is Destination.Detail

        Scaffold(
            containerColor = Color(0xFF0D0D1A),
            bottomBar = {
                if (!isDetail) {
                    NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                        NavigationBarItem(
                            selected = bottomTab is Destination.Discover,
                            onClick = {
                                bottomTab = Destination.Discover
                                currentDestination = Destination.Discover
                            },
                            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            label = { Text("Discover") },
                        )
                        NavigationBarItem(
                            selected = bottomTab is Destination.Watchlist,
                            onClick = {
                                bottomTab = Destination.Watchlist
                                currentDestination = Destination.Watchlist
                            },
                            icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                            label = { Text("Watchlist") },
                        )
                        NavigationBarItem(
                            selected = bottomTab is Destination.Jellyfin,
                            onClick = {
                                bottomTab = Destination.Jellyfin
                                currentDestination = Destination.Jellyfin
                            },
                            icon = { Icon(Icons.Default.Tv, contentDescription = null) },
                            label = { Text("Jellyfin") },
                        )
                        NavigationBarItem(
                            selected = bottomTab is Destination.Settings,
                            onClick = {
                                bottomTab = Destination.Settings
                                currentDestination = Destination.Settings
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Nastavení") },
                        )
                    }
                }
            },
        ) { paddingValues ->
            when (val dest = currentDestination) {
                is Destination.Discover -> DiscoverScreen(
                    onItemClick = { item ->
                        bottomTab = Destination.Discover
                        currentDestination = Destination.Detail(item)
                    },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Watchlist -> WatchlistScreen(
                    onItemClick = { item ->
                        bottomTab = Destination.Watchlist
                        currentDestination = Destination.Detail(item)
                    },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Jellyfin -> JellyfinBrowserScreen(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Settings -> SettingsScreen(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Detail -> DetailScreen(
                    item = dest.item,
                    onBack = { currentDestination = bottomTab },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
