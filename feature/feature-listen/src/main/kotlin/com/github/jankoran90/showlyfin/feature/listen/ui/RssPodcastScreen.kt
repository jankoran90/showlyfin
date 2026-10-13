package com.github.jankoran90.showlyfin.feature.listen.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.data.uploader.model.EpisodeVideo
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.RssEpisode
import com.github.jankoran90.showlyfin.feature.listen.RssPodcastViewModel
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookChapter
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.choosePlaybackResume
import com.github.jankoran90.showlyfin.feature.listen.player.isNearEnd

/**
 * PRESET (SHW-65): obrazovka RSS podcastu jako zdroj Poslechu. Seznam epizod, u každé „Poslech"
 * (přehrání přímé enclosure URL přes náš poslechový přehrávač). Streaming, nic se nestahuje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssPodcastScreen(
    feedUrl: String,
    title: String,
    onBack: () -> Unit,
    onOpenAudioPlayer: () -> Unit,
    // REWIND (SHW-68): resumeKey = episodeKey(ep) — sdílený klíč s audio řádkem → resume/progres videa.
    onPlayVideo: (jfItemId: String, title: String, resumeKey: String) -> Unit,
    // AGORA (F5): přehrání VIDEO verze epizody z YouTube (externí proxy URL, jako YouTube kanál).
    onPlayYoutubeVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    // NAVIGATE (SHW-73): klíč epizody (`rss:…`) k zvýraznění + scrollu (přišlo z Timeline řádku / cover prokliku).
    highlightEpisodeKey: String? = null,
    // SLOVO-KIDS-EPISODE (2026-08-15) — dětský profil: vždy jen audio, video volby úplně skryté.
    audioOnly: Boolean = false,
    // SLOVO-KIDS-EPISODE — non-null = otevřeno JEN na jednu AUTO-detekovanou sérii (dětská cesta,
    // nikdy neuvidí zbytek podcastu). Hodnota = [PodcastEpisodeSeriesGrouping.seriesSlug].
    seriesFilter: String? = null,
    // User 2026-10-12 („děti mají offline vidět obsah jen když je stažený") — true = jen stažené díly.
    onlyDownloaded: Boolean = false,
    // SLOVO-KIDS-EPISODE — admin (Dospělý) může dlouhým stiskem na kartě série „Zobrazit/Skrýt dětem".
    isAdmin: Boolean = false,
    kidsVisibleSeriesKeys: Set<String> = emptySet(),
    onSetSeriesVisibleForKids: (key: String, visible: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: RssPodcastViewModel = hiltViewModel(),
    bookViewModel: SeriesBookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layout by bookViewModel.layout.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    val videoResumeMarks by viewModel.videoResumeMarks.collectAsStateWithLifecycle()
    val castMessage by viewModel.castMessage.collectAsStateWithLifecycle()
    // AGORA (F5) ruční výběr: kandidáti video verze + běžící hledání + doporučený kandidát.
    val videoCandidates by viewModel.videoCandidates.collectAsStateWithLifecycle()
    val videoLoadingFor by viewModel.videoLoadingFor.collectAsStateWithLifecycle()
    val recommendedVideoId by viewModel.recommendedVideoId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(feedUrl) { viewModel.load(feedUrl) }
    // EXODUS E2: výsledek castu videa na TV → jednorázový Toast.
    LaunchedEffect(castMessage) {
        castMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeCastMessage()
        }
    }
    var actionEpisode by remember { mutableStateOf<RssEpisode?>(null) }
    // AGORA (F5): epizoda, pro kterou je otevřený picker video verze (drží kontext pro play/cast).
    var videoForEpisode by remember { mutableStateOf<RssEpisode?>(null) }
    val fallbackTitle = state.title ?: title
    // FOCUS (2026-09-03, user „hlavně bych měl hledat na jedním zdrojem, ne plošně") — lupa v TopAppBar
    // otevře fulltext hledání SCOPED jen na tenhle feed (parita s YoutubeChannelScreen).
    var showSearch by remember { mutableStateOf(false) }

    // SLOVO-KIDS-EPISODE — [seriesFilter] != null: dětská cesta, jen epizody DANÉ série, plochý seznam
    // (žádné další seskupování). Jinak (admin): AUTO seskupení do sérií + samostatné epizody.
    val sourceKey = "rss:$feedUrl"
    // Slovo 2026-10-13 — ruční knihy (pravidla) mají přednost před automatickou sérií z názvu.
    val seriesRules by bookViewModel.seriesRules.collectAsStateWithLifecycle()
    val seriesTitleOf: (String) -> String? = remember(seriesRules) {
        { t -> bookViewModel.ruleTitle(sourceKey, t) ?: PodcastEpisodeSeriesGrouping.detectSeriesTitle(t) }
    }
    val ruleDraft = remember { mutableStateOf<SeriesRuleDraft?>(null) }
    val openBookSlug = rememberSaveable { mutableStateOf<String?>(null) }
    // Dětská pojistka otevřela první schválenou sérii → díl z přehrávače otevře SVOU schválenou sérii.
    @Suppress("NAME_SHADOWING")
    val seriesFilter = remember(seriesFilter, highlightEpisodeKey, state.episodes, seriesTitleOf, kidsVisibleSeriesKeys) {
        seriesFilterForHighlight(seriesFilter, highlightEpisodeKey, state.episodes, viewModel::episodeKey, { seriesTitleOf(it.title) }, sourceKey, kidsVisibleSeriesKeys)
    }
    val filteredEpisodes = remember(state.episodes, seriesFilter, onlyDownloaded, offlineStates, seriesTitleOf) {
        val inSeries = if (seriesFilter == null) state.episodes
        else state.episodes.filter {
            seriesTitleOf(it.title)?.let { t -> PodcastEpisodeSeriesGrouping.seriesSlug(t) } == seriesFilter
        }
        if (!onlyDownloaded) inSeries
        else inSeries.filter { offlineStates[viewModel.episodeKey(it)]?.status == OfflineStatus.DOWNLOADED }
    }
    val shelfItems = remember(filteredEpisodes, seriesFilter, seriesTitleOf) {
        if (seriesFilter != null) null
        else PodcastEpisodeSeriesGrouping.group(filteredEpisodes, titleOf = { it.title }, seriesTitleOf = { seriesTitleOf(it.title) })
    }
    // BUG (2026-09-04, user screenshot): rozposlouchané/rozkoukané epizody nahoru, parita s YoutubeChannelScreen/CtvProgramScreen.
    val inProgressIds = resumeMarks.filterValues { !it.isFinished }.keys + videoResumeMarks.filterValues { !it.isNearEnd() }.keys
    val orderedEpisodes = remember(filteredEpisodes, inProgressIds) {
        PodcastEpisodeSeriesGrouping.pinInProgressFlat(filteredEpisodes) { viewModel.episodeKey(it) in inProgressIds }
    }
    val orderedShelfItems = remember(shelfItems, inProgressIds) {
        shelfItems?.let { PodcastEpisodeSeriesGrouping.pinInProgress(it) { ep -> viewModel.episodeKey(ep) in inProgressIds } }
    }
    var expandedSeries by remember { mutableStateOf(setOf<String>()) }
    var seriesForAction by remember {
        mutableStateOf<PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<RssEpisode>?>(null)
    }
    // WATCHDOG (2026-08-15, user „tlačítko série vpravo nahoře, aktivní se zabarví, jen série, sbalené,
    // řazené sestupně dle data posledního dílu") — přepínač „Jen série" v TopAppBar (jen admin pohled).
    var seriesOnly by remember { mutableStateOf(false) }
    val seriesOnlyGroups = remember(shelfItems) {
        shelfItems?.filterIsInstance<PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<RssEpisode>>()
            ?.sortedByDescending { g -> PodcastEpisodeSeriesGrouping.latestDateMs(g.members) { it.date } ?: 0L }
    }
    // NAVIGATE (SHW-73): jakmile se epizody načtou, jednorázově odscrolluj na zvýrazněnou epizodu.
    val listState = rememberLazyListState()
    var scrolledToHighlight by remember { mutableStateOf(false) }
    LaunchedEffect(highlightEpisodeKey, filteredEpisodes) {
        if (!scrolledToHighlight && highlightEpisodeKey != null && filteredEpisodes.isNotEmpty()) {
            val idx = filteredEpisodes.indexOfFirst { viewModel.episodeKey(it) == highlightEpisodeKey }
            if (idx >= 0) {
                listState.scrollToItem(idx)
                scrolledToHighlight = true
            }
        }
    }

    // Vytaženo z původního items()-lambda, aby ho šlo sdílet mezi plochým (seriesFilter) i
    // seskupeným (admin) režimem beze zdvojení kódu.
    @Composable
    fun EpisodeRowFor(ep: RssEpisode) {
        val key = viewModel.episodeKey(ep)
        val isCurrent = playerState.currentEpisodeId == key && playerState.isActive
        // NAVIGATE: epizoda, ze které se uživatel proklikl (Timeline/cover) — zvýrazni i když nehraje.
        val isHighlighted = highlightEpisodeKey != null && key == highlightEpisodeKey
        // ADAPT (2026-09-04): vyhrává POKROČILEJŠÍ pozice mezi audio/video markem (ne poslední zápis) —
        // progres + „Pokračovat" funguje i u VIDEO epizody.
        val rssAudioMark = resumeMarks[key]
        val rssVideoMark = videoResumeMarks[key]
        val rssChoice = choosePlaybackResume(rssAudioMark?.posMs, rssAudioMark?.durMs, rssAudioMark?.isFinished == true, rssVideoMark?.posMs, rssVideoMark?.durMs, rssAudioMark?.updatedAt ?: 0L, rssVideoMark?.updatedAt ?: 0L)
        val markPos = rssChoice?.posMs
        val markDur = rssChoice?.durMs
        // User (2026-08-16) — dohraná epizoda (mark na konci) = „poslechnuto", ne rozposlouchaná.
        val isFinished = rssChoice?.isFinished == true
        val progress: Float? = when {
            isCurrent && playerState.durationMs > 0 ->
                (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
            markPos != null && markDur != null && markDur > 0 ->
                (markPos.toFloat() / markDur).coerceIn(0f, 1f)
            else -> null
        }
        val canResume = !isCurrent && markPos != null && !isFinished
        val remainingLabel = if (!isCurrent && !isFinished && markPos != null && markDur != null && markDur > 0)
            "zbývá ${formatClock((markDur - markPos).coerceAtLeast(0L))}" else null
        PodcastEpisodeRow(
            title = ep.title,
            image = ep.image ?: state.image,
            date = ep.date,
            duration = ep.duration,
            description = ep.description,
            downloaded = offlineStates[key]?.status == OfflineStatus.DOWNLOADED,
            isCurrent = isCurrent,
            isPlaying = isCurrent && playerState.isPlaying,
            progress = progress,
            canResume = canResume,
            remainingLabel = remainingLabel,
            hasVideo = ep.jfItemId != null && !audioOnly,
            highlighted = isHighlighted,
            isFinished = isFinished,
            onPlay = {
                // L2b: ťuk vždy ROVNOU spustí přehrávání (current=resume bez reloadu, jinak nová epizoda).
                if (isCurrent) viewModel.resumeCurrent() else viewModel.playAudio(ep, fallbackTitle)
                onOpenAudioPlayer()
            },
            onVideo = { ep.jfItemId?.let { onPlayVideo(it, ep.title.ifBlank { fallbackTitle }, key) } },
            onMore = { actionEpisode = ep },
            onEndListening = { viewModel.resetPosition(ep) },
        )
    }

    @Composable
    fun SeriesRowFor(group: PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<RssEpisode>) {
        val latestMs = PodcastEpisodeSeriesGrouping.latestDateMs(group.members) { it.date }
        PodcastSeriesRow(
            title = group.title,
            memberCount = group.members.size,
            latestDateLabel = latestMs?.let { PodcastEpisodeSeriesGrouping.formatSeriesDate(it) },
            thumbnail = group.members.firstOrNull()?.image ?: state.image,
            expanded = group.slug in expandedSeries,
            asBook = layout.seriesAsBook,
            onClick = {
                if (layout.seriesAsBook) openBookSlug.value = group.slug
                else expandedSeries = if (group.slug in expandedSeries) expandedSeries - group.slug else expandedSeries + group.slug
            },
            onLongClick = if (isAdmin) ({ seriesForAction = group }) else null,
        )
    }

    // Slovo 2026-10-13 — série jako kniha (Ďábelské káry = kapitoly), viz [SeriesBookGate].
    fun chapterOf(ep: RssEpisode) = SeriesBookChapter(
        key = viewModel.episodeKey(ep), title = ep.title.ifBlank { fallbackTitle }, durationSec = viewModel.durationSecOf(ep),
        date = ep.date, image = ep.image ?: state.image, description = ep.description,
        queued = viewModel.toQueued(ep, fallbackTitle), download = viewModel.offlineRequest(ep, fallbackTitle),
        onVideo = ep.jfItemId?.takeIf { !audioOnly }?.let { jf -> { onPlayVideo(jf, ep.title.ifBlank { fallbackTitle }, viewModel.episodeKey(ep)) } },
    )
    SeriesRuleDialog(
        draft = ruleDraft,
        onSave = { original, rule ->
            bookViewModel.saveRule(sourceKey, original, rule)
            openBookSlug.value = PodcastEpisodeSeriesGrouping.seriesSlug(rule.title)
        },
        onDelete = { t -> bookViewModel.removeRule(sourceKey, t); openBookSlug.value = null },
    )
    if (!showSearch && SeriesBookGate(
            layout = layout, shelfItems = shelfItems, seriesFiltered = if (seriesFilter != null) filteredEpisodes else null,
            showTitle = fallbackTitle, seriesTitleOf = { seriesTitleOf(it.title) },
            keyOf = viewModel::episodeKey,
            chaptersOf = { m -> PodcastEpisodeSeriesGrouping.chapterOrder(m, { it.title }, { it.date }).map(::chapterOf) },
            openSlug = openBookSlug, highlightKey = highlightEpisodeKey, wideCover = false,
            onBack = onBack, onOpenAudioPlayer = onOpenAudioPlayer, modifier = modifier,
            onEditBook = if (isAdmin) ({ t -> ruleDraft.value = bookRuleDraft(bookViewModel.ruleFor(sourceKey, t), t) }) else null,
            kidsVisible = if (isAdmin) ({ t -> PodcastEpisodeSeriesGrouping.buildSeriesKey(sourceKey, t) in kidsVisibleSeriesKeys }) else null,
            onToggleKids = { t, v -> onSetSeriesVisibleForKids(PodcastEpisodeSeriesGrouping.buildSeriesKey(sourceKey, t), v) },
        )
    ) return

    if (showSearch) {
        PodcastSearchScreen(
            onBack = { showSearch = false },
            scopeSource = PodcastSource(id = sourceKey, type = "rss", ref = feedUrl, title = fallbackTitle),
            scopeLabel = fallbackTitle,
            audioOnly = audioOnly,
            onPlayVideo = onPlayYoutubeVideo,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    // Přepínač jen v admin (neserials-filtrovaném) pohledu — dětská cesta ho nepotřebuje.
                    if (seriesFilter == null && !seriesOnlyGroups.isNullOrEmpty()) {
                        IconButton(onClick = { seriesOnly = !seriesOnly }) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = if (seriesOnly) "Zobrazit vše" else "Jen série",
                                tint = if (seriesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // FOCUS (2026-09-03) — hledání scoped na tenhle feed.
                    if (seriesFilter == null) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Hledat v tomto podcastu")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && filteredEpisodes.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.error != null && filteredEpisodes.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                    start = 12.dp, end = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (shelfItems == null) {
                    // Dětská cesta (seriesFilter) NEBO fallback — plochý seznam, žádné seskupení.
                    items(orderedEpisodes, key = { it.id }) { ep -> EpisodeRowFor(ep) }
                } else if (seriesOnly) {
                    // WATCHDOG — „Jen série": jen sbalené série, řazené sestupně dle data posledního dílu.
                    seriesOnlyGroups.orEmpty().forEach { group ->
                        item(key = "series_${group.slug}") { SeriesRowFor(group) }
                        if (group.slug in expandedSeries) {
                            items(group.members, key = { it.id }) { ep -> EpisodeRowFor(ep) }
                        }
                    }
                } else {
                    // Admin — AUTO seskupené série (SLOVO-KIDS-EPISODE) + samostatné epizody, rozposlouchané nahoře.
                    orderedShelfItems.orEmpty().forEach { shelfItem ->
                        when (shelfItem) {
                            is PodcastEpisodeSeriesGrouping.EpisodeShelfItem.Standalone ->
                                item(key = shelfItem.item.id) { EpisodeRowFor(shelfItem.item) }

                            is PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup -> {
                                item(key = "series_${shelfItem.slug}") { SeriesRowFor(shelfItem) }
                                if (shelfItem.slug in expandedSeries) {
                                    items(shelfItem.members, key = { it.id }) { ep -> EpisodeRowFor(ep) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // LEVER (SHW-61) L2: sjednocené akční menu epizody (Přehrát / Do fronty) jako u ABS/YouTube.
    actionEpisode?.let { ep ->
        ListenEpisodeActionSheet(
            title = ep.title.ifBlank { fallbackTitle },
            actions = listOfNotNull(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playAudio(ep, fallbackTitle); onOpenAudioPlayer()
                },
                ListenEpisodeAction(Icons.Default.Speaker, "Na repro (receiver)") {
                    viewModel.castAudioToSpeaker(ep, fallbackTitle)
                },
                // EXODUS E2: video epizoda (v JF knihovně) → přehrát video / poslat na TV.
                // SLOVO-KIDS-EPISODE: dětský profil = vždy jen audio, video volby se ani nenabídnou.
                if (!audioOnly) {
                    ep.jfItemId?.let {
                        ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video") {
                            onPlayVideo(it, ep.title.ifBlank { fallbackTitle }, viewModel.episodeKey(ep))
                        }
                    }
                } else null,
                if (!audioOnly) {
                    ep.jfItemId?.let {
                        ListenEpisodeAction(Icons.Default.Tv, "Přehrát na TV (video)") {
                            viewModel.castVideoToTv(ep)
                        }
                    }
                } else null,
                // AGORA (F5): u epizod BEZ vlastního JF videa dohledej video verze na YouTube
                // → otevři picker s kandidáty, uživatel si sám vybere (+ Přehrát / Na TV per kandidát).
                if (ep.jfItemId == null && !audioOnly) {
                    ListenEpisodeAction(Icons.Default.OndemandVideo, "Video verze (YouTube)") {
                        videoForEpisode = ep
                        viewModel.requestVideoVersions(ep, fallbackTitle)
                    }
                } else null,
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Přidat do fronty (další)") {
                    viewModel.enqueue(ep, fallbackTitle, atFront = true)
                },
                if (isAdmin) {
                    ListenEpisodeAction(Icons.AutoMirrored.Filled.MenuBook, "Udělat knihu z dílů…") {
                        ruleDraft.value = SeriesRuleDraft.fromEpisode(ep.title)
                    }
                } else null,
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)") {
                    viewModel.enqueue(ep, fallbackTitle, atFront = false)
                },
                offlineDownloadAction(
                    status = offlineStates[viewModel.episodeKey(ep)]?.status ?: OfflineStatus.NONE,
                    progress = offlineStates[viewModel.episodeKey(ep)]?.progress ?: 0f,
                    onDownload = { viewModel.download(ep, fallbackTitle) },
                    onDelete = { viewModel.deleteOffline(ep) },
                ),
                // User (2026-08-15 16:49) — „Reset poslechu" jen u rozposlouchané epizody.
                if (resumeMarks[viewModel.episodeKey(ep)] != null) {
                    ListenEpisodeAction(
                        Icons.Default.Close, "Ukončit poslech",
                        confirmMessage = "Smaže se uložená pozice poslechu a epizoda zmizí z Domů z „Pokračovat“.",
                    ) { viewModel.resetPosition(ep) }
                } else null,
                // User (2026-08-16 12:51, „chci volbu, která označí jako poslechnuto") — parita s audioknihami.
                if (resumeMarks[viewModel.episodeKey(ep)]?.isFinished != true) {
                    ListenEpisodeAction(
                        Icons.Default.CheckCircle, "Označit jako poslechnuté",
                        confirmMessage = "Epizoda se označí jako poslechnutá a zmizí z Domů z „Pokračovat“.",
                    ) { viewModel.markFinished(ep) }
                } else null,
            ),
            onDismiss = { actionEpisode = null },
        )
    }

    // SLOVO-KIDS-EPISODE — admin dlouhý stisk na kartě AUTO-detekované série → Zobrazit/Skrýt dětem.
    seriesForAction?.let { group ->
        val seriesKey = PodcastEpisodeSeriesGrouping.buildSeriesKey(sourceKey, group.title)
        val visibleToKids = seriesKey in kidsVisibleSeriesKeys
        ListenEpisodeActionSheet(
            title = group.title,
            actions = listOf(
                ListenEpisodeAction(
                    if (visibleToKids) Icons.Default.ChildCare else Icons.Default.Visibility,
                    if (visibleToKids) "Skrýt dětem" else "Zobrazit dětem",
                ) { onSetSeriesVisibleForKids(seriesKey, !visibleToKids) },
            ),
            onDismiss = { seriesForAction = null },
        )
    }

    // AGORA (F5) ruční výběr: picker video verzí epizody — uživatel si sám vybere kandidáta + akci.
    videoForEpisode?.let { ep ->
        VideoVersionPicker(
            episodeTitle = ep.title.ifBlank { fallbackTitle },
            loading = videoLoadingFor == viewModel.episodeKey(ep),
            candidates = videoCandidates,
            recommendedId = recommendedVideoId,
            onPlay = { video -> viewModel.playVideoVersion(video, ep, fallbackTitle, onPlayYoutubeVideo); videoForEpisode = null },
            onCast = { video -> viewModel.castVideoVersion(video, ep); videoForEpisode = null },
            onDismiss = { viewModel.clearVideoCandidates(); videoForEpisode = null },
        )
    }
}

/** ms → "m:ss" / "h:mm:ss". */
private fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// formatRssDate / formatRssDuration přesunuty do sdíleného PodcastEpisodeRow.kt (RESONANCE SHW-81).
