package com.github.jankoran90.showlyfin.feature.remux
import com.github.jankoran90.showlyfin.core.ui.ShowlyfinStatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDetectScreen(
    imdbId: String,
    title: String,
    titleCs: String,
    year: Int?,
    mediaType: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartDetectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(imdbId) {
        viewModel.loadStreams(mediaType, imdbId, title, titleCs, year)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Remux — $title") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState.phase) {
                SmartDetectPhase.LOADING -> LoadingContent()
                SmartDetectPhase.CONFIRM -> ConfirmContent(
                    uiState = uiState,
                    onConfirm = { viewModel.confirm(imdbId, title, year, mediaType) },
                    onSelectVideo = viewModel::selectVideo,
                    onSelectAudio = viewModel::selectAudio,
                )
                SmartDetectPhase.PROGRESS -> ProgressContent(uiState = uiState)
                SmartDetectPhase.TRACK_SELECT -> TrackSelectContent(
                    uiState = uiState,
                    onToggleVideo = viewModel::toggleVideoTrack,
                    onToggleAudio = viewModel::toggleAudioTrack,
                    onConfirm = { viewModel.confirmTracks() },
                    onRetryPair = viewModel::retryPair,
                )
                SmartDetectPhase.DONE -> DoneContent(onBack = onBack)
                SmartDetectPhase.ERROR -> ErrorContent(
                    error = uiState.error,
                    canRetryPair = uiState.capturedVideoFileId.isNotBlank(),
                    onRetryPair = viewModel::retryPair,
                    onRetryAll = { viewModel.retry(mediaType, imdbId, title, titleCs, year) },
                    onCancel = { viewModel.cancelPair(); onBack() },
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Hledám streamy…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConfirmContent(
    uiState: SmartDetectUiState,
    onConfirm: () -> Unit,
    onSelectVideo: (UploaderStream) -> Unit,
    onSelectAudio: (UploaderStream) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Vybrané streamy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Text("Video", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            uiState.selectedVideo?.let { StreamRow(it, selected = true) }
            Spacer(Modifier.height(8.dp))
        }
        item {
            Text("Audio (CZ/SK)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            uiState.selectedAudio?.let { StreamRow(it, selected = true) }
            Spacer(Modifier.height(24.dp))
        }
        if (uiState.availableVideoStreams.size > 1) {
            item { Text("Jiné video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(uiState.availableVideoStreams.filter { it != uiState.selectedVideo }.take(5)) { s ->
                StreamRow(s, selected = false, onClick = { onSelectVideo(s) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (uiState.availableAudioStreams.size > 1) {
            item { Text("Jiné audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(uiState.availableAudioStreams.filter { it != uiState.selectedAudio }.take(5)) { s ->
                StreamRow(s, selected = false, onClick = { onSelectAudio(s) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Spustit remux")
            }
        }
    }
}

@Composable
private fun SourceBadge(stream: UploaderStream) {
    val (label, color) = when {
        stream.quality.rdReady -> "RD ✓" to ShowlyfinStatus.SuccessDim
        stream.infoHash != null -> "Torrent" to ShowlyfinStatus.SourceTorrent
        else -> "Addon" to ShowlyfinStatus.SourceAddon
    }
    Box(
        Modifier
            .padding(end = 6.dp)
            .background(color, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
    }
}

@Composable
private fun StreamRow(stream: UploaderStream, selected: Boolean, onClick: (() -> Unit)? = null) {
    val label = buildString {
        stream.name?.let { append(it) }
        stream.quality.resolution?.let { append(" $it") }
        stream.quality.videoCodec?.let { append(" [$it]") }
        stream.quality.audioLanguage?.let { append(" $it") }
        stream.quality.channels?.let { append(" $it") }
        stream.quality.sizeGB?.let { append(" %.1fGB".format(it)) }
        append(" ✦${stream.quality.score}")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(stream)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            OutlinedButton(onClick = onClick) { Text("Vybrat", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ProgressContent(uiState: SmartDetectUiState) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Průběh", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Text("Video: ${uiState.videoProgress.toInt()}%", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { (uiState.videoProgress / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("Audio: ${uiState.audioProgress.toInt()}%", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { (uiState.audioProgress / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        val statusLabel = when (uiState.pairStatus) {
            "pending" -> "Čekám…"
            "downloading" -> "Stahuji…"
            "detecting" -> "Detekuji sync…"
            "merging" -> "Merguju…"
            else -> uiState.pairStatus
        }
        Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun TrackSelectContent(
    uiState: SmartDetectUiState,
    onToggleVideo: (Int) -> Unit,
    onToggleAudio: (Int) -> Unit,
    onConfirm: () -> Unit,
    onRetryPair: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Výběr stop", style = MaterialTheme.typography.titleMedium)
            uiState.syncResult?.finalOffsetS?.let {
                Text("Offset: %.3fs | %s".format(it, if (uiState.syncResult.agree) "✓ shoduje" else "⚠ neshoduje"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uiState.fpsOrig != 0.0 && uiState.fpsSource != 0.0) {
                Text("FPS orig: %.3f → source: %.3f".format(uiState.fpsOrig, uiState.fpsSource), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (uiState.videoTracks.isNotEmpty()) {
            item { Text("Video stopy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(uiState.videoTracks) { track ->
                TrackCheckRow(
                    label = "[${track.index}] ${track.codec} ${track.title}",
                    checked = track.index in uiState.selectedVideoTrackIndices,
                    onToggle = { onToggleVideo(track.index) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        if (uiState.audioTracks.isNotEmpty()) {
            item { Text("Audio stopy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(uiState.audioTracks) { track ->
                val isCz = track.lang.lowercase().let { it == "cze" || it == "ces" || it == "cs" || it == "cz" || it == "und" }
                TrackCheckRow(
                    label = "[${track.index}] ${track.codec} ${track.lang} ${track.title}" + if (isCz) " ⭐" else "",
                    checked = track.index in uiState.selectedTrackIndices,
                    onToggle = { onToggleAudio(track.index) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        item {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Potvrdit a mergovat") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetryPair, modifier = Modifier.fillMaxWidth()) { Text("Znovu detekovat sync") }
        }
    }
}

@Composable
private fun TrackCheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun DoneContent(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", style = MaterialTheme.typography.displayLarge, color = ShowlyfinStatus.Success)
        Spacer(Modifier.height(16.dp))
        Text("Remux dokončen!", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Zpět") }
    }
}

@Composable
private fun ErrorContent(
    error: String?,
    canRetryPair: Boolean,
    onRetryPair: () -> Unit,
    onRetryAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Chyba", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(error ?: "Neznámá chyba", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (canRetryPair) {
            Button(onClick = onRetryPair, modifier = Modifier.fillMaxWidth()) { Text("Zkusit znovu (pair)") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRetryAll, modifier = Modifier.fillMaxWidth()) { Text("Načíst streamy znovu") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Zrušit") }
    }
}
