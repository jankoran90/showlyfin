package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Knihovna" appky Filmy.
 * Reuse sdíleného [LibraryRowsViewModel] (každá JF knihovna = řada, per-profil whitelist/pořadí). Položka s
 * Trakt/TMDB matchem → bohatá karta (klik → DetailScreen); JF-only → jednoduchý plakát (JF detail = pozdější
 * milník). Render = řady (mřížka/seznam, přepínač v liště, výchozí seznam).
 */
@Composable
fun FilmyLibraryScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    vm: LibraryRowsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    LaunchedEffect(Unit) { vm.load() }

    val rails = state.rows.map { row ->
        FilmyRailData(row.libraryId, row.libraryName, row.items.map { it.toHomeRowItem() })
    }
    fun onJf(row: com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem) {
        row.jellyfinId?.let(onOpenJellyfinDetail)
    }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = { FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID } },
        ) {
            Text(
                text = "Knihovna",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.isLoading && rails.isEmpty() -> CircularProgressIndicator()
                rails.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "Knihovna je prázdná",
                    text = "Přihlas se k Jellyfinu v Nastavení — tvoje filmové knihovny se objeví tady.",
                )
                viewMode == ViewMode.LIST -> FilmyRailsList(rails, onOpenDetail, ::onJf)
                else -> FilmyRailsGrid(rails, onOpenDetail, ::onJf)
            }
        }
    }
}
