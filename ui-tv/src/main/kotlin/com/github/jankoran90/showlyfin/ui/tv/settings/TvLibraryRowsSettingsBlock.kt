package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.feature.discover.settings.TvLibraryRowsSettingsViewModel

/**
 * CONVERGE (SHW-97) V1 — blok „Řady knihovny" v TV Nastavení: řazení (↑/↓) a skrývání (Zap/Vyp) Jellyfin
 * knihoven v sekci Knihovna. Per-profil, synced TV↔telefon (viz [TvLibraryRowsSettingsViewModel]). Seznam
 * knihoven dodává obrazovka (stejný zdroj jako řady Knihovny — `LibraryRowsViewModel`).
 */
@Composable
fun TvLibraryRowsSettingsBlock(
    libraries: List<LibrarySummary>,
    vm: TvLibraryRowsSettingsViewModel = hiltViewModel(),
) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Řady knihovny") {
        if (libraries.isEmpty()) {
            Text(
                text = "Žádné Jellyfin knihovny (nebo se ještě načítají).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            val ids = remember(libraries) { libraries.map { it.id } }
            val ordered = remember(cfg, libraries) {
                cfg.orderedLibraryIds(ids).mapNotNull { id -> libraries.firstOrNull { it.id == id } }
            }
            ordered.forEachIndexed { index, lib ->
                val visible = cfg.jellyfinLibraryWhitelist?.let { lib.id in it } ?: true
                TvRowReorderRow(
                    label = lib.name,
                    index = index,
                    count = ordered.size,
                    visible = visible,
                    onMove = { up -> vm.move(ids, lib.id, up) },
                    onToggle = { on -> vm.setVisible(ids, lib.id, on) },
                )
            }
        }
    }
}
