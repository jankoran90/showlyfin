package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaSettingsViewModel

/**
 * CINEMATHEQUE (SHW-90) — blok „Filmotéka" v TV Nastavení: 4 toggly zdrojů + výchozí osa. Regiony (osa
 * Země) přijdou ve F2. Vzor [TvContentSettingsBlocks].
 */
@Composable
fun TvFilmotekaSettingsBlock(vm: TvFilmotekaSettingsViewModel = hiltViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val axis by vm.defaultAxis.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Filmotéka") {
        TvToggleRow(
            label = "Jellyfin knihovna",
            subtitle = "Filmy a seriály z tvých Jellyfin knihoven",
            checked = FilmotekaSource.JELLYFIN in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.JELLYFIN, it) },
        )
        TvToggleRow(
            label = "Zapamatované zdroje",
            subtitle = "Tituly s uloženým zdrojem přehrávání",
            checked = FilmotekaSource.WORKING in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.WORKING, it) },
        )
        TvToggleRow(
            label = "Trakt watchlist",
            subtitle = "Filmy a seriály z tvého Trakt watchlistu",
            checked = FilmotekaSource.TRAKT_WATCHLIST in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.TRAKT_WATCHLIST, it) },
        )
        TvToggleRow(
            label = "Oblíbené",
            subtitle = "Filmy přidané mezi oblíbené",
            checked = FilmotekaSource.FAVORITES in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.FAVORITES, it) },
        )
        TvOptionStepperRow(
            label = "Výchozí osa",
            subtitle = "Podle čeho se Filmotéka po otevření přeskupí",
            // F1: jen Žánr. Osa Země (kinematografie) přijde ve F2.
            options = listOf(FilmotekaAxis.GENRE),
            selected = axis,
            labelOf = ::axisLabel,
            onSelect = vm::setDefaultAxis,
        )
    }
}

private fun axisLabel(axis: FilmotekaAxis): String = when (axis) {
    FilmotekaAxis.GENRE -> "Žánr"
    FilmotekaAxis.COUNTRY -> "Země"
}
