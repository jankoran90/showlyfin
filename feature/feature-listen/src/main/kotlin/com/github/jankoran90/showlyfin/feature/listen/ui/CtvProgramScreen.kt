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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
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
import com.github.jankoran90.showlyfin.data.uploader.model.CtvEpisode
import com.github.jankoran90.showlyfin.feature.listen.CtvProgramViewModel

/**
 * KAVKA (SHW-76): obrazovka ČT pořadu jako podcast. Seznam dílů, u každého VIDEO (DASH) nebo AUDIO
 * (poslech, audio-only DASH). Streaming přes backend (api/ctv), nic se nestahuje. Action menu =
 * Přehrát · Přehrát video · Na TV (video) · fronta×2 (sjednocené jako YouTube/RSS). ČT díl nese zvuk
 * i obraz, takže žádné párování (TWINE). Offline a sdílení = navazující (Known gap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtvProgramScreen(
    ctvId: String,
    title: String,
    onBack: () -> Unit,
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    onOpenAudioPlayer: () -> Unit,
    highlightEpisodeKey: String? = null,
    modifier: Modifier = Modifier,
    viewModel: CtvProgramViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    val castMessage by viewModel.castMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var actionEpisode by remember { mutableStateOf<CtvEpisode?>(null) }

    LaunchedEffect(ctvId) { viewModel.load(ctvId) }

    // Po načtení dílů jednorázově odscrolluj na zvýrazněný díl (z Timeline / cover prokliku).
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
                title = { Text(state.showTitle ?: title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        thumbnail = ep.image,
                        durationSec = ep.duration,
                        date = ep.date,
                        description = ep.description,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playerState.isPlaying,
                        progress = progress,
                        canResume = canResume,
                        remainingLabel = remainingLabel,
                        highlighted = isHighlighted,
                        onVideo = { onPlayVideo(viewModel.videoUrl(ep), ep.title, ep.image) },
                        onAudio = {
                            if (isCurrent) viewModel.resumeCurrent() else viewModel.playAudio(ep)
                            onOpenAudioPlayer()
                        },
                        onMore = { actionEpisode = ep },
                    )
                }
            }
        }
    }

    actionEpisode?.let { ep ->
        ListenEpisodeActionSheet(
            title = ep.title,
            actions = listOf(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playAudio(ep); onOpenAudioPlayer()
                },
                ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video") {
                    onPlayVideo(viewModel.videoUrl(ep), ep.title, ep.image)
                },
                ListenEpisodeAction(Icons.Default.Tv, "Přehrát na TV (video)") {
                    viewModel.castVideoToTv(ep)
                },
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Přidat do fronty (další)") {
                    viewModel.enqueue(ep, atFront = true)
                },
                ListenEpisodeAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)") {
                    viewModel.enqueue(ep, atFront = false)
                },
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
    date: String?,
    description: String?,
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
                val meta = listOfNotNull(formatDate(date), durationSec?.let { formatDuration(it) }, remainingLabel)
                    .joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
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
                isCurrent -> Icons.Default.PlayArrow to "Pokračovat"
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

/** ISO "YYYY-MM-DD..." → "D. M. YYYY" (null/neúplné → null). */
private fun formatDate(d: String?): String? {
    if (d.isNullOrBlank()) return null
    val p = d.take(10).split("-")
    if (p.size != 3) return null
    val day = p[2].toIntOrNull() ?: return null
    val mon = p[1].toIntOrNull() ?: return null
    return "$day. $mon. ${p[0]}"
}
