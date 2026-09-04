package com.github.jankoran90.showlyfin.feature.listen.ui

import android.widget.Toast
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.github.jankoran90.showlyfin.core.ui.ShareLinks
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.YtEpisode
import com.github.jankoran90.showlyfin.feature.listen.YoutubeChannelViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.choosePlaybackResume

/**
 * TUNER (SHW-62): obrazovka YouTube kanálu jako podcast. Seznam epizod, u každé VIDEO / AUDIO.
 * Streaming přes backend, nic se nestahuje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeChannelScreen(
    channel: String,
    channelTitle: String,
    onBack: () -> Unit,
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    onOpenAudioPlayer: () -> Unit,
    // NAVIGATE (SHW-73): klíč epizody (`yt:…`) k zvýraznění + scrollu (z Timeline řádku / cover prokliku).
    highlightEpisodeKey: String? = null,
    // SLOVO-KIDS-EPISODE (2026-08-15) — dětský profil: vždy jen audio, video volby úplně skryté.
    audioOnly: Boolean = false,
    // SLOVO-KIDS-EPISODE — non-null = otevřeno JEN na jednu AUTO-detekovanou sérii (dětská cesta).
    seriesFilter: String? = null,
    // WATCHDOG — admin (Dospělý) dlouhým stiskem na kartě série „Zobrazit/Skrýt dětem".
    isAdmin: Boolean = false,
    kidsVisibleSeriesKeys: Set<String> = emptySet(),
    onSetSeriesVisibleForKids: (key: String, visible: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: YoutubeChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    // BUG (2026-09-04): video watch pozice — parita s RssPodcastScreen (jiný store, žádné isFinished).
    val videoResumeMarks by viewModel.videoResumeMarks.collectAsStateWithLifecycle()
    val castMessage by viewModel.castMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var actionEpisode by remember { mutableStateOf<YtEpisode?>(null) }
    // FOCUS (2026-09-03, user „hlavně bych měl hledat na jedním zdrojem, ne plošně") — nahrazuje
    // starý pevný textový filtr (ChannelFilterField): lupa v TopAppBar otevře fulltext hledání SCOPED
    // jen na tenhle kanál (celá historie živě přes YouTube-side search, ne jen načtené epizody).
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(channel) { viewModel.load(channel) }

    // SLOVO-KIDS-EPISODE / WATCHDOG — série stejným vzorem jako RssPodcastScreen.
    val sourceKey = "youtube:$channel"
    val filteredEpisodes = remember(state.episodes, seriesFilter) {
        if (seriesFilter == null) state.episodes
        else state.episodes.filter {
            PodcastEpisodeSeriesGrouping.detectSeriesTitle(it.title)
                ?.let { t -> PodcastEpisodeSeriesGrouping.seriesSlug(t) } == seriesFilter
        }
    }
    val shelfItems = remember(filteredEpisodes, seriesFilter) {
        if (seriesFilter != null) null else PodcastEpisodeSeriesGrouping.group(filteredEpisodes, titleOf = { it.title })
    }
    // BUG (2026-09-04, user screenshot): rozposlouchané/rozkoukané epizody nahoru. Klíč jen na
    // MNOŽINU rozečtených id (ne na `resumeMarks` samotné) — nepřeuspořádá se s každým tikem pozice
    // právě hrané epizody, jen když se do/ze skupiny rozečtených něco přidá/ubere.
    val inProgressIds = resumeMarks.filterValues { !it.isFinished }.keys + videoResumeMarks.keys
    val orderedEpisodes = remember(filteredEpisodes, inProgressIds) {
        PodcastEpisodeSeriesGrouping.pinInProgressFlat(filteredEpisodes) { viewModel.episodeKey(it) in inProgressIds }
    }
    val orderedShelfItems = remember(shelfItems, inProgressIds) {
        shelfItems?.let { PodcastEpisodeSeriesGrouping.pinInProgress(it) { ep -> viewModel.episodeKey(ep) in inProgressIds } }
    }
    var expandedSeries by remember { mutableStateOf(setOf<String>()) }
    var seriesForAction by remember {
        mutableStateOf<PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<YtEpisode>?>(null)
    }
    var seriesOnly by remember { mutableStateOf(false) }
    val seriesOnlyGroups = remember(shelfItems) {
        shelfItems?.filterIsInstance<PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<YtEpisode>>()
            ?.sortedByDescending { g -> PodcastEpisodeSeriesGrouping.latestDateMs(g.members) { it.uploadDate } ?: 0L }
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

    // L4 (LEVER): výsledek castu na TV → jednorázový Toast.
    LaunchedEffect(castMessage) {
        castMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeCastMessage()
        }
    }

    if (showSearch) {
        val chTitle = state.channelTitle ?: channelTitle
        PodcastSearchScreen(
            onBack = { showSearch = false },
            scopeSource = PodcastSource(id = sourceKey, type = "youtube", ref = channel, title = chTitle),
            scopeLabel = chTitle,
            audioOnly = audioOnly,
            onPlayVideo = onPlayVideo,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.channelTitle ?: channelTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    val chTitle = state.channelTitle ?: channelTitle
                    // WATCHDOG — „Jen série" přepínač (jen admin, neserials-filtrovaný pohled).
                    if (seriesFilter == null && !seriesOnlyGroups.isNullOrEmpty()) {
                        IconButton(onClick = { seriesOnly = !seriesOnly }) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = if (seriesOnly) "Zobrazit vše" else "Jen série",
                                tint = if (seriesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // FOCUS (2026-09-03) — hledání scoped na tenhle kanál, nahrazuje starý textový filtr.
                    if (seriesFilter == null) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Hledat v tomto kanálu")
                        }
                    }
                    IconButton(onClick = {
                        ShareLinks.share(context, chTitle, ShareLinks.youtube(channel, chTitle))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Sdílet kanál")
                    }
                },
            )
        },
    ) { padding ->
        @Composable
        fun EpisodeRowFor(ep: YtEpisode) {
            val key = viewModel.episodeKey(ep)
            val isCurrent = playerState.currentEpisodeId == key && playerState.isActive
            // NAVIGATE: epizoda, ze které se uživatel proklikl (Timeline/cover) — zvýrazni i když nehraje.
            val isHighlighted = highlightEpisodeKey != null && key == highlightEpisodeKey
            // ADAPT (2026-09-04): vyhrává POKROČILEJŠÍ pozice, ne poslední zápis — parita s RssPodcastScreen.
            // Video mark nemá isFinished (store ho při dohrání sám smaže).
            val videoMark = videoResumeMarks[key]
            val mark = resumeMarks[key]
            val choice = choosePlaybackResume(mark?.posMs, mark?.durMs, mark?.isFinished == true, videoMark?.posMs, videoMark?.durMs)
            val markPos = choice?.posMs
            val markDur = choice?.durMs
            val isFinished = choice?.isFinished == true
            val progress: Float? = when {
                isCurrent && playerState.durationMs > 0 ->
                    (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                markPos != null && markDur != null && markDur > 0 -> (markPos.toFloat() / markDur).coerceIn(0f, 1f)
                else -> null
            }
            val canResume = !isCurrent && markPos != null && !isFinished
            val remainingLabel = if (canResume && markDur != null && markDur > 0)
                "zbývá ${formatDuration((markDur - markPos!!).coerceAtLeast(0L) / 1000.0)}" else null
            EpisodeRow(
                title = ep.title,
                thumbnail = ep.thumbnail,
                durationSec = ep.duration,
                uploadDate = ep.uploadDate,
                description = ep.description,
                downloaded = offlineStates[key]?.status == OfflineStatus.DOWNLOADED,
                isCurrent = isCurrent,
                isPlaying = isCurrent && playerState.isPlaying,
                progress = progress,
                canResume = canResume,
                remainingLabel = remainingLabel,
                highlighted = isHighlighted,
                showVideo = !audioOnly,
                isFinished = isFinished,
                onVideo = { onPlayVideo(viewModel.videoUrl(ep), ep.title, ep.thumbnail) },
                onAudio = {
                    // L2b: ťuk vždy ROVNOU spustí přehrávání (current=resume bez reloadu, jinak nová epizoda).
                    if (isCurrent) viewModel.resumeCurrent() else viewModel.playAudio(ep)
                    onOpenAudioPlayer()
                },
                onMore = { actionEpisode = ep },
                onEndListening = { viewModel.resetPosition(ep) },
            )
        }

        @Composable
        fun SeriesRowFor(group: PodcastEpisodeSeriesGrouping.EpisodeShelfItem.SeriesGroup<YtEpisode>) {
            val latestMs = PodcastEpisodeSeriesGrouping.latestDateMs(group.members) { it.uploadDate }
            PodcastSeriesRow(
                title = group.title,
                memberCount = group.members.size,
                latestDateLabel = latestMs?.let { PodcastEpisodeSeriesGrouping.formatSeriesDate(it) },
                thumbnail = group.members.firstOrNull()?.thumbnail,
                expanded = group.slug in expandedSeries,
                onClick = {
                    expandedSeries = if (group.slug in expandedSeries) expandedSeries - group.slug else expandedSeries + group.slug
                },
                onLongClick = if (isAdmin) ({ seriesForAction = group }) else null,
            )
        }

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
                    items(orderedEpisodes, key = { it.id }) { ep -> EpisodeRowFor(ep) }
                } else if (seriesOnly) {
                    seriesOnlyGroups.orEmpty().forEach { group ->
                        item(key = "series_${group.slug}") { SeriesRowFor(group) }
                        if (group.slug in expandedSeries) {
                            items(group.members, key = { it.id }) { ep -> EpisodeRowFor(ep) }
                        }
                    }
                } else {
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

    // WATCHDOG — admin dlouhý stisk na kartě AUTO-detekované série → Zobrazit/Skrýt dětem.
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

    // LEVER (SHW-61) L2: sjednocené akční menu epizody (Do fronty / Sdílet) jako u ABS/RSS.
    actionEpisode?.let { ep ->
        ListenEpisodeActionSheet(
            title = ep.title,
            // WEFT (SHW-75/W1): jednotné menu jako NaVýbornou/RSS — Přehrát · Video · Na TV · fronta×2 · …
            // SLOVO-KIDS-EPISODE: dětský profil (audioOnly) = video volby se ani nenabídnou.
            actions = listOfNotNull(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playAudio(ep); onOpenAudioPlayer()
                },
                if (!audioOnly) {
                    ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video") {
                        onPlayVideo(viewModel.videoUrl(ep), ep.title, ep.thumbnail)
                    }
                } else null,
                // L4 (LEVER): VIDEO verze epizody na TV/box (jako film). U YouTube vždy dostupné (video).
                if (!audioOnly) {
                    ListenEpisodeAction(Icons.Default.Tv, "Přehrát na TV (video)") {
                        viewModel.castVideoToTv(ep)
                    }
                } else null,
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Přidat do fronty (další)") {
                    viewModel.enqueue(ep, atFront = true)
                },
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)") {
                    viewModel.enqueue(ep, atFront = false)
                },
                ListenEpisodeAction(Icons.Default.Share, "Sdílet epizodu") {
                    val chTitle = state.channelTitle ?: channelTitle
                    ShareLinks.share(context, ep.title, ShareLinks.youtube(channel, chTitle, v = ep.id))
                },
                offlineDownloadAction(
                    status = offlineStates[viewModel.episodeKey(ep)]?.status ?: OfflineStatus.NONE,
                    progress = offlineStates[viewModel.episodeKey(ep)]?.progress ?: 0f,
                    onDownload = { viewModel.download(ep) },
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
}

@Composable
private fun EpisodeRow(
    title: String,
    thumbnail: String?,
    durationSec: Double?,
    uploadDate: String?,
    description: String?,
    downloaded: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    progress: Float?,
    canResume: Boolean,
    remainingLabel: String?,
    highlighted: Boolean,
    showVideo: Boolean = true,
    isFinished: Boolean = false,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onMore: () -> Unit,
    onEndListening: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    // NAVIGATE: zvýrazni hranou epizodu (isCurrent) i tu, ze které se uživatel proklikl (highlighted).
    val emphasized = isCurrent || highlighted
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (emphasized) accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(if (emphasized) 6.dp else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (emphasized) accent else MaterialTheme.colorScheme.onBackground,
                )
                val meta = listOfNotNull(formatDate(uploadDate), durationSec?.let { formatDuration(it) }, remainingLabel)
                    .joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (downloaded) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "Staženo do telefonu",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                    }
                    if (isFinished) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Poslechnuto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (progress != null && !isFinished) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 6.dp),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable { expanded = !expanded },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showVideo) {
                FilledTonalButton(onClick = onVideo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Video", Modifier.padding(start = 6.dp))
                }
            }
            val (audioIcon, audioLabel) = when {
                isCurrent && isPlaying -> Icons.Default.GraphicEq to "Hraje"
                isCurrent -> Icons.Default.PlayArrow to "Pokračovat"   // načtená, pozastavená → resume
                canResume && !isFinished -> Icons.Default.PlayArrow to "Pokračovat"
                isFinished -> Icons.Default.Headphones to "Přehrát znovu"
                else -> Icons.Default.Headphones to "Poslech"
            }
            OutlinedButton(onClick = onAudio, modifier = Modifier.weight(1f)) {
                Icon(audioIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(audioLabel, Modifier.padding(start = 6.dp))
            }
            if (canResume && !isFinished && onEndListening != null) {
                EndListeningButton(onConfirm = onEndListening)
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = "Další akce", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatDuration(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "YYYY-MM-DD" → "D. M. YYYY" (null/neúplné → null). */
private fun formatDate(d: String?): String? {
    if (d.isNullOrBlank()) return null
    val p = d.take(10).split("-")
    if (p.size != 3) return null
    val day = p[2].toIntOrNull() ?: return null
    val mon = p[1].toIntOrNull() ?: return null
    return "$day. $mon. ${p[0]}"
}
