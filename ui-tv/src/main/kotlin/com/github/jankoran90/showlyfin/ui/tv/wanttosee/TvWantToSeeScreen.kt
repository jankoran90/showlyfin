package com.github.jankoran90.showlyfin.ui.tv.wanttosee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.wanttosee.TvWantToSeeViewModel
import com.github.jankoran90.showlyfin.feature.discover.wanttosee.WatchlistSourceCacheViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.settings.TvActionChip
import kotlin.math.roundToInt

/**
 * BESPOKE (SHW-95) F1/T5a — sekce „Chci vidět" (Trakt watchlist) v TV sidebaru. Plakátová mřížka watchlistu
 * ([TvMediaCard], vzor `TvDiscoverScreen`), nejnověji přidané první. Fokusovaná karta hlásí [onFocusItem]
 * (immersive pozadí shellu). Prázdno = nepřihlášený Trakt / prázdný watchlist.
 *
 * Hlavička: počet „N filmů · X s uloženým zdrojem" ([TvWantToSeeViewModel.savedCount]) + chip „Dohledat
 * zdroje" = backfill zdrojů pro celý watchlist ([WatchlistSourceCacheViewModel], sdílený s telefonem) se
 * živým průběhem — parita s telefonní sekcí (user 2026-07-18).
 */
@Composable
fun TvWantToSeeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvWantToSeeViewModel = hiltViewModel(),
    cacheViewModel: WatchlistSourceCacheViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cacheState by cacheViewModel.state.collectAsStateWithLifecycle()
    val cardScale = LocalTvCardScale.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = state.items.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    val running = cacheState is WatchlistSourceCacheViewModel.State.Running

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Chci vidět",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val sub = cacheStatusLine(cacheState)
                    ?: if (state.items.isNotEmpty()) "${state.items.size} filmů · ${state.savedCount} s uloženým zdrojem" else null
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Backfill zdrojů pro celý watchlist — „nakopnout" dohledání a vidět průběh rovnou tady (parita telefon).
            if (state.items.isNotEmpty()) {
                Spacer(Modifier.padding(start = 16.dp))
                TvActionChip(
                    label = if (running) "Dohledávám…" else "Dohledat zdroje",
                    enabled = !running,
                    onClick = { cacheViewModel.runBackfill() },
                )
            }
        }

        when {
            state.loading && state.items.isEmpty() ->
                Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.items.isEmpty() -> Centered {
                Text(
                    text = "Zatím tu nic není. Filmy si sem přidáš tlačítkem Chci vidět v detailu.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed((6f / cardScale.widthScale).roundToInt().coerceIn(3, 9)),
                horizontalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
                verticalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(state.items, key = { _, item -> "${item.type}_${item.traktId}_${item.tmdbId}" }) { index, item ->
                    Box(Modifier.onFocusChanged { if (it.hasFocus && immersive) onFocusItem(item.toImmersiveInfo()) }) {
                        TvMediaCard(
                            item = item,
                            onClick = { onOpenDetail(item) },
                            focusRequester = if (index == 0) firstFocus else null,
                        )
                    }
                }
            }
        }
    }
}

/** Průběh backfillu jako podtitulek — má přednost před statickým počtem, dokud běží / hlásí výsledek. */
private fun cacheStatusLine(s: WatchlistSourceCacheViewModel.State): String? = when (s) {
    is WatchlistSourceCacheViewModel.State.Running -> "Dohledávám zdroje na pozadí… ${s.done}/${s.total}"
    is WatchlistSourceCacheViewModel.State.Done ->
        if (s.requested == 0) null
        else "Spuštěno dohledání ${s.requested} filmů (na pozadí, chvíli to potrvá)."
    is WatchlistSourceCacheViewModel.State.Error -> "Chyba: ${s.message}"
    WatchlistSourceCacheViewModel.State.Idle -> null
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
