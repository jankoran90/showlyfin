package com.github.jankoran90.showlyfin.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.feature.discover.foryou.ForYouViewModel

/**
 * BESPOKE (SHW-95) V2 — telefonní sekce „Pro tebe" (nahrazuje „Objevit" v pageru [MainScreen]). Mřížka
 * akumulovaných kurátorských doporučení nad sdíleným [ForYouViewModel] (tentýž VM i store jako TV → sekce
 * roste a je synchronizovaná TV↔telefon). Telefon přepínač zobrazení nepoužívá — jede jednoduchá mřížka.
 * Kurátorská doporučení jsou TMDB/Trakt tituly (ne nutně v Jellyfinu) → klik jde přes `onItemClick(item, null)`
 * = bohatý Trakt/TMDB detail (viz [MainScreen]).
 */
@Composable
fun ForYouScreen(
    onItemClick: (MediaItem, jellyfinId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForYouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        when {
            state.loading && state.items.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.items.isEmpty() ->
                Text(
                    text = "Kurátor zatím nemá doporučení. Zapni „Pro tebe" v Nastavení a přihlas se k Traktu — " +
                        "doporučení se tu začnou hromadit.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            else -> {
                val colPref = rememberGridColumnPref()
                LazyVerticalGrid(
                    columns = gridCellsFor(ViewMode.GRID, colPref),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { "${it.type}_${it.traktId}_${it.tmdbId}" }) { item ->
                        MediaCard(item = item, onClick = { onItemClick(item, null) })
                    }
                }
            }
        }
    }
}
