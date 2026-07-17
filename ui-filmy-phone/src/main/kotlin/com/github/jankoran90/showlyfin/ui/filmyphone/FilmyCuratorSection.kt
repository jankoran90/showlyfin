package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.CuratorKind
import com.github.jankoran90.showlyfin.core.domain.CuratorSurprise
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorSettingsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita — blok „Kurátor „Pro tebe"" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [CuratorSettingsViewModel] (per-profil `CuratorPrefs`, sync TV↔telefon) → volby jsou
 * 1:1 s TV `TvCuratorSettingsBlock`. Touch ovladače ([FilmySettingRows]). Přímo řídí dnešní „Pro tebe".
 */
@Composable
fun FilmyCuratorSection(vm: CuratorSettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Kurátor „Pro tebe\"")
        SettingSwitchRow(
            title = "Kurátorská doporučení",
            subtitle = "AI mozek navrhuje z tvého vkusu; vypnuto = jen dle historie",
            checked = prefs.enabled,
            onCheckedChange = vm::setEnabled,
        )
        AnimatedVisibility(visible = prefs.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingPercentSlider(
                    label = "Míra objevování",
                    subtitle = "Nižší = jistota (drž se vkusu) · vyšší = překvapení",
                    value = prefs.discovery,
                    onValue = vm::setDiscovery,
                )
                SettingChips(
                    label = "Druh obsahu",
                    subtitle = "Co má kurátor doporučovat",
                    options = listOf(CuratorKind.BOTH, CuratorKind.MOVIE, CuratorKind.SHOW),
                    selected = prefs.kind,
                    labelOf = ::kindLabel,
                    onSelect = vm::setKind,
                )
                SettingMultiChips(
                    label = "Kudy překvapovat",
                    subtitle = "Nevybráno = kurátor volí sám (blízké tituly + sousední žánry)",
                    options = CuratorSurprise.entries,
                    enabled = prefs.surprise,
                    labelOf = ::surpriseLabel,
                    onToggle = vm::setSurprise,
                )
            }
        }
    }
}

private fun kindLabel(kind: CuratorKind): String = when (kind) {
    CuratorKind.BOTH -> "Filmy i seriály"
    CuratorKind.MOVIE -> "Filmy"
    CuratorKind.SHOW -> "Seriály"
}

private fun surpriseLabel(mode: CuratorSurprise): String = when (mode) {
    CuratorSurprise.NEAR -> "Blízké tituly"
    CuratorSurprise.GENRES -> "Sousední žánry"
    CuratorSurprise.UNKNOWN -> "Skryté klenoty"
    CuratorSurprise.ERA -> "Jiná éra"
}
