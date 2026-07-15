package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.lapidary.LapidaryCountry
import com.github.jankoran90.showlyfin.feature.discover.lapidary.TvLapidarySettingsViewModel

/**
 * LAPIDARY (SHW-96) — blok „Vzácné klenoty" v TV Nastavení: přepínače zemí (které regionální řady sekce
 * zobrazit) + řazení klenotů. Per-profil, synced TV↔telefon (viz [TvLapidarySettingsViewModel]).
 */
@Composable
fun TvLapidarySettingsBlock(vm: TvLapidarySettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Vzácné klenoty") {
        LapidaryCountry.entries.forEach { country ->
            TvToggleRow(
                label = country.label,
                checked = vm.isEnabled(prefs, country.iso),
                onCheckedChange = { vm.setCountry(country.iso, it) },
            )
        }
        TvOptionStepperRow(
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
