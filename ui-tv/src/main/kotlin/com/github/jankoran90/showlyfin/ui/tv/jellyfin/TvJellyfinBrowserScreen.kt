package com.github.jankoran90.showlyfin.ui.tv.jellyfin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinItem
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinLibraryItemsViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinSort
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinTypeFilter
import com.github.jankoran90.showlyfin.ui.tv.components.TvJellyfinPosterCard

/**
 * TENFOOT (SHW-87) Fáze 2 — mřížka položek jedné Jellyfin knihovny na TV (10-foot, D-pad).
 * Sdílí [JellyfinLibraryItemsViewModel] s telefonem (řazení/filtr uložené v prefs, limit 200, bez stránkování).
 *
 * Klik replikuje bezpečnou telefonní logiku ([com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinLibraryItemsScreen]):
 *  - složka / BOX_SET → zanoř (drill) do vnořené mřížky,
 *  - bohatý detail dostupný (detailRich + tmdbId) → nativní TV karta obsahu (fanart hero),
 *  - jinak → Jellyfin detail (bezpečné pro seriál i film bez tmdb).
 *
 * BACK řeší globální `TvNavigator` (popuje stack) — obrazovka proto nemá vlastní horní lištu.
 */
@Composable
fun TvJellyfinBrowserScreen(
    libraryId: String,
    libraryName: String,
    collectionType: String?,
    parentItemType: String?,
    onOpenRich: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    onDrillIn: (itemId: String, itemName: String, itemType: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JellyfinLibraryItemsViewModel = hiltViewModel(),
) {
    LaunchedEffect(libraryId, collectionType, parentItemType) {
        viewModel.load(libraryId, libraryName, collectionType, parentItemType)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Text(
            text = state.libraryName.ifBlank { libraryName },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Řazení + filtr typu (parita s telefonem; VM to už umí, prefs sdílené telefon↔TV). „Víc voleb" (credo).
        if (state.error == null) {
            TvJellyfinFilters(
                sort = state.sort,
                typeFilter = state.typeFilter,
                showTypeFilter = !state.isBoxSetContext,
                onSort = viewModel::selectSort,
                onType = viewModel::selectTypeFilter,
            )
        }

        when {
            state.isLoading -> Centered { CircularProgressIndicator() }
            state.error != null -> Centered {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
            state.items.isEmpty() -> Centered {
                Text("Knihovna je prázdná", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.items, key = { it.id }) { item ->
                    TvJellyfinPosterCard(
                        item = item,
                        onClick = {
                            // BUG fix: SERIES má v Jellyfinu isFolder==true → dřív spadl do „drill" (prázdná
                            // mřížka, děti jsou EPISODE/SEASON) místo JF detailu s epizodami/next-up.
                            val isSeries = item.type.equals("SERIES", ignoreCase = true)
                            val folderLike = !isSeries &&
                                (item.isFolder || item.type.equals("BOX_SET", ignoreCase = true))
                            when {
                                folderLike -> onDrillIn(item.id, item.name, item.type)
                                isSeries -> onOpenJellyfinDetail(item.id)
                                state.detailRich && item.tmdbId != null -> onOpenRich(item.toStubMediaItem())
                                else -> onOpenJellyfinDetail(item.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Řazení (vždy) + filtr typu (mimo BOX_SET kontext) jako D-pad chipy. FlowRow se sám zalomí, nepřeteče. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvJellyfinFilters(
    sort: JellyfinSort,
    typeFilter: JellyfinTypeFilter,
    showTypeFilter: Boolean,
    onSort: (JellyfinSort) -> Unit,
    onType: (JellyfinTypeFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JellyfinSort.entries.forEach { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { onSort(s) },
                    label = { Text(s.label) },
                    modifier = Modifier.tvFocusable(),
                )
            }
        }
        if (showTypeFilter) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                JellyfinTypeFilter.entries.forEach { t ->
                    FilterChip(
                        selected = typeFilter == t,
                        onClick = { onType(t) },
                        label = { Text(t.label) },
                        modifier = Modifier.tvFocusable(),
                    )
                }
            }
        }
    }
}

/** Stub MediaItem z Jellyfin položky — bohatý detail si zbytek dotáhne z TMDB dle tmdbId (parita s telefonem). */
private fun JellyfinItem.toStubMediaItem() = MediaItem(
    traktId = 0L,
    tmdbId = tmdbId,
    imdbId = imdbId,
    title = name,
    year = year,
    overview = null,
    rating = null,
    genres = null,
    type = if (type.equals("SERIES", ignoreCase = true)) MediaType.SHOW else MediaType.MOVIE,
    posterPath = null,
    backdropPath = null,
)
