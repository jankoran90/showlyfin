package com.github.jankoran90.showlyfin.feature.discover.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
    val isSearchMode = uiState.searchQuery.isNotBlank()

    val gridState = rememberLazyGridState()
    var isHeaderVisible by remember { mutableStateOf(true) }
    LaunchedEffect(gridState) {
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val direction = when {
                    index < prevIndex -> 1
                    index > prevIndex -> -1
                    offset < prevOffset - 4 -> 1
                    offset > prevOffset + 4 -> -1
                    else -> 0
                }
                if (index == 0 && offset < 60) isHeaderVisible = true
                else if (direction == 1) isHeaderVisible = true
                else if (direction == -1) isHeaderVisible = false
                prevIndex = index
                prevOffset = offset
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Hledat filmy a seriály…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Vymazat")
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                )
                IconButton(onClick = { viewModel.openFilterSheet() }) {
                    BadgedBox(badge = {
                        if (uiState.filters.isActive) {
                            Badge {}
                        }
                    }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtry")
                    }
                }
            }
        }

        if (!isSearchMode) {
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
            ) {
                Column {
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
                }
            }
        }

        val items = if (isSearchMode) uiState.searchResults else uiState.items
        val isLoading = if (isSearchMode) uiState.isSearching else uiState.isLoading

        if (isLoading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (isSearchMode && !isLoading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Žádné výsledky pro \"${uiState.searchQuery}\"",
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else if (uiState.error != null && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error ?: "Chyba",
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            val animKey = "${uiState.activeTab.name}_${uiState.activeFilter.name}_${isSearchMode}"
            AnimatedContent(
                targetState = animKey,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "discover-grid-swap",
            ) { _ ->
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { "${it.type}_${it.traktId}" }) { item ->
                        val inLibrary = item.imdbId?.let { uiState.ownedImdbIds.contains(it) } ?: false
                        MediaCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            inLibrary = inLibrary,
                        )
                    }
                }
            }
        }
    }

    if (uiState.isFilterSheetOpen) {
        FilterBottomSheet(
            filters = uiState.filters,
            availableGenres = uiState.availableGenres,
            isParentalLocked = uiState.parentalLockedAgeRating != null,
            onDismiss = { viewModel.closeFilterSheet() },
            onApply = {
                viewModel.updateFilters(it)
                viewModel.closeFilterSheet()
            },
            onReset = {
                viewModel.resetFilters()
                viewModel.closeFilterSheet()
            },
        )
    }
}
