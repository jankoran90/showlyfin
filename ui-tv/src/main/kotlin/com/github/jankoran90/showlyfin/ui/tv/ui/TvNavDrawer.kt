package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.github.jankoran90.showlyfin.ui.tv.TvDestination

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavDrawer(
    selected: TvDestination,
    onNavigateHome: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenJellyfin: () -> Unit,
    onFilterMovies: () -> Unit,
    onFilterSeries: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isHome = selected is TvDestination.Home
    val isDiscover = selected is TvDestination.Discover
    val isWatchlist = selected is TvDestination.Watchlist
    val isJellyfin = selected is TvDestination.JellyfinBrowse || selected is TvDestination.JellyfinLibrary
    val isMovies = selected is TvDestination.HomeFiltered && selected.mediaType.name == "MOVIE"
    val isSeries = selected is TvDestination.HomeFiltered && selected.mediaType.name == "SERIES"
    val isSettings = selected is TvDestination.Settings

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = { _ ->
            Column(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E))
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NavigationDrawerItem(
                    selected = isHome,
                    onClick = onNavigateHome,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Domů",
                            tint = if (isHome) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Domů", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = isDiscover,
                    onClick = onOpenDiscover,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Discover",
                            tint = if (isDiscover) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Discover", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = isWatchlist,
                    onClick = onOpenWatchlist,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Watchlist",
                            tint = if (isWatchlist) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Watchlist", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = isJellyfin,
                    onClick = onOpenJellyfin,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Knihovna",
                            tint = if (isJellyfin) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Knihovna", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = isMovies,
                    onClick = onFilterMovies,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Filmy",
                            tint = if (isMovies) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Filmy", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = isSeries,
                    onClick = onFilterSeries,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Seriály",
                            tint = if (isSeries) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Seriály", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = isSettings,
                    onClick = onOpenSettings,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Nastavení",
                            tint = if (isSettings) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Nastavení", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    ) {
        content()
    }
}
