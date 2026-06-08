package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistSort
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistTab
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvWatchlistScreen(
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Watchlist",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 64.dp, end = 64.dp, top = 32.dp),
            )
            Spacer(Modifier.height(12.dp))

            // Tabs
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvChip("Filmy", uiState.activeTab == WatchlistTab.MOVIES) {
                    viewModel.selectTab(WatchlistTab.MOVIES)
                }
                TvChip("Seriály", uiState.activeTab == WatchlistTab.SHOWS) {
                    viewModel.selectTab(WatchlistTab.SHOWS)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Sort + 💾 Na RD
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvChip(if (uiState.rdMatchLoading) "💾 Na RD…" else "💾 Na RD", uiState.rdOnly) {
                    viewModel.toggleRdOnly()
                }
                WatchlistSort.entries.forEach { sort ->
                    TvChip(sort.label, uiState.sort == sort) { viewModel.selectSort(sort) }
                }
            }
            // Žánry
            if (uiState.availableGenres.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvChip("Vše", uiState.genreFilter == null) { viewModel.selectGenre(null) }
                    uiState.availableGenres.forEach { genre ->
                        TvChip(genre, uiState.genreFilter == genre) {
                            viewModel.selectGenre(if (uiState.genreFilter == genre) null else genre)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    !uiState.isLoggedIn -> Text(
                        text = "Pro Watchlist se přihlas přes Trakt v Nastavení",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    uiState.items.isEmpty() -> Text(
                        text = "Watchlist je prázdný",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(start = 64.dp, end = 64.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(uiState.items, key = { "${it.type}_${it.traktId}" }) { item ->
                            val inLib = (item.imdbId != null && item.imdbId in uiState.ownedImdbIds) ||
                                (item.tmdbId != null && uiState.tmdbToJellyfin.containsKey(item.tmdbId))
                            val watched = (item.imdbId?.let { it in uiState.watchedImdbIds } ?: false) ||
                                (item.tmdbId?.let { it in uiState.watchedTmdbIds } ?: false) ||
                                item.traktId in uiState.watchedTraktIds
                            TvDiscoverCard(
                                item = item,
                                onClick = { onItemClick(item) },
                                progress = uiState.progressMap[item.traktId]?.fraction,
                                inLibrary = inLib,
                                watched = watched,
                            )
                        }
                    }
                }
            }
        }
    }
}
