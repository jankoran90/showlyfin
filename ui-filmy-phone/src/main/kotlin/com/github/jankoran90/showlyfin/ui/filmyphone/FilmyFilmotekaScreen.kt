package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
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
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.4 — telefonní Filmotéka appky „Filmy".
 *
 * Tenký entrypoint: datový mozek [TvFilmotekaViewModel] (feature vrstva, bez TV závislosti) → veškeré nástroje
 * a render obstarává SDÍLENÝ [FilmyBrowseSection] (osy Vše/Žánr/Země, filtr žánru+země, řazení, hledání vč. režie,
 * mřížka/seznam, sheety). Tentýž komponent renderuje i sekce „Pro tebe" → obě vypadají a ovládají se 1:1 (MIRROR).
 */
@Composable
fun FilmyFilmotekaScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvFilmotekaViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // VM je retained na úrovni shellu (sekce se přepínají when-em) → při vstupu obnov výchozí osu z Nastavení.
    LaunchedEffect(Unit) { vm.applyDefaultAxis() }

    FilmyBrowseSection(
        state = state,
        onMenu = onMenu,
        onOpenDetail = onOpenDetail,
        onAxis = vm::setAxis,
        onAllSort = vm::setAllSort,
        onToggleGenre = vm::toggleGenreFilter,
        onClearGenre = vm::clearGenreFilter,
        onToggleCountry = vm::toggleCountryFilter,
        onClearCountry = vm::clearCountryFilter,
        emptyContent = { FilmotekaEmpty() },
        modifier = modifier,
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
