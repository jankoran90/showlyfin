package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.lapidary.TvLapidaryViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Vzácné klenoty" appky Filmy.
 * Reuse sdíleného [TvLapidaryViewModel] (osa = ZEMĚ, jedna řada na zapnutou zemi, backend /gems/catalog).
 * Render = řady `HomeRowItem` (mřížka/seznam, přepínač v liště, výchozí seznam). Klik → sdílený DetailScreen.
 */
@Composable
fun FilmyGemsScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvLapidaryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    val rails = state.rails.map { FilmyRailData(it.id, it.title, it.items) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = { FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID } },
        ) {
            Text(
                text = "Vzácné klenoty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading && rails.isEmpty() -> CircularProgressIndicator()
                rails.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.Diamond,
                    title = "Zatím žádné klenoty",
                    text = "Vzácné klenoty jsou hůř dostupné filmy, které se podařilo najít v kvalitních zdrojích. " +
                        "Zapni si země v Nastavení a objeví se tu.",
                )
                viewMode == ViewMode.LIST -> FilmyRailsList(rails, onOpenDetail)
                else -> FilmyRailsGrid(rails, onOpenDetail)
            }
        }
    }
}
