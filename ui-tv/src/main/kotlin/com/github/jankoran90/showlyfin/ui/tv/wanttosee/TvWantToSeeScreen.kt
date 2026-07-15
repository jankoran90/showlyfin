package com.github.jankoran90.showlyfin.ui.tv.wanttosee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import kotlin.math.roundToInt

/**
 * BESPOKE (SHW-95) F1/T5a — sekce „Chci vidět" (Trakt watchlist) v TV sidebaru. Plakátová mřížka watchlistu
 * ([TvMediaCard], vzor `TvDiscoverScreen`), nejnověji přidané první. Fokusovaná karta hlásí [onFocusItem]
 * (immersive pozadí shellu). Prázdno = nepřihlášený Trakt / prázdný watchlist.
 */
@Composable
fun TvWantToSeeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvWantToSeeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cardScale = LocalTvCardScale.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = state.items.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Text(
            text = "Chci vidět",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )

        when {
            state.loading && state.items.isEmpty() ->
                Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.items.isEmpty() -> Centered {
                Text(
                    text = "Zatím tu nic není. Přidávej si filmy do „Chci vidět" v detailu — sejdou se ti tady.",
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

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
