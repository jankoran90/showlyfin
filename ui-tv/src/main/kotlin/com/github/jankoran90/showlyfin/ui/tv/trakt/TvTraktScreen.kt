package com.github.jankoran90.showlyfin.ui.tv.trakt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktCategory
import com.github.jankoran90.showlyfin.feature.discover.trakt.TvTraktViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import kotlin.math.roundToInt

/**
 * COUCH (SHW-88) — sekce „Trakt" (strukturálně jako [com.github.jankoran90.showlyfin.ui.tv.discover.TvDiscoverScreen]):
 * chip lišta kategorií (Doporučeno = couchmonkey sloučení / Watchlist / Zhlédnuto / Moje seznamy) + plakátová
 * mřížka. „Moje seznamy" navíc vykreslí řadu chipů seznamů. Šířka/rozestupy z [LocalTvCardScale] (DA4).
 */
@Composable
fun TvTraktScreen(
    onOpenDetail: (MediaItem) -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvTraktViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val cardScale = LocalTvCardScale.current
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = state.items.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Text(
            text = "Trakt",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        // Kategorie (chipy) — jako filtr chipy v Objevovat.
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            TraktCategory.entries.forEach { cat ->
                FilterChip(
                    selected = state.category == cat,
                    onClick = { viewModel.selectCategory(cat) },
                    label = { Text(cat.label) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }
        // „Moje seznamy" → řada chipů jednotlivých seznamů (v pořadí z Trakt API).
        if (state.category == TraktCategory.MY_LISTS && state.lists.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                items(state.lists, key = { it.id }) { l ->
                    FilterChip(
                        selected = state.selectedListId == l.id,
                        onClick = { viewModel.selectList(l.id) },
                        label = { Text(l.name) },
                        modifier = Modifier.tvFocusable(),
                    )
                }
            }
        }

        when {
            state.isLoading && state.items.isEmpty() ->
                Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.items.isEmpty() -> Centered {
                Text(
                    text = "Nic k zobrazení — přihlaš se k Traktu (Nastavení → Účty), nebo tu zatím nic není.",
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
