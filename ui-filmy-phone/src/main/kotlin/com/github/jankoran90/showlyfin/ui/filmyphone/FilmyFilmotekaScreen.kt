package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.4 — telefonní Filmotéka appky „Filmy".
 *
 * Transpozice TV Filmotéky: sdílený datový mozek [TvFilmotekaViewModel] (feature vrstva, bez TV závislosti),
 * telefonní render = MŘÍŽKA plakátů NEBO SEZNAM bohatých řádků (přepínač vpravo v liště os — žádná další lišta).
 * Nahoře chipy osy (Vše / Žánr / Země) + pro osu „Vše" chipy řazení (Nedávno / Abecedně). Klik → sdílený
 * DetailScreen (přes shell back-stack). Live registrace uloženého zdroje řeší VM (savedKeys reload).
 *
 * View-styl (mřížka/seznam) je zatím lokální (per-session); per-profil uložení s presety = M2.5 (Nastavení).
 */
@Composable
fun FilmyFilmotekaScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvFilmotekaViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Výchozí = SEZNAM (bohaté řádky jako domov, přání usera 2026-07-17). Přepínač vpravo v liště.
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    // VM je retained na úrovni shellu (sekce se přepínají when-em) → při vstupu obnov výchozí osu z Nastavení.
    LaunchedEffect(Unit) { vm.applyDefaultAxis() }

    Column(modifier.fillMaxSize()) {
        FilmotekaChips(
            axis = state.axis,
            allSort = state.allSort,
            viewMode = viewMode,
            total = state.total,
            onMenu = onMenu,
            onAxis = vm::setAxis,
            onAllSort = vm::setAllSort,
            onToggleView = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.rails.isEmpty() -> FilmotekaEmpty()
                viewMode == ViewMode.LIST -> FilmotekaList(state.rails, onOpenDetail)
                else -> FilmotekaGrid(state.rails, onOpenDetail)
            }
        }
    }
}

/**
 * Lišta os splynulá s horní lištou (☰ + chipy os + přepínač zobrazení vpravo) a — jen pro osu „Vše" —
 * druhá lišta řazení pod ní. Max 2 lišty (přání usera). Vzor ovladačů = princip „lišta v každé sekci".
 */
@Composable
private fun FilmotekaChips(
    axis: FilmotekaAxis,
    allSort: FilmotekaAllSort,
    viewMode: ViewMode,
    total: Int,
    onMenu: () -> Unit,
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onToggleView: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ☰ + chipy os (scroll) + počet titulů + přepínač zobrazení vpravo — jeden pruh (splynutí s lištou).
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                if (total > 0) {
                    Text(
                        text = "$total filmů",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                IconButton(onClick = onToggleView) {
                    if (viewMode == ViewMode.GRID) {
                        Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = "Zobrazit jako seznam")
                    } else {
                        Icon(Icons.Rounded.GridView, contentDescription = "Zobrazit jako mřížku")
                    }
                }
            },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAxis.entries.forEach { a ->
                    FilterChip(selected = axis == a, onClick = { onAxis(a) }, label = { Text(a.chipLabel) })
                }
            }
        }
        if (axis == FilmotekaAxis.ALL) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAllSort.entries.forEach { s ->
                    FilterChip(selected = allSort == s, onClick = { onAllSort(s) }, label = { Text(s.chipLabel) })
                }
            }
        }
    }
}

/** Mřížka plakátů se sekcemi. Víc řad (žánr/země) → full-width nadpis mezi sekcemi; jedna řada (Vše) → bez nadpisu. */
@Composable
private fun FilmotekaGrid(rails: List<FilmotekaRail>, onOpenDetail: (MediaItem) -> Unit) {
    val cols = rememberGridColumnPref()
    val showHeaders = rails.size > 1
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(rail.title)
                }
            }
            gridItems(rail.items, key = { it.key }) { row ->
                row.mediaItem?.let { mi -> MediaCard(item = mi, onClick = { onOpenDetail(mi) }) }
            }
        }
    }
}

/** Seznam bohatých řádků (cover + název + režie + rok · žánry + popis) — stejný řádek jako domov. */
@Composable
private fun FilmotekaList(rails: List<FilmotekaRail>, onOpenDetail: (MediaItem) -> Unit) {
    val showHeaders = rails.size > 1
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}") { SectionHeader(rail.title) }
            }
            listItems(rail.items, key = { it.key }) { row ->
                row.mediaItem?.let { mi ->
                    MediaRow(
                        item = mi,
                        onClick = { onOpenDetail(mi) },
                        watched = row.watched,
                        genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                        showDirector = true,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** Prázdná Filmotéka — žádné zdroje/přihlášení. */
@Composable
private fun FilmotekaEmpty() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Filmotéka je zatím prázdná",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Přihlas se k Traktu nebo Jellyfinu a přidej zdroje — objeví se tu tvoje filmy seřazené podle žánru, země i abecedy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val FilmotekaAxis.chipLabel: String
    get() = when (this) {
        FilmotekaAxis.ALL -> "Vše"
        FilmotekaAxis.GENRE -> "Žánr"
        FilmotekaAxis.COUNTRY -> "Země"
    }

private val FilmotekaAllSort.chipLabel: String
    get() = when (this) {
        FilmotekaAllSort.RECENT -> "Nedávno přidané"
        FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
    }
