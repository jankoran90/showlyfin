package com.github.jankoran90.showlyfin.feature.discover

import com.github.jankoran90.showlyfin.core.domain.MediaItem

// VISTA V2a — pořadí enumu = pořadí chipů v Objevit (DiscoverFilter.entries). Doporučené první.
enum class DiscoverFilter { RECOMMENDED, TRENDING, POPULAR, ANTICIPATED }
enum class DiscoverTab { MOVIES, SHOWS }

data class DiscoverUiState(
    val items: List<MediaItem> = emptyList(),
    val rawItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: DiscoverTab = DiscoverTab.MOVIES,
    val activeFilter: DiscoverFilter = DiscoverFilter.RECOMMENDED,
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val rawSearchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val filters: DiscoverFilters = DiscoverFilters(),
    val availableGenres: List<String> = emptyList(),
    val ownedImdbIds: Set<String> = emptySet(),
    val imdbToJellyfin: Map<String, String> = emptyMap(),
    val tmdbToJellyfin: Map<Long, String> = emptyMap(),
    val watchlistTraktIds: Set<Long> = emptySet(),
    val watchedTraktIds: Set<Long> = emptySet(),
    val ratedTraktIds: Set<Long> = emptySet(),
    val watchedImdbIds: Set<String> = emptySet(),
    val watchedTmdbIds: Set<Long> = emptySet(),
    val isFilterSheetOpen: Boolean = false,
    val rdOnly: Boolean = false,
    val rdMatchedTraktIds: Set<Long> = emptySet(),
    val rdMatchLoading: Boolean = false,
    val parentalLockedAgeRating: com.github.jankoran90.showlyfin.core.domain.AgeRating? = null,
    val sessionAgeOverride: com.github.jankoran90.showlyfin.core.domain.AgeRating? = null,
    val isTraktLoggedIn: Boolean = false,
    // VISTA V2b — stránkování Objevit (Trakt page). „Doporučené" Trakt nestránkuje → canLoadMore=false.
    val page: Int = 1,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
)
