package com.github.jankoran90.showlyfin.ui.tv.watchlist

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.TvPosterCard

/**
 * TENFOOT (SHW-87) — nativní 10-foot „Oblíbené" na TV. Zdroj = per-profil [TvFavoritesViewModel]
 * (FavoritesStore, TÝŽ jako telefonní „Oblíbené") → TV vidí uživatelovy oblíbené filmy jeho profilu.
 * Plakátová mřížka (sdílená [TvPosterCard]); ťuk → nativní TV karta obsahu (stub s tmdbId).
 * Autofokus na první plakát přes [AutoFocusFirst] (robustní, čeká na umístění uzlu).
 */
@Composable
fun TvWatchlistScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvFavoritesViewModel = hiltViewModel(),
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val firstItemFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstItemFocus,
        enabled = movies.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zpět",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .tvFocusBorder(shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Text(
                text = "Oblíbené",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (movies.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "Zatím žádné oblíbené filmy — přidej si je v kartě filmu (❤).",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(movies, key = { _, it -> "fav_${it.id}" }) { index, fav ->
                    TvPosterCard(
                        posterUrl = fav.imageUrl,
                        title = fav.name,
                        year = fav.year,
                        onClick = {
                            onOpenDetail(
                                MediaItem(
                                    traktId = 0L,
                                    tmdbId = fav.id,
                                    imdbId = null,
                                    title = fav.name,
                                    year = fav.year,
                                    overview = null,
                                    rating = null,
                                    genres = null,
                                    type = MediaType.MOVIE,
                                ),
                            )
                        },
                        focusRequester = if (index == 0) firstItemFocus else null,
                    )
                }
            }
        }
    }
}
