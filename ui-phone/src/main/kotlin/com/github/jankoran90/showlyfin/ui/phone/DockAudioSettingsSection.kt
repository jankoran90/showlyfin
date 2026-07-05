package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * REVERB (SHW-82): kategorický blok „Zvukový výstup přehrávače" v Nastavení → Domácí sestava.
 * Určuje VÝCHOZÍ chování zvuku, když pouštíš video na Zenbook dock: kam se po startu automaticky
 * pošle zvuk (Zenbook / AV receiver) + výchozí lip-sync posun pro AVR. Přepnout a doladit posun jde
 * vždy i za běhu v ovladači. Parita Nastavení (HARD RULE).
 */
@Composable
fun DockAudioSettingsSection(
    viewModel: DockAudioSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Zvukový výstup přehrávače",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Když pouštíš video na Zenbook, tímto určíš, kam se po startu automaticky pošle zvuk. " +
                "Video zůstává na Zenbooku. Přepnout výstup a doladit posun jde vždy i za běhu v ovladači.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.defaultTarget == DockAudioSettingsViewModel.TARGET_LOCAL,
                onClick = { viewModel.setDefaultTarget(DockAudioSettingsViewModel.TARGET_LOCAL) },
                label = { Text("Zenbook") },
            )
            FilterChip(
                selected = state.defaultTarget == DockAudioSettingsViewModel.TARGET_AVR,
                onClick = { viewModel.setDefaultTarget(DockAudioSettingsViewModel.TARGET_AVR) },
                label = { Text("AV receiver") },
            )
        }

        // Výchozí lip-sync posun pro AV receiver (AirPlay má ~2 s buffer). V ovladači se pak dolaďuje živě.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Výchozí posun zvuku pro AV receiver", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (state.avrDelayMs == 0) "synchronní" else "%+.1f s".format(state.avrDelayMs / 1000f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { viewModel.nudgeAvrDelay(-100) }) { Icon(Icons.Filled.Remove, "Zvuk dřív") }
                TextButton(onClick = { viewModel.resetAvrDelay() }) { Text("0") }
                FilledTonalIconButton(onClick = { viewModel.nudgeAvrDelay(100) }) { Icon(Icons.Filled.Add, "Zvuk později") }
            }
        }
    }
}
