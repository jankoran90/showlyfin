package com.github.jankoran90.showlyfin.data.tmdb.model

/** TMDB `movie/{id}/release_dates` — certifikace filmu per země. */
data class TmdbReleaseDatesResponse(
    val results: List<TmdbReleaseDatesCountry>? = null,
)

data class TmdbReleaseDatesCountry(
    val iso_3166_1: String? = null,
    val release_dates: List<TmdbReleaseDateEntry>? = null,
)

data class TmdbReleaseDateEntry(
    val certification: String? = null,
    val release_date: String? = null,
)

/** TMDB `tv/{id}/content_ratings` — certifikace seriálu per země. */
data class TmdbContentRatingsResponse(
    val results: List<TmdbContentRatingEntry>? = null,
)

data class TmdbContentRatingEntry(
    val iso_3166_1: String? = null,
    val rating: String? = null,
)
