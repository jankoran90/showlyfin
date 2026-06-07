package com.github.jankoran90.showlyfin.feature.jellyfin

data class EpisodeRow(
    val id: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val name: String,
    val imageUrl: String,
    val overview: String?,
    val watched: Boolean,
    val progressPct: Int?,
    val isNextUp: Boolean,
)

data class EpisodePickerUiState(
    val seriesName: String = "",
    val episodes: List<EpisodeRow> = emptyList(),
    val nextUpIndex: Int = -1,
    val isLoading: Boolean = true,
    val error: String? = null,
)
