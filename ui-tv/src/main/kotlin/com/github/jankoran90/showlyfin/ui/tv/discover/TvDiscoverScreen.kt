package com.github.jankoran90.showlyfin.ui.tv.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilter
import com.github.jankoran90.showlyfin.feature.discover.DiscoverTab
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo

/**
 * TENFOOT (SHW-87) — sekce „Objevovat". Vrací 10-foot browse plochu ztracenou v redesignu 293: taby
 * Filmy/Seriály + kategorie chipy (Doporučené/Trendy/Populární/Očekávané) nad sdíleným [DiscoverViewModel]
 * + plakátová mřížka ([TvMediaCard]). Fokusovaná karta hlásí [onFocusItem] nahoru (immersive pozadí).
 */
@Composable
fun TvDiscoverScreen(
    onOpenDetail: (MediaItem) -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Nekonečné dotahování Objevit (Trakt page) při dojetí ke konci mřížky.
    LaunchedEffect(gridState, state.items.size, state.canLoadMore) {
        snapshotFlowLastVisible(gridState) { last ->
            if (last >= state.items.size - 8 && state.canLoadMore && !state.isLoadingMore) viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Text(
            text = "Objevovat",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            DiscoverTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.activeTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = { Text(tab.czLabel()) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            DiscoverFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.activeFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
                    label = { Text(filter.czLabel()) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error == "needs_trakt_login" -> Centered {
                Text(
                    "Doporučené vyžadují přihlášení k Traktu (Nastavení → Účty).",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            state.items.isEmpty() -> Centered { Text("Nic k zobrazení", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(state.items, key = { _, item -> "${item.type}_${item.traktId}_${item.tmdbId}" }) { _, item ->
                    Box(Modifier.onFocusChanged { if (it.hasFocus && immersive) onFocusItem(item.toImmersiveInfo()) }) {
                        TvMediaCard(item = item, onClick = { onOpenDetail(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun DiscoverTab.czLabel() = if (this == DiscoverTab.SHOWS) "Seriály" else "Filmy"

private fun DiscoverFilter.czLabel() = when (this) {
    DiscoverFilter.RECOMMENDED -> "Doporučené"
    DiscoverFilter.TRENDING -> "Trendy"
    DiscoverFilter.POPULAR -> "Populární"
    DiscoverFilter.ANTICIPATED -> "Očekávané"
}

/** Malý most: sleduj poslední viditelný index mřížky (paging trigger). */
private suspend fun snapshotFlowLastVisible(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onChange: (Int) -> Unit,
) {
    androidx.compose.runtime.snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .collect { onChange(it) }
}
