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
    val hybridGenres by vm.hybridGenres.collectAsStateWithLifecycle()
    val showCollections by vm.showCollections.collectAsStateWithLifecycle()
    val onlyWithSource by vm.onlyWithSource.collectAsStateWithLifecycle()

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
            title = "Chci vidět",
            subtitle = "Tituly z Trakt watchlistu, a u profilů bez Traktu (dětských) z místního " +
                "seznamu „Chci vidět\" — filmy i seriály",
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
            options = listOf(FilmotekaAllSort.RECENT, FilmotekaAllSort.ALPHABETICAL, FilmotekaAllSort.RUNTIME),
            selected = allSort,
            labelOf = ::allSortLabel,
            onSelect = vm::setAllSort,
        )
        SettingSwitchRow(
            title = "Jen s dohledaným zdrojem",
            subtitle = "Ukázat jen tituly, které jdou hned pustit — mají uložený zdroj nebo jsou v Jellyfin " +
                "knihovně. Vypnuto = uvidíš i filmy z „Chci vidět\" a Oblíbených, kterým se zdroj teprve shání. " +
                "Platí i pro řadu Filmotéka na domovské obrazovce. U dětských profilů je to VÝCHOZÍ " +
                "zapnuté — dítě nepozná, že se zdroj teprve shání, a karta bez zdroje je pro něj rozbitá.",
            checked = onlyWithSource,
            onCheckedChange = { vm.setOnlyWithSource(it) },
        )
        SettingSwitchRow(
            title = "Sdružovat kolekce",
            subtitle = "Díly jedné kolekce (Auta, Auta 2, Auta 3…) zastoupí JEDNA karta na svém místě v seznamu; klik otevře její obsah. Spojí i díly z různých zdrojů (Jellyfin + uložené zdroje). Vypnuto = každý díl zvlášť.",
            checked = showCollections,
            onCheckedChange = { vm.setShowCollections(it) },
        )
        SettingSwitchRow(
            title = "Hybridní žánry",
            subtitle = "Slučovat žánry do kombinovaných řad (Akční komedie, Sci-fi horor, Superhrdinský…). Vypnuto = řada podle prvního žánru.",
            checked = hybridGenres,
            onCheckedChange = { vm.setHybridGenres(it) },
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
    FilmotekaAllSort.RUNTIME -> "Od nejkratšího"
}
