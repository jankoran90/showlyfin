package com.github.jankoran90.showlyfin.feature.watchlist.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.SectionBar
import com.github.jankoran90.showlyfin.core.ui.rememberScrollHeaderVisibility

/**
 * Plan STRATA B5 — Historie zhlédnutého (Trakt `sync/watched`, vzor yeshowly). Pohledy Naposledy/Vše,
 * hledání, mřížka karet. Položky jsou ze své podstaty „zhlédnuté" → badge watched vždy.
 */
@Composable
fun HistoryScreen(
    onItemClick: (MediaItem, jellyfinId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    Column(modifier = modifier.fillMaxSize()) {
        val headerVisible by rememberScrollHeaderVisibility(
            { gridState.firstVisibleItemIndex },
            { gridState.firstVisibleItemScrollOffset },
        )
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        ) {
            SectionBar(
                segments = HistoryView.entries.map { it.label },
                selectedSegment = uiState.view.ordinal,
                onSegmentSelected = { viewModel.selectView(HistoryView.entries[it]) },
                searchQuery = if (uiState.isLoggedIn) uiState.searchQuery else null,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                searchPlaceholder = "Hledat (česky i originál)…",
            )
        }

        when {
            !uiState.isLoggedIn -> CenteredText("Přihlaš se k Traktu,\naby se zobrazila historie.")
            uiState.isLoading && uiState.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.items.isEmpty() && uiState.searchQuery.isBlank() -> EmptyHistory()
            uiState.items.isEmpty() -> CenteredText("Nic nenalezeno pro „${uiState.searchQuery}\"")
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.items, key = { "${it.type}_${it.traktId}" }) { item ->
                    val jellyfinId = item.imdbId?.let { uiState.imdbToJellyfin[it] }
                        ?: item.tmdbId?.let { uiState.tmdbToJellyfin[it] }
                    val inLibrary = jellyfinId != null
                        || (item.imdbId?.let { uiState.ownedImdbIds.contains(it) } ?: false)
                    MediaCard(
                        item = item,
                        onClick = { onItemClick(item, jellyfinId) },
                        inLibrary = inLibrary,
                        watched = true,
                    )
                }
                if (uiState.hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { viewModel.loadMore() }) {
                                Text("Načíst dalších 20")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyHistory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Historie je prázdná",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Až něco zhlédneš a označíš na Traktu, objeví se to tady.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
