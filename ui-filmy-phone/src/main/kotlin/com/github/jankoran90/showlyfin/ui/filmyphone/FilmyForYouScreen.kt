package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Recommend
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
import com.github.jankoran90.showlyfin.feature.discover.foryou.ForYouViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Pro tebe" appky Filmy.
 * Reuse sdíleného [ForYouViewModel] (kurátor „Pro tebe", per-profil akumulace přes backend). Plochý seznam
 * `MediaItem` → mřížka/seznam (přepínač v liště, výchozí seznam). Klik → sdílený DetailScreen (shell stack).
 */
@Composable
fun FilmyForYouScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: ForYouViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = { FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID } },
        ) {
            Text(
                text = "Pro tebe",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading && state.items.isEmpty() -> CircularProgressIndicator()
                state.items.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.Recommend,
                    title = "Zatím žádná doporučení",
                    text = "Přihlas se k Traktu a ohodnoť pár filmů — kurátor ti tu začne skládat tipy podle tvého vkusu.",
                )
                viewMode == ViewMode.LIST -> FilmyMediaList(state.items, onOpenDetail)
                else -> FilmyMediaGrid(state.items, onOpenDetail)
            }
        }
    }
}
