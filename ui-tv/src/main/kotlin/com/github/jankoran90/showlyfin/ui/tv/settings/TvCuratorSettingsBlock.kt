package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.CuratorKind
import com.github.jankoran90.showlyfin.core.domain.CuratorSurprise
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorSettingsViewModel
import kotlin.math.roundToInt

/**
 * AUTEUR (SHW-91) Fáze C1 — blok „Kurátor" v TV Nastavení: master switch + osa jistota↔překvapení
 * (stepper %) + druh obsahu + rozbalovací módy překvapení. Vše přes D-pad steppery/přepínače (žádný
 * `Slider`). Nálada/žánr/model (textové jemnosti) jsou na telefonu, kde je klávesnice. Vzor
 * [TvFilmotekaSettingsBlock].
 */
@Composable
fun TvCuratorSettingsBlock(vm: CuratorSettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Kurátor") {
        TvToggleRow(
            label = "Kurátorská doporučení „Pro tebe\"",
            subtitle = "AI mozek navrhuje z tvého vkusu; vypnuto = jen doporučení dle historie",
            checked = prefs.enabled,
            onCheckedChange = vm::setEnabled,
        )
        AnimatedVisibility(visible = prefs.enabled) {
            Column {
                TvValueStepperRow(
                    label = "Míra objevování",
                    subtitle = "Nižší = jistota (drž se vkusu) · vyšší = překvapení",
                    percent = (prefs.discovery * 100).roundToInt(),
                    onPercent = { vm.setDiscovery(it / 100f) },
                    step = 5,
                )
                TvOptionStepperRow(
                    label = "Druh obsahu",
                    subtitle = "Co má kurátor doporučovat",
                    options = listOf(CuratorKind.BOTH, CuratorKind.MOVIE, CuratorKind.SHOW),
                    selected = prefs.kind,
                    labelOf = ::kindLabel,
                    onSelect = vm::setKind,
                )
                SurpriseSection(enabled = prefs.surprise, onToggle = vm::setSurprise)
            }
        }
    }
}

/** Rozbalovací seznam módů překvapení — kudy smí kurátor odbočit od vkusu. Prázdné = default (blízké + žánry). */
@Composable
private fun SurpriseSection(
    enabled: Set<CuratorSurprise>,
    onToggle: (CuratorSurprise, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Kudy překvapovat",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Nevybráno = kurátor volí sám (blízké tituly + sousední žánry)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column {
            CuratorSurprise.entries.forEach { mode ->
                TvToggleRow(
                    label = surpriseLabel(mode),
                    subtitle = surpriseSubtitle(mode),
                    checked = mode in enabled,
                    onCheckedChange = { onToggle(mode, it) },
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

private fun surpriseSubtitle(mode: CuratorSurprise): String = when (mode) {
    CuratorSurprise.NEAR -> "Příbuzné oblíbeným (režisér, herci, tón)"
    CuratorSurprise.GENRES -> "Kousek vedle, ne úplně mimo"
    CuratorSurprise.UNKNOWN -> "Málo známé klenoty sedící vkusu"
    CuratorSurprise.ERA -> "Jiná dekáda ve stejném duchu"
}
