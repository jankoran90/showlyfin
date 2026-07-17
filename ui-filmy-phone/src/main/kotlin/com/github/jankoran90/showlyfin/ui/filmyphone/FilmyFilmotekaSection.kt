package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaSettingsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita vlna 2 — blok „Filmotéka" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [TvFilmotekaSettingsViewModel] (per-profil, sync TV↔telefon) → volby 1:1 s TV
 * `TvFilmotekaSettingsBlock`. Touch ovladače ([FilmySettingRows]). Konfiguruje `FilmyFilmotekaScreen`.
 * Pozn.: settings VM je jiný než obsahový `TvFilmotekaViewModel`.
 */
@Composable
fun FilmyFilmotekaSection(vm: TvFilmotekaSettingsViewModel = hiltViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val axis by vm.defaultAxis.collectAsStateWithLifecycle()
    val allSort by vm.allSort.collectAsStateWithLifecycle()
    val enabledRegions by vm.enabledRegions.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Filmotéka")
        SettingSwitchRow(
            title = "Jellyfin knihovna",
            subtitle = "Filmy a seriály z tvých Jellyfin knihoven",
            checked = FilmotekaSource.JELLYFIN in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.JELLYFIN, it) },
        )
        SettingSwitchRow(
            title = "Zapamatované zdroje",
            subtitle = "Tituly s uloženým zdrojem přehrávání",
            checked = FilmotekaSource.WORKING in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.WORKING, it) },
        )
        SettingSwitchRow(
            title = "Trakt watchlist",
            subtitle = "Filmy a seriály z tvého Trakt watchlistu",
            checked = FilmotekaSource.TRAKT_WATCHLIST in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.TRAKT_WATCHLIST, it) },
        )
        SettingSwitchRow(
            title = "Oblíbené",
            subtitle = "Filmy přidané mezi oblíbené",
            checked = FilmotekaSource.FAVORITES in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.FAVORITES, it) },
        )
        SettingChips(
            label = "Výchozí osa",
            subtitle = "Podle čeho se Filmotéka po otevření přeskupí",
            options = listOf(FilmotekaAxis.ALL, FilmotekaAxis.GENRE, FilmotekaAxis.COUNTRY),
            selected = axis,
            labelOf = ::axisLabel,
            onSelect = vm::setDefaultAxis,
        )
        SettingChips(
            label = "Řazení řady „Vše\"",
            subtitle = "Jak seřadit plochý výpis v ose Vše",
            options = listOf(FilmotekaAllSort.RECENT, FilmotekaAllSort.ALPHABETICAL),
            selected = allSort,
            labelOf = ::allSortLabel,
            onSelect = vm::setAllSort,
        )
        SettingMultiChips(
            label = "Kinematografie (osa Země)",
            subtitle = "Které regionální řady zobrazit v ose Země",
            options = CinematographyRegion.entries.filter { it != CinematographyRegion.OSTATNI },
            enabled = enabledRegions,
            labelOf = { it.label },
            onToggle = vm::setRegion,
        )
    }
}

private fun axisLabel(axis: FilmotekaAxis): String = when (axis) {
    FilmotekaAxis.ALL -> "Vše"
    FilmotekaAxis.GENRE -> "Žánr"
    FilmotekaAxis.COUNTRY -> "Země"
}

private fun allSortLabel(sort: FilmotekaAllSort): String = when (sort) {
    FilmotekaAllSort.RECENT -> "Nedávno přidané"
    FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
}
