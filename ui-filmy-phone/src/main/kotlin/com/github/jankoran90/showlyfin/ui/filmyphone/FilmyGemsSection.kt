package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.lapidary.LapidaryCountry
import com.github.jankoran90.showlyfin.feature.discover.lapidary.TvLapidarySettingsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita vlna 2 — blok „Vzácné klenoty" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [TvLapidarySettingsViewModel] (per-profil, sync TV↔telefon) → volby 1:1 s TV
 * `TvLapidarySettingsBlock`. Touch ovladače ([FilmySettingRows]). Konfiguruje `FilmyGemsScreen`.
 */
@Composable
fun FilmyGemsSection(vm: TvLapidarySettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val enabledCountries = LapidaryCountry.entries.filter { vm.isEnabled(prefs, it.iso) }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Vzácné klenoty")
        SettingMultiChips(
            label = "Země",
            subtitle = "Které regionální řady sekce zobrazit",
            options = LapidaryCountry.entries,
            enabled = enabledCountries,
            labelOf = { it.label },
            onToggle = { country, on -> vm.setCountry(country.iso, on) },
        )
        SettingChips(
            label = "Řazení",
            subtitle = "Podle čeho řadit klenoty v řadě",
            options = listOf("rank", "vote"),
            selected = prefs.sort,
            labelOf = ::sortLabel,
            onSelect = vm::setSort,
        )
    }
}

private fun sortLabel(sort: String): String = when (sort) {
    "vote" -> "Podle hodnocení"
    else -> "Podle kánonu"
}
