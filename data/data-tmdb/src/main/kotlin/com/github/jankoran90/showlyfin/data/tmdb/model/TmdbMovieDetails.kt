package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbMovieDetails(
    val id: Long,
    val title: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Float?,
    val release_date: String?,
    val runtime: Int?,
    val genres: List<TmdbGenre>?,
    val tagline: String?,
    val status: String?,
)
