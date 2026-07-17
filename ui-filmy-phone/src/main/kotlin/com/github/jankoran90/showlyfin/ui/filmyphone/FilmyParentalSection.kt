package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.ui.phone.ParentalPrefsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita vlna 2 — blok „Rodičovská kontrola" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [ParentalPrefsViewModel] → volby 1:1 s TV `TvParentalSettingsBlock`. Filmy má 2 profily
 * (Dospělý/Děti), takže věkový strop dává smysl. Touch ovladače ([FilmySettingRows]).
 */
@Composable
fun FilmyParentalSection(vm: ParentalPrefsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Rodičovská kontrola")
        SettingChips(
            label = "Věkový strop obsahu",
            subtitle = effectiveCapSubtitle(state.explicitCap, state.effectiveCap),
            options = ParentalPrefsViewModel.AGE_CAP_OPTIONS,
            selected = state.explicitCap,
            labelOf = { if (it <= 0) "Vypnuto (dle Jellyfinu)" else "do $it let" },
            onSelect = vm::setCap,
        )
        SettingSwitchRow(
            title = "Skrýt i neohodnocené",
            subtitle = "Přísný režim: skryj i tituly bez věkové certifikace (jen když je strop aktivní)",
            checked = state.hideUnrated,
            onCheckedChange = vm::setHideUnrated,
        )
    }
}

private fun effectiveCapSubtitle(explicit: Int, effective: Int?): String = when {
    effective == null -> "Vypnuto — zobrazí se vše"
    explicit > 0 && effective == explicit -> "Aktivní: do $effective let (skrývá se napříč doporučeními a hledáním)"
    else -> "Aktivní: do $effective let (řízeno Jellyfinem)"
}
