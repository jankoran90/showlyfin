package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.ListenOfflineViewModel
import java.util.Locale

/**
 * LEVER (SHW-61) L5: blok „Stažené epizody (offline)" v Nastavení → Poslech — kolik je staženo do
 * telefonu, kolik to zabírá / kolik je volné místo, a hromadné smazání. Self-contained (vlastní VM,
 * ať neroste SettingsScreen). Parita Nastavení pro offline stahování podcastů (PRESET/TUNER zdroje).
 */
@Composable
fun ListenOfflineSettingsSection(
    viewModel: ListenOfflineViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Stažené epizody (offline)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        // RESONANCE (SHW-81) A: řazení offline detailu — parita Nastavení (dřív jen ikona v liště detailu).
        var newestFirst by remember { mutableStateOf(viewModel.offlinePodcastNewestFirst) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Nejnovější epizody nahoře",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Řazení epizod v detailu staženého pořadu. Změníš i přímo v detailu ikonou řazení.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = newestFirst,
                onCheckedChange = { newestFirst = it; viewModel.offlinePodcastNewestFirst = it },
            )
        }
        if (downloads.isEmpty()) {
            Text(
                "Zatím nic staženého. Epizodu stáhneš do telefonu v podcastu přes ⋮ → Stáhnout do telefonu — pak ji přehraješ i bez sítě (na chatě).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "V telefonu: ${downloads.size} epizod · zabráno ${formatBytes(viewModel.usedBytes())} · volných ${formatBytes(viewModel.freeBytes())}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { viewModel.deleteAll() }) {
                Text("Smazat všechny stažené epizody")
            }
        }
    }
}

private fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    var value = b.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024.0
        i++
    }
    return String.format(Locale.getDefault(), if (i == 0) "%.0f %s" else "%.1f %s", value, units[i])
}
