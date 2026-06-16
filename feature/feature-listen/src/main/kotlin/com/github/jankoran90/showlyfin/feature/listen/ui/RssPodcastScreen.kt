package com.github.jankoran90.showlyfin.feature.listen.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
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
    modifier: Modifier = Modifier,
    viewModel: RssPodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    LaunchedEffect(feedUrl) { viewModel.load(feedUrl) }
    var actionEpisode by remember { mutableStateOf<RssEpisode?>(null) }
    val fallbackTitle = state.title ?: title

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
                    val mark = resumeMarks[key]
                    val progress: Float? = when {
                        isCurrent && playerState.durationMs > 0 ->
                            (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                        mark != null && mark.durMs > 0 -> (mark.posMs.toFloat() / mark.durMs).coerceIn(0f, 1f)
                        else -> null
                    }
                    val canResume = !isCurrent && mark != null
                    val remainingLabel = if (canResume && mark.durMs > 0)
                        "zbývá ${formatClock((mark.durMs - mark.posMs).coerceAtLeast(0L))}" else null
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
                        onPlay = {
                            if (isCurrent) onOpenAudioPlayer()
                            else { viewModel.playAudio(ep, fallbackTitle); onOpenAudioPlayer() }
                        },
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
            actions = listOf(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playAudio(ep, fallbackTitle); onOpenAudioPlayer()
                },
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
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(if (isCurrent) 6.dp else 0.dp),
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
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
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
                isCurrent -> Icons.Default.Pause to "Pozastaveno"
                canResume -> Icons.Default.PlayArrow to "Pokračovat"
                else -> Icons.Default.Headphones to "Poslech"
            }
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(playIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(playLabel, Modifier.padding(start = 6.dp))
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
