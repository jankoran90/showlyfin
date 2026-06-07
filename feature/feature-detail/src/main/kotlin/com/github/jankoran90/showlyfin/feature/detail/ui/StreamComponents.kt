package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStreamQuality

internal fun qualityBadge(q: UploaderStreamQuality): String = buildList {
    q.resolution?.let { add(it) }
    q.videoCodec?.let { add(it) }
    q.audioLanguage?.let { add(it) }
    q.audioFormat?.let { add(it) }
    q.channels?.let { add(it) }
    q.sizeGB?.let { add("%.1f GB".format(it)) }
    q.csfdPct?.let { add("ČSFD $it%") }
}.joinToString(" · ")

/**
 * Označení zdroje streamu (jen Stremio picker):
 * RD ✓ = na RealDebrid připravené (přehraje se hned), RD = přes RealDebrid (chvíli se připraví),
 * Addon = addon-proxy odkaz (aiostreams apod.) — nespolehlivý, často „Invalid link".
 */
@Composable
private fun SourceBadge(stream: UploaderStream) {
    val (label, color) = when {
        stream.infoHash != null && stream.quality.rdReady -> "RD ✓" to Color(0xFF2E7D32)
        stream.infoHash != null -> "RD" to MaterialTheme.colorScheme.primary
        else -> "Addon" to Color(0xFFB26A00)
    }
    Box(
        Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamRow(
    stream: UploaderStream,
    trailingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    showSourceBadge: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSourceBadge) SourceBadge(stream)
        Column(Modifier.weight(1f)) {
            Text(
                text = stream.name?.replace("\n", " ")?.trim().orEmpty().ifBlank { "Stream" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val badge = qualityBadge(stream.quality)
            if (badge.isNotBlank()) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            stream.addon?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailingIcon()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamPickerSheet(
    streams: List<UploaderStream>,
    isLoading: Boolean,
    isResolving: Boolean,
    error: String?,
    onPlay: (UploaderStream) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("Stream přes Stremio", Icons.Default.PlayArrow)
        when {
            isLoading -> SheetCenter { CircularProgressIndicator() }
            error != null && streams.isEmpty() -> SheetMessage(error)
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(streams, key = { it.infoHash ?: it.url ?: it.name.orEmpty() }) { s ->
                    StreamRow(
                        stream = s,
                        trailingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = "Přehrát") },
                        onClick = { if (!isResolving) onPlay(s) },
                        showSourceBadge = true,
                    )
                    HorizontalDivider()
                }
            }
        }
        if (isResolving) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.height(20.dp))
                Text("Připravuji stream z RealDebrid…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadMenuSheet(
    onSdilej: () -> Unit,
    onSmartRemux: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("Stáhnout", Icons.Default.Download)
        MenuRow("Sdílej.cz", "Stáhnout přímý soubor do knihovny", Icons.Default.Download, onSdilej)
        HorizontalDivider()
        MenuRow("Smart Remux (4K + CZ audio)", "Automaticky složí 4K video + CZ audio", Icons.Default.Build, onSmartRemux)
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SdilejPickerSheet(
    streams: List<UploaderStream>,
    isLoading: Boolean,
    error: String?,
    onCapture: (UploaderStream) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("Sdílej.cz", Icons.Default.Download)
        when {
            isLoading -> SheetCenter { CircularProgressIndicator() }
            error != null && streams.isEmpty() -> SheetMessage(error)
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(streams, key = { it.url ?: it.name.orEmpty() }) { s ->
                    StreamRow(
                        stream = s,
                        trailingIcon = { Icon(Icons.Default.Download, contentDescription = "Stáhnout") },
                        onClick = { onCapture(s) },
                    )
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider()
}

@Composable
private fun MenuRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SheetMessage(msg: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
