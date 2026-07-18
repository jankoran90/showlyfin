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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val ctx = LocalContext.current
    var defaultFilmoteka by remember { mutableStateOf(FilmyShellPrefs.defaultFilmoteka(ctx)) }
    var showUploaderLogin by remember { mutableStateOf(false) }
    var showTraktLogin by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(title = "Nastavení", onMenu = onMenu)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── ÚČET (Trakt + server pro české popisky) ───────────────────────────
            FilmyCollapsibleSection("Účet", Icons.Rounded.AccountCircle, initiallyExpanded = true) {
                SettingSectionTitle("Účet Trakt")
                Text(
                    text = "Doporučení „Pro tebe\", watchlist a historie se berou z Traktu. Tady se můžeš odhlásit " +
                        "nebo přihlásit (např. přepnout účet).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.traktLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "  Přihlášeno k Traktu",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { settingsVm.logout() }) { Text("Odhlásit") }
                    }
                } else {
                    Button(onClick = { showTraktLogin = true }) { Text("Přihlásit se k Traktu") }
                }

                SettingSectionTitle("České popisky (ČSFD)")
                Text(
                    text = "Filmy bez českého překladu na TMDB (např. asijské) berou popis, galerii a komentáře " +
                        "z ČSFD přes server. Přihlas se jednou heslem a začnou chodit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.configured) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("  Přihlášeno k serveru", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Button(onClick = { showUploaderLogin = true }) { Text("Přihlásit k serveru") }
                }
            }

            // ── ZDROJE A PŘEHRÁVÁNÍ ───────────────────────────────────────────────
            FilmyCollapsibleSection("Zdroje a přehrávání", Icons.Rounded.Movie) {
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
                FilmyPlayerSection()
                FilmyAudioSection()
            }

            // ── OBJEVOVÁNÍ (kurátor + filmotéka + klenoty) ─────────────────────────
            FilmyCollapsibleSection("Objevování", Icons.Rounded.Explore) {
                // CELLULOID (SHW-98) — Filmotéka jako výchozí obrazovka; jen dospělý účet (user 2026-07-18).
                if (settings.autoRefreshSourcesAvailable) {
                    SettingSwitchRow(
                        title = "Filmotéka jako výchozí obrazovka",
                        subtitle = "Po otevření appky naskočí rovnou Filmotéka a v menu bude nahoře. Jen pro dospělý účet. Projeví se při dalším otevření appky.",
                        checked = defaultFilmoteka,
                        onCheckedChange = { defaultFilmoteka = it; FilmyShellPrefs.setDefaultFilmoteka(ctx, it) },
                    )
                }
                FilmyCuratorSection()
                FilmyFilmotekaSection()
                FilmyGemsSection()
            }

            // ── VZHLED (téma/písmo + detail obsahu) ───────────────────────────────
            FilmyCollapsibleSection("Vzhled", Icons.Rounded.Palette) {
                FilmyAppearanceSection()
                FilmyDetailContentSection()
            }

            // ── RODIČOVSKÁ KONTROLA ───────────────────────────────────────────────
            FilmyCollapsibleSection("Rodičovská kontrola", Icons.Rounded.ChildCare) {
                FilmyParentalSection()
            }

            // ── APLIKACE (o aplikaci + ladění) ────────────────────────────────────
            FilmyCollapsibleSection("Aplikace", Icons.Rounded.Info) {
                FilmyAboutSection()
                SettingSwitchRow(
                    title = "Živé logování",
                    subtitle = "Periodicky posílá logy na server (pomáhá při ladění). Vypni, když není potřeba.",
                    checked = settings.liveLogging,
                    onCheckedChange = { settingsVm.setLiveLogging(it) },
                )
            }
        }
    }

    if (showUploaderLogin) {
        FilmyUploaderLoginDialog(onDismiss = { showUploaderLogin = false })
    }
    if (showTraktLogin) {
        FilmyTraktLoginDialog(onDismiss = { showTraktLogin = false })
    }
}
