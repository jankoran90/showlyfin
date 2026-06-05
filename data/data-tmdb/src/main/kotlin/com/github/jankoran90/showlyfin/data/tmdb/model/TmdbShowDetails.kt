package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbShowDetails(
    val id: Long,
    val name: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Float?,
    val first_air_date: String?,
    val number_of_seasons: Int?,
    val number_of_episodes: Int?,
    val genres: List<TmdbGenre>?,
    val status: String?,
    val tagline: String?,
)
