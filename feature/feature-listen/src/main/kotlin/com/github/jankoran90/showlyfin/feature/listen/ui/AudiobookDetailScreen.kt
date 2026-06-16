package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.ShareLinks
import com.github.jankoran90.showlyfin.data.abs.model.AudiobookDetail
import com.github.jankoran90.showlyfin.data.abs.model.Chapter
import com.github.jankoran90.showlyfin.data.abs.model.DownloadState
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.feature.listen.AudiobookDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlay: (itemId: String, fromStart: Boolean, startSec: Double?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiobookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val playingChapterIndex = playerState.currentChapterIndex
        ?.takeIf { playerState.isActive && playerState.currentItemId == itemId }
    LaunchedEffect(itemId) { viewModel.load(itemId) }

    ListenExpressiveTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(state.detail?.book?.title ?: "Audiokniha", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                        }
                    },
                    actions = {
                        val ctx = LocalContext.current
                        val bTitle = state.detail?.book?.title ?: "Audiokniha"
                        IconButton(onClick = {
                            ShareLinks.share(ctx, bTitle, ShareLinks.audiobook(itemId))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Sdílet")
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
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.detail != null -> DetailContent(
                    detail = state.detail!!,
                    playingChapterIndex = playingChapterIndex,
                    downloadState = downloadState,
                    onPlay = { fromStart, startSec -> onPlay(itemId, fromStart, startSec) },
                    onDownload = viewModel::downloadAudiobook,
                    onCancelDownload = viewModel::cancelDownload,
                    onDeleteDownload = viewModel::deleteDownload,
                    modifier = Modifier.fillMaxSize().padding(pad),
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: AudiobookDetail,
    playingChapterIndex: Int?,
    downloadState: DownloadState,
    onPlay: (fromStart: Boolean, startSec: Double?) -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = detail.book
    val resumeSec = book.currentTimeSec
    val canResume = resumeSec > 1.0 && !book.isFinished

    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            // Hlavička: cover vlevo, info vpravo VYPLNÍ přesnou výšku coveru (název je jen v app-baru).
            Row {
                Box(
                    Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (book.coverUrl != null) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .height(140.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        book.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        book.narrator?.let {
                            Text("Čte: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        val meta = buildList {
                            detail.publishedYear?.takeIf { it.isNotBlank() }?.let { add(it) }
                            if (book.durationSec > 0) add(formatDuration(book.durationSec))
                            if (detail.chapters.isNotEmpty()) add("${detail.chapters.size} kap.")
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    AudiobookDownloadRow(
                        state = downloadState,
                        onDownload = onDownload,
                        onCancel = onCancelDownload,
                        onDelete = onDeleteDownload,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = { onPlay(false, null) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        if (canResume) "Pokračovat ${formatDuration(resumeSec)}" else "Přehrát",
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (canResume) {
                    FilledTonalButton(onClick = { onPlay(true, null) }) {
                        Icon(Icons.Default.Replay, contentDescription = "Od začátku", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        detail.description?.let { desc ->
            item {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }

        if (detail.chapters.isNotEmpty()) {
            item {
                Text(
                    "Kapitoly",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
                )
            }
            items(detail.chapters, key = { it.index }) { ch ->
                ChapterRow(ch, isCurrent = ch.index == playingChapterIndex, onClick = { onPlay(false, ch.startSec) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}

/**
 * Řádek stažení CELÉ audioknihy (dole v hlavičce). NONE/FAILED = klik stáhne; DOWNLOADING = progres +
 * klik zruší; DOWNLOADED = „Staženo" + dlouhý stisk smaže.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudiobookDownloadRow(
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val clickMod = when (state.status) {
        DownloadStatus.NONE, DownloadStatus.FAILED -> Modifier.clickable(onClick = onDownload)
        DownloadStatus.DOWNLOADING -> Modifier.clickable(onClick = onCancel)
        DownloadStatus.DOWNLOADED -> Modifier.combinedClickable(onClick = {}, onLongClick = onDelete)
    }
    Row(
        clickMod.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.status) {
            DownloadStatus.DOWNLOADED -> {
                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text("Staženo", style = MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.padding(start = 8.dp))
            }
            DownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )
                Text("Stahuji… ${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(start = 8.dp))
                Icon(Icons.Default.Close, contentDescription = "Zrušit", tint = muted, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
            DownloadStatus.FAILED -> {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text("Stáhnout znovu", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
            }
            DownloadStatus.NONE -> {
                Icon(Icons.Default.Download, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                Text("Stáhnout", style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ChapterRow(ch: Chapter, isCurrent: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) accent.copy(alpha = 0.16f) else Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isCurrent) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
            contentDescription = if (isCurrent) "Hraje" else null,
            tint = accent,
            modifier = Modifier.size(18.dp).padding(end = 2.dp),
        )
        Text(
            text = ch.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Text(
            text = formatDuration(ch.startSec),
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m, s)
        else -> "%d:%02d".format(m, s)
    }
}
