package com.github.jankoran90.showlyfin.feature.discover.mapper

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.trakt.model.Movie
import com.github.jankoran90.showlyfin.data.trakt.model.SearchResult
import com.github.jankoran90.showlyfin.data.trakt.model.Show

internal fun Movie.toMediaItem() = MediaItem(
    traktId = ids?.trakt ?: 0L,
    tmdbId = ids?.tmdb,
    imdbId = ids?.imdb,
    title = title ?: "",
    year = year,
    overview = overview,
    rating = rating,
    genres = genres,
    type = MediaType.MOVIE,
)

internal fun Show.toMediaItem() = MediaItem(
    traktId = ids?.trakt ?: 0L,
    tmdbId = ids?.tmdb,
    imdbId = ids?.imdb,
    title = title ?: "",
    year = year,
    overview = overview,
    rating = rating,
    genres = genres,
    type = MediaType.SHOW,
)

internal fun SearchResult.toMediaItem(): MediaItem? {
    movie?.let { return it.toMediaItem() }
    show?.let { return it.toMediaItem() }
    return null
}
