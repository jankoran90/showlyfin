package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.PodcastDiscoveryViewModel
import com.github.jankoran90.showlyfin.feature.listen.SourceManagerViewModel

/** Vnitřní přepínač Objevu: procházet katalog vs hledat (a přidat vlastní YT/RSS). */
private enum class DiscoverMode(val label: String) { BROWSE("Procházet"), SEARCH("Hledat") }

/**
 * AGORA-TABS: záložka „Objev" sekce Podcasty — SLOUČENÍ objevování katalogu (browse přes
 * [PodcastDiscoveryViewModel]) a přidávání vlastních zdrojů přes hledání ([SourceManagerViewModel]).
 * Nahoře segment „Procházet / Hledat"; Procházet = bohatý katalog (země/režim/kategorie + karty),
 * Hledat = vyhledání podcastu / YouTube kanálu podle názvu a přidání do sdílených zdrojů rodiny.
 * Obě části sdílejí stejný serverový store → „Přidáno" je konzistentní. Reuse karet [DiscoveryCard] /
 * [SourceResultCard]; vše čte z [MaterialTheme] tokenů (UNISON).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDiscoverSection(
    modifier: Modifier = Modifier,
    discoveryVm: PodcastDiscoveryViewModel = hiltViewModel(),
    searchVm: SourceManagerViewModel = hiltViewModel(),
) {
    var mode by rememberSaveable { mutableStateOf(DiscoverMode.BROWSE) }
    // TABULA: proklik na výsledek → detail zdroje → krok Zpět nesmí zobrazit staré hledání podcastů.
    DisposableEffect(Unit) { onDispose { searchVm.onQueryChange("") } }

    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            DiscoverMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { mode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = DiscoverMode.entries.size),
                ) { Text(m.label) }
            }
        }
        when (mode) {
            DiscoverMode.BROWSE -> BrowseList(discoveryVm)
            DiscoverMode.SEARCH -> SearchList(searchVm)
        }
    }
}

// ───────────────────────── Procházet katalog ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseList(vm: PodcastDiscoveryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.message) { if (state.message != null) vm.consumeMessage() }
    val gridState = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= layout.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.results.size, state.mode) { if (nearEnd) vm.loadMore() }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                CountryRow(state.country, vm::setCountry)
                ModeRow(state.country, state.mode, vm::setMode, Modifier.padding(top = 8.dp))
                if (state.categories.isNotEmpty() && state.mode != PodcastDiscoveryViewModel.Mode.FAVORITES) {
                    CategoryRow(
                        state.categories.filterNot { it.id in state.excluded },
                        state.selectedCategory,
                        vm::selectCategory,
                        Modifier.padding(top = 8.dp),
                    )
                }
                if (state.notConfigured) {
                    Text(
                        "Pro objevování se přihlas k serveru v Nastavení.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (state.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (state.mode == PodcastDiscoveryViewModel.Mode.FAVORITES)
                        "Zatím žádné oblíbené. Klepni na srdíčko u karty."
                    else "Nic nenalezeno.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            gridItems(state.results, key = { "${it.type}:${it.ref}" }) { r ->
                DiscoveryCard(
                    result = r,
                    added = vm.isAdded(r),
                    onAdd = { vm.add(r) },
                    favorite = vm.isFavorite(r),
                    onToggleFavorite = { vm.toggleFavorite(r) },
                    showSummary = state.showSummary,
                    showEpisodeCount = state.showEpisodeCount,
                )
            }
            if (state.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryRow(
    selected: PodcastDiscoveryViewModel.Country,
    onSelect: (PodcastDiscoveryViewModel.Country) -> Unit,
) {
    val entries = PodcastDiscoveryViewModel.Country.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { i, c ->
            SegmentedButton(
                selected = selected == c,
                onClick = { onSelect(c) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = entries.size),
            ) { Text("${c.flag} ${c.label}", maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(
    country: PodcastDiscoveryViewModel.Country,
    selected: PodcastDiscoveryViewModel.Mode,
    onSelect: (PodcastDiscoveryViewModel.Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ČT umí univerzálně jen populární + abecedně → ostatní řazení skryjeme (jinak by dělala totéž).
    val modes = if (country == PodcastDiscoveryViewModel.Country.CTV)
        listOf(
            PodcastDiscoveryViewModel.Mode.POPULAR,
            PodcastDiscoveryViewModel.Mode.AZ,
            PodcastDiscoveryViewModel.Mode.FAVORITES,
        )
    else PodcastDiscoveryViewModel.Mode.entries.toList()
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(modes) { m ->
            FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(m.label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    categories: List<com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory>,
    selected: com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory?,
    onSelect: (com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Vše") })
        }
        items(categories, key = { it.id }) { cat ->
            FilterChip(
                selected = selected?.id == cat.id,
                onClick = { onSelect(if (selected?.id == cat.id) null else cat) },
                label = { Text(cat.name) },
            )
        }
    }
}

// ───────────────────────── Hledat + přidat ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchList(vm: SourceManagerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Po přidání zdroje se karta sama přepne na „Přidáno" (reaktivně z repo.sources) — message jen
    // tiše uklidíme, ať se v sekci nehromadí (snackbar tu nemáme).
    LaunchedEffect(state.message) { if (state.message != null) vm.consumeMessage() }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Najít podcast nebo YouTube kanál podle názvu") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Vymazat")
                        }
                    }
                },
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SourceManagerViewModel.TypeFilter.entries) { f ->
                    FilterChip(
                        selected = state.typeFilter == f,
                        onClick = { vm.setTypeFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }
        }
        if (state.notConfigured) {
            item {
                Text(
                    "Pro hledání a přidávání zdrojů se přihlas k serveru v Nastavení.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.searching) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.results.isNotEmpty()) {
            item {
                Text(
                    "Výsledky",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(state.results, key = { "${it.type}:${it.ref}" }) { r ->
                SourceResultCard(result = r, added = vm.isAdded(r), onAdd = { vm.add(r) })
            }
        } else if (state.searched && state.query.trim().length >= 2) {
            item {
                Text(
                    "Nic se nenašlo. Zkus jiný název nebo přepni typ.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    "Napiš název podcastu nebo YouTube kanálu — přidaný zdroj uvidí celá rodina v Timeline i Sledovaných.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
