package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Badge
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.data.abs.model.EpisodeDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.feature.listen.ListenMode
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.SwapVert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Poslechová sekce — přepínač Audioknihy ↔ Podcasty, knihovní chips + grid obálek s progressem.
 * Vše v Material 3 Expressive tématu (ListenExpressiveTheme). ABS login je v Nastavení.
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
/** WEFT (SHW-75/W3): položka knihovny Sledovaných pro JEDEN abecedně řazený grid (merged + zdroj + ABS). */
private sealed interface LibraryCard {
    val sortTitle: String
    val itemKey: String
    /** WEFT (SHW-75/W5): klíče pro skrytí (sloučená = všichni členové, zdroj = `type:ref`, ABS = `abs:id`). */
    val hideKeys: Set<String>

    data class Merged(val groupId: String, val title: String, val thumbnail: String?, val members: List<String>) : LibraryCard {
        override val sortTitle get() = title
        override val itemKey get() = "lg:$groupId"
        override val hideKeys get() = members.toSet()
    }

    data class Plain(val source: com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource, val key: String) : LibraryCard {
        override val sortTitle get() = source.title
        override val itemKey get() = "src:${source.id}"
        override val hideKeys get() = setOf(key)
    }

    data class Abs(val podcast: com.github.jankoran90.showlyfin.data.abs.model.Podcast) : LibraryCard {
        override val sortTitle get() = podcast.title
        override val itemKey get() = "abs:${podcast.id}"
        override val hideKeys get() = setOf("abs:${podcast.id}")
    }
}

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

/** Plan CASTAWAY — proužek „Offline", když není síť; vysvětlí, proč jsou vidět jen stažené věci. */
@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Offline — zobrazeny jen stažené položky.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryChips(
    libraries: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        libraries.forEach { (id, name) ->
            FilterChip(
                selected = id == selectedId,
                onClick = { onSelect(id) },
                label = { Text(name) },
            )
        }
    }
}

/**
 * Správa offline stažení Poslechu: ABS epizody + stažené RSS/YouTube podcasty (LEVER L3) v jednom
 * seznamu — klik = přehrát offline, koš = smazat, „Smazat vše" = obojí (NE filmy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsSheet(
    downloads: List<EpisodeDownload>,
    podcastDownloads: List<com.github.jankoran90.showlyfin.data.offline.OfflineDownload>,
    onDismiss: () -> Unit,
    onPlay: (EpisodeDownload) -> Unit,
    onPlayPodcast: (com.github.jankoran90.showlyfin.data.offline.OfflineDownload) -> Unit,
    onDelete: (String) -> Unit,
    onDeletePodcast: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val total = downloads.size + podcastDownloads.size
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Stažené · $total",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (total > 0) {
                TextButton(onClick = onDeleteAll) { Text("Smazat vše") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (total == 0) {
            Text(
                "Žádné stažené epizody.\nStáhni je v detailu podcastu přes ⋮ → Stáhnout do telefonu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(Modifier.height(420.dp)) {
                columnItems(downloads, key = { "abs_${it.episodeId}" }) { dl ->
                    val meta = buildList {
                        dl.podcastTitle?.let { add(it) }
                        if (dl.sizeBytes > 0) add(formatSize(dl.sizeBytes))
                    }.joinToString(" · ")
                    DownloadEntryRow(dl.title, meta, onPlay = { onPlay(dl) }, onDelete = { onDelete(dl.episodeId) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                columnItems(podcastDownloads, key = { "off_${it.key}" }) { dl ->
                    val meta = buildList {
                        dl.subtitle?.let { add(it) }
                        add(dl.sourceLabel)
                        if (dl.sizeBytes > 0) add(formatSize(dl.sizeBytes))
                    }.joinToString(" · ")
                    DownloadEntryRow(dl.title, meta, onPlay = { onPlayPodcast(dl) }, onDelete = { onDeletePodcast(dl.key) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun DownloadEntryRow(
    title: String,
    meta: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Smazat stažení", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}

/** WEFT (SHW-75/W6): offline pořad = stažené epizody seskupené pod jednu kartu (parita s Audioknihy grid). */
private data class OfflinePodcastShow(
    val title: String,
    val poster: String?,
    val sourceLabel: String,
    val episodes: List<OfflineDownload>,
)

private fun episodesWord(n: Int): String = when {
    n == 1 -> "epizoda"
    n in 2..4 -> "epizody"
    else -> "epizod"
}

/**
 * Offline sekce Podcasty — stažené epizody jako GRID KARET POŘADŮ (parita s Audioknihy/Sledované grid),
 * NE plochý seznam. Karta = jeden pořad (obálka + počet stažených), klik → [OfflinePodcastEpisodesSheet]
 * se staženými epizodami toho pořadu. Nahrazuje online taby (Timeline/Sledované/Objev), které bez sítě
 * nemají data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineDownloadedPodcasts(
    podcastDownloads: List<OfflineDownload>,
    viewModel: ListenViewModel,
) {
    if (podcastDownloads.isEmpty()) {
        CenteredMessage(
            "Jsi offline a nemáš žádné stažené epizody.\nStáhni je v detailu podcastu přes ⋮ → Stáhnout do telefonu, dokud jsi připojený.",
        )
        return
    }
    // Seskup stažené epizody dle pořadu (subtitle = název pořadu; fallback zdroj) → jedna karta na pořad.
    // RESONANCE (SHW-81): epizody NEJNOVĚJŠÍ NAHOŘE dle data vydání (fallback datum stažení u starých).
    val shows = remember(podcastDownloads) {
        podcastDownloads
            .groupBy { it.subtitle?.takeIf { s -> s.isNotBlank() } ?: it.sourceLabel }
            .map { (title, eps) ->
                OfflinePodcastShow(
                    title = title,
                    poster = eps.firstNotNullOfOrNull { it.posterPath ?: it.posterUrl },
                    sourceLabel = eps.first().sourceLabel,
                    episodes = eps.sortedByDescending { it.publishedAt ?: it.addedAt },
                )
            }
            .sortedBy { it.title.lowercase(Locale("cs")) }
    }
    var openShow by remember { mutableStateOf<OfflinePodcastShow?>(null) }
    var highlightKey by remember { mutableStateOf<String?>(null) }

    // RESONANCE (SHW-81): proklik z přehrávače (offline epizoda) → otevři kartu pořadu + zvýrazni epizodu.
    val requested by viewModel.requestedOfflineShow.collectAsStateWithLifecycle()
    LaunchedEffect(requested, shows) {
        requested?.let { (title, epKey) ->
            shows.firstOrNull { it.title == title }?.let { openShow = it; highlightKey = epKey }
            viewModel.consumeOfflineRequest()
        }
    }

    // Grid + detail v jednom Boxu → detail (Scaffold, neprůhledné pozadí) PŘEKRYJE grid. Kdyby byl
    // detail sourozencem gridu v Column, `fillMaxSize` grid by mu nenechal výšku (0 px = „nic se neděje").
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shows, key = { it.title }) { show ->
                OfflinePodcastCard(show = show, onClick = { openShow = show })
            }
        }

        openShow?.let { opened ->
            // Živý přemap (po smazání epizody ať se detail zaktualizuje / zavře, když pořad zmizí).
            val live = shows.firstOrNull { it.title == opened.title }
            LaunchedEffect(live) { if (live == null) openShow = null }
            if (live != null) {
                OfflinePodcastDetailScreen(
                    show = live,
                    viewModel = viewModel,
                    highlightEpisodeKey = highlightKey,
                    onBack = { openShow = null; highlightKey = null },
                )
            }
        }
    }
}

/** WEFT (SHW-75/W6): karta staženého pořadu — CHORUS Osa 2 delegát nad [CoverCard] (badge počtu). */
@Composable
private fun OfflinePodcastCard(
    show: OfflinePodcastShow,
    onClick: () -> Unit,
) {
    CoverCard(
        title = show.title,
        subtitle = "${show.sourceLabel} · ${show.episodes.size} ${episodesWord(show.episodes.size)}",
        imageUrl = show.poster,
        onClick = onClick,
        placeholder = Icons.Default.Podcasts,
        overlay = {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(show.episodes.size.toString())
            }
        },
    )
}

/**
 * RESONANCE (SHW-81): offline detail pořadu = PARITA s online vzhledem. Plná obrazitka (TopAppBar
 * s názvem pořadu, cover + zdroj + počet), epizody přes sdílený [PodcastEpisodeRow] (obrázek + název
 * + datum + popis + [Poslech/Pokračovat] + [⋮]). Nejnovější nahoře. Koš NENÍ v popředí → jen v ⋮ menu
 * (Přehrát · Smazat z telefonu). Zvýrazní + resume právě hranou epizodu (`playerState`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflinePodcastDetailScreen(
    show: OfflinePodcastShow,
    viewModel: ListenViewModel,
    highlightEpisodeKey: String? = null,
    onBack: () -> Unit,
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val playingKey = playerState.currentEpisodeId?.takeIf { playerState.isActive }
    var actionEp by remember { mutableStateOf<OfflineDownload?>(null) }
    var newestFirst by remember { mutableStateOf(viewModel.offlinePodcastNewestFirst) }
    // show.episodes je defaultně nejnovější nahoře (publishedAt?:addedAt); obrácení = reversed.
    val episodes = remember(show.episodes, newestFirst) {
        if (newestFirst) show.episodes else show.episodes.reversed()
    }
    val listState = rememberLazyListState()
    // Proklik z přehrávače: doskroluj na zvýrazněnou/hranou epizodu (+2 kvůli hlavičce a nadpisu „Epizody").
    LaunchedEffect(highlightEpisodeKey, episodes) {
        val key = highlightEpisodeKey ?: playingKey
        if (key != null) {
            val idx = episodes.indexOfFirst { it.key == key }
            if (idx >= 0) listState.scrollToItem((idx + 2).coerceAtMost(episodes.size + 1))
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(show.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        newestFirst = !newestFirst
                        viewModel.offlinePodcastNewestFirst = newestFirst
                    }) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = if (newestFirst) "Řazení: nejnovější nahoře (ťuk = obrátit)"
                            else "Řazení: nejstarší nahoře (ťuk = obrátit)",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Row {
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (show.poster != null) {
                            AsyncImage(
                                model = show.poster,
                                contentDescription = show.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Column(
                        Modifier.padding(start = 16.dp).height(120.dp).weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            show.sourceLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${show.episodes.size} ${episodesWord(show.episodes.size)} · staženo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    "Epizody",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
                )
            }
            columnItems(episodes, key = { "off_${it.key}" }) { dl ->
                val isCurrent = dl.key == playingKey
                // Progress: hraje-li → z přehrávače; jinak z uložené resume pozice stažené epizody.
                val progress: Float? = when {
                    isCurrent && playerState.durationMs > 0 ->
                        (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                    dl.resumePositionMs > 0 && dl.durationSec > 0 ->
                        (dl.resumePositionMs / 1000f / dl.durationSec.toFloat()).coerceIn(0f, 1f)
                    else -> null
                }
                PodcastEpisodeRow(
                    title = dl.title,
                    image = dl.posterPath ?: dl.posterUrl,
                    date = msToIso(dl.publishedAt),
                    duration = dl.durationSec.takeIf { it > 0 }?.toLong()?.toString(),
                    description = dl.description,
                    downloaded = false, // v offline detailu je vše staženo → ikona redundantní
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playerState.isPlaying,
                    progress = progress,
                    canResume = !isCurrent && dl.resumePositionMs > 0,
                    remainingLabel = null,
                    hasVideo = false, // offline podcast = zatím jen audio
                    highlighted = dl.key == highlightEpisodeKey,
                    onPlay = { viewModel.playOfflinePodcast(dl) },
                    onVideo = {},
                    onMore = { actionEp = dl },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }

    actionEp?.let { dl ->
        ListenEpisodeActionSheet(
            title = dl.title,
            actions = listOf(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playOfflinePodcast(dl); actionEp = null
                },
                ListenEpisodeAction(Icons.Default.Delete, "Smazat z telefonu") {
                    viewModel.deleteOfflinePodcast(dl.key); actionEp = null
                },
            ),
            onDismiss = { actionEp = null },
        )
    }
}

/** RESONANCE (SHW-81): epoch ms → "yyyy-MM-dd" pro [formatRssDate] v řádku epizody (jednotné datum). */
private fun msToIso(ms: Long?): String? {
    if (ms == null || ms <= 0L) return null
    return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms)) }.getOrNull()
}

/** Vystředěná zpráva — funguje v Column i Box (vlastní fillMaxSize Box). */
@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
