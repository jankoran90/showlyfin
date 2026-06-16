package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.ListenOrderViewModel

/**
 * PRESET (SHW-65): self-contained sekce „Pořadí v Poslechu" pro Nastavení → Poslech (vlastní VM, ať
 * neroste SettingsScreen monolit). (1) Audioknihy/Podcasty co první, (2) pořadí knihoven pod oběma
 * (↑/↓). Společné pro zařízení.
 */
@Composable
fun ListenOrderSettingsSection(
    viewModel: ListenOrderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubHeader("Zobrazit první")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.booksFirst,
                onClick = { viewModel.setBooksFirst(true) },
                label = { Text("Audioknihy") },
            )
            FilterChip(
                selected = !state.booksFirst,
                onClick = { viewModel.setBooksFirst(false) },
                label = { Text("Podcasty") },
            )
        }

        if (state.notConfigured) {
            Text(
                "Pořadí knihoven se objeví po připojení Audiobookshelf.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (state.audiobookLibraries.size > 1) {
            SubHeader("Pořadí knihoven – Audioknihy")
            state.audiobookLibraries.forEachIndexed { i, lib ->
                ReorderRow(
                    name = lib.name,
                    canUp = i > 0,
                    canDown = i < state.audiobookLibraries.lastIndex,
                    onUp = { viewModel.moveAudiobook(lib.id, up = true) },
                    onDown = { viewModel.moveAudiobook(lib.id, up = false) },
                )
            }
        }

        if (state.podcastLibraries.size > 1) {
            SubHeader("Pořadí knihoven – Podcasty")
            state.podcastLibraries.forEachIndexed { i, lib ->
                ReorderRow(
                    name = lib.name,
                    canUp = i > 0,
                    canDown = i < state.podcastLibraries.lastIndex,
                    onUp = { viewModel.movePodcast(lib.id, up = true) },
                    onDown = { viewModel.movePodcast(lib.id, up = false) },
                )
            }
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ReorderRow(
    name: String,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onUp, enabled = canUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nahoru")
        }
        IconButton(onClick = onDown, enabled = canDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dolů")
        }
    }
}
