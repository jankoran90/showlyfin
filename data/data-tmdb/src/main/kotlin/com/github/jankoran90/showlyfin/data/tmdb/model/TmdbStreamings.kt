package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbStreamings(
    val id: Long,
    val results: Map<String, TmdbStreamingCountry>,
)
