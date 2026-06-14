package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType

/**
 * Karta filmu/seriálu v Objevit gridu — deleguje na kanonickou [PosterCard] (CANVAS B, UNISON).
 * ČSFD hodnocení se líně dotáhne per karta; žánry z [MediaItem.genres] (≤4 štítky).
 */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    inLibrary: Boolean = false,
    watched: Boolean = false,
) {
    PosterCard(
        posterUrl = item.posterUrl(),
        title = item.titleCz?.takeIf { it.isNotBlank() } ?: item.title,
        year = item.year?.toString(),
        onClick = onClick,
        modifier = modifier,
        isShow = item.type != MediaType.MOVIE,
        imdbId = item.imdbId,
        tmdbId = item.tmdbId,
        csfdYear = item.year,
        genres = item.genres.orEmpty(),
        inLibrary = inLibrary,
        watched = watched,
        progress = progress,
    )
}
