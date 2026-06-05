package com.github.jankoran90.showlyfin.feature.watchlist

import com.github.jankoran90.showlyfin.core.domain.MediaItem

enum class WatchlistTab { MOVIES, SHOWS }

data class WatchlistUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: WatchlistTab = WatchlistTab.MOVIES,
    val isLoggedIn: Boolean = false,
)
