package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
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
    // ORCHARD (user 07-19) — řazení v Knihovně (parita s Filmotékou). Render-time nad řadami per knihovna:
    // NEDÁVNO = pořadí ze serveru (DATE_CREATED desc), ABECEDNĚ = dle názvu (Collator cs). Zatím per-session
    // (perzistence výchozího řazení = follow-up).
    var sort by remember { mutableStateOf(FilmotekaAllSort.RECENT) }
    LaunchedEffect(Unit) { vm.load() }

    val rails = state.rows.map { row ->
        FilmyRailData(row.libraryId, row.libraryName, sortItems(row.items.map { it.toHomeRowItem() }, sort))
    }
    fun onJf(row: HomeRowItem) {
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
        if (rails.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAllSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(sortLabel(s)) })
                }
            }
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

/** Řazení položek knihovní řady. NEDÁVNO = pořadí ze serveru (beze změny); ABECEDNĚ = dle názvu (Collator cs). */
private fun sortItems(items: List<HomeRowItem>, sort: FilmotekaAllSort): List<HomeRowItem> = when (sort) {
    FilmotekaAllSort.RECENT -> items
    FilmotekaAllSort.ALPHABETICAL -> {
        val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
        items.sortedWith(Comparator { a, b -> coll.compare(a.title, b.title) })
    }
}

private fun sortLabel(sort: FilmotekaAllSort): String = when (sort) {
    FilmotekaAllSort.RECENT -> "Nedávno přidané"
    FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
}
