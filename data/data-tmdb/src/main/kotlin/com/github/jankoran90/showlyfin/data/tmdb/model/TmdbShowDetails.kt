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
    // TENFOOT WS-C (SHW-87): souhrn sezón (TMDB `tv/{id}` vrací pole `seasons`).
    val seasons: List<TmdbSeasonSummary>? = null,
    // CINEMATHEQUE (SHW-90) F2 — země původu/produkce (osa Země Filmotéky).
    val origin_country: List<String>? = null,
    val production_countries: List<TmdbProductionCountry>? = null,
)
