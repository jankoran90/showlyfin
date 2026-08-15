package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import java.util.Locale

/**
 * Profily (2026-08-15, user „profily jak jsme je používali v showlyfin") — dětský profil dostane
 * JEDNU sloučenou sekci Poslech (bez přepínače Audioknihy/Podcasty, bez knihovních chips — děti
 * knihovna je jediná viditelná díky [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.absLibraryWhitelist]).
 * Obsah = audioknihy dětské ABS knihovny (série sloučené jako u dospělého, [groupBooksBySeries]) +
 * stažené epizody ABS podcastů, které admin (Dospělý profil) nezakázal ([ProfileConfig.hiddenPodcastIds]).
 * Vlastní RSS/YouTube zdroje (Sledované/Objevit) jsou „dospělácká" vrstva — dětskému profilu se nenabízí.
 */
internal sealed interface KidsShelfItem {
    val sortKey: String
    val itemKey: String

    data class BookItem(val item: BookShelfItem) : KidsShelfItem {
        override val sortKey get() = item.sortKey
        override val itemKey get() = "book_${item.itemKey}"
    }

    data class ShowItem(val show: OfflinePodcastShow) : KidsShelfItem {
        override val sortKey get() = show.title.lowercase(Locale("cs"))
        override val itemKey get() = "show_${show.title}"
    }
}

/** Stažené epizody ABS podcastů (sourceKey `abs:<id>`), odfiltrované o [hiddenPodcastIds] admina. */
private fun List<OfflineDownload>.absDownloadsAllowed(hiddenPodcastIds: Set<String>): List<OfflineDownload> =
    filter { dl ->
        val absId = dl.sourceKey?.takeIf { it.startsWith("abs:") }?.removePrefix("abs:") ?: return@filter false
        absId !in hiddenPodcastIds
    }

@Composable
fun KidsListenContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    podcastDownloads: List<OfflineDownload>,
    hiddenPodcastIds: Set<String>,
    onOpenBook: (String) -> Unit,
    onEditBook: (String, String, String?) -> Unit,
) {
    when {
        state.isLoading && state.books.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.books.isEmpty() -> CenteredMessage(state.error)

        else -> {
            val shelfItems = remember(state.books) { groupBooksBySeries(state.books) }
            val shows = remember(podcastDownloads, hiddenPodcastIds) {
                buildOfflineShows(podcastDownloads.absDownloadsAllowed(hiddenPodcastIds))
            }
            val items = remember(shelfItems, shows) {
                (shelfItems.map { KidsShelfItem.BookItem(it) } + shows.map { KidsShelfItem.ShowItem(it) })
                    .sortedBy { it.sortKey }
            }
            var openSeries by remember { mutableStateOf<BookShelfItem.SeriesGroup?>(null) }
            var openShow by remember { mutableStateOf<OfflinePodcastShow?>(null) }

            if (items.isEmpty() && !state.isLoading) {
                CenteredMessage("V dětské knihovně zatím nic není.")
                return
            }

            Box(Modifier.fillMaxSize()) {
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
                                    onLongClick = { onEditBook(b.book.id, b.book.title, b.book.author) },
                                )
                                is BookShelfItem.SeriesGroup -> SeriesCard(
                                    group = b,
                                    onClick = { openSeries = b },
                                )
                            }
                            is KidsShelfItem.ShowItem -> OfflinePodcastCard(
                                show = item.show,
                                onClick = { openShow = item.show },
                            )
                        }
                    }
                }

                openSeries?.let { group ->
                    SeriesVolumesSheet(
                        group = group,
                        downloadedBookIds = state.downloadedBookIds,
                        onOpenBook = { id -> openSeries = null; onOpenBook(id) },
                        onLongClickBook = { book -> openSeries = null; onEditBook(book.id, book.title, book.author) },
                        onDismiss = { openSeries = null },
                    )
                }

                openShow?.let { opened ->
                    val live = shows.firstOrNull { it.title == opened.title }
                    if (live != null) {
                        OfflinePodcastDetailScreen(
                            show = live,
                            viewModel = viewModel,
                            onBack = { openShow = null },
                        )
                    } else {
                        openShow = null
                    }
                }
            }
        }
    }
}
