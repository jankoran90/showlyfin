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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
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
import com.github.jankoran90.showlyfin.data.uploader.model.RssEpisode
import com.github.jankoran90.showlyfin.feature.listen.RssPodcastViewModel

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
    modifier: Modifier = Modifier,
    viewModel: RssPodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    // NAVIGATE (SHW-73): jakmile se epizody načtou, jednorázově odscrolluj na zvýrazněnou epizodu.
    val listState = rememberLazyListState()
    var scrolledToHighlight by remember { mutableStateOf(false) }
    LaunchedEffect(highlightEpisodeKey, state.episodes) {
        if (!scrolledToHighlight && highlightEpisodeKey != null && state.episodes.isNotEmpty()) {
            val idx = state.episodes.indexOfFirst { viewModel.episodeKey(it) == highlightEpisodeKey }
            if (idx >= 0) {
                listState.scrollToItem(idx)
                scrolledToHighlight = true
            }
        }
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
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.episodes.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.error != null && state.episodes.isEmpty() ->
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
                items(state.episodes, key = { it.id }) { ep ->
                    val key = viewModel.episodeKey(ep)
                    val isCurrent = playerState.currentEpisodeId == key && playerState.isActive
                    // NAVIGATE: epizoda, ze které se uživatel proklikl (Timeline/cover) — zvýrazni i když nehraje.
                    val isHighlighted = highlightEpisodeKey != null && key == highlightEpisodeKey
                    // REWIND (SHW-68): video resume má přednost (sdílený klíč = „poslední vyhrává"),
                    // jinak audio resume → progres + „Pokračovat" funguje i u VIDEO epizody.
                    val markPos = videoResumeMarks[key]?.posMs ?: resumeMarks[key]?.posMs
                    val markDur = videoResumeMarks[key]?.durMs ?: resumeMarks[key]?.durMs
                    val progress: Float? = when {
                        isCurrent && playerState.durationMs > 0 ->
                            (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                        markPos != null && markDur != null && markDur > 0 ->
                            (markPos.toFloat() / markDur).coerceIn(0f, 1f)
                        else -> null
                    }
                    val canResume = !isCurrent && markPos != null
                    val remainingLabel = if (!isCurrent && markPos != null && markDur != null && markDur > 0)
                        "zbývá ${formatClock((markDur - markPos).coerceAtLeast(0L))}" else null
                    RssEpisodeRow(
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
                        hasVideo = ep.jfItemId != null,
                        highlighted = isHighlighted,
                        onPlay = {
                            // L2b: ťuk vždy ROVNOU spustí přehrávání (current=resume bez reloadu, jinak nová epizoda).
                            if (isCurrent) viewModel.resumeCurrent() else viewModel.playAudio(ep, fallbackTitle)
                            onOpenAudioPlayer()
                        },
                        onVideo = { ep.jfItemId?.let { onPlayVideo(it, ep.title.ifBlank { fallbackTitle }, key) } },
                        onMore = { actionEpisode = ep },
                    )
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
                // EXODUS E2: video epizoda (v JF knihovně) → přehrát video / poslat na TV.
                ep.jfItemId?.let {
                    ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video") {
                        onPlayVideo(it, ep.title.ifBlank { fallbackTitle }, viewModel.episodeKey(ep))
                    }
                },
                ep.jfItemId?.let {
                    ListenEpisodeAction(Icons.Default.Tv, "Přehrát na TV (video)") {
                        viewModel.castVideoToTv(ep)
                    }
                },
                // AGORA (F5): u epizod BEZ vlastního JF videa dohledej video verze na YouTube
                // → otevři picker s kandidáty, uživatel si sám vybere (+ Přehrát / Na TV per kandidát).
                if (ep.jfItemId == null) {
                    ListenEpisodeAction(Icons.Default.OndemandVideo, "Video verze (YouTube)") {
                        videoForEpisode = ep
                        viewModel.requestVideoVersions(ep, fallbackTitle)
                    }
                } else null,
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Přidat do fronty (další)") {
                    viewModel.enqueue(ep, fallbackTitle, atFront = true)
                },
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)") {
                    viewModel.enqueue(ep, fallbackTitle, atFront = false)
                },
                offlineDownloadAction(
                    status = offlineStates[viewModel.episodeKey(ep)]?.status ?: OfflineStatus.NONE,
                    progress = offlineStates[viewModel.episodeKey(ep)]?.progress ?: 0f,
                    onDownload = { viewModel.download(ep, fallbackTitle) },
                    onDelete = { viewModel.deleteOffline(ep) },
                ),
            ),
            onDismiss = { actionEpisode = null },
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

/**
 * AGORA (F5): ModalBottomSheet s kandidáty VIDEO verze epizody (YouTube). U každého název / kanál /
 * délka + akce Přehrát a Na TV. Doporučený (heuristika [pickBestVideo]) je nahoře s odznakem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoVersionPicker(
    episodeTitle: String,
    loading: Boolean,
    candidates: List<EpisodeVideo>?,
    recommendedId: String?,
    onPlay: (EpisodeVideo) -> Unit,
    onCast: (EpisodeVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            "Video verze (YouTube)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            episodeTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        when {
            loading || candidates == null ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            candidates.isEmpty() ->
                Text(
                    "Video verze nenalezena.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )

            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(candidates, key = { it.id }) { video ->
                    VideoVersionRow(
                        video = video,
                        recommended = video.id == recommendedId,
                        onPlay = { onPlay(video) },
                        onCast = { onCast(video) },
                    )
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun VideoVersionRow(
    video: EpisodeVideo,
    recommended: Boolean,
    onPlay: () -> Unit,
    onCast: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (recommended) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Doporučeno") },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.primary,
                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Box(Modifier.height(4.dp))
        }
        Text(
            video.title.ifBlank { "Video" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            video.uploader.takeIf { it.isNotBlank() },
            formatVideoDuration(video.duration),
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Přehrát", Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onCast, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Na TV", Modifier.padding(start = 6.dp))
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Sekundy → "mm:ss" / "h:mm:ss"; 0 = bez délky. */
private fun formatVideoDuration(sec: Int): String? {
    if (sec <= 0) return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun RssEpisodeRow(
    title: String,
    image: String?,
    date: String?,
    duration: String?,
    description: String?,
    downloaded: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    progress: Float?,
    canResume: Boolean,
    remainingLabel: String?,
    hasVideo: Boolean,
    highlighted: Boolean,
    onPlay: () -> Unit,
    onVideo: () -> Unit,
    onMore: () -> Unit,
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
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(72.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (emphasized) accent else MaterialTheme.colorScheme.onBackground,
                )
                val meta = listOfNotNull(formatRssDate(date), formatRssDuration(duration), remainingLabel)
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
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (progress != null) {
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (playIcon, playLabel) = when {
                isCurrent && isPlaying -> Icons.Default.GraphicEq to "Hraje"
                isCurrent -> Icons.Default.PlayArrow to "Pokračovat"   // načtená, pozastavená → resume
                canResume -> Icons.Default.PlayArrow to "Pokračovat"
                else -> Icons.Default.Headphones to "Poslech"
            }
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(playIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(playLabel, Modifier.padding(start = 6.dp))
            }
            // EXODUS E2: druhé tlačítko Video u epizod, co mají video v JF knihovně (NaVýbornou).
            if (hasVideo) {
                OutlinedButton(onClick = onVideo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OndemandVideo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Video", Modifier.padding(start = 6.dp))
                }
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = "Další akce", modifier = Modifier.size(20.dp))
            }
        }
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

/** "YYYY-MM-DD" → "D. M. YYYY"; jinak vrať vstup zkrácený (RFC822 pubDate). */
private fun formatRssDate(d: String?): String? {
    if (d.isNullOrBlank()) return null
    val p = d.take(10).split("-")
    if (p.size == 3) {
        val day = p[2].toIntOrNull()
        val mon = p[1].toIntOrNull()
        val year = p[0].toIntOrNull()
        if (day != null && mon != null && year != null) return "$day. $mon. $year"
    }
    return d.take(16)
}

/** itunes:duration → čitelně. "1:02:03"/"02:03" nech, čisté sekundy zformátuj. */
private fun formatRssDuration(d: String?): String? {
    val t = d?.trim().orEmpty()
    if (t.isEmpty()) return null
    if (":" in t) return t
    val sec = t.toLongOrNull() ?: return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
