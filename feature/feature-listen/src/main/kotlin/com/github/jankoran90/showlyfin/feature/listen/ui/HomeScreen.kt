package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel

/**
 * Domů (user 2026-08-15: „home obrazovka, co se vždy otevře, naposledy přehráno + pokračovat,
 * hezky v mřížce"). Sjednocené rozposlouchané napříč audioknihami i podcastovými epizodami
 * (viz [HomeViewModel]), seřazené podle posledního poslechu. Tap = otevře detail/kontext (stejná
 * konvence jako zbytek appky — nespouští playback rovnou z dlaždice).
 */
@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            items.isEmpty() -> Text(
                "Zatím nic rozposloucháno.\nZačni poslouchat audioknihu nebo epizodu a najdeš ji tady.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = ::continueItemKey) { item ->
                    when (item) {
                        is HomeViewModel.ContinueItem.Book -> AudiobookCard(
                            book = item.book,
                            onClick = { onOpenBook(item.book.id) },
                        )
                        is HomeViewModel.ContinueItem.Episode -> ContinueEpisodeCard(
                            episode = item.episode,
                            sourceTitle = item.sourceTitle,
                            progress = item.progress,
                            onClick = {
                                onOpenSourceEpisode(item.sourceType, item.sourceRef, item.sourceTitle, item.episode.resumeKey ?: item.episode.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun continueItemKey(item: HomeViewModel.ContinueItem): String = when (item) {
    is HomeViewModel.ContinueItem.Book -> "book:${item.book.id}"
    is HomeViewModel.ContinueItem.Episode -> "ep:${item.sourceType}:${item.sourceRef}:${item.episode.id}"
}
