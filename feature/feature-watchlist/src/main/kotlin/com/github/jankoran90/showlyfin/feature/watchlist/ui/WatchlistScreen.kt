package com.github.jankoran90.showlyfin.feature.watchlist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistSort
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistTab
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistViewModel

@Composable
fun WatchlistScreen(
    onItemClick: (MediaItem, jellyfinId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
            WatchlistTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.activeTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(if (tab == WatchlistTab.MOVIES) "Filmy" else "Seriály") },
                )
            }
        }

        if (uiState.isLoggedIn && (uiState.items.isNotEmpty() || uiState.genreFilter != null)) {
            WatchlistChips(
                sort = uiState.sort,
                genreFilter = uiState.genreFilter,
                availableGenres = uiState.availableGenres,
                onSortSelected = { viewModel.selectSort(it) },
                onGenreSelected = { viewModel.selectGenre(it) },
            )
        }

        when {
            !uiState.isLoggedIn -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Přihlaš se k Traktu\naby se zobrazil watchlist.",
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            uiState.isLoading && uiState.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.items.isEmpty() && uiState.genreFilter == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Watchlist je prázdný",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Přidej filmy a seriály na Discover, ať se ti tu nahromadí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            uiState.items.isEmpty() && uiState.genreFilter != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Žádné položky pro filtr „${uiState.genreFilter}\"",
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { "${it.type}_${it.traktId}" }) { item ->
                        val progressData = uiState.progressMap[item.traktId]
                        val jellyfinId = item.imdbId?.let { uiState.imdbToJellyfin[it] }
                            ?: item.tmdbId?.let { uiState.tmdbToJellyfin[it] }
                        val inLibrary = jellyfinId != null
                            || (item.imdbId?.let { uiState.ownedImdbIds.contains(it) } ?: false)
                        val watched = (item.imdbId?.let { uiState.watchedImdbIds.contains(it) } ?: false)
                            || (item.tmdbId?.let { uiState.watchedTmdbIds.contains(it) } ?: false)
                            || uiState.watchedTraktIds.contains(item.traktId)
                        timber.log.Timber.d("[Watchlist] render '${item.title}' imdb=${item.imdbId} tmdb=${item.tmdbId} → jfId=$jellyfinId inLib=$inLibrary watched=$watched")
                        Column(modifier = Modifier.fillMaxWidth()) {
                            MediaCard(
                                item = item,
                                onClick = { onItemClick(item, jellyfinId) },
                                progress = if (item.type == MediaType.SHOW) progressData?.fraction else null,
                                inLibrary = inLibrary,
                                watched = watched,
                            )
                            if (item.type == MediaType.SHOW && progressData != null) {
                                Text(
                                    text = "${progressData.watchedEpisodes}/${progressData.totalEpisodes} epizod",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistChips(
    sort: WatchlistSort,
    genreFilter: String?,
    availableGenres: List<String>,
    onSortSelected: (WatchlistSort) -> Unit,
    onGenreSelected: (String?) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box {
                FilterChip(
                    selected = sort != WatchlistSort.DEFAULT,
                    onClick = { sortMenuOpen = true },
                    label = { Text(sort.label) },
                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    WatchlistSort.entries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label) },
                            onClick = {
                                onSortSelected(entry)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        item {
            FilterChip(
                selected = genreFilter == null,
                onClick = { onGenreSelected(null) },
                label = { Text("Vše") },
            )
        }
        items(availableGenres) { genre ->
            FilterChip(
                selected = genreFilter == genre,
                onClick = { onGenreSelected(if (genreFilter == genre) null else genre) },
                label = { Text(genre) },
            )
        }
    }
}
