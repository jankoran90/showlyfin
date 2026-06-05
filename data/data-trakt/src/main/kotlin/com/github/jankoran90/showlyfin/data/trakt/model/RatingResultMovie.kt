package com.github.jankoran90.showlyfin.data.trakt.model

data class RatingResultMovie(
    val rated_at: String?,
    val rating: Int,
    val movie: RatingResultValue,
)
