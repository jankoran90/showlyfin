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
    val imdb_id: String? = null,
    val belongs_to_collection: TmdbBelongsToCollection? = null,
    val production_companies: List<TmdbProductionCompany>? = null,
)

data class TmdbProductionCompany(
    val id: Long,
    val name: String? = null,
    val logo_path: String? = null,
)

data class TmdbBelongsToCollection(
    val id: Long,
    val name: String?,
    val poster_path: String?,
    val backdrop_path: String?,
)
