package com.github.jankoran90.showlyfin.feature.jellyfin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.github.jankoran90.showlyfin.core.ui.InLibraryBadge
import com.github.jankoran90.showlyfin.core.ui.InLibraryTitleBadge
import com.github.jankoran90.showlyfin.core.ui.InLibraryTitleBadgeSpacer
import com.github.jankoran90.showlyfin.core.ui.WatchedBadge
import com.github.jankoran90.showlyfin.core.ui.WatchedTitleBadge
import com.github.jankoran90.showlyfin.core.ui.rememberScrollHeaderVisibility
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.matchesQuery
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinItem
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinLibraryItemsViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinSort
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinTypeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinLibraryItemsScreen(
    libraryId: String,
    libraryName: String,
    collectionType: String?,
    parentItemType: String?,
    onBack: () -> Unit,
    onItemPlay: (itemId: String) -> Unit,
    onItemDrillIn: (itemId: String, itemName: String, itemType: String) -> Unit,
    onItemOpenRich: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: JellyfinLibraryItemsViewModel = hiltViewModel(),
) {
    LaunchedEffect(libraryId, collectionType, parentItemType) {
        viewModel.load(libraryId, libraryName, collectionType, parentItemType)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF0D0D1A),
        topBar = {
            TopAppBar(
                title = { Text(state.libraryName.ifBlank { libraryName }, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusable(shape = CircleShape)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zpět",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E)),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Hlavička (řazení/typ + hledání) se skrývá při scrollu dolů — konzistentně s Objevit/Chci vidět.
            val headerVisible by rememberScrollHeaderVisibility(
                { gridState.firstVisibleItemIndex },
                { gridState.firstVisibleItemScrollOffset },
            )
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
            ) {
                Column {
                    JellyfinChipsRow(
                        sort = state.sort,
                        typeFilter = state.typeFilter,
                        showTypeFilter = !state.isBoxSetContext,
                        onSortSelected = { viewModel.selectSort(it) },
                        onTypeSelected = { viewModel.selectTypeFilter(it) },
                    )
                    if (state.items.isNotEmpty()) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            placeholder = { Text("Hledat v knihovně…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.tvFocusable(),
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Vymazat")
                                    }
                                }
                            },
                            singleLine = true,
                        )
                    }
                }
            }
            val visibleItems = if (state.searchQuery.isBlank()) state.items
                else state.items.filter { matchesQuery(state.searchQuery, it.name) }
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                    state.items.isEmpty() -> Text(
                        text = "Knihovna je prázdná",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    visibleItems.isEmpty() -> Text(
                        text = "Nic nenalezeno pro „${state.searchQuery}\"",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleItems, key = { it.id }) { item ->
                            JellyfinItemCard(
                                item = item,
                                onClick = {
                                    val isFolderLike = item.isFolder ||
                                        item.type.equals("BOX_SET", ignoreCase = true)
                                    when {
                                        isFolderLike -> onItemDrillIn(item.id, item.name, item.type)
                                        // Bohatý režim + TMDB match → otevři bohatý Trakt/TMDB detail
                                        state.detailRich && item.tmdbId != null && onItemOpenRich != null ->
                                            onItemOpenRich(item.toStubMediaItem())
                                        else -> onItemPlay(item.id)
                                    }
                                },
                                watched = item.watched,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyfinChipsRow(
    sort: JellyfinSort,
    typeFilter: JellyfinTypeFilter,
    showTypeFilter: Boolean,
    onSortSelected: (JellyfinSort) -> Unit,
    onTypeSelected: (JellyfinTypeFilter) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box {
                FilterChip(
                    selected = sort != JellyfinSort.NAME,
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.tvFocusable(),
                    label = { Text(sort.label) },
                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    JellyfinSort.entries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label) },
                            onClick = {
                                onSortSelected(entry)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        if (showTypeFilter) {
            items(JellyfinTypeFilter.entries.toList()) { entry ->
                FilterChip(
                    selected = typeFilter == entry,
                    onClick = { onTypeSelected(entry) },
                    modifier = Modifier.tvFocusable(),
                    label = { Text(entry.label) },
                )
            }
        }
    }
}

/** Stub MediaItem z Jellyfin položky — bohatý detail si zbytek dotáhne z TMDB dle tmdbId. */
private fun JellyfinItem.toStubMediaItem() = MediaItem(
    traktId = 0L,
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = name,
    year = year,
    overview = null,
    rating = null,
    genres = null,
    type = if (type.equals("SERIES", ignoreCase = true)) MediaType.SHOW else MediaType.MOVIE,
    posterPath = null,
    backdropPath = null,
)

@Composable
private fun JellyfinItemCard(
    item: JellyfinItem,
    onClick: () -> Unit,
    watched: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).tvFocusable(shape = RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (watched) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (watched) {
                        InLibraryTitleBadgeSpacer()
                        WatchedTitleBadge()
                    }
                }
                item.year?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it.toString(),
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item.progressPct?.takeIf { it > 0 }?.let { pct ->
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}
