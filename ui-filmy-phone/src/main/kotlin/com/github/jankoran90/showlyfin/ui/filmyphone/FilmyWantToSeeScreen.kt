package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * zdroj" + tlačítko „dohledat zdroje" (backfill [FilmyWatchlistCacheViewModel]) s živým průběhem — user chtěl
 * status dohledávání vidět rovnou tady, ne zahrabaný v Nastavení (2026-07-18). Klik → sdílený DetailScreen.
 */
@Composable
fun FilmyWantToSeeScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvWantToSeeViewModel = hiltViewModel(),
    cacheVm: FilmyWatchlistCacheViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cacheState by cacheVm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    val running = cacheState is FilmyWatchlistCacheViewModel.State.Running

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                // Backfill zdrojů pro celý watchlist — user chce „nakopnout" a vidět status rovnou tady.
                IconButton(enabled = !running, onClick = { cacheVm.runBackfill() }) {
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = "Dohledat zdroje pro celý watchlist")
                    }
                }
                FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }
            },
        ) {
            Column {
                Text(
                    text = "Chci vidět",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val sub = cacheStatusLine(cacheState)
                    ?: if (state.items.isNotEmpty()) "${state.items.size} filmů · ${state.savedCount} s uloženým zdrojem" else null
                if (sub != null) {
                    Text(
                        text = sub,
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

/** Průběh backfillu jako řádek pod titulkem — má přednost před statickým počtem, dokud běží / hlásí výsledek. */
private fun cacheStatusLine(s: FilmyWatchlistCacheViewModel.State): String? = when (s) {
    is FilmyWatchlistCacheViewModel.State.Running -> "Dohledávám zdroje na pozadí… ${s.done}/${s.total}"
    is FilmyWatchlistCacheViewModel.State.Done ->
        if (s.requested == 0) null
        else "Spuštěno dohledání ${s.requested} filmů (na pozadí, chvíli to potrvá)."
    is FilmyWatchlistCacheViewModel.State.Error -> "Chyba: ${s.message}"
    FilmyWatchlistCacheViewModel.State.Idle -> null
}
