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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.ListenMode
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel

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
    onOpenPodcast: (itemId: String) -> Unit,
    onPlayEpisode: (itemId: String, episodeId: String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    /** Timeline: otevři obsah zdroje (RSS feedUrl / YouTube handle) a zvýrazni epizodu [episodeKey]. */
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    /** TWINE: otevři sloučený pohled propojeného pořadu (audio+video) podle [groupId]. */
    onOpenMerged: (groupId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val podcastDownloads by viewModel.offlinePodcasts.collectAsStateWithLifecycle()
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
            } else {
                // PRESET (SHW-65) — pořadí záložek dle nastavení (Audioknihy / Podcasty první).
                val modes = remember(state.booksFirst) {
                    if (state.booksFirst) listOf(ListenMode.BOOKS, ListenMode.PODCASTS)
                    else listOf(ListenMode.PODCASTS, ListenMode.BOOKS)
                }
                key(state.booksFirst) {
                    val pagerState = rememberPagerState(
                        initialPage = modes.indexOf(state.mode).coerceAtLeast(0),
                    ) { modes.size }
                    val scope = rememberCoroutineScope()
                    // Swipe ⇄ přepíná režim (a tím i načítání dat).
                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.setMode(modes[pagerState.currentPage])
                    }
                    Column(Modifier.fillMaxSize()) {
                        if (state.isOffline) OfflineBanner()
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            modes.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = pagerState.currentPage == i,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                                ) { Text(if (m == ListenMode.BOOKS) "Audioknihy" else "Podcasty") }
                            }
                        }

                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            when (modes[page]) {
                                ListenMode.BOOKS -> BooksContent(state, viewModel, onOpenBook)
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

@Composable
private fun BooksContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenBook: (String) -> Unit,
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        AudiobookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            downloaded = book.id in state.downloadedBookIds,
                        )
                    }
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
    // Bump po zavření filtru → Timeline přepočítá feed dle nového rozsahu/typu.
    var filterEpoch by remember { mutableStateOf(0) }

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
            activeFilterCount = filterVm.activeCount(discoveryState.excluded.size),
        )

        // „Stažené" chip přesunut z řady do filtru (ikona Filtr) — viz PodcastFilterSheet.

        when (tab) {
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

/** AGORA-TABS: Sledované = dnešní grid vlastních zdrojů + ABS podcastů (s filtrem typu zdroje). */
@Composable
private fun FollowingContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    onOpenMerged: (groupId: String, title: String) -> Unit,
    downloadCount: Int,
    sourceType: String,
) {
    // Filtr typu zdroje (z filtru): all|rss|youtube — aplikuje se na vlastní zdroje. ABS podcasty
    // ukazujeme jen u „Vše" nebo „Podcasty" (jsou to RSS-like pořady, ne YouTube).
    val sources = state.customSources.filter { sourceType == "all" || it.type == sourceType }
    val showAbs = sourceType == "all" || sourceType == "rss"

    // TWINE (SHW-74 / plán F7): slinkované zdroje (audio+video = týž pořad) → 1 sloučená karta.
    val links by viewModel.sourceLinks.collectAsStateWithLifecycle()
    var linkFor by remember { mutableStateOf<com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource?>(null) }
    // WEFT (SHW-75/W5): per-profil skrytí pořadů ve Sledovaných + akční sheet při dlouhém stisku karty.
    val cfg by viewModel.profileConfig.collectAsStateWithLifecycle()
    val hiddenFollowing = cfg.hiddenFollowingSourceKeys
    var actionCard by remember { mutableStateOf<LibraryCard?>(null) }
    val byKey = remember(state.customSources) { state.customSources.associateBy { viewModel.sourceKey(it) } }
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
                                    PodcastCard(podcast = card.podcast, onClick = { onOpenPodcast(card.podcast.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    // WEFT (SHW-75/W5): dlouhý stisk karty → akce (Propojit / Skrýt ve Sledovaných / Skrýt na časové ose).
    actionCard?.let { card ->
        ListenEpisodeActionSheet(
            title = card.sortTitle,
            actions = listOfNotNull(
                (card as? LibraryCard.Plain)?.let { c ->
                    ListenEpisodeAction(Icons.Default.Link, "Propojit s jinou verzí (audio + video)") {
                        linkFor = c.source
                    }
                },
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
