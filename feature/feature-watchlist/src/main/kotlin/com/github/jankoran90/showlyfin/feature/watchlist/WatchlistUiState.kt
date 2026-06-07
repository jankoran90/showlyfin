package com.github.jankoran90.showlyfin.feature.watchlist

import com.github.jankoran90.showlyfin.core.domain.MediaItem

enum class WatchlistTab { MOVIES, SHOWS }

enum class WatchlistSort(val label: String) {
    DEFAULT("Naposledy přidané"),
    TITLE("Název A–Z"),
    YEAR_DESC("Nejnovější"),
    YEAR_ASC("Nejstarší"),
    RATING_DESC("Hodnocení"),
}

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
    val sort: WatchlistSort = WatchlistSort.DEFAULT,
    val genreFilter: String? = null,
    val availableGenres: List<String> = emptyList(),
    val ownedImdbIds: Set<String> = emptySet(),
    val imdbToJellyfin: Map<String, String> = emptyMap(),
    val tmdbToJellyfin: Map<Long, String> = emptyMap(),
    val watchedImdbIds: Set<String> = emptySet(),
    val watchedTmdbIds: Set<Long> = emptySet(),
    val watchedTraktIds: Set<Long> = emptySet(),
    val rdOnly: Boolean = false,
    val rdMatchedTraktIds: Set<Long> = emptySet(),
    val rdMatchLoading: Boolean = false,
)
