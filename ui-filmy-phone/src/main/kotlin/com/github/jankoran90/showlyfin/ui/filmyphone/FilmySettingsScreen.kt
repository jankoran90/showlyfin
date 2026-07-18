package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel

/**
 * CELLULOID (SHW-98) M2.3b/M2.5 — Nastavení appky Filmy. Zatím: (1) přihlášení k serveru pro české
 * ČSFD popisky/galerii/komentáře, (2) vypínač živého logu (default ON pro Filmy, odsud vypnutelný).
 * Trakt login je u prázdného domova; plné Nastavení + config presety = pozdější milník.
 */
@Composable
fun FilmySettingsScreen(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FilmyUploaderViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.uiState.collectAsStateWithLifecycle()
    var showUploaderLogin by remember { mutableStateOf(false) }
    var showTraktLogin by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(title = "Nastavení", onMenu = onMenu)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Vzhled + Písmo (M2.7 parita s TV, reuse sdílených Theme/FontPrefs VM) ---
            FilmyAppearanceSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- České popisky (ČSFD) ---
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

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Účet Trakt (M2.6) ---
            Text(
                text = "Účet Trakt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Doporučení „Pro tebe\", watchlist a historie se berou z Traktu. Tady se můžeš odhlásit " +
                    "nebo přihlásit (např. přepnout účet).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.traktLoggedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "  Přihlášeno k Traktu",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { settingsVm.logout() }) { Text("Odhlásit") }
                }
            } else {
                Button(onClick = { showTraktLogin = true }) {
                    Text("Přihlásit se k Traktu")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Dohledat zdroje pro celý watchlist (dávkový backfill, reuse triggerAutoCache) ---
            FilmyWatchlistCacheSection()

            // CELLULOID (SHW-98) — živé dotahování nacachovaných zdrojů bez restartu; jen dospělý profil.
            if (settings.autoRefreshSourcesAvailable) {
                SettingSwitchRow(
                    title = "Živě dotahovat zdroje (bez restartu)",
                    subtitle = "Při otevření filmu dotáhne nově nacachovaný zdroj ze serveru, takže jde přehrát rovnou — bez vypínání a zapínání appky. Jen pro dospělý účet.",
                    checked = settings.autoRefreshSources,
                    onCheckedChange = { settingsVm.setAutoRefreshSources(it) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Kurátor „Pro tebe" (M2.7 parita, reuse CuratorSettingsViewModel) ---
            FilmyCuratorSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Filmotéka (M2.7 parita vlna 2, reuse TvFilmotekaSettingsViewModel) ---
            FilmyFilmotekaSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Vzácné klenoty (M2.7 parita vlna 2, reuse TvLapidarySettingsViewModel) ---
            FilmyGemsSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Detail obsahu (M2.7 parita vlna 2, reuse DetailPrefsViewModel) ---
            FilmyDetailContentSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Přehrávač (M2.7 parita vlna 2, reuse SettingsViewModel) ---
            FilmyPlayerSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Obraz a zvuk (M2.7 parita vlna 2, reuse SettingsViewModel) ---
            FilmyAudioSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Rodičovská kontrola (M2.7 parita vlna 2, reuse ParentalPrefsViewModel) ---
            FilmyParentalSection()

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- Ladění a log ---
            Text(
                text = "Ladění",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Živé logování",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Periodicky posílá logy na server (pomáhá při ladění). Vypni, když není potřeba.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.liveLogging,
                    onCheckedChange = { settingsVm.setLiveLogging(it) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            // --- O aplikaci (verze + changelog + kontrola aktualizace) ---
            FilmyAboutSection()
        }
    }

    if (showUploaderLogin) {
        FilmyUploaderLoginDialog(onDismiss = { showUploaderLogin = false })
    }
    if (showTraktLogin) {
        FilmyTraktLoginDialog(onDismiss = { showTraktLogin = false })
    }
}
