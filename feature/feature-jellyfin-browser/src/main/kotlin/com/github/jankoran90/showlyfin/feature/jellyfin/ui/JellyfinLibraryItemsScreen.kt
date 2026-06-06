package com.github.jankoran90.showlyfin.feature.jellyfin.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.github.jankoran90.showlyfin.core.ui.InLibraryBadge
import com.github.jankoran90.showlyfin.core.ui.InLibraryTitleBadge
import com.github.jankoran90.showlyfin.core.ui.InLibraryTitleBadgeSpacer
import com.github.jankoran90.showlyfin.core.ui.WatchedBadge
import com.github.jankoran90.showlyfin.core.ui.WatchedTitleBadge
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
    modifier: Modifier = Modifier,
    viewModel: JellyfinLibraryItemsViewModel = hiltViewModel(),
) {
    LaunchedEffect(libraryId, collectionType, parentItemType) {
        viewModel.load(libraryId, libraryName, collectionType, parentItemType)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF0D0D1A),
        topBar = {
            TopAppBar(
                title = { Text(state.libraryName.ifBlank { libraryName }, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            JellyfinChipsRow(
                sort = state.sort,
                typeFilter = state.typeFilter,
                showTypeFilter = !state.isBoxSetContext,
                onSortSelected = { viewModel.selectSort(it) },
                onTypeSelected = { viewModel.selectTypeFilter(it) },
            )
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
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            JellyfinItemCard(
                                item = item,
                                onClick = {
                                    val isFolderLike = item.isFolder ||
                                        item.type.equals("BOX_SET", ignoreCase = true)
                                    if (isFolderLike) {
                                        onItemDrillIn(item.id, item.name, item.type)
                                    } else {
                                        onItemPlay(item.id)
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
                    label = { Text(entry.label) },
                )
            }
        }
    }
}

@Composable
private fun JellyfinItemCard(
    item: JellyfinItem,
    onClick: () -> Unit,
    watched: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
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
