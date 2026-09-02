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
                    onEditBook = onEditBook,
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
        PodcastSearchScreen(onBack = { showSearch = false }, modifier = Modifier.fillMaxSize())
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

/**
 * AGORA-TABS: Sledované = grid vlastních zdrojů + ABS podcastů. User (2026-08-16 18:23, „Poslech
 * čisté jako Domů") — samostatná swipe strana bez filtru typu zdroje (ten dřív žil ve sdíleném
 * filtr sheetu s Timeline/Objev — na téhle čisté stránce se neukazuje; `sourceType` volající strana
 * nechá na "all").
 */
@Composable
fun FollowingContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    onOpenMerged: (groupId: String, title: String) -> Unit,
    downloadCount: Int,
    sourceType: String,
) {
    // WEFT (SHW-75/W5): per-profil skrytí pořadů ve Sledovaných + akční sheet při dlouhém stisku karty.
    val cfg by viewModel.profileConfig.collectAsStateWithLifecycle()
    val hiddenFollowing = cfg.hiddenFollowingSourceKeys
    var actionCard by remember { mutableStateOf<LibraryCard?>(null) }
    // User (2026-08-15) — dlouhý stisk na ABS podcast (jen Dospělý) → "Zobrazit/Skrýt dětem".
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    // PROFIL (2026-08-16, „zdroje per profil + sdílení") — vidím jen svoje vlastní zdroje + co mi kdo
    // sdílel. Legacy zdroje (addedBy == null, vznikly PŘED touhle featurou) zůstávají viditelné všem.
    val myUuid = activeProfile?.profileUuid
    val visibleSources = remember(state.customSources, myUuid, cfg.sharedSourceKeys) {
        state.customSources.filter { src ->
            src.addedBy.isNullOrBlank() || src.addedBy == myUuid ||
                "${src.type}:${src.ref}" in cfg.sharedSourceKeys
        }
    }
    // Filtr typu zdroje (z filtru): all|rss|youtube — aplikuje se na vlastní zdroje. ABS podcasty
    // ukazujeme jen u „Vše" nebo „Podcasty" (jsou to RSS-like pořady, ne YouTube).
    val sources = visibleSources.filter { sourceType == "all" || it.type == sourceType }
    val showAbs = sourceType == "all" || sourceType == "rss"

    // TWINE (SHW-74 / plán F7): slinkované zdroje (audio+video = týž pořad) → 1 sloučená karta.
    val links by viewModel.sourceLinks.collectAsStateWithLifecycle()
    var linkFor by remember { mutableStateOf<com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource?>(null) }
    val kidsHidden by viewModel.kidsHiddenPodcastIds.collectAsStateWithLifecycle()
    // SLOVO-KIDS-EPISODE — dlouhý stisk i na vlastních zdrojích (Plain/Merged) → "Zobrazit/Skrýt dětem".
    val kidsVisibleSources by viewModel.kidsVisibleSourceKeys.collectAsStateWithLifecycle()
    val byKey = remember(visibleSources) { visibleSources.associateBy { viewModel.sourceKey(it) } }
    val linkedKeys = remember(links) { links.flatMap { it.members }.toSet() }
    // Samostatné karty = filtrované zdroje, které nejsou v žádné skupině.
    val plainSources = sources.filter { viewModel.sourceKey(it) !in linkedKeys }

    // WEFT (SHW-75/W3+W5): sloučené + samostatné + ABS karty do JEDNOHO abecedně řazeného gridu
    // (dřív sloučené vždy první → nepůsobilo „podle abecedy"), odfiltrované o per-profil skryté
    // ve Sledovaných (W5). Sloučená karta zmizí, až když jsou skryté VŠECHNY její verze.
    val libraryCards = remember(links, byKey, plainSources, state.podcasts, showAbs, sourceType, hiddenFollowing) {
        buildList<LibraryCard> {
            links.forEach { g ->
                val members = g.members.mapNotNull { byKey[it] }
                if (members.isEmpty()) return@forEach
                if (sourceType != "all" && members.none { it.type == sourceType }) return@forEach
                add(LibraryCard.Merged(g.id, g.title ?: members.first().title, g.thumbnail ?: members.firstNotNullOfOrNull { it.thumbnail }, g.members))
            }
            plainSources.forEach { add(LibraryCard.Plain(it, viewModel.sourceKey(it))) }
            if (showAbs) state.podcasts.forEach { add(LibraryCard.Abs(it)) }
        }
            .filter { card -> card.hideKeys.any { it !in hiddenFollowing } }
            .sortedBy { it.sortTitle.lowercase(java.util.Locale("cs")) }
    }
    val hasContent = libraryCards.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        when {
            state.isLoading && !hasContent ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            state.isOffline && !hasContent ->
                CenteredMessage(
                    if (downloadCount > 0) {
                        "Offline — online seznam podcastů není dostupný.\nStažené epizody najdeš nahoře."
                    } else {
                        "Jsi offline a nemáš žádné stažené epizody."
                    },
                )

            state.error != null && !hasContent -> CenteredMessage(state.error)

            else -> {
                if (showAbs && state.podcastLibraries.size > 1) {
                    LibraryChips(
                        libraries = state.podcastLibraries.map { it.id to it.name },
                        selectedId = state.selectedPodcastLibraryId,
                        onSelect = viewModel::selectPodcastLibrary,
                    )
                }
                if (!hasContent && !state.isLoading) {
                    CenteredMessage("Zatím nesleduješ žádné podcasty.\nPřidej zdroje v záložce Objev.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // WEFT (SHW-75/W3): jeden abecedně řazený grid (sloučené + samostatné + ABS).
                        items(libraryCards, key = { it.itemKey }) { card ->
                            when (card) {
                                is LibraryCard.Merged ->
                                    MergedSourceCard(
                                        title = card.title,
                                        thumbnail = card.thumbnail,
                                        onClick = { onOpenMerged(card.groupId, card.title) },
                                        onLongClick = { actionCard = card },
                                    )
                                is LibraryCard.Plain ->
                                    SourceCard(
                                        source = card.source,
                                        onClick = { onOpenSource(card.source) },
                                        onLongClick = { actionCard = card },
                                    )
                                is LibraryCard.Abs ->
                                    PodcastCard(
                                        podcast = card.podcast,
                                        onClick = { onOpenPodcast(card.podcast.id) },
                                        onLongClick = { actionCard = card },
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    // WEFT (SHW-75/W5): dlouhý stisk karty → akce (Propojit / Skrýt ve Sledovaných / Skrýt na časové ose).
    // User (2026-08-15): u ABS podcastu navíc (jen Dospělý) "Zobrazit/Skrýt dětem" — zkratka k témuž
    // hiddenPodcastIds, co jinak nastavuje sekce Profil → Podcasty pro Děti (rychlejší z gridu).
    // PROFIL (2026-08-16) — ostatní dospělí profilové → "Sdílet s…" akce, jedna na profil.
    val otherAdultProfiles by viewModel.otherAdultProfiles.collectAsStateWithLifecycle()
    actionCard?.let { card ->
        val absPodcastId = (card as? LibraryCard.Abs)?.podcast?.id
        val shareActions = if (card is LibraryCard.Plain || card is LibraryCard.Merged) {
            otherAdultProfiles.map { target ->
                val shared = viewModel.isSourceSharedWith(card.hideKeys, target)
                ListenEpisodeAction(
                    if (shared) Icons.Default.Visibility else Icons.Default.Share,
                    if (shared) "Přestat sdílet s ${target.name}" else "Sdílet s ${target.name}",
                ) { viewModel.setSourceSharedWith(card.hideKeys, target.id, !shared) }
            }
        } else emptyList()
        ListenEpisodeActionSheet(
            title = card.sortTitle,
            infoLine = if (card is LibraryCard.Plain || card is LibraryCard.Merged) {
                val owner = card.hideKeys.firstNotNullOfOrNull { viewModel.ownerOfSourceKey(it) }
                viewModel.ownershipInfoLine(owner, otherAdultProfiles.filter { viewModel.isSourceSharedWith(card.hideKeys, it) })
            } else null,
            actions = listOfNotNull(
                (card as? LibraryCard.Plain)?.let { c ->
                    ListenEpisodeAction(Icons.Default.Link, "Propojit s jinou verzí (audio + video)") {
                        linkFor = c.source
                    }
                },
                if (absPodcastId != null && activeProfile?.isAdmin == true) {
                    val visibleToKids = absPodcastId !in kidsHidden
                    ListenEpisodeAction(
                        if (visibleToKids) Icons.Default.ChildCare else Icons.Default.Visibility,
                        if (visibleToKids) "Skrýt dětem" else "Zobrazit dětem",
                    ) { viewModel.setPodcastVisibleForKids(absPodcastId, !visibleToKids) }
                } else null,
                // SLOVO-KIDS-EPISODE — totéž pro vlastní zdroje (Plain/Merged), whitelist místo blacklistu.
                if ((card is LibraryCard.Plain || card is LibraryCard.Merged) && activeProfile?.isAdmin == true) {
                    val visibleToKids = card.hideKeys.any { it in kidsVisibleSources }
                    ListenEpisodeAction(
                        if (visibleToKids) Icons.Default.ChildCare else Icons.Default.Visibility,
                        if (visibleToKids) "Skrýt dětem" else "Zobrazit dětem",
                    ) { viewModel.setSourceVisibleForKids(card.hideKeys, !visibleToKids) }
                } else null,
            ) + shareActions + listOfNotNull(
                ListenEpisodeAction(Icons.Default.VisibilityOff, "Skrýt ve Sledovaných") {
                    viewModel.setHidden(card.hideKeys, timeline = false, hidden = true)
                },
                ListenEpisodeAction(Icons.Default.VisibilityOff, "Skrýt na časové ose") {
                    viewModel.setHidden(card.hideKeys, timeline = true, hidden = true)
                },
            ),
            onDismiss = { actionCard = null },
        )
    }

    // TWINE: dlouhý stisk karty → vyber druhou verzi pořadu k propojení (auto-návrh nahoře, potvrdí user).
    linkFor?.let { src ->
        SourceLinkSheet(
            source = src,
            candidates = viewModel.linkCandidates(src),
            suggested = viewModel.suggestLinkMatch(src),
            onLink = { target -> viewModel.linkSources(src, target) },
            onDismiss = { linkFor = null },
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
