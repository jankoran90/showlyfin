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
    // CINEMATHEQUE (SHW-90) F2 — země produkce (osa Země Filmotéky).
    val production_countries: List<TmdbProductionCountry>? = null,
)

/** CINEMATHEQUE (SHW-90) F2 — země produkce z TMDB (`production_countries`). ISO-3166-1 alpha-2. */
data class TmdbProductionCountry(
    val iso_3166_1: String? = null,
    val name: String? = null,
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
