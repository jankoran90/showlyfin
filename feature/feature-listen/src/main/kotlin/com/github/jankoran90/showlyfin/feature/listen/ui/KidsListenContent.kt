package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import java.util.Locale

/**
 * Profily (2026-08-15, user zpřesnění — „plná sekce audioknihy i když nejsou stažené, plus sloučené
 * podcasty které dětem schválím") — dětský profil dostane JEDNU sloučenou sekci Poslech (bez
 * přepínače Audioknihy/Podcasty, bez knihovních chips — dětská knihovna je jediná viditelná díky
 * [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.absLibraryWhitelist]).
 *
 * Obsah = PLNÁ dětská knihovna audioknih (série sloučené, [groupBooksBySeries], bez omezení na
 * stažené) + živé podcasty, které admin (Dospělý profil) schválil ([ProfileConfig.hiddenPodcastIds]
 * na profilu Děti — [state.podcasts] je touhle whitelistí filtrovaný už v [ListenViewModel]).
 * Tap na podcast otevře STEJNÝ plný detail jako u dospělého (streamované epizody, ne jen stažené) —
 * proto tenhle Composable NEstaví vlastní detail, jen deleguje na [onOpenPodcast] (shell naviguje).
 */
sealed interface KidsShelfItem {
    val sortKey: String
    val itemKey: String

    data class BookItem(val item: BookShelfItem) : KidsShelfItem {
        override val sortKey get() = item.sortKey
        override val itemKey get() = "book_${item.itemKey}"
    }

    data class PodcastItem(val podcast: Podcast) : KidsShelfItem {
        override val sortKey get() = podcast.title.lowercase(Locale("cs"))
        override val itemKey get() = "podcast_${podcast.id}"
    }
}

@Composable
fun KidsListenContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenBook: (String) -> Unit,
    onEditBook: (String, String, String?) -> Unit,
    onOpenPodcast: (String) -> Unit,
) {
    // Podcasty se jinak načtou líně jen při přepnutí na záložku Podcasty — dětský profil žádnou
    // záložku nemá, takže si o načtení řekneme sami (idempotentní, no-op když už jsou načtené).
    LaunchedEffect(Unit) { viewModel.ensurePodcastsLoaded() }

    when {
        state.isLoading && state.books.isEmpty() && state.podcasts.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.books.isEmpty() && state.podcasts.isEmpty() -> CenteredMessage(state.error)

        else -> {
            val shelfItems = remember(state.books) { groupBooksBySeries(state.books) }
            val items = remember(shelfItems, state.podcasts) {
                (shelfItems.map { KidsShelfItem.BookItem(it) } + state.podcasts.map { KidsShelfItem.PodcastItem(it) })
                    .sortedBy { it.sortKey }
            }
            var openSeries by remember { mutableStateOf<BookShelfItem.SeriesGroup?>(null) }
            var actionBook by remember { mutableStateOf<Audiobook?>(null) }

            if (items.isEmpty() && !state.isLoading) {
                CenteredMessage("V dětské knihovně zatím nic není.")
                return
            }

            val notDownloaded = state.books.any { it.id !in state.downloadedBookIds }
            val batchProgress by viewModel.batchDownloadProgress.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if (!state.isOffline && (notDownloaded || batchProgress != null)) {
                        DownloadAllRow(progress = batchProgress, onClick = viewModel::downloadAllBooks)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.itemKey }) { item ->
                            when (item) {
                                is KidsShelfItem.BookItem -> when (val b = item.item) {
                                    is BookShelfItem.Standalone -> AudiobookCard(
                                        book = b.book,
                                        onClick = { onOpenBook(b.book.id) },
                                        downloaded = b.book.id in state.downloadedBookIds,
                                        onLongClick = { actionBook = b.book },
                                    )
                                    is BookShelfItem.SeriesGroup -> SeriesCard(
                                        group = b,
                                        onClick = { openSeries = b },
                                    )
                                }
                                is KidsShelfItem.PodcastItem -> PodcastCard(
                                    podcast = item.podcast,
                                    onClick = { onOpenPodcast(item.podcast.id) },
                                )
                            }
                        }
                    }
                }

                openSeries?.let { group ->
                    SeriesVolumesSheet(
                        group = group,
                        downloadedBookIds = state.downloadedBookIds,
                        onOpenBook = { id -> openSeries = null; onOpenBook(id) },
                        onLongClickBook = { book -> openSeries = null; actionBook = book },
                        onDismiss = { openSeries = null },
                    )
                }

                actionBook?.let { book ->
                    AudiobookActionSheet(
                        book = book,
                        canDownload = !state.isOffline && book.id !in state.downloadedBookIds,
                        onDownload = { viewModel.downloadBook(book) },
                        onEdit = { onEditBook(book.id, book.title, book.author) },
                        onDismiss = { actionBook = null },
                    )
                }
            }
        }
    }
}
