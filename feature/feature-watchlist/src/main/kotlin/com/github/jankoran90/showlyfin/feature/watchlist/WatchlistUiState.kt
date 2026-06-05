package com.github.jankoran90.showlyfin.feature.watchlist

import com.github.jankoran90.showlyfin.core.domain.MediaItem

enum class WatchlistTab { MOVIES, SHOWS }

data class WatchProgress(
    val watchedEpisodes: Int,
    val totalEpisodes: Int,
) {
    val fraction: Float get() = if (totalEpisodes > 0) watchedEpisodes.toFloat() / totalEpisodes else 0f
}

data class WatchlistUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: WatchlistTab = WatchlistTab.MOVIES,
    val isLoggedIn: Boolean = false,
    val progressMap: Map<Long, WatchProgress> = emptyMap(),
)
