package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita vlna 2 — blok „Obraz a zvuk" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [SettingsViewModel]. Zatím jen normalizace hlasitosti filmu (DRC) — ovlivňuje
 * telefonní přehrávání. Passthrough 5.1 do AVR je TV/AVR-specifické → vynecháno (parita gap ověřit).
 */
@Composable
fun FilmyAudioSection(vm: SettingsViewModel = hiltViewModel()) {
    val sys by vm.uiState.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Obraz a zvuk")
        // user 2026-08-18 (Rurouni Kenshin: "zapnul jsem ho na jednom filmu a teď je všude") —
        // profilový jazykový chip PŘESUNUT SEM z menu karty filmu, kde matl vedle per-titul volby
        // a hrozilo omylem přepnout zvuk VŠECH filmů. Tady je jasné, že se týká celého profilu.
        SettingSwitchRow(
            title = "Zvuk: český dabing",
            subtitle = "Když soubor nabízí obě stopy, appka pustí dabing místo originálu. Platí pro celý profil — jednotlivý titul lze přebít v jeho vlastním menu (\"Tenhle titul\").",
            checked = sys.audioChoice == com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.CZ,
            onCheckedChange = {
                vm.setAudioChoice(
                    if (it) com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.CZ
                    else com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.ORIGINAL,
                )
            },
        )
        SettingChips(
            label = "Normalizace hlasitosti filmu",
            subtitle = "Ztlumí hlasité scény, zesílí ticho",
            options = listOf(0, 1, 2, 3),
            selected = sys.movieDrcLevel,
            labelOf = ::drcLabel,
            onSelect = vm::setMovieDrcLevel,
        )
    }
}

private fun drcLabel(level: Int): String = when (level) {
    0 -> "Vypnuto"
    1 -> "Mírná"
    2 -> "Střední"
    else -> "Noční"
}
