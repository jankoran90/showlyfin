package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbTranslation(
    val iso_639_1: String,
    val iso_3166_1: String,
    val data: Data?,
) {
    data class Data(
        val biography: String? = null,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        val tagline: String? = null,
    )
}
