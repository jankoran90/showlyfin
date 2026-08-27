package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode

/**
 * SPOTLIGHT (FLM-02, user 2026-08-27: „1 - nova sekce novinky, systemove upozorneni") — sekce
 * „Novinky": nové filmy a seriály od tvůrců, které uživatel sleduje (⭐ v jejich filmografii).
 *
 * Server porovná filmografie jednou týdně v pátek; tahle obrazovka jen ukáže výsledek, seskupený
 * po tvůrcích — u novinky je „od koho" hlavní informace. Sem míří i proklik systémové notifikace
 * ([com.github.jankoran90.showlyfin.core.ui.ListenNavSignal.EXTRA_OPEN_NOVINKY]).
 */
@Composable
fun FilmyNovinkyScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: FilmyNovinkyViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.GRID) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                FilmyViewToggle(viewMode) {
                    viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                }
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Načíst znovu")
                }
            },
        ) {
            Text(
                text = "Novinky",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.offline -> FilmyEmpty(
                    icon = Icons.Rounded.NewReleases,
                    title = "Novinky se nepodařilo načíst",
                    text = "Server se neozval. Zkus to za chvíli znovu ikonou nahoře.",
                )
                state.rails.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.NewReleases,
                    title = "Zatím žádné novinky",
                    text = "Sleduj tvůrce hvězdičkou v jeho filmografii (sekce Tvůrci) a jakmile mu vyjde " +
                        "nový film nebo seriál, objeví se tady. Kontrolujeme to jednou týdně v pátek.",
                )
                viewMode == ViewMode.LIST -> FilmyRailsList(state.rails, onOpenDetail)
                else -> FilmyRailsGrid(state.rails, onOpenDetail)
            }
        }
    }
}
