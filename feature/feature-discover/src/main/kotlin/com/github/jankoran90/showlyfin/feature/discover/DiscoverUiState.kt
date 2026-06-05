package com.github.jankoran90.showlyfin.feature.discover

import com.github.jankoran90.showlyfin.core.domain.MediaItem

enum class DiscoverFilter { TRENDING, POPULAR, ANTICIPATED }
enum class DiscoverTab { MOVIES, SHOWS }

data class DiscoverUiState(
    val items: List<MediaItem> = emptyList(),
    val rawItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: DiscoverTab = DiscoverTab.MOVIES,
    val activeFilter: DiscoverFilter = DiscoverFilter.TRENDING,
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val rawSearchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val filters: DiscoverFilters = DiscoverFilters(),
    val availableGenres: List<String> = emptyList(),
    val ownedImdbIds: Set<String> = emptySet(),
    val watchlistTraktIds: Set<Long> = emptySet(),
    val watchedTraktIds: Set<Long> = emptySet(),
    val isFilterSheetOpen: Boolean = false,
    val parentalLockedAgeRating: com.github.jankoran90.showlyfin.core.domain.AgeRating? = null,
)
