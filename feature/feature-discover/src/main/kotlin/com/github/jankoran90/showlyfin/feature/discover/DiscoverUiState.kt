package com.github.jankoran90.showlyfin.feature.discover

import com.github.jankoran90.showlyfin.core.domain.MediaItem

enum class DiscoverFilter { TRENDING, POPULAR, ANTICIPATED }
enum class DiscoverTab { MOVIES, SHOWS }

data class DiscoverUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: DiscoverTab = DiscoverTab.MOVIES,
    val activeFilter: DiscoverFilter = DiscoverFilter.TRENDING,
)
