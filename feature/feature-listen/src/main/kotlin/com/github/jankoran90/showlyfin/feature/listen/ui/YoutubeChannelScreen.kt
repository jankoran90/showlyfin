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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
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
import com.github.jankoran90.showlyfin.data.uploader.model.YtEpisode
import com.github.jankoran90.showlyfin.feature.listen.YoutubeChannelViewModel

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
    modifier: Modifier = Modifier,
    viewModel: YoutubeChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    val castMessage by viewModel.castMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var actionEpisode by remember { mutableStateOf<YtEpisode?>(null) }

    LaunchedEffect(channel) { viewModel.load(channel) }

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

    // L4 (LEVER): výsledek castu na TV → jednorázový Toast.
    LaunchedEffect(castMessage) {
        castMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeCastMessage()
        }
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
                    IconButton(onClick = {
                        ShareLinks.share(context, chTitle, ShareLinks.youtube(channel, chTitle))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Sdílet kanál")
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
                    val mark = resumeMarks[key]
                    val progress: Float? = when {
                        isCurrent && playerState.durationMs > 0 ->
                            (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                        mark != null && mark.durMs > 0 -> (mark.posMs.toFloat() / mark.durMs).coerceIn(0f, 1f)
                        else -> null
                    }
                    val canResume = !isCurrent && mark != null
                    val remainingLabel = if (canResume && mark.durMs > 0)
                        "zbývá ${formatDuration((mark.durMs - mark.posMs).coerceAtLeast(0L) / 1000.0)}" else null
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
                        onVideo = { onPlayVideo(viewModel.videoUrl(ep), ep.title, ep.thumbnail) },
                        onAudio = {
                            // L2b: ťuk vždy ROVNOU spustí přehrávání (current=resume bez reloadu, jinak nová epizoda).
                            if (isCurrent) viewModel.resumeCurrent() else viewModel.playAudio(ep)
                            onOpenAudioPlayer()
                        },
                        onMore = { actionEpisode = ep },
                    )
                }
            }
        }
    }

    // LEVER (SHW-61) L2: sjednocené akční menu epizody (Do fronty / Sdílet) jako u ABS/RSS.
    actionEpisode?.let { ep ->
        ListenEpisodeActionSheet(
            title = ep.title,
            // WEFT (SHW-75/W1): jednotné menu jako NaVýbornou/RSS — Přehrát · Video · Na TV · fronta×2 · …
            actions = listOf(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playAudio(ep); onOpenAudioPlayer()
                },
                ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video") {
                    onPlayVideo(viewModel.videoUrl(ep), ep.title, ep.thumbnail)
                },
                // L4 (LEVER): VIDEO verze epizody na TV/box (jako film). U YouTube vždy dostupné (video).
                ListenEpisodeAction(Icons.Default.Tv, "Přehrát na TV (video)") {
                    viewModel.castVideoToTv(ep)
                },
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
    onVideo: () -> Unit,
    onAudio: () -> Unit,
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
        ) {
            FilledTonalButton(onClick = onVideo, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Video", Modifier.padding(start = 6.dp))
            }
            val (audioIcon, audioLabel) = when {
                isCurrent && isPlaying -> Icons.Default.GraphicEq to "Hraje"
                isCurrent -> Icons.Default.PlayArrow to "Pokračovat"   // načtená, pozastavená → resume
                canResume -> Icons.Default.PlayArrow to "Pokračovat"
                else -> Icons.Default.Headphones to "Poslech"
            }
            OutlinedButton(onClick = onAudio, modifier = Modifier.weight(1f)) {
                Icon(audioIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(audioLabel, Modifier.padding(start = 6.dp))
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
