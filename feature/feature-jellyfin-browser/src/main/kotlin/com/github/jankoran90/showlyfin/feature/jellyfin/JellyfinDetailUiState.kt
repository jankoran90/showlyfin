package com.github.jankoran90.showlyfin.feature.jellyfin

data class JellyfinDetail(
    val id: String,
    val name: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val rating: Float?,
    val officialRating: String?,
    val genres: List<String>,
    val type: String,
)

data class JellyfinDetailUiState(
    val isLoading: Boolean = true,
    val detail: JellyfinDetail? = null,
    val error: String? = null,
)
