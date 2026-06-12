package com.github.jankoran90.showlyfin.feature.watchlist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.SectionBar
import com.github.jankoran90.showlyfin.core.ui.SectionSortChip
import com.github.jankoran90.showlyfin.core.ui.rememberScrollHeaderVisibility
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
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
        // Hlavička (Filmy/Seriály + hledání + filtry) se skrývá při scrollu dolů — stejně jako v Objevit.
        val headerVisible by rememberScrollHeaderVisibility(
            { gridState.firstVisibleItemIndex },
            { gridState.firstVisibleItemScrollOffset },
        )
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        ) {
            val showChips = uiState.isLoggedIn &&
                (uiState.items.isNotEmpty() || uiState.genreFilter != null || uiState.rdOnly)
            SectionBar(
                segments = listOf("Filmy", "Seriály"),
                selectedSegment = uiState.activeTab.ordinal,
                onSegmentSelected = { viewModel.selectTab(WatchlistTab.entries[it]) },
                searchQuery = if (uiState.isLoggedIn) uiState.searchQuery else null,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                searchPlaceholder = "Hledat (česky i originál)…",
                chips = if (showChips) {
                    {
                        watchlistChips(
                            sort = uiState.sort,
                            genreFilter = uiState.genreFilter,
                            availableGenres = uiState.availableGenres,
                            rdOnly = uiState.rdOnly,
                            rdMatchLoading = uiState.rdMatchLoading,
                            onSortSelected = { viewModel.selectSort(it) },
                            onGenreSelected = { viewModel.selectGenre(it) },
                            onRdOnlyToggle = { viewModel.toggleRdOnly() },
                        )
                    }
                } else null,
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
            uiState.items.isEmpty() && uiState.genreFilter == null && uiState.searchQuery.isBlank() -> {
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
            uiState.items.isEmpty() -> {
                val msg = if (uiState.searchQuery.isNotBlank()) {
                    "Nic nenalezeno pro „${uiState.searchQuery}\""
                } else {
                    "Žádné položky pro filtr „${uiState.genreFilter}\""
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = msg,
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

/** Chipy Chci vidět vykreslené do sdílené [SectionBar] lišty (RD · řazení · žánry). */
private fun LazyListScope.watchlistChips(
    sort: WatchlistSort,
    genreFilter: String?,
    availableGenres: List<String>,
    rdOnly: Boolean,
    rdMatchLoading: Boolean,
    onSortSelected: (WatchlistSort) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onRdOnlyToggle: () -> Unit,
) {
    item {
        FilterChip(
            selected = rdOnly,
            onClick = onRdOnlyToggle,
            modifier = Modifier.tvFocusable(),
            label = { Text("💾 Na RD") },
            leadingIcon = if (rdMatchLoading) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else null,
        )
    }
    item {
        SectionSortChip(
            label = if (sort == WatchlistSort.DEFAULT) "Řazení" else "Řazení: ${sort.label}",
            options = WatchlistSort.entries.map { it to it.label },
            selected = sort,
            onSelect = onSortSelected,
        )
    }
    item {
        FilterChip(
            selected = genreFilter == null,
            onClick = { onGenreSelected(null) },
            modifier = Modifier.tvFocusable(),
            label = { Text("Vše") },
        )
    }
    items(availableGenres) { genre ->
        FilterChip(
            selected = genreFilter == genre,
            onClick = { onGenreSelected(if (genreFilter == genre) null else genre) },
            modifier = Modifier.tvFocusable(),
            label = { Text(genre) },
        )
    }
}
