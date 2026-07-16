package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.LandscapeCard
import com.github.jankoran90.showlyfin.core.ui.PosterCard
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/** Šířky karet v telefonní řadě (bez D-padu; TV má vlastní v ui-tv). */
internal val FilmyPosterWidth = 118.dp
internal val FilmyLandscapeWidth = 300.dp

/**
 * CELLULOID (SHW-98) Fáze 2 M2.2 — jedna karta v telefonní řadě domova.
 *
 * Reuse sdílených karet z `core-ui` ([PosterCard]/[LandscapeCard] — form-factor aware, `tvFocusable`
 * je na PHONE no-op), takže telefon dostane TÝŽ vzhled jako TV bez D-pad ovládání. Styl řady řídí
 * [HomeCardStyle] z konfigurace (parita s TV domovem). Fanart styly potřebují bohatý [MediaItem];
 * JF-only položky (bez `mediaItem`) padnou na plakát (mají hotovou `posterUrl`).
 */
@Composable
fun FilmyHomeCard(
    item: HomeRowItem,
    style: HomeCardStyle,
    onClick: () -> Unit,
) {
    val progress = item.progressPct?.takeIf { it in 1..99 }?.let { it / 100f }
    when (style) {
        HomeCardStyle.LANDSCAPE, HomeCardStyle.FANART_DETAIL -> {
            val mi = item.mediaItem
            if (mi != null) {
                LandscapeCard(
                    item = mi,
                    onClick = onClick,
                    modifier = Modifier.width(FilmyLandscapeWidth),
                    watched = item.watched,
                    progress = progress,
                )
            } else {
                // JF-only fanart bez MediaItem → plakát (fallback, jako telefonní LibraryRowsScreen).
                FilmyPosterCard(item, onClick, progress)
            }
        }
        // POSTER / COVER / LIST → plakát 2:3 (LIST-řada vodorovně = taky plakát; svislý seznam = Filmotéka M2.4).
        else -> FilmyPosterCard(item, onClick, progress)
    }
}

@Composable
private fun FilmyPosterCard(item: HomeRowItem, onClick: () -> Unit, progress: Float?) {
    PosterCard(
        posterUrl = item.posterUrl,
        title = item.title,
        year = item.year?.toString(),
        onClick = onClick,
        modifier = Modifier.width(FilmyPosterWidth),
        imdbId = item.mediaItem?.imdbId,
        tmdbId = item.mediaItem?.tmdbId,
        csfdYear = item.year,
        watched = item.watched,
        progress = progress,
    )
}
