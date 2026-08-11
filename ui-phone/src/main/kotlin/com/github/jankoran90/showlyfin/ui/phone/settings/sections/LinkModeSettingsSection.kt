package com.github.jankoran90.showlyfin.ui.phone.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.network.LinkKind
import com.github.jankoran90.showlyfin.ui.phone.LinkModeState
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel

/**
 * BACKLOG (autodetekce rychlosti linky, user 2026-08-03) — „Kvalita podle linky".
 *
 * Per-ZAŘÍZENÍ (telefon): na WiFi/ethernet (doma) hraje nejvyšší kvalitu, na mobilních datech (venku)
 * dohledá menší alternativu, aby přehrávání nestagovalo. TV je vždy doma → sekce se nezobrazuje na TV.
 * Zdroje jsou per-profil (sdílené TV↔telefon) → tohle ovlivňuje jen PŘEHRÁVÁNÍ na tomto zařízení,
 * nezapisuje nic do profilu (domácí TV by jinak dostala „venkovský" malý zdroj).
 */
@Composable
internal fun LinkModeSection(viewModel: SettingsViewModel) {
    val mode by viewModel.linkMode.collectAsState()
    val kind by viewModel.linkKind.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Kvalita podle linky", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Na domácí WiFi/TV hraje nejvyšší kvalitu. Na mobilních datech dohledá menší zdroj, " +
                    "aby přehrávání nestagovalo. Platí jen pro přehrávání v telefonu (TV je vždy doma).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                "Detekováno: ${detectedLabel(kind)} · Režim: ${effectiveLabel(mode, kind)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            FilterSwitchRow(
                label = "Auto-detekce sítě (WiFi vs. mobilní data)",
                checked = mode.autoDetect,
                onChange = { viewModel.setLinkModeAutoDetect(it) },
            )
            Spacer(Modifier.height(12.dp))

            Text("Režim (lze přepnout i ručně)", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            LinkModeChipRow(
                options = listOf("Auto" to "auto", "Domů" to "home", "Venek" to "away"),
                selected = mode.override,
                onSelect = { viewModel.setLinkModeOverride(it) },
            )
            Spacer(Modifier.height(12.dp))

            Text("Práh pro „venek" (kdy nahradit větší zdroj)", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            FilterStepRow(
                label = "Max. bitrate: ${mode.awayMaxBitrate} Mbps",
                onMinus = { viewModel.setLinkAwayMaxBitrate(mode.awayMaxBitrate - 1) },
                onPlus = { viewModel.setLinkAwayMaxBitrate(mode.awayMaxBitrate + 1) },
            )
            FilterStepRow(
                label = "Max. velikost: ${"%.1f".format(mode.awayMaxSizeGB)} GB",
                onMinus = { viewModel.setLinkAwayMaxSizeGB(mode.awayMaxSizeGB - 0.5) },
                onPlus = { viewModel.setLinkAwayMaxSizeGB(mode.awayMaxSizeGB + 0.5) },
            )
        }
    }
}

@Composable
private fun LinkModeChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun detectedLabel(kind: LinkKind): String = when (kind) {
    LinkKind.WIFI -> "WiFi (doma)"
    LinkKind.ETHERNET -> "Ethernet (doma)"
    LinkKind.CELLULAR -> "Mobilní data (venku)"
    LinkKind.OTHER -> "Neznámá síť"
    LinkKind.NONE -> "Offline"
}

/** Zrcadlí `LinkModePrefs.effectiveMode` čistě pro zobrazení (nepotřebuje SharedPreferences). */
private fun effectiveLabel(mode: LinkModeState, kind: LinkKind): String {
    val away = when {
        mode.override == "away" -> true
        mode.override == "home" -> false
        !mode.autoDetect -> false
        kind == LinkKind.CELLULAR -> true
        else -> false
    }
    return if (away) "Venek (menší zdroj)" else "Doma (nejvyšší kvalita)"
}
