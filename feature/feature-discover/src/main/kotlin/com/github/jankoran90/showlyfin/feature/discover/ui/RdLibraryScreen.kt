package com.github.jankoran90.showlyfin.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
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
import com.github.jankoran90.showlyfin.core.ui.LandscapeCard
import com.github.jankoran90.showlyfin.core.ui.LandscapeDetailCard
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.core.ui.SectionBar
import com.github.jankoran90.showlyfin.core.ui.SectionSortChip
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.RdLibraryViewModel
import com.github.jankoran90.showlyfin.feature.discover.RdSort

/**
 * Podsekce „Na RD" v sekci Hlavní — uložené filmy na RealDebrid (TMDB-matchnuté).
 * Klik na kartu otevře bohatý Detail (přes tmdbId). Plan QUASAR Fáze D.
 * VANTAGE/SWEEP: parita s ostatními sekcemi — sjednocená lišta (hledání + mřížka/seznam + řazení).
 */
@Composable
fun RdLibraryScreen(
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RdLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        SectionBar(
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            searchPlaceholder = "Hledat na RD…",
            viewMode = viewMode,
            onSelectViewMode = viewModel::setViewMode,
            chips = {
                item {
                    SectionSortChip(
                        label = "Řazení: ${uiState.sortBy.label}",
                        options = RdSort.entries.map { it to it.label },
                        selected = uiState.sortBy,
                        onSelect = viewModel::setSort,
                    )
                }
            },
        )

        Box(Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.error != null -> Text(
                    text = uiState.error!!,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                uiState.items.isEmpty() -> Text(
                    text = "Na RealDebrid zatím nejsou žádné rozpoznané filmy.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    val items = uiState.displayedItems
                    if (items.isEmpty()) {
                        Text(
                            text = "Nic neodpovídá hledání „${uiState.searchQuery}\".",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (viewMode == ViewMode.LIST || viewMode == ViewMode.LANDSCAPE_DETAIL) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(items, key = { "rd_${it.tmdbId}" }) { item ->
                                if (viewMode == ViewMode.LANDSCAPE_DETAIL) {
                                    LandscapeDetailCard(item = item, onClick = { onItemClick(item) })
                                } else {
                                    MediaRow(item = item, onClick = { onItemClick(item) })
                                }
                            }
                        }
                    } else {
                        val colPref = rememberGridColumnPref()
                        LazyVerticalGrid(
                            columns = gridCellsFor(viewMode, colPref),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items, key = { "rd_${it.tmdbId}" }) { item ->
                                if (viewMode == ViewMode.LANDSCAPE) {
                                    LandscapeCard(item = item, onClick = { onItemClick(item) })
                                } else {
                                    MediaCard(item = item, onClick = { onItemClick(item) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
