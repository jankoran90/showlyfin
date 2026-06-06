package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbSearchMovieResponse(
    val page: Int? = null,
    val results: List<TmdbSearchMovieItem> = emptyList(),
)

data class TmdbSearchMovieItem(
    val id: Long,
    val title: String? = null,
    val original_title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val vote_average: Float? = null,
)

data class TmdbSearchShowResponse(
    val page: Int? = null,
    val results: List<TmdbSearchShowItem> = emptyList(),
)

data class TmdbSearchShowItem(
    val id: Long,
    val name: String? = null,
    val original_name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val vote_average: Float? = null,
)
