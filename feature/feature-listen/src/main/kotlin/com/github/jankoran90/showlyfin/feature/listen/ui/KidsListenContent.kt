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
internal sealed interface KidsShelfItem {
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

    /**
     * SLOVO-KIDS-EPISODE — admin-schválený vlastní zdroj (RSS/YouTube/ČT, whitelist
     * [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.visibleForKidsSourceKeys]).
     * [card] je vždy [LibraryCard.Plain] nebo [LibraryCard.Merged] (nikdy Abs — ty jdou přes [PodcastItem]).
     */
    data class SourceItem(val card: LibraryCard) : KidsShelfItem {
        override val sortKey get() = card.sortTitle.lowercase(Locale("cs"))
        override val itemKey get() = "src_${card.itemKey}"
    }

    /**
     * SLOVO-KIDS-EPISODE — admin schválil jen JEDNU AUTO-detekovanou sérii uvnitř zdroje (např.
     * „Ďábelský káry" v „Na Výbornou"), ne celý zdroj — [source] zůstává nefiltrovaný (tap otevře
     * jen sérii přes [seriesSlug], viz `onOpenSourceSeries`).
     */
    data class SeriesItem(
        val source: com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource,
        val seriesSlug: String,
        val seriesTitle: String,
    ) : KidsShelfItem {
        override val sortKey get() = seriesTitle.lowercase(Locale("cs"))
        override val itemKey get() = "series_${source.id}_$seriesSlug"
    }
}

@Composable
fun KidsListenContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenBook: (String) -> Unit,
    onEditBook: (String, String, String?) -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    onOpenMerged: (groupId: String, title: String) -> Unit,
    // SLOVO-KIDS-EPISODE — otevři JEN sérii uvnitř zdroje (admin schválil sérii, ne celý zdroj).
    onOpenSourceSeries: (source: com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource, seriesSlug: String, seriesTitle: String) -> Unit,
) {
    // Podcasty se jinak načtou líně jen při přepnutí na záložku Podcasty — dětský profil žádnou
    // záložku nemá, takže si o načtení řekneme sami (idempotentní, no-op když už jsou načtené).
    LaunchedEffect(Unit) { viewModel.ensurePodcastsLoaded() }

    when {
        state.isLoading && state.books.isEmpty() && state.podcasts.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.books.isEmpty() && state.podcasts.isEmpty() -> CenteredMessage(state.error)

        else -> {
            // SLOVO-KIDS-EPISODE — vlastní zdroje (RSS/YouTube/ČT) admin schválil pro tenhle (dětský)
            // profil: [ListenViewModel.profileConfig] tady čte config AKTIVNÍHO profilu = přímo dětský
            // profil sám, žádná zvláštní whitelist StateFlow potřeba (na rozdíl od admin session).
            val cfg by viewModel.profileConfig.collectAsStateWithLifecycle()
            val links by viewModel.sourceLinks.collectAsStateWithLifecycle()
            val visibleKeys = cfg.visibleForKidsSourceKeys
            val byKey = remember(state.customSources) { state.customSources.associateBy { viewModel.sourceKey(it) } }
            val linkedKeys = remember(links) { links.flatMap { it.members }.toSet() }
            val sourceCards = remember(links, byKey, visibleKeys, state.customSources) {
                buildList<LibraryCard> {
                    links.forEach { g ->
                        if (g.members.none { it in visibleKeys }) return@forEach
                        val members = g.members.mapNotNull { byKey[it] }
                        if (members.isEmpty()) return@forEach
                        add(
                            LibraryCard.Merged(
                                g.id, g.title ?: members.first().title,
                                g.thumbnail ?: members.firstNotNullOfOrNull { it.thumbnail }, g.members,
                            ),
                        )
                    }
                    state.customSources
                        .filter { viewModel.sourceKey(it) !in linkedKeys && viewModel.sourceKey(it) in visibleKeys }
                        .forEach { add(LibraryCard.Plain(it, viewModel.sourceKey(it))) }
                }
            }
            // SLOVO-KIDS-EPISODE — schválené JEDNOTLIVÉ série (klíč `type:ref#series:slug|Titulek`,
            // dekódováno beze sítě). Zdroj, který je schválený CELÝ, se tu přeskočí (`sourceCards` už
            // ho nese jako plnou kartu) — série by pak byla nadbytečná duplicita.
            val wholeSourceApproved = remember(state.customSources, visibleKeys) {
                state.customSources.map { viewModel.sourceKey(it) }.filter { it in visibleKeys }.toSet()
            }
            val seriesItems = remember(visibleKeys, byKey, wholeSourceApproved) {
                visibleKeys.mapNotNull { key -> PodcastEpisodeSeriesGrouping.parseSeriesKey(key) }
                    .filter { it.sourceKey !in wholeSourceApproved }
                    .mapNotNull { parsed -> byKey[parsed.sourceKey]?.let { src -> Triple(src, parsed.slug, parsed.title) } }
            }

            val shelfItems = remember(state.books) { groupBooksBySeries(state.books) }
            val items = remember(shelfItems, state.podcasts, sourceCards, seriesItems) {
                (
                    shelfItems.map { KidsShelfItem.BookItem(it) } +
                        state.podcasts.map { KidsShelfItem.PodcastItem(it) } +
                        sourceCards.map { KidsShelfItem.SourceItem(it) } +
                        seriesItems.map { (src, slug, t) -> KidsShelfItem.SeriesItem(src, slug, t) }
                    ).sortedBy { it.sortKey }
            }
            var openSeries by remember { mutableStateOf<BookShelfItem.SeriesGroup?>(null) }
            var actionBook by remember { mutableStateOf<Audiobook?>(null) }

            if (items.isEmpty() && !state.isLoading) {
                CenteredMessage("V dětské knihovně zatím nic není.")
                return
            }

            val notDownloaded = state.books.any { it.id !in state.downloadedBookIds }
            val batchProgress by viewModel.batchDownloadProgress.collectAsStateWithLifecycle()
            val playerState by viewModel.playerState.collectAsStateWithLifecycle()
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
                                        isPlaying = playerState.isActive && playerState.currentItemId == b.book.id,
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
                                is KidsShelfItem.SourceItem -> when (val c = item.card) {
                                    is LibraryCard.Plain -> SourceCard(
                                        source = c.source,
                                        onClick = { onOpenSource(c.source) },
                                    )
                                    is LibraryCard.Merged -> MergedSourceCard(
                                        title = c.title,
                                        thumbnail = c.thumbnail,
                                        onClick = { onOpenMerged(c.groupId, c.title) },
                                    )
                                    is LibraryCard.Abs -> Unit // nikdy nevzniká pro SourceItem (jde přes PodcastItem)
                                }
                                is KidsShelfItem.SeriesItem -> SourceCard(
                                    source = item.source.copy(title = item.seriesTitle),
                                    onClick = { onOpenSourceSeries(item.source, item.seriesSlug, item.seriesTitle) },
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
                        onResetProgress = { viewModel.resetBookProgress(book) },
                        onMarkFinished = { viewModel.markBookFinished(book) },
                        onDismiss = { actionBook = null },
                    )
                }
            }
        }
    }
}
