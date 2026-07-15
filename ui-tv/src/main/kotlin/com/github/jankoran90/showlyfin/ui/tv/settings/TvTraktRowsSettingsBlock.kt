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
import com.github.jankoran90.showlyfin.feature.discover.settings.TvTraktRowsSettingsViewModel

/**
 * CONVERGE (SHW-97) V1 — blok „Řady Traktu" v TV Nastavení: řazení (↑/↓) a skrývání (Zap/Vyp) řad sekce
 * Trakt (Watchlist, Zhlédnuto, Doporučeno + userovy seznamy). Per-profil, synced TV↔telefon (viz
 * [TvTraktRowsSettingsViewModel]). Nepřihlášený k Traktu → jen pevné řady (nastavení se uplatní po přihlášení).
 */
@Composable
fun TvTraktRowsSettingsBlock(vm: TvTraktRowsSettingsViewModel = hiltViewModel()) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Řady Traktu") {
        Text(
            text = "Pořadí a viditelnost řad v sekci Trakt. Userovy seznamy naskočí po přihlášení k Traktu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val ids = remember(candidates) { candidates.map { it.id } }
        val ordered = remember(cfg, candidates) {
            cfg.orderedTraktRows(ids).mapNotNull { id -> candidates.firstOrNull { it.id == id } }
        }
        ordered.forEachIndexed { index, opt ->
            TvRowReorderRow(
                label = opt.title,
                index = index,
                count = ordered.size,
                visible = cfg.isTraktRowVisible(opt.id),
                onMove = { up -> vm.move(ids, opt.id, up) },
                onToggle = { on -> vm.setVisible(opt.id, on) },
            )
        }
    }
}
