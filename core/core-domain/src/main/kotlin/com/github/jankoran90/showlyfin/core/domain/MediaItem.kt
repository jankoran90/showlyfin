package com.github.jankoran90.showlyfin.core.domain

data class MediaItem(
    val traktId: Long,
    val tmdbId: Long?,
    val imdbId: String?,
    val title: String,
    val year: Int?,
    val overview: String?,
    val rating: Float?,
    val genres: List<String>?,
    val type: MediaType,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val titleCz: String? = null,
    val overviewCz: String? = null,
) {
    fun posterUrl(size: String = "w342") = posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }
    fun backdropUrl(size: String = "w780") = backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }
}
