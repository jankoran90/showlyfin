package com.github.jankoran90.showlyfin.data.trakt.model.request

data class RatingRequest(
    val shows: List<RatingRequestValue>? = null,
    val movies: List<RatingRequestValue>? = null,
    val episodes: List<RatingRequestValue>? = null,
    val seasons: List<RatingRequestValue>? = null,
)

data class RatingRequestValue(
    val rating: Int,
    val rated_at: String,
    val ids: RatingRequestIds,
)

/** Trakt přijímá kterékoli id (`trakt`/`tmdb`/`imdb`); titul z katalogu (TMDB) Trakt id nemá. Gson null pole vynechá. */
data class RatingRequestIds(val trakt: Long? = null, val tmdb: Long? = null, val imdb: String? = null)
