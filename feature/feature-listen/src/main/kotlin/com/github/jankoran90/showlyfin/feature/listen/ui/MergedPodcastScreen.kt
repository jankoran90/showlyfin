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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
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
import com.github.jankoran90.showlyfin.feature.listen.MergedPodcastViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastPairing

/**
 * TWINE (SHW-74 / plán F7): sloučená obrazovka propojeného pořadu (audio RSS + video YouTube pod 1 kartou).
 * Spárované epizody = jeden řádek s „Přehrát" (audio) i „Video"; nespárované se svou dostupnou verzí.
 * Datum se bere z audio/RSS (vydáno dřív = správnější). Přehrávání jde sdíleným poslechovým přehrávačem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergedPodcastScreen(
    groupId: String,
    title: String,
    onBack: () -> Unit,
    onOpenAudioPlayer: () -> Unit,
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    onUnlinked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MergedPodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    var actionItem by remember { mutableStateOf<PodcastPairing.MergedEpisode?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) { viewModel.load(groupId) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { title }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Další akce")
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Zrušit propojení") },
                            leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null) },
                            onClick = { menuOpen = false; viewModel.unlink(); onUnlinked() },
                        )
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
                items(state.episodes, key = { it.key }) { item ->
                    val key = item.key
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
                        "zbývá ${formatMergedClock((mark.durMs - mark.posMs).coerceAtLeast(0L))}" else null
                    MergedEpisodeRow(
                        item = item,
                        downloaded = offlineStates[key]?.status == OfflineStatus.DOWNLOADED,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playerState.isPlaying,
                        progress = progress,
                        canResume = canResume,
                        remainingLabel = remainingLabel,
                        onPlayAudio = {
                            if (isCurrent) viewModel.resumeCurrent() else viewModel.playAudio(item)
                            onOpenAudioPlayer()
                        },
                        onPlayVideo = {
                            viewModel.videoUrl(item)?.let { url -> onPlayVideo(url, item.title, item.imageUrl) }
                        },
                        onMore = { actionItem = item },
                    )
                }
            }
        }
    }

    actionItem?.let { item ->
        ListenEpisodeActionSheet(
            title = item.title.ifBlank { state.title },
            actions = listOfNotNull(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát (zvuk)") {
                    viewModel.playAudio(item); onOpenAudioPlayer()
                },
                item.video?.let {
                    ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video") {
                        viewModel.videoUrl(item)?.let { url -> onPlayVideo(url, item.title, item.imageUrl) }
                    }
                },
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)") {
                    viewModel.enqueue(item)
                },
                offlineDownloadAction(
                    status = offlineStates[item.key]?.status ?: OfflineStatus.NONE,
                    progress = offlineStates[item.key]?.progress ?: 0f,
                    onDownload = { viewModel.download(item) },
                    onDelete = { viewModel.deleteOffline(item) },
                ),
            ),
            onDismiss = { actionItem = null },
        )
    }
}

@Composable
private fun MergedEpisodeRow(
    item: PodcastPairing.MergedEpisode,
    downloaded: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    progress: Float?,
    canResume: Boolean,
    remainingLabel: String?,
    onPlayAudio: () -> Unit,
    onPlayVideo: () -> Unit,
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
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(72.dp).height(72.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
                )
                val meta = listOfNotNull(
                    formatMergedDate(item.date),
                    if (item.durationSec > 0) formatMergedDuration(item.durationSec) else null,
                    remainingLabel,
                    if (item.video != null) "video" else null,
                ).joinToString(" · ")
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
        if (!item.description.isNullOrBlank()) {
            Text(
                cleanMergedDescription(item.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { expanded = !expanded },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (playIcon, playLabel) = when {
                isCurrent && isPlaying -> Icons.Default.GraphicEq to "Hraje"
                isCurrent -> Icons.Default.PlayArrow to "Pokračovat"
                canResume -> Icons.Default.PlayArrow to "Pokračovat"
                else -> Icons.Default.Headphones to "Přehrát"
            }
            FilledTonalButton(onClick = onPlayAudio, modifier = Modifier.weight(1f)) {
                Icon(playIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(playLabel, Modifier.padding(start = 6.dp))
            }
            if (item.video != null) {
                OutlinedButton(onClick = onPlayVideo, modifier = Modifier.weight(1f)) {
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
private fun formatMergedClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** sekundy → "m:ss" / "h:mm:ss". */
private fun formatMergedDuration(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Datum epizody čitelně přes sjednocený parser (RFC822 / ISO / YYYYMMDD) → "d. M. yyyy". */
private fun formatMergedDate(date: String?): String? {
    val ms = com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel.parseEpisodeDate(date) ?: return null
    return java.text.SimpleDateFormat("d. M. yyyy", java.util.Locale("cs")).format(java.util.Date(ms))
}

/** Očistí popis od HTML tagů a nadbytečných bílých znaků. */
private fun cleanMergedDescription(raw: String): String =
    raw.replace(Regex("<[^>]*>"), " ")
        .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()
