package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType

/**
 * Karta filmu/seriálu v Objevit gridu — deleguje na kanonickou [PosterCard] (CANVAS B, UNISON).
 * ČSFD hodnocení se líně dotáhne per karta. (VANTAGE F: žánry na kartách zrušeny.)
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
        // KANON (user 2026-08-30): tatáž politika názvů jako řádky seznamů a karta detailu —
        // česky → anglicky → originál. Líně přes provider (TMDB cs → ČSFD → TMDB en), než dorazí
        // drží displayTitle položky (cz → en → latinka → originál).
        title = rememberRowTitle(
            item.imdbId, item.tmdbId, item.titleCz, item.type != MediaType.MOVIE,
            title = item.title, year = item.year,
        ) ?: item.displayTitle,
        year = item.year?.toString(),
        onClick = onClick,
        modifier = modifier,
        isShow = item.type != MediaType.MOVIE,
        imdbId = item.imdbId,
        tmdbId = item.tmdbId,
        csfdYear = item.year,
        inLibrary = inLibrary,
        watched = watched,
        progress = progress,
        // VLTAVA F6b — ČT titul bez svislého plakátu nese 16:9 grafiku → vykresli ji celou, ne napůl.
        wideArtwork = item.hasWideArtworkOnly,
        ratingTarget = RatingTarget(
            tmdbId = item.tmdbId,
            imdbId = item.imdbId,
            traktId = item.traktId,
            title = item.displayTitle,
            year = item.year,
            isShow = item.type != MediaType.MOVIE,
        ),
    )
}
