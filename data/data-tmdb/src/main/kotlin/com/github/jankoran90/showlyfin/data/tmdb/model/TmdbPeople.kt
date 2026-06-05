package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbPeople(
    val id: Long,
    val cast: List<TmdbPerson>?,
    val crew: List<TmdbPerson>?,
)
