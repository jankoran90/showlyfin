package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * DOCK (SHW-77): kategorický blok „Výchozí zařízení pro Na TV" v Nastavení → Domácí sestava.
 * Zvolený cíl (televize automaticky / Zenbook / jiné zařízení) se uloží a použije při „Na TV".
 * Parita Nastavení (HARD RULE). Cíl jde přepnout i za běhu v ovladači.
 */
@Composable
fun CastTargetSettingsSection(
    viewModel: CastTargetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Výchozí zařízení pro Na TV",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Kam se pošle obsah tlačítkem \"Na TV\". Automaticky = televize. Vyber Zenbook (nebo jiné " +
                "zařízení), ať se přehrávání pouští tam. Cíl jde kdykoli přepnout i v ovladači.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.selectedDeviceId == null,
                onClick = { viewModel.select(null) },
                label = { Text("Automaticky (TV)") },
            )
            state.devices.forEach { d ->
                FilterChip(
                    selected = state.selectedDeviceId == d.deviceId,
                    onClick = { viewModel.select(d) },
                    label = { Text(if (d.online) d.name else "${d.name} (offline)") },
                )
            }
        }

        TextButton(onClick = { viewModel.refresh() }) {
            Text(if (state.loading) "Načítám zařízení…" else "Aktualizovat zařízení")
        }
    }
}
