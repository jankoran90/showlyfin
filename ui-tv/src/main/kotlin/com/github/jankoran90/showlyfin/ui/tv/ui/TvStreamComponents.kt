package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStreamQuality
import com.github.jankoran90.showlyfin.feature.detail.RdDownloadState

private fun tvQualityBadge(q: UploaderStreamQuality): String = buildList {
    q.resolution?.let { add(it) }
    q.videoCodec?.let { add(it) }
    q.audioLanguage?.let { add(it) }
    q.audioFormat?.let { add(it) }
    q.channels?.let { add(it) }
    q.sizeGB?.let { add("%.1f GB".format(it)) }
    q.csfdPct?.let { add("ČSFD $it%") }
}.joinToString(" · ")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvOverlayPanel(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(720.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF12121F))
                .padding(24.dp),
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvStreamRow(stream: UploaderStream, action: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stream.name?.replace("\n", " ")?.trim().orEmpty().ifBlank { "Stream" },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badge = tvQualityBadge(stream.quality)
                if (badge.isNotBlank()) {
                    Text(badge, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(action, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvStreamPicker(
    streams: List<UploaderStream>,
    isLoading: Boolean,
    isResolving: Boolean,
    error: String?,
    onPlay: (UploaderStream) -> Unit,
    onDismiss: () -> Unit,
) {
    TvOverlayPanel("Stream přes Stremio", onDismiss) {
        when {
            isLoading -> TvCenter { CircularProgressIndicator() }
            error != null && streams.isEmpty() -> Text(error, color = Color.White.copy(alpha = 0.7f))
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(streams, key = { it.infoHash ?: it.url ?: it.name.orEmpty() }) { s ->
                    TvStreamRow(s, "▶ Přehrát") { if (!isResolving) onPlay(s) }
                }
            }
        }
        if (isResolving) {
            Spacer(Modifier.height(12.dp))
            Text("Připravuji stream z RealDebrid…", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Overlay s průběhem nahrávání necachovaného torrentu na RealDebrid (Fáze F, TV). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRdDownloadOverlay(state: RdDownloadState, onCancel: () -> Unit) {
    val isDownloading = state.status == "downloading"
    val pct = (state.progress / 100.0).toFloat().coerceIn(0f, 1f)
    val mbps = state.speedBytesPerSec / 1_000_000.0
    val label = when (state.status) {
        "magnet_conversion", "waiting_files_selection" -> "Příprava torrentu…"
        "queued" -> "Ve frontě na RealDebrid…"
        "downloading" -> "Stahuje se na RealDebrid…"
        "compressing", "uploading" -> "Dokončuje se…"
        "downloaded" -> "Hotovo, spouštím přehrávání…"
        else -> "Připravuji stream…"
    }
    TvOverlayPanel("RealDebrid", onCancel) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        if (isDownloading && state.progress > 0.0) {
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val detail = buildList {
                add("%.0f %%".format(state.progress))
                if (mbps > 0.0) add("%.1f MB/s".format(mbps))
                if (state.seeders > 0) add("${state.seeders} seedů")
            }.joinToString("  ·  ")
            Text(detail, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        TvMenuRow("Zrušit", "Přerušit nahrávání a zavřít", onCancel)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDownloadMenu(
    onSdilej: () -> Unit,
    onSmartRemux: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvOverlayPanel("Stáhnout", onDismiss) {
        TvMenuRow("Sdílej.cz", "Stáhnout přímý soubor do knihovny", onSdilej)
        Spacer(Modifier.height(8.dp))
        TvMenuRow("Smart Remux (4K + CZ audio)", "Automaticky složí 4K video + CZ audio", onSmartRemux)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSdilejPicker(
    streams: List<UploaderStream>,
    isLoading: Boolean,
    error: String?,
    onCapture: (UploaderStream) -> Unit,
    onDismiss: () -> Unit,
) {
    TvOverlayPanel("Sdílej.cz", onDismiss) {
        when {
            isLoading -> TvCenter { CircularProgressIndicator() }
            error != null && streams.isEmpty() -> Text(error, color = Color.White.copy(alpha = 0.7f))
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(streams, key = { it.url ?: it.name.orEmpty() }) { s ->
                    TvStreamRow(s, "⬇ Stáhnout") { onCapture(s) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TvCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { content() }
}
