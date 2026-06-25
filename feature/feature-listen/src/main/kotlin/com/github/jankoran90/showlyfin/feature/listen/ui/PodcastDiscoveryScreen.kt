package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory
import com.github.jankoran90.showlyfin.feature.listen.PodcastDiscoveryViewModel

/**
 * AGORA — objevovací obrazovka podcastů. Nahoře segment ZEMĚ (🇨🇿 výchozí), pod tím řádek REŽIMŮ
 * (Populární/Aktivní/Nové/A-Z), pod tím vodorovný scroll KATEGORIÍ a bohaté karty v gridu. Akce
 * na kartě = Přidat (zůstává na obrazovce). Vše čte z [MaterialTheme] tokenů (UNISON) a běží uvnitř
 * [ListenExpressiveTheme] jako ostatní poslechové obrazovky.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDiscoveryScreen(
    modifier: Modifier = Modifier,
    viewModel: PodcastDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    // Stránkování: jakmile se doroluje blízko konce gridu, donačti další stránku (append).
    val nearEnd by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= layout.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.results.size, state.mode) {
        if (nearEnd) viewModel.loadMore()
    }

    ListenExpressiveTheme {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                    start = 12.dp, end = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Hlavička + filtry zabírají celou šířku (span = celá mřížka).
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Objevit podcasty",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                            )
                            // F4: filtr vyloučení kategorií (badge = počet aktivních vyloučení).
                            BadgedBox(badge = {
                                if (state.excluded.isNotEmpty()) Badge { Text("${state.excluded.size}") }
                            }) {
                                IconButton(onClick = { showFilterSheet = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filtr kategorií")
                                }
                            }
                        }
                        CountrySegment(
                            selected = state.country,
                            onSelect = viewModel::setCountry,
                        )
                        ModeRow(
                            selected = state.mode,
                            onSelect = viewModel::setMode,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (state.categories.isNotEmpty() && state.mode != PodcastDiscoveryViewModel.Mode.FAVORITES) {
                            CategoryRow(
                                categories = state.categories.filterNot { it.id in state.excluded },
                                selected = state.selectedCategory,
                                onSelect = viewModel::selectCategory,
                                modifier = Modifier.padding(top = 8.dp),
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
                            text = if (state.mode == PodcastDiscoveryViewModel.Mode.FAVORITES)
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
                            added = viewModel.isAdded(r),
                            onAdd = { viewModel.add(r) },
                            favorite = viewModel.isFavorite(r),
                            onToggleFavorite = { viewModel.toggleFavorite(r) },
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

            if (showFilterSheet) {
                CategoryFilterSheet(
                    categories = state.categories,
                    excluded = state.excluded,
                    onToggle = viewModel::toggleExclude,
                    onDismiss = { showFilterSheet = false },
                )
            }
        }
    }
}

/** F4: spodní panel se seznamem kategorií jako přepínatelné chips „skrýt". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    categories: List<SourceCategory>,
    excluded: Set<Int>,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Skrýt kategorie",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (excluded.isEmpty()) "Žádné skryté" else "Skryto: ${excluded.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            if (categories.isEmpty()) {
                Text(
                    "Kategorie jsou dostupné jen pro ČR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = cat.id in excluded,
                            onClick = { onToggle(cat.id) },
                            label = { Text(cat.name) },
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySegment(
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
            ) {
                Text("${c.flag} ${c.label}", maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(
    selected: PodcastDiscoveryViewModel.Mode,
    onSelect: (PodcastDiscoveryViewModel.Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PodcastDiscoveryViewModel.Mode.entries) { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onSelect(m) },
                label = { Text(m.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    categories: List<SourceCategory>,
    selected: SourceCategory?,
    onSelect: (SourceCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // „Vše" = bez filtru kategorie.
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Vše") },
            )
        }
        items(categories, key = { it.id }) { cat ->
            val accent = parseColor(cat.color)
            FilterChip(
                selected = selected?.id == cat.id,
                onClick = { onSelect(if (selected?.id == cat.id) null else cat) },
                label = { Text(cat.name) },
                colors = if (accent != null) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/** Bezpečné parsování barvy kategorie z backendu (#RRGGBB / #AARRGGBB); null → fallback na theme. */
private fun parseColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    return runCatching {
        when (h.length) {
            6 -> Color(0xFF000000 or h.toLong(16))
            8 -> Color(h.toLong(16))
            else -> null
        }
    }.getOrNull()
}
