package com.github.jankoran90.showlyfin.data.tmdb.model

import kotlin.math.sqrt

data class TmdbImage(
    val file_path: String,
    val vote_average: Float,
    val vote_count: Long,
    val iso_639_1: String?,
) {
    fun isPlain() = iso_639_1 == null
    fun isEnglish() = iso_639_1 == "en"
    fun isLanguage(language: String) = iso_639_1 == language

    fun getVoteScore(): Double {
        val z = 1.96
        val phat = vote_average / 10.0
        val numerator = phat + (z * z) / (2 * vote_count) - z * sqrt((phat * (1 - phat) + (z * z) / (4 * vote_count)) / vote_count)
        val denominator = 1 + (z * z) / vote_count
        return if (vote_count > 0) (numerator / denominator) else 0.0
    }
}
