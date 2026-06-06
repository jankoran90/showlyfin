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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilter
import com.github.jankoran90.showlyfin.feature.discover.DiscoverTab
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDiscoverScreen(
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Discover",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 64.dp, end = 64.dp, top = 32.dp),
            )
            Spacer(Modifier.height(12.dp))

            // Tabs: Filmy / Seriály
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvChip("Filmy", uiState.activeTab == DiscoverTab.MOVIES) {
                    viewModel.selectTab(DiscoverTab.MOVIES)
                }
                TvChip("Seriály", uiState.activeTab == DiscoverTab.SHOWS) {
                    viewModel.selectTab(DiscoverTab.SHOWS)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Filters: Trending / Popular / Anticipated / Recommended
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvChip("Trending", uiState.activeFilter == DiscoverFilter.TRENDING) {
                    viewModel.selectFilter(DiscoverFilter.TRENDING)
                }
                TvChip("Populární", uiState.activeFilter == DiscoverFilter.POPULAR) {
                    viewModel.selectFilter(DiscoverFilter.POPULAR)
                }
                TvChip("Očekávané", uiState.activeFilter == DiscoverFilter.ANTICIPATED) {
                    viewModel.selectFilter(DiscoverFilter.ANTICIPATED)
                }
                TvChip("Doporučené", uiState.activeFilter == DiscoverFilter.RECOMMENDED) {
                    viewModel.selectFilter(DiscoverFilter.RECOMMENDED)
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    uiState.error != null -> Text(
                        text = uiState.error!!,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    uiState.items.isEmpty() -> Text(
                        text = "Žádné výsledky",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(start = 64.dp, end = 64.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(uiState.items, key = { "${it.type}_${it.traktId}" }) { item ->
                            TvDiscoverCard(item = item, onClick = { onItemClick(item) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick) {
        Text(if (selected) "✓ $label" else label)
    }
}
