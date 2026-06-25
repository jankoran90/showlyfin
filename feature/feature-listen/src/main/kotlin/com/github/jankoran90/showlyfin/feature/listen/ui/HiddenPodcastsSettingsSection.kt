package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.github.jankoran90.showlyfin.feature.listen.HiddenPodcastsSettingsViewModel

/**
 * WEFT (SHW-75/W5): blok „Skryté pořady" v Nastavení → Poslech. Per profil, dvě nezávislé dimenze
 * (časová osa / Sledované). Skrýt jde dlouhým stiskem karty nebo z ⋮ menu řádku; tady se OBNOVÍ
 * (parita Nastavení, HARD RULE).
 */
@Composable
fun HiddenPodcastsSettingsSection(
    viewModel: HiddenPodcastsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Skryté pořady",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.onTimeline.isEmpty() && state.inFollowing.isEmpty()) {
            Text(
                "Nic skrytého. Pořad můžeš skrýt podržením prstu na kartě ve Sledovaných nebo přes ⋮ " +
                    "u epizody na časové ose — zvlášť pro časovou osu a zvlášť pro Sledované.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (state.onTimeline.isNotEmpty()) {
                HiddenGroupLabel("Na časové ose")
                state.onTimeline.forEach { row ->
                    HiddenSourceRow(row.label) { viewModel.unhide(row.key, timeline = true) }
                }
            }
            if (state.inFollowing.isNotEmpty()) {
                HiddenGroupLabel("Ve Sledovaných")
                state.inFollowing.forEach { row ->
                    HiddenSourceRow(row.label) { viewModel.unhide(row.key, timeline = false) }
                }
            }
        }
    }
}

@Composable
private fun HiddenGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HiddenSourceRow(label: String, onRestore: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRestore) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Obnovit")
            }
        }
    }
}
