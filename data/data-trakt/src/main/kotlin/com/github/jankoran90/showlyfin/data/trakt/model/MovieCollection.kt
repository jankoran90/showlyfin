package com.github.jankoran90.showlyfin.data.trakt.model

data class MovieCollection(
    val ids: Ids,
    val name: String,
    val description: String,
    val privacy: String,
    val item_count: Int,
    val likes: Int,
)
