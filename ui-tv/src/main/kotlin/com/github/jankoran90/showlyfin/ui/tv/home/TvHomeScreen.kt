package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilter
import com.github.jankoran90.showlyfin.feature.discover.DiscoverTab
import com.github.jankoran90.showlyfin.feature.discover.DiscoverUiState
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.jellyfin.TvLibrariesContent

/** Sekce TV Home: doporučovač Trakt (Sleduj) vs. Jellyfin knihovny (Knihovna). */
private enum class TvHomeSection(val label: String) {
    WATCH("Sleduj"),
    LIBRARY("Knihovna"),
}

/**
 * TENFOOT (SHW-87) — TV domov. **JEDNA kompaktní horní lišta** (user feedback 2026-07-12: dřív 3 samostatné
 * řady chipů = 3× dolů než se člověk dostal na plakáty + ubíraly výšku mřížce). Lišta = sekce
 * `Sleduj | Knihovna`, a pro „Sleduj" hned za oddělovačem podsekce (Filmy/Seriály) + kategorie
 * (Doporučené/Trendy/…). Horizontálně scrollovatelná (D-pad si posune). Pod ní hned obsah → 1× dolů na výběr.
 *
 * `DiscoverViewModel` je zvednutý sem (dřív byl uvnitř mřížky), aby lišta i mřížka sdílely jeden stav.
 */
@Composable
fun TvHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
) {
    var section by rememberSaveable { mutableStateOf(TvHomeSection.WATCH) }
    val ui by discoverViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        // ── Jedna kompaktní lišta: sekce + (pro Sleduj) podsekce + kategorie ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 14.dp),
        ) {
            // Vstup do Hledání + Nastavení (vlevo v liště) — ikony, aby lišta zůstala kompaktní.
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Hledat",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .tvFocusable(shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSearch)
                    .padding(8.dp),
            )
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Oblíbené",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .tvFocusable(shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenWatchlist)
                    .padding(8.dp),
            )
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Nastavení",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .tvFocusable(shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp),
            )
            BarDivider()
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

            if (section == TvHomeSection.WATCH) {
                BarDivider()
                DiscoverTab.entries.forEach { tab ->
                    FilterChip(
                        selected = ui.activeTab == tab,
                        onClick = { discoverViewModel.selectTab(tab) },
                        label = { Text(tabLabel(tab)) },
                        modifier = Modifier.tvFocusable(),
                    )
                }
                BarDivider()
                DiscoverFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = ui.activeFilter == filter,
                        onClick = { discoverViewModel.selectFilter(filter) },
                        label = { Text(filterLabel(filter)) },
                        modifier = Modifier.tvFocusable(),
                    )
                }
            }
        }

        when (section) {
            TvHomeSection.WATCH -> WatchGrid(
                ui = ui,
                onLoadMore = discoverViewModel::loadMore,
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

/** Jemný oddělovač skupin chipů v liště (barva z motivu, žádný hardcoded odstín). */
@Composable
private fun BarDivider() {
    Spacer(Modifier.width(4.dp))
    VerticalDivider(
        modifier = Modifier.height(28.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun WatchGrid(
    ui: DiscoverUiState,
    onLoadMore: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (ui.canLoadMore && lastVisible >= ui.items.size - 8) onLoadMore()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Prostor kolem obsahu pro fokusový lift (1.08×) — bez něj mřížka ořízne zvětšený vršek/kraje plakátů.
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
        modifier = modifier,
    ) {
        items(ui.items, key = { it.traktId }) { item ->
            TvMediaCard(item = item, onClick = { onOpenDetail(item) })
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
