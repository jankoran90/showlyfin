package com.github.jankoran90.showlyfin.ui.tv.foryou

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.foryou.TvForYouViewModel
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import kotlin.math.roundToInt

/**
 * BESPOKE (SHW-95) F1/T1 — sekce „Pro tebe" (nahrazuje Objevovat). Kurátorská doporučení ([TvForYouViewModel]
 * nad `CuratorLoader`) s přepínačem zobrazení **Mřížka ↔ Immersive řada**. Mřížka = plakáty ([TvMediaCard],
 * vzor `TvDiscoverScreen`); Immersive řada = fanart hero + popis ([TvRailList] immersive, vzor Filmotéky).
 * Fokusovaná karta hlásí [onFocusItem] nahoru (immersive pozadí shellu).
 */
@Composable
fun TvForYouScreen(
    onOpenDetail: (MediaItem) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvForYouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val cardScale = LocalTvCardScale.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = state.items.isNotEmpty() && viewMode == ViewMode.GRID,
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Text(
            text = "Pro tebe",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        // Přepínač zobrazení: Mřížka ↔ Immersive řada (per-sekce ViewModeStore.SECTION_FOR_YOU).
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            FilterChip(
                selected = viewMode == ViewMode.GRID,
                onClick = { viewModel.setViewMode(ViewMode.GRID) },
                label = { Text("Mřížka") },
                modifier = Modifier.tvFocusable(),
            )
            FilterChip(
                selected = viewMode == ViewMode.LIST,
                onClick = { viewModel.setViewMode(ViewMode.LIST) },
                label = { Text("Immersive řada") },
                modifier = Modifier.tvFocusable(),
            )
        }

        when {
            state.loading && state.items.isEmpty() ->
                Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.items.isEmpty() -> Centered {
                Text(
                    text = "Kurátor zatím nemá doporučení — kouká na tvůj Trakt a Oblíbené. Přidej si oblíbené " +
                        "filmy nebo ohodnoť, co jsi viděl, a zkus to za chvíli.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            viewMode == ViewMode.LIST -> {
                val rail = remember(state.items) {
                    TvRail(
                        id = "foryou",
                        title = "Pro tebe",
                        style = HomeCardStyle.POSTER,
                        items = state.items.map { it.toForYouRowItem() },
                        configId = "foryou",
                        showTitles = true,
                        immersiveHeader = false,
                    )
                }
                TvRailList(
                    rails = listOf(rail),
                    sectionTitle = "Pro tebe",
                    immersive = immersive,
                    immersiveHeader = immersiveHeader,
                    onFocusItem = onFocusItem,
                    onItemClick = { item -> item.mediaItem?.let(onOpenDetail) },
                    modifier = Modifier.weight(1f),
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

/** MediaItem → řadový model pro immersive řadu (vzor `TvTraktViewModel.toHomeRowItem`). */
private fun MediaItem.toForYouRowItem(): HomeRowItem = HomeRowItem(
    key = "foryou_${type}_${tmdbId ?: traktId}",
    title = displayTitle,
    year = year,
    posterUrl = posterUrl("w342"),
    landscapeUrl = backdropUrl("w780"),
    mediaItem = this,
)
