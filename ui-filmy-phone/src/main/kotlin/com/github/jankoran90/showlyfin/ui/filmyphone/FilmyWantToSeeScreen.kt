package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
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
import com.github.jankoran90.showlyfin.feature.discover.wanttosee.TvWantToSeeViewModel

/**
 * CINEMATHEQUE — telefonní sekce „Chci vidět" appky Filmy = CELÝ Trakt watchlist (parita s TV
 * `TvWantToSeeScreen`). Dřív šel watchlist na telefon jen jako ořezaná řada v Domů (limit 30 → ~20 vidět);
 * user chtěl vidět všech 90+ jako na TV. Reuse sdíleného [TvWantToSeeViewModel] (žádný ořez). Plochý seznam
 * `MediaItem` → mřížka/seznam (přepínač v liště, výchozí seznam). V liště ukazatel „N filmů · X má uložený
 * zdroj" (kolik watchlistu je rovnou přehratelných — přání usera 2026-07-18). Klik → sdílený DetailScreen.
 */
@Composable
fun FilmyWantToSeeScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvWantToSeeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = { FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID } },
        ) {
            Column {
                Text(
                    text = "Chci vidět",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (state.items.isNotEmpty()) {
                    Text(
                        text = "${state.items.size} filmů · ${state.savedCount} s uloženým zdrojem",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading && state.items.isEmpty() -> CircularProgressIndicator()
                state.items.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.Bookmark,
                    title = "Watchlist je prázdný",
                    text = "Filmy přidané na Traktu do Chci vidět se objeví tady — celý watchlist. Jestli je prázdno i po chvíli, přihlas se k Traktu v Nastavení.",
                )
                viewMode == ViewMode.LIST -> FilmyMediaList(state.items, onOpenDetail)
                else -> FilmyMediaGrid(state.items, onOpenDetail)
            }
        }
    }
}
