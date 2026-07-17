package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.4 — telefonní Filmotéka appky „Filmy".
 *
 * Transpozice TV Filmotéky: sdílený datový mozek [TvFilmotekaViewModel] (feature vrstva, bez TV závislosti),
 * telefonní render = MŘÍŽKA plakátů se sekcemi (řada = full-width nadpis + karty pod ním) místo TV řad.
 * Nahoře chipy osy (Vše / Žánr / Země) + pro osu „Vše" chipy řazení (Nedávno / Abecedně). Klik na kartu →
 * sdílený DetailScreen (přes shell back-stack). Live registrace uloženého zdroje řeší VM (savedKeys reload).
 */
@Composable
fun FilmyFilmotekaScreen(
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvFilmotekaViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // VM je retained na úrovni shellu (sekce se přepínají when-em) → při vstupu obnov výchozí osu z Nastavení.
    LaunchedEffect(Unit) { vm.applyDefaultAxis() }

    Column(modifier.fillMaxSize()) {
        FilmotekaChips(
            axis = state.axis,
            allSort = state.allSort,
            onAxis = vm::setAxis,
            onAllSort = vm::setAllSort,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.rails.isEmpty() -> FilmotekaEmpty()
                else -> FilmotekaGrid(state.rails, onOpenDetail)
            }
        }
    }
}

/** Chipy osy (vždy) + chipy řazení (jen osa „Vše"). Vodorovně scrollovatelné, tenký pruh. */
@Composable
private fun FilmotekaChips(
    axis: FilmotekaAxis,
    allSort: FilmotekaAllSort,
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChipRow {
            FilmotekaAxis.entries.forEach { a ->
                FilterChip(
                    selected = axis == a,
                    onClick = { onAxis(a) },
                    label = { Text(a.chipLabel) },
                )
            }
        }
        if (axis == FilmotekaAxis.ALL) {
            ChipRow {
                FilmotekaAllSort.entries.forEach { s ->
                    FilterChip(
                        selected = allSort == s,
                        onClick = { onAllSort(s) },
                        label = { Text(s.chipLabel) },
                    )
                }
            }
        }
    }
}

/** Tenká vodorovná řada chipů (šetří výšku, ať zbyde místo na obsah). */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Mřížka plakátů se sekcemi. Víc řad (žánr/země) → full-width nadpis mezi sekcemi; jedna řada (Vše) → bez nadpisu. */
@Composable
private fun FilmotekaGrid(
    rails: List<com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail>,
    onOpenDetail: (MediaItem) -> Unit,
) {
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
                    Text(
                        text = rail.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
            }
            items(rail.items, key = { it.key }) { row ->
                row.mediaItem?.let { mi ->
                    MediaCard(item = mi, onClick = { onOpenDetail(mi) })
                }
            }
        }
    }
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
