package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.discover.wanttosee.WatchlistSourceCacheViewModel

/** Blok „Dohledat zdroje pro celý watchlist" v Nastavení Filmy — tlačítko + průběh. VM sdílený s TV. */
@Composable
fun FilmyWatchlistCacheSection(vm: WatchlistSourceCacheViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val running = state is WatchlistSourceCacheViewModel.State.Running

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingSectionTitle("Zdroje k watchlistu")
        OutlinedButton(enabled = !running, onClick = { vm.runBackfill() }) {
            Text(if (running) "Dohledávám…" else "Dohledat zdroje pro celý watchlist")
        }
        val status = when (val s = state) {
            is WatchlistSourceCacheViewModel.State.Running ->
                "Spouštím dohledání zdrojů na pozadí… ${s.done}/${s.total}"
            is WatchlistSourceCacheViewModel.State.Done ->
                if (s.requested == 0) "Všechny filmy ve watchlistu už zdroj mají (${s.already})."
                else "Spuštěno dohledání ${s.requested} filmů (na pozadí, chvíli to potrvá). ${s.already} už zdroj mělo."
            is WatchlistSourceCacheViewModel.State.Error -> "Chyba: ${s.message}"
            else -> "Projde filmy v „Chci vidět\" bez uloženého zdroje a na pozadí je předstáhne na Real-Debrid, ať jdou přehrát rovnou."
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
