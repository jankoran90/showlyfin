package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.feature.listen.ListenMode
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import kotlinx.coroutines.launch

/**
 * Poslechová sekce — přepínač Audioknihy ↔ Podcasty, knihovní chips + grid obálek s progressem.
 * Vše v Material 3 Expressive tématu (ListenExpressiveTheme). ABS login je v Nastavení.
 *
 * Anti-monolit (rozděleno z původního 942ř. souboru): knihovní chips + LibraryCard →
 * [ListenLibraryChips]; offline banner / správa stažení / offline grid+detail → [ListenOfflineSection].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    onOpenBook: (itemId: String) -> Unit,
    /** DROPSHIP F2c — long press na audioknihu → úprava metadata + cover (itemId, title, author). */
    onEditBook: (itemId: String, title: String, author: String?) -> Unit = { _, _, _ -> },
    onOpenPodcast: (itemId: String) -> Unit,
    onPlayEpisode: (itemId: String, episodeId: String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    /** Timeline: otevři obsah zdroje (RSS feedUrl / YouTube handle) a zvýrazni epizodu [episodeKey]. */
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    /** TWINE: otevři sloučený pohled propojeného pořadu (audio+video) podle [groupId]. */
    onOpenMerged: (groupId: String, title: String) -> Unit,
    /** SLOVO-KIDS-EPISODE: dětský profil otevře JEN admin-schválenou sérii uvnitř zdroje. */
    onOpenSourceSeries: (
        source: com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource,
        seriesSlug: String,
        seriesTitle: String,
    ) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: ListenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val podcastDownloads by viewModel.offlinePodcasts.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    var showDownloads by remember { mutableStateOf(false) }

    // PRESET (SHW-65) — po návratu z Nastavení převezmi případně změněné pořadí v Poslechu.
    LaunchedEffect(Unit) { viewModel.reloadOrderPrefs() }

    ListenExpressiveTheme {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (!state.isConfigured) {
                CenteredMessage(
                    "Poslech zatím není nastaven.\nPřihlas se k Audiobookshelf serveru v Nastavení → Poslech (Audiobookshelf).",
                )
            } else if (activeProfile?.isAdmin == false) {
                // Profily (2026-08-15): dětský profil = JEDNA sloučená sekce, žádný přepínač Audioknihy/Podcasty.
                KidsListenContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenBook = onOpenBook,
                    onOpenPodcast = onOpenPodcast,
                    onOpenSource = onOpenSource,
                    onOpenMerged = onOpenMerged,
                    onOpenSourceSeries = onOpenSourceSeries,
                )
            } else {
                // User (2026-08-16 13:43, „horizontal scroll zůstane jen v aktivní sekci, ne mezi
                // Podcasty/Audioknihy") — přepínač [ListenMode] se teď ovládá TAPEM z horní lišty
                // (viz SlovoPhoneShell.SlovoShellContent), žádné swipe stránkování mezi režimy; obsah
                // se jen přepne. Horizontal scroll/tab uvnitř podsekcí (Timeline/Sledované/Objev v
                // [PodcastsContent], knihovní chips v [BooksContent]) beze změny.
                Column(Modifier.fillMaxSize()) {
                    if (state.isOffline) OfflineBanner()
                    when (state.mode) {
                        ListenMode.BOOKS -> BooksContent(state, viewModel, onOpenBook, onEditBook)
                        ListenMode.PODCASTS -> PodcastsContent(
                            state, viewModel, onOpenPodcast,
                            downloadCount = downloads.size + podcastDownloads.size,
                            onOpenDownloads = { showDownloads = true },
                            onOpenSource = onOpenSource,
                            onOpenSourceEpisode = onOpenSourceEpisode,
                            onOpenMerged = onOpenMerged,
                            podcastDownloads = podcastDownloads,
                        )
                    }
                }
            }
        }
    }

    if (showDownloads) {
        DownloadsSheet(
            downloads = downloads,
            podcastDownloads = podcastDownloads,
            onDismiss = { showDownloads = false },
            onPlay = { dl -> onPlayEpisode(dl.itemId, dl.episodeId); showDownloads = false },
            onPlayPodcast = { dl -> viewModel.playOfflinePodcast(dl); showDownloads = false },
            onDelete = { viewModel.deleteDownload(it) },
            onDeletePodcast = { viewModel.deleteOfflinePodcast(it) },
            onDeleteAll = { viewModel.deleteAllDownloads(); showDownloads = false },
        )
    }
}

/**
 * User (2026-08-16 18:23, „Poslech čisté jako Domů") — Timeline jako samostatná swipe strana
 * sekce Domů (viz `SlovoPhoneShell`), bez vnořeného tab řádku/filtru. `onGoToObjevit` nahrazuje
 * dřívější interní přepnutí na tab Objev (ten teď žije jen v sidebaru, viz [SlovoSection.OBJEVIT]
 * v ui-slovo-phone) — prázdný stav Timeline („Zatím nesleduješ žádné zdroje") tam navede.
 */
@Composable
fun TimelinePage(
    state: ListenUiState,
    viewModel: ListenViewModel,
    podcastDownloads: List<com.github.jankoran90.showlyfin.data.offline.OfflineDownload>,
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    onGoToObjevit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isOffline) {
        OfflineDownloadedPodcasts(podcastDownloads, viewModel)
    } else {
        PodcastTimelineSection(
            onOpenDiscover = onGoToObjevit,
            onOpenSource = { item -> onOpenSourceEpisode(item.sourceType, item.sourceRef, item.sourceTitle, item.key) },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
fun BooksContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenBook: (String) -> Unit,
    onEditBook: (String, String, String?) -> Unit,
) {
    when {
        state.isLoading && state.books.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.books.isEmpty() -> CenteredMessage(state.error)

        else -> Column(Modifier.fillMaxSize()) {
            if (state.libraries.size > 1) {
                LibraryChips(
                    libraries = state.libraries.map { it.id to it.name },
                    selectedId = state.selectedLibraryId,
                    onSelect = viewModel::selectLibrary,
                )
            }
            if (state.books.isEmpty() && !state.isLoading) {
                CenteredMessage(
                    if (state.isOffline) {
                        "Jsi offline a nemáš žádné stažené audioknihy.\nStáhni je v detailu knihy, dokud jsi připojený."
                    } else {
                        "V této knihovně zatím nejsou žádné audioknihy."
                    },
                )
            } else {
                // DROPSHIP série v knihovně: víc dílů stejné série → jedna karta, tap otevře díly.
                val shelfItems = remember(state.books) { groupBooksBySeries(state.books) }
                var openSeries by remember { mutableStateOf<BookShelfItem.SeriesGroup?>(null) }
                var actionBook by remember { mutableStateOf<Audiobook?>(null) }
                val notDownloaded = state.books.any { it.id !in state.downloadedBookIds }
                val batchProgress by viewModel.batchDownloadProgress.collectAsStateWithLifecycle()
                val playerState by viewModel.playerState.collectAsStateWithLifecycle()
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
                        items(shelfItems, key = { it.itemKey }) { item ->
                            when (item) {
                                is BookShelfItem.Standalone -> AudiobookCard(
                                    book = item.book,
                                    onClick = { onOpenBook(item.book.id) },
                                    downloaded = item.book.id in state.downloadedBookIds,
                                    isPlaying = playerState.isActive && playerState.currentItemId == item.book.id,
                                    onLongClick = { actionBook = item.book },
                                    onEndListening = { viewModel.resetBookProgress(item.book) },
                                )
                                is BookShelfItem.SeriesGroup -> SeriesCard(
                                    group = item,
                                    onClick = { openSeries = item },
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
                    val otherAdults by viewModel.otherAdultProfiles.collectAsStateWithLifecycle()
                    val kidsLibraryIds by viewModel.kidsLibraryIds.collectAsStateWithLifecycle()
                    // User (2026-08-16 14:42, „Sdílet s Nel u dětské knihy nedává smysl") — vlastnictví/
                    // sdílení se dětské knihovny netýká, sheet pro ni nenabídne žádné „Sdílet s…".
                    val isKidsBook = book.libraryId in kidsLibraryIds
                    AudiobookActionSheet(
                        book = book,
                        canDownload = !state.isOffline && book.id !in state.downloadedBookIds,
                        onDownload = { viewModel.downloadBook(book) },
                        isDownloaded = book.id in state.downloadedBookIds,
                        onDeleteDownload = { viewModel.deleteBookDownload(book) },
                        onRedownload = if (state.isOffline) null else ({ viewModel.redownloadBook(book) }),
                        onEdit = { onEditBook(book.id, book.title, book.author) },
                        onResetProgress = { viewModel.resetBookProgress(book) },
                        onMarkFinished = { viewModel.markBookFinished(book) },
                        onDismiss = { actionBook = null },
                        shareActions = if (isKidsBook) emptyList() else otherAdults.map { target ->
                            val shared = viewModel.isBookSharedWith(book.id, target)
                            ListenEpisodeAction(
                                if (shared) Icons.Default.Visibility else Icons.Default.Share,
                                if (shared) "Přestat sdílet s ${target.name}" else "Sdílet s ${target.name}",
                            ) { viewModel.setBookSharedWith(book.id, target.id, !shared) }
                        },
                        infoLine = if (isKidsBook) null else viewModel.ownershipInfoLine(
                            viewModel.ownerOfBook(book.id),
                            otherAdults.filter { viewModel.isBookSharedWith(book.id, it) },
                        ),
                        onDelete = if (viewModel.canDeleteBook(book.id)) {
                            { viewModel.deleteBook(book); actionBook = null }
                        } else null,
                    )
                }
            }
        }
    }
}

/**
 * AGORA-TABS: sekce Podcasty s přepínacími záložkami. PRVNÍ prvek řady = ikona filtru, pak
 * Timeline (default) · Sledované · Objev. Timeline = chronologický feed nových epizod ze všech zdrojů;
 * Sledované = grid vlastních + ABS podcastů; Objev = katalog + přidání vlastních YT/RSS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastsContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenPodcast: (String) -> Unit,
    downloadCount: Int,
    onOpenDownloads: () -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    onOpenMerged: (groupId: String, title: String) -> Unit,
    podcastDownloads: List<com.github.jankoran90.showlyfin.data.offline.OfflineDownload>,
) {
    val discoveryVm: com.github.jankoran90.showlyfin.feature.listen.PodcastDiscoveryViewModel = hiltViewModel()
    val filterVm: com.github.jankoran90.showlyfin.feature.listen.PodcastFilterViewModel = hiltViewModel()
    val discoveryState by discoveryVm.state.collectAsStateWithLifecycle()
    val filterState by filterVm.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable(stateSaver = PodcastTabSaver) {
        mutableStateOf(PodcastTab.fromPref(viewModel.podcastDefaultTab))
    }
    var showFilter by remember { mutableStateOf(false) }
    // TRAWL (Slovo, 2026-09-02): fulltext hledání jako overlay nad taby, bez zásahu do nav grafu.
    var showSearch by remember { mutableStateOf(false) }
    // Bump po zavření filtru → Timeline přepočítá feed dle nového rozsahu/typu.
    var filterEpoch by remember { mutableStateOf(0) }

    if (showSearch) {
        // SLOVO-KIDS-EPISODE: tahle obrazovka je reachable jen z dětského profilu (SlovoPhoneShell
        // kids větev) — audioOnly natvrdo true, ať se v hledání (BUG 2026-09-04) nezobrazí mrtvé
        // tlačítko Video (onPlayVideo tu žádný chytač nemá, callback by beztak nikam nevedl).
        PodcastSearchScreen(onBack = { showSearch = false }, audioOnly = true, modifier = Modifier.fillMaxSize())
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Offline: online taby (Timeline/Sledované/Objev) nemají data → zobraz rovnou stažené epizody
        // v ploše (parita se sekcí Audioknihy, která offline ukazuje stažené knihy přímo v gridu).
        if (state.isOffline) {
            OfflineDownloadedPodcasts(podcastDownloads, viewModel)
        } else {
        PodcastTabRow(
            selected = tab,
            onSelect = { tab = it },
            onOpenFilter = { showFilter = true },
            onOpenSearch = { showSearch = true },
        )

        // „Stažené" chip přesunut z řady do filtru (ikona Filtr) — viz PodcastFilterSheet.

        // User (2026-08-16 14:26, „udělej ten horizontal scroll mezi podsekcemi jak jsem chtěl") —
        // Timeline/Sledované/Objev jde teď i swipe gestem, ne jen tapem na PodcastTabRow. Tab a
        // pagerState se drží ve dvou směrech synchronizované (tap → scroll na stránku, swipe → tab).
        val tabs = PodcastTab.entries
        val pagerState = rememberPagerState(initialPage = tabs.indexOf(tab).coerceAtLeast(0)) { tabs.size }
        val pagerScope = rememberCoroutineScope()
        LaunchedEffect(tab) {
            if (tabs[pagerState.currentPage] != tab) pagerScope.launch { pagerState.animateScrollToPage(tabs.indexOf(tab)) }
        }
        LaunchedEffect(pagerState.currentPage) { tab = tabs[pagerState.currentPage] }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (tabs[page]) {
                PodcastTab.TIMELINE -> PodcastTimelineSection(
                    onOpenDiscover = { tab = PodcastTab.DISCOVER },
                    onOpenSource = { item ->
                        onOpenSourceEpisode(item.sourceType, item.sourceRef, item.sourceTitle, item.key)
                    },
                    refreshKey = filterEpoch,
                    modifier = Modifier.fillMaxSize(),
                )
                PodcastTab.FOLLOWING -> FollowingContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenPodcast = onOpenPodcast,
                    onOpenSource = onOpenSource,
                    onOpenMerged = onOpenMerged,
                    downloadCount = downloadCount,
                    sourceType = filterState.sourceType,
                )
                PodcastTab.DISCOVER -> PodcastDiscoverSection(modifier = Modifier.fillMaxSize())
            }
        }
        }
    }

    if (showFilter) {
        PodcastFilterSheet(
            timelineRangeDays = filterState.timelineRangeDays,
            onSetRange = filterVm::setTimelineRange,
            sourceType = filterState.sourceType,
            onSetSourceType = filterVm::setSourceType,
            minEpisodes = filterState.minEpisodes,
            onSetMinEpisodes = { filterVm.setMinEpisodes(it); discoveryVm.setMinEpisodes(it) },
            onlyDownloaded = filterState.onlyDownloaded,
            onSetOnlyDownloaded = filterVm::setOnlyDownloaded,
            downloadCount = downloadCount,
            onOpenDownloads = { showFilter = false; onOpenDownloads() },
            categories = discoveryState.categories,
            excluded = discoveryState.excluded,
            onToggleCategory = discoveryVm::toggleExclude,
            onDismiss = { showFilter = false; filterEpoch++ },
        )
    }
}

/** Vystředěná zpráva — funguje v Column i Box (vlastní fillMaxSize Box). Sdíleno s [ListenOfflineSection]. */
@Composable
internal fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
