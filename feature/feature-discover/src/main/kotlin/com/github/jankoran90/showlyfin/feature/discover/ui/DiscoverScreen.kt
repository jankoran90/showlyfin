package com.github.jankoran90.showlyfin.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilter
import com.github.jankoran90.showlyfin.feature.discover.DiscoverTab
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel

@Composable
fun DiscoverScreen(
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
            DiscoverTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.activeTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(if (tab == DiscoverTab.MOVIES) "Filmy" else "Seriály") },
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DiscoverFilter.entries) { filter ->
                FilterChip(
                    selected = uiState.activeFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
                    label = {
                        Text(
                            when (filter) {
                                DiscoverFilter.TRENDING -> "Trending"
                                DiscoverFilter.POPULAR -> "Populární"
                                DiscoverFilter.ANTICIPATED -> "Očekávané"
                            }
                        )
                    },
                )
            }
        }

        if (uiState.isLoading && uiState.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null && uiState.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "Chyba",
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.items, key = { "${it.type}_${it.traktId}" }) { item ->
                    MediaCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}
