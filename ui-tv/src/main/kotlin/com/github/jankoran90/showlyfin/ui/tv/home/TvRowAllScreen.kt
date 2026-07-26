package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.feature.discover.tv.TvSectionViewModel
import com.github.jankoran90.showlyfin.feature.discover.tv.sortedBy
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvHomeCard
import com.github.jankoran90.showlyfin.ui.tv.components.TvSectionHeader
import com.github.jankoran90.showlyfin.ui.tv.components.TvViewChips
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import kotlin.math.roundToInt

/**
 * FOYER (SHW-107) — „Zobrazit vše" pro řadu domova, která NEMÁ vlastní sekci (Pokračovat, Další díly,
 * Historie, Uloženo k přehrání, Objevovat…): plná plocha načtených položek řady. Podle přání usera
 * (2026-07-26) je na TV výchozí **mřížka + abecedně**; přepnutí se uloží ([TvSectionViewModel]).
 *
 * Data si nebere znovu — čte JIŽ NAČTENÝ stav řady z [TvHomeViewModel] (tentýž activity-scoped VM jako
 * domov), takže drill je okamžitý a neplýtvá TMDB dotazy. Kolik je za dlaždicí = „Načíst položek" v editoru řady.
 */
@Composable
fun TvRowAllScreen(
    configId: String,
    title: String,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenDetailPlay: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (String) -> Unit,
    onBack: () -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    homeVm: TvHomeViewModel = hiltViewModel(),
    sectionVm: TvSectionViewModel = hiltViewModel(),
) {
    BackHandler(enabled = true) { onBack() }

    val states by homeVm.states.collectAsStateWithLifecycle()
    val modes by sectionVm.modes.collectAsStateWithLifecycle()
    val sectionKey = remember(configId) { "${ViewModeStore.SECTION_ROW_ALL}_$configId" }
    val viewMode = sectionVm.viewModeOf(modes, sectionKey)
    val sort = sectionVm.sortOf(modes, sectionKey)

    val items = remember(states, configId, sort) {
        states[configId]?.items.orEmpty().sortedBy(sort)
    }

    fun click(item: HomeRowItem) {
        val mi = item.mediaItem
        val jf = item.jellyfinId
        when {
            mi != null && item.playDirectly && homeVm.autoplayRememberedEnabled() -> onOpenDetailPlay(mi)
            mi != null -> onOpenDetail(mi)
            jf != null -> onOpenJellyfinDetail(jf)
        }
    }

    val cardScale = LocalTvCardScale.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = items.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    Column(modifier.fillMaxSize().tvOverscan()) {
        TvSectionHeader(
            title = title,
            actions = {
                TvViewChips(
                    viewMode = viewMode,
                    sort = sort,
                    onViewMode = { sectionVm.setViewMode(sectionKey, it) },
                    onSort = { sectionVm.setSort(sectionKey, it) },
                )
            },
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Zatím tu nic není.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed((6f / cardScale.widthScale).roundToInt().coerceIn(3, 9)),
                horizontalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
                verticalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                    Box(
                        Modifier.onFocusChanged {
                            if (it.hasFocus && immersive) onFocusItem(item.toImmersiveInfo())
                        },
                    ) {
                        TvHomeCard(
                            item = item,
                            style = HomeCardStyle.POSTER,
                            onClick = { click(item) },
                            focusRequester = if (index == 0) firstFocus else null,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(cardScale.spacing(10.dp)),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(items, key = { it.key }) { item ->
                    Box(
                        Modifier.onFocusChanged {
                            if (it.hasFocus && immersive) onFocusItem(item.toImmersiveInfo())
                        },
                    ) {
                        TvHomeCard(
                            item = item,
                            style = HomeCardStyle.LIST,
                            onClick = { click(item) },
                            focusRequester = if (item.key == items.first().key) firstFocus else null,
                        )
                    }
                }
            }
        }
    }
}
