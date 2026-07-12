package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilter
import com.github.jankoran90.showlyfin.feature.discover.DiscoverTab
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.jellyfin.TvLibrariesContent

/** Sekce TV Home: doporučovač Trakt (Sleduj) vs. Jellyfin knihovny (Knihovna). */
private enum class TvHomeSection(val label: String) {
    WATCH("Sleduj"),
    LIBRARY("Knihovna"),
}

/**
 * TENFOOT (SHW-87) — TV domov. Nahoře rail **Sleduj | Knihovna** (odložený vstupní bod z Fáze 1),
 * pod ním obsah dle sekce:
 *  - Sleduj = mřížka doporučovače nad sdíleným [DiscoverViewModel] (D-pad, chipy tab/filtr),
 *  - Knihovna = mřížka Jellyfin knihoven ([TvLibrariesContent]); klik na knihovnu → mřížka položek.
 */
@Composable
fun TvHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by rememberSaveable { mutableStateOf(TvHomeSection.WATCH) }

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            TvHomeSection.entries.forEach { s ->
                FilterChip(
                    selected = section == s,
                    onClick = { section = s },
                    label = {
                        Text(
                            s.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (section == s) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }

        when (section) {
            TvHomeSection.WATCH -> WatchGrid(
                onOpenDetail = onOpenDetail,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            TvHomeSection.LIBRARY -> TvLibrariesContent(
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WatchGrid(
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (ui.canLoadMore && lastVisible >= ui.items.size - 8) viewModel.loadMore()
            }
    }

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiscoverTab.entries.forEach { tab ->
                FilterChip(
                    selected = ui.activeTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = { Text(tabLabel(tab)) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        ) {
            DiscoverFilter.entries.forEach { filter ->
                FilterChip(
                    selected = ui.activeFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
                    label = { Text(filterLabel(filter)) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(ui.items, key = { it.traktId }) { item ->
                TvMediaCard(item = item, onClick = { onOpenDetail(item) })
            }
        }
    }
}

private fun tabLabel(tab: DiscoverTab): String = when (tab) {
    DiscoverTab.MOVIES -> "Filmy"
    DiscoverTab.SHOWS -> "Seriály"
}

private fun filterLabel(filter: DiscoverFilter): String = when (filter) {
    DiscoverFilter.RECOMMENDED -> "Doporučené"
    DiscoverFilter.TRENDING -> "Trendy"
    DiscoverFilter.POPULAR -> "Populární"
    DiscoverFilter.ANTICIPATED -> "Očekávané"
}
