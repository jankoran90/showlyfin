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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.LandscapeCard
import com.github.jankoran90.showlyfin.core.ui.LandscapeDetailCard
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.core.ui.SectionBar
import com.github.jankoran90.showlyfin.core.ui.SectionSortChip
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.rememberScrollHeaderVisibility
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.feature.watchlist.WatchProgress
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistSort
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistTab
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistViewModel

/** VISTA V3 — výška coveru řádku „Chci vidět"; výška textového sloupce = výška coveru. */
private val RowCoverHeight = 120.dp
private val RowCoverWidth = 80.dp
private val RowCoverShape = RoundedCornerShape(10.dp)

@Composable
fun WatchlistScreen(
    onItemClick: (MediaItem, jellyfinId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val csfdRatings by viewModel.csfdRatings.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    Column(modifier = modifier.fillMaxSize()) {
        // Hlavička se skrývá při scrollu — z aktivního scroll stavu (seznam / mřížka).
        val listHeaderVisible by rememberScrollHeaderVisibility(
            { listState.firstVisibleItemIndex },
            { listState.firstVisibleItemScrollOffset },
        )
        val gridHeaderVisible by rememberScrollHeaderVisibility(
            { gridState.firstVisibleItemIndex },
            { gridState.firstVisibleItemScrollOffset },
        )
        val headerVisible = if (viewMode == ViewMode.GRID || viewMode == ViewMode.LANDSCAPE) gridHeaderVisible else listHeaderVisible
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
                viewMode = viewMode,
                onSelectViewMode = { viewModel.setViewMode(it) },
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
            // VANTAGE A: Chci vidět = seznam (řádky s popisem) NEBO mřížka karet, dle přepínače v liště.
            viewMode == ViewMode.GRID || viewMode == ViewMode.LANDSCAPE -> {
                val colPref = rememberGridColumnPref()
                LazyVerticalGrid(
                    state = gridState,
                    columns = gridCellsFor(viewMode, colPref),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { "${it.type}_${it.traktId}" }) { item ->
                        val jellyfinId = item.imdbId?.let { uiState.imdbToJellyfin[it] }
                            ?: item.tmdbId?.let { uiState.tmdbToJellyfin[it] }
                        val inLibrary = jellyfinId != null
                            || (item.imdbId?.let { uiState.ownedImdbIds.contains(it) } ?: false)
                        val watched = (item.imdbId?.let { uiState.watchedImdbIds.contains(it) } ?: false)
                            || (item.tmdbId?.let { uiState.watchedTmdbIds.contains(it) } ?: false)
                            || uiState.watchedTraktIds.contains(item.traktId)
                        if (viewMode == ViewMode.LANDSCAPE) {
                            LandscapeCard(
                                item = item,
                                onClick = { onItemClick(item, jellyfinId) },
                                inLibrary = inLibrary,
                                watched = watched,
                            )
                        } else {
                            MediaCard(
                                item = item,
                                onClick = { onItemClick(item, jellyfinId) },
                                inLibrary = inLibrary,
                                watched = watched,
                            )
                        }
                    }
                }
            }
            else -> {
                // VISTA V3: „Chci vidět" jako řádky (cover + titulek/rok/ČSFD/popis), kanonická MediaRow.
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        // Líně načti ČSFD hodnocení jen pro skutečně zobrazený řádek.
                        LaunchedEffect(item.traktId) { viewModel.loadCsfdRating(item) }
                        val progressText = if (item.type == MediaType.SHOW) {
                            progressData?.let { "${it.watchedEpisodes}/${it.totalEpisodes} epizod" }
                        } else null
                        if (viewMode == ViewMode.LANDSCAPE_DETAIL) {
                            LandscapeDetailCard(
                                item = item,
                                csfdRating = csfdRatings[item.traktId],
                                inLibrary = inLibrary,
                                watched = watched,
                                progressText = progressText,
                                onClick = { onItemClick(item, jellyfinId) },
                            )
                        } else {
                            MediaRow(
                                item = item,
                                csfdRating = csfdRatings[item.traktId],
                                inLibrary = inLibrary,
                                watched = watched,
                                progressText = progressText,
                                onClick = { onItemClick(item, jellyfinId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * VISTA V3 — jeden řádek „Chci vidět": cover vlevo, vpravo titulek + meta (rok · ČSFD · stav) +
 * český popis, jehož text je omezený výškou coveru (zbytek se ořízne s „…").
 */
@Composable
private fun WatchlistRow(
    item: MediaItem,
    csfdRating: Int?,
    inLibrary: Boolean,
    watched: Boolean,
    progress: WatchProgress?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowCoverShape)
            .clickable(onClick = onClick)
            .tvFocusable()
            .padding(vertical = 2.dp),
    ) {
        val posterUrl = item.posterUrl("w342")
        Box(
            modifier = Modifier
                .height(RowCoverHeight)
                .width(RowCoverWidth)
                .clip(RowCoverShape),
            contentAlignment = Alignment.Center,
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.height(RowCoverHeight).fillMaxWidth()) {
            Text(
                text = item.titleCz?.takeIf { it.isNotBlank() } ?: item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (csfdRating != null) {
                    if (item.year != null) DotSeparator()
                    Text(
                        text = "ČSFD $csfdRating %",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (progress != null) {
                    DotSeparator()
                    Text(
                        text = "${progress.watchedEpisodes}/${progress.totalEpisodes} epizod",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (watched) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Zhlédnuto",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (inLibrary) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "V knihovně",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val description = item.overviewCz?.takeIf { it.isNotBlank() }
                ?: item.overview?.takeIf { it.isNotBlank() }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

/** Tečkový oddělovač meta údajů v řádku. */
@Composable
private fun DotSeparator() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
