package com.github.jankoran90.showlyfin.feature.watchlist.mapper

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.trakt.model.SyncItem

internal fun SyncItem.toMovieMediaItem() = MediaItem(
    traktId = movie?.ids?.trakt ?: 0L,
    tmdbId = movie?.ids?.tmdb,
    imdbId = movie?.ids?.imdb,
    title = movie?.title ?: "",
    year = movie?.year,
    overview = movie?.overview,
    rating = movie?.rating,
    genres = movie?.genres,
    type = MediaType.MOVIE,
)

internal fun SyncItem.toShowMediaItem() = MediaItem(
    traktId = show?.ids?.trakt ?: 0L,
    tmdbId = show?.ids?.tmdb,
    imdbId = show?.ids?.imdb,
    title = show?.title ?: "",
    year = show?.year,
    overview = show?.overview,
    rating = show?.rating,
    genres = show?.genres,
    type = MediaType.SHOW,
)
