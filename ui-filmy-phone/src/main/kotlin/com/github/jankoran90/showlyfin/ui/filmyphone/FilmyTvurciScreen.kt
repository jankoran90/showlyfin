package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.ui.phone.CREATOR_CATEGORIES
import com.github.jankoran90.showlyfin.ui.phone.OblibeniScreen

/**
 * SPOTLIGHT (FLM-02, user 2026-08-27: „udelej Postranni sekci Tvurci, kde budou horní tab list
 * sekce Rezie, Herci, atd.") — sekce „Tvůrci" telefonní appky Filmy.
 *
 * Tělo je SDÍLENÁ [OblibeniScreen] (COMPASS, SHW-44) — ta už má přesně ten tab list, který user
 * nakreslil (Herci / Režiséři / Scénáristé / Producenti / Skladatelé / Vydavatelství), mřížku
 * portrétů, proklik na filmografii i dlouhý stisk = odebrat. Filmy chyběl jen vstup do navigace.
 * Tab „Filmy" je tu ZÁMĚRNĚ pryč ([CREATOR_CATEGORIES]) — sekce se jmenuje Tvůrci a oblíbené filmy
 * mají vlastní řadu na Domově i ve Filmotéce, takže se nic neztrácí.
 */
@Composable
fun FilmyTvurciScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        FilmySectionBar(onMenu = onMenu) {
            Text(
                text = "Tvůrci",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        OblibeniScreen(
            onOpenDetail = { tmdbId, title ->
                onOpenDetail(
                    MediaItem(
                        traktId = 0L, tmdbId = tmdbId, imdbId = null, title = title, year = null,
                        overview = null, rating = null, genres = null, type = MediaType.MOVIE,
                    ),
                )
            },
            categories = CREATOR_CATEGORIES,
            // Nadpis nese lišta výše — druhý nadpis pod ní by byl duplicitní.
            title = null,
        )
    }
}
