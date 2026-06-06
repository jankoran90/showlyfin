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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilter
import com.github.jankoran90.showlyfin.feature.discover.DiscoverSort
import com.github.jankoran90.showlyfin.feature.discover.DiscoverTab
import com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(
    onItemClick: (MediaItem, jellyfinId: String?) -> Unit,
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

    var searchExpanded by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var genresMenuOpen by remember { mutableStateOf(false) }
    var ageMenuOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.error) {
        if (uiState.error == "needs_trakt_login") {
            snackbarHostState.showSnackbar("Pro doporučení se přihlas k Trakt v Nastavení")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
            ) {
                if (searchExpanded || isSearchMode) {
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
                                IconButton(onClick = {
                                    viewModel.clearSearch()
                                    searchExpanded = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Vymazat")
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                        )
                    }
                } else {
                    DiscoverFilterRow(
                        uiState = uiState,
                        sortMenuOpen = sortMenuOpen,
                        onSortMenuToggle = { sortMenuOpen = it },
                        genresMenuOpen = genresMenuOpen,
                        onGenresMenuToggle = { genresMenuOpen = it },
                        ageMenuOpen = ageMenuOpen,
                        onAgeMenuToggle = { ageMenuOpen = it },
                        onSearchClick = { searchExpanded = true },
                        onFilterClick = { viewModel.openFilterSheet() },
                        onSortSelect = {
                            viewModel.updateFilters(uiState.filters.copy(sortBy = it))
                            sortMenuOpen = false
                        },
                        onGenreToggle = { genre ->
                            val current = uiState.filters.selectedGenres
                            val updated = if (current.contains(genre)) current - genre else current + genre
                            viewModel.updateFilters(uiState.filters.copy(selectedGenres = updated))
                        },
                        onGenresClear = {
                            viewModel.updateFilters(uiState.filters.copy(selectedGenres = emptySet()))
                            genresMenuOpen = false
                        },
                        onAgeSelect = {
                            viewModel.setSessionAgeOverride(it)
                            ageMenuOpen = false
                        },
                        onFilterChipSelect = { filter ->
                            if (filter == DiscoverFilter.RECOMMENDED && !uiState.isTraktLoggedIn) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Pro doporučení se přihlas k Trakt v Nastavení")
                                }
                            } else {
                                viewModel.selectFilter(filter)
                            }
                        },
                    )
                }
            }

            if (!isSearchMode && !searchExpanded) {
                AnimatedVisibility(
                    visible = isHeaderVisible,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
                ) {
                    TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
                        DiscoverTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.activeTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = { Text(if (tab == DiscoverTab.MOVIES) "Filmy" else "Seriály") },
                            )
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
            } else if (uiState.error != null && items.isEmpty() && uiState.error != "needs_trakt_login") {
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
                            val jellyfinId = item.imdbId?.let { uiState.imdbToJellyfin[it] }
                                ?: item.tmdbId?.let { uiState.tmdbToJellyfin[it] }
                            val inLibrary = jellyfinId != null
                                || (item.imdbId?.let { uiState.ownedImdbIds.contains(it) } ?: false)
                            val watched = (item.imdbId?.let { uiState.watchedImdbIds.contains(it) } ?: false)
                                || (item.tmdbId?.let { uiState.watchedTmdbIds.contains(it) } ?: false)
                                || uiState.watchedTraktIds.contains(item.traktId)
                            timber.log.Timber.d("[Discover] render '${item.title}' imdb=${item.imdbId} tmdb=${item.tmdbId} → jfId=$jellyfinId inLib=$inLibrary watched=$watched")
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

@Composable
private fun DiscoverFilterRow(
    uiState: com.github.jankoran90.showlyfin.feature.discover.DiscoverUiState,
    sortMenuOpen: Boolean,
    onSortMenuToggle: (Boolean) -> Unit,
    genresMenuOpen: Boolean,
    onGenresMenuToggle: (Boolean) -> Unit,
    ageMenuOpen: Boolean,
    onAgeMenuToggle: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSortSelect: (DiscoverSort) -> Unit,
    onGenreToggle: (String) -> Unit,
    onGenresClear: () -> Unit,
    onAgeSelect: (AgeRating?) -> Unit,
    onFilterChipSelect: (DiscoverFilter) -> Unit,
) {
    val activeAge = uiState.sessionAgeOverride ?: uiState.parentalLockedAgeRating
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            val isSearchActive = uiState.searchQuery.isNotBlank()
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Hledat",
                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            Box {
                val sortLabel = when (uiState.filters.sortBy) {
                    DiscoverSort.DEFAULT -> "Řazení"
                    DiscoverSort.RATING_DESC -> "Řazení: Hodnocení"
                    DiscoverSort.YEAR_DESC -> "Řazení: Nejnovější"
                    DiscoverSort.YEAR_ASC -> "Řazení: Nejstarší"
                    DiscoverSort.ALPHABETICAL -> "Řazení: A–Z"
                }
                AssistChip(
                    onClick = { onSortMenuToggle(true) },
                    label = { Text(sortLabel) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { onSortMenuToggle(false) }) {
                    val sortItems = listOf(
                        DiscoverSort.DEFAULT to "Výchozí",
                        DiscoverSort.RATING_DESC to "Hodnocení ↓",
                        DiscoverSort.YEAR_DESC to "Rok ↓",
                        DiscoverSort.YEAR_ASC to "Rok ↑",
                        DiscoverSort.ALPHABETICAL to "Abecedně",
                    )
                    sortItems.forEach { (sort, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onSortSelect(sort) },
                            trailingIcon = if (uiState.filters.sortBy == sort) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }
        }
        item {
            Box {
                val genresLabel = if (uiState.filters.selectedGenres.isEmpty()) "Žánry"
                else "Žánry (${uiState.filters.selectedGenres.size})"
                AssistChip(
                    onClick = { onGenresMenuToggle(true) },
                    label = { Text(genresLabel) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = genresMenuOpen, onDismissRequest = { onGenresMenuToggle(false) }) {
                    if (uiState.availableGenres.isEmpty()) {
                        DropdownMenuItem(text = { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {})
                    } else {
                        if (uiState.filters.selectedGenres.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { TextButton(onClick = onGenresClear) { Text("Vymazat výběr") } },
                                onClick = onGenresClear,
                            )
                        }
                        uiState.availableGenres.forEach { genre ->
                            val checked = uiState.filters.selectedGenres.contains(genre)
                            DropdownMenuItem(
                                text = { Text(genre) },
                                onClick = { onGenreToggle(genre) },
                                trailingIcon = if (checked) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
        item {
            Box {
                val ageLabel = when (activeAge) {
                    null, AgeRating.UNRESTRICTED -> "Věk"
                    AgeRating.CHILDREN -> "Věk: Děti"
                    AgeRating.FAMILY -> "Věk: Rodinné"
                    AgeRating.TEEN -> "Věk: Teen"
                    AgeRating.ADULT -> "Věk: Dospělí"
                }
                AssistChip(
                    onClick = { onAgeMenuToggle(true) },
                    label = { Text(ageLabel) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = ageMenuOpen, onDismissRequest = { onAgeMenuToggle(false) }) {
                    val ageItems = listOf<Pair<AgeRating?, String>>(
                        null to "Bez omezení",
                        AgeRating.CHILDREN to "Děti",
                        AgeRating.FAMILY to "Rodinné",
                        AgeRating.TEEN to "Teen",
                        AgeRating.ADULT to "Dospělí (vše)",
                    )
                    ageItems.forEach { (rating, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onAgeSelect(rating) },
                            trailingIcon = if (activeAge == rating || (activeAge == AgeRating.UNRESTRICTED && rating == null)) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }
        }
        items(DiscoverFilter.entries) { filter ->
            FilterChip(
                selected = uiState.activeFilter == filter,
                onClick = { onFilterChipSelect(filter) },
                label = {
                    Text(
                        when (filter) {
                            DiscoverFilter.TRENDING -> "Trending"
                            DiscoverFilter.POPULAR -> "Populární"
                            DiscoverFilter.ANTICIPATED -> "Očekávané"
                            DiscoverFilter.RECOMMENDED -> if (uiState.isTraktLoggedIn) "Doporučené" else "Doporučené 🔒"
                        }
                    )
                },
            )
        }
        item {
            IconButton(onClick = onFilterClick) {
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
}
