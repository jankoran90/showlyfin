package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita vlna 2 — blok „Přehrávač" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [SettingsViewModel] (respektuje ho sdílený PlaybackScreen) → volby 1:1 s TV
 * `TvSettingsScreen` položka Přehrávač. „Vzdát necachovaný zdroj po" = raw `trakt_prefs` klíč
 * `rd_stall_timeout_sec` (čte i DetailViewModel). Touch ovladače ([FilmySettingRows]).
 */
@Composable
fun FilmyPlayerSection(vm: SettingsViewModel = hiltViewModel()) {
    val sys by vm.uiState.collectAsStateWithLifecycle()
    // PASSPORT ③ — kdy vzdát necachovaný RD zdroj (drží 0 %). Raw trakt_prefs (čte i DetailViewModel).
    val ctx = LocalContext.current
    val rdPrefs = remember { ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE) }
    var rdStall by remember { mutableStateOf(rdPrefs.getInt("rd_stall_timeout_sec", 120)) }
    // FISSION (SHW-98) — vynutit SW dekodér obrazu (Exynos/Tensor padá na některých HEVC). Stejný raw
    // klíč `trakt_prefs` jako čte MoviePlayerService.
    var forceSw by remember {
        mutableStateOf(rdPrefs.getBoolean(PlayerPrefs.FORCE_SW_DECODER_KEY, PlayerPrefs.DEFAULT_FORCE_SW_DECODER))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Přehrávač")
        SettingChips(
            label = "Skrýt ovládání po",
            subtitle = "Nečinnost, po které zmizí lišta přehrávače",
            options = PlayerPrefs.CONTROLS_HIDE_SEC_OPTIONS,
            selected = sys.playerControlsHideSec,
            labelOf = ::hideDelayLabel,
            onSelect = vm::setPlayerControlsHideSec,
        )
        SettingChips(
            label = "Krok převíjení",
            subtitle = "O kolik posunou tlačítka ⏮ ⏭ a osa",
            options = PlayerPrefs.SEEK_STEP_SEC_OPTIONS,
            selected = sys.playerSeekStepSec,
            labelOf = { "$it s" },
            onSelect = vm::setPlayerSeekStepSec,
        )
        SettingChips(
            label = "Vzdát necachovaný zdroj po",
            subtitle = "Když se na Real-Debrid nezačne stahovat (0 %) za tuhle dobu, appka to vzdá a vyzve k jinému zdroji",
            options = listOf(5, 10, 30, 60, 120),
            selected = rdStall,
            labelOf = { if (it >= 60) "${it / 60} min" else "$it s" },
            onSelect = { rdStall = it; rdPrefs.edit().putInt("rd_stall_timeout_sec", it).apply() },
        )
        SettingSwitchRow(
            title = "Vynutit softwarové dekódování obrazu",
            subtitle = "Když nějaký film (často HEVC/H.265) bliká, trhá se nebo hlásí „nelze přehrát\", zapni — " +
                "přehraje se softwarově (spolehlivější, o něco vyšší zátěž baterie). Vypnuto = appka zkusí " +
                "softwarový dekodér sama až při chybě.",
            checked = forceSw,
            onCheckedChange = { forceSw = it; rdPrefs.edit().putBoolean(PlayerPrefs.FORCE_SW_DECODER_KEY, it).apply() },
        )
    }
}

private fun hideDelayLabel(sec: Int): String = if (sec <= 0) "Nikdy" else "$sec s"
