package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbCollection(
    val id: Long,
    val name: String?,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val parts: List<TmdbCollectionPart>?,
)

data class TmdbCollectionPart(
    val id: Long,
    val title: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val release_date: String?,
    val vote_average: Float?,
    val overview: String?,
)
