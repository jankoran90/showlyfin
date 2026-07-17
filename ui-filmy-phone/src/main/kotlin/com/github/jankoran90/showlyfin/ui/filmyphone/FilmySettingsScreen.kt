package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * CELLULOID (SHW-98) M2.3b — Nastavení appky Filmy (zatím minimální: přihlášení k serveru pro české
 * ČSFD popisky/galerii/komentáře). Trakt login je u prázdného domova; plné Nastavení + config = M2.5.
 */
@Composable
fun FilmySettingsScreen(
    modifier: Modifier = Modifier,
    vm: FilmyUploaderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showUploaderLogin by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "České popisky (ČSFD)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Filmy bez českého překladu na TMDB (např. asijské) berou popis, galerii a komentáře " +
                "z ČSFD přes server. Přihlas se jednou heslem a začnou chodit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.configured) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  Přihlášeno k serveru",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Button(onClick = { showUploaderLogin = true }) {
                Text("Přihlásit k serveru")
            }
        }
    }

    if (showUploaderLogin) {
        FilmyUploaderLoginDialog(onDismiss = { showUploaderLogin = false })
    }
}
