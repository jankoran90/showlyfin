package com.github.jankoran90.showlyfin.data.trakt.model

data class SearchResult(
    val order: Int?,
    val score: Float?,
    val show: Show?,
    val movie: Movie?,
    val person: Person?,
)
