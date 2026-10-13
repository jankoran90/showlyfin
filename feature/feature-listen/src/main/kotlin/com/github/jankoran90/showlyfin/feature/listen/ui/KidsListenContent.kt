package com.github.jankoran90.showlyfin.feature.listen.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.Collator
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
    onOpenPodcast: (String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    onOpenMerged: (groupId: String, title: String) -> Unit,
    // SLOVO-KIDS-EPISODE — otevři JEN sérii uvnitř zdroje (admin schválil sérii, ne celý zdroj).
    onOpenSourceSeries: (source: com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource, seriesSlug: String, seriesTitle: String) -> Unit,
) {
    // Podcasty se jinak načtou líně jen při přepnutí na záložku Podcasty — dětský profil žádnou
    // záložku nemá, takže si o načtení řekneme sami (idempotentní, no-op když už jsou načtené).
    LaunchedEffect(Unit) { viewModel.ensurePodcastsLoaded() }
    // Schválené vlastní zdroje / série jsou i offline z paměti — chyba/načítání knih je nesmí schovat
    // (user 2026-10-12, „musí fungovat i offline"). Rozhoduje jen to, co dítě SMÍ vidět, ne celý seznam.
    val kidsCfg by viewModel.profileConfig.collectAsStateWithLifecycle()
    val hasApprovedSources = remember(state.customSources, kidsCfg.visibleForKidsSourceKeys) {
        val approved = kidsCfg.visibleForKidsSourceKeys.mapTo(HashSet()) { key ->
            PodcastEpisodeSeriesGrouping.parseSeriesKey(key)?.sourceKey ?: key
        }
        state.customSources.any { it.key in approved }
    }

    when {
        state.isLoading && state.books.isEmpty() && state.podcasts.isEmpty() && !hasApprovedSources ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.books.isEmpty() && state.podcasts.isEmpty() && !hasApprovedSources ->
            CenteredMessage(state.error)

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

            // User 2026-10-12: offline dítě vidí jen pořad/sérii, ze které je něco STAŽENÉ (knihy offline
            // řeší už ListenViewModel — jen stažené dětské). ABS podcasty offline nejsou.
            val offlineDls by viewModel.offlinePodcasts.collectAsStateWithLifecycle()
            val downloadedTitlesBySource = remember(offlineDls) {
                offlineDls.filter { it.sourceKey != null }.groupBy({ it.sourceKey!! }, { it.title })
            }
            fun hasDownloaded(sourceKey: String, slug: String? = null): Boolean =
                downloadedTitlesBySource[sourceKey].orEmpty().any { t ->
                    slug == null || PodcastEpisodeSeriesGrouping.detectSeriesTitle(t)
                        ?.let(PodcastEpisodeSeriesGrouping::seriesSlug) == slug
                }
            val offline = state.isOffline

            val shelfItems = remember(state.books) { groupBooksBySeries(state.books) }
            // České řazení (Collator) — „Ď" dřív spadlo podle kódu znaku až za „Z" (user 2026-10-12).
            val collator = remember { Collator.getInstance(Locale("cs")) }
            val items = remember(shelfItems, state.podcasts, sourceCards, seriesItems, offline, downloadedTitlesBySource) {
                (
                    shelfItems.map { KidsShelfItem.BookItem(it) } +
                        (if (offline) emptyList() else state.podcasts.map { KidsShelfItem.PodcastItem(it) }) +
                        sourceCards
                            .filter { c ->
                                !offline || when (c) {
                                    is LibraryCard.Plain -> hasDownloaded(c.source.key)
                                    is LibraryCard.Merged -> c.members.any { hasDownloaded(it) }
                                    is LibraryCard.Abs -> false
                                }
                            }
                            .map { KidsShelfItem.SourceItem(it) } +
                        seriesItems
                            .filter { (src, slug, _) -> !offline || hasDownloaded(src.key, slug) }
                            .map { (src, slug, t) -> KidsShelfItem.SeriesItem(src, slug, t) }
                    ).sortedWith(compareBy(collator) { it.sortKey })
            }
            // Dlouhý stisk na pořadu/sérii → „Stáhnout všechny díly a zařadit do fronty" (user 2026-10-12).
            val shelfActions: com.github.jankoran90.showlyfin.feature.listen.KidsShelfActionsViewModel = hiltViewModel()
            val shelfMessage by shelfActions.message.collectAsStateWithLifecycle()
            val seriesCovers by shelfActions.seriesCovers.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(shelfMessage) {
                shelfMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); shelfActions.consumeMessage() }
            }
            var downloadFor by remember { mutableStateOf<SourceDownloadTarget?>(null) }
            fun mergedSource(c: LibraryCard.Merged): com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource? =
                c.members.filter { it in visibleKeys }.mapNotNull { byKey[it] }
                    .sortedBy { it.type == "youtube" }.firstOrNull()
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
                                        onLongClick = { downloadFor = SourceDownloadTarget(c.source, setOf(c.source.key), null, c.source.title) },
                                    )
                                    is LibraryCard.Merged -> MergedSourceCard(
                                        title = c.title,
                                        thumbnail = c.thumbnail,
                                        onClick = { onOpenMerged(c.groupId, c.title) },
                                        onLongClick = {
                                            downloadFor = SourceDownloadTarget(mergedSource(c), c.members.filter { it in visibleKeys }.toSet(), null, c.title)
                                        },
                                    )
                                    is LibraryCard.Abs -> Unit // nikdy nevzniká pro SourceItem (jde přes PodcastItem)
                                }
                                is KidsShelfItem.SeriesItem -> {
                                    LaunchedEffect(item.itemKey) { shelfActions.requestSeriesCover(item.source, item.seriesSlug) }
                                    SourceCard(
                                        source = item.source.copy(
                                            title = item.seriesTitle,
                                            thumbnail = seriesCovers["${item.source.key}#${item.seriesSlug}"] ?: item.source.thumbnail,
                                        ),
                                        onClick = { onOpenSourceSeries(item.source, item.seriesSlug, item.seriesTitle) },
                                        onLongClick = { downloadFor = SourceDownloadTarget(item.source, setOf(item.source.key), item.seriesSlug, item.seriesTitle) },
                                    )
                                }
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

                downloadFor?.let { target ->
                    // User 2026-10-13 („nabízí jen stažení, ne odstranění; po úpravách musím stáhnout znovu").
                    val (info, actions) = sourceDownloadActions(target, offline, shelfActions)
                    ListenEpisodeActionSheet(title = target.title, infoLine = info, actions = actions, onDismiss = { downloadFor = null })
                }

                actionBook?.let { book ->
                    AudiobookActionSheet(
                        book = book,
                        canDownload = !state.isOffline && book.id !in state.downloadedBookIds,
                        onDownload = { viewModel.downloadBook(book) },
                        isDownloaded = book.id in state.downloadedBookIds,
                        onDeleteDownload = { viewModel.deleteBookDownload(book) },
                        onRedownload = if (state.isOffline) null else ({ viewModel.redownloadBook(book) }),
                        // Audit dětí (2026-10-12): dítě metadata knih neupravuje.
                        onEdit = null,
                        onResetProgress = { viewModel.resetBookProgress(book) },
                        onMarkFinished = { viewModel.markBookFinished(book) },
                        onDismiss = { actionBook = null },
                    )
                }
            }
        }
    }
}
