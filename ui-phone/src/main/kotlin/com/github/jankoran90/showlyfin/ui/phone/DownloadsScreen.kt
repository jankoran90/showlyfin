package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import java.io.File
import java.util.Locale

/**
 * NOMAD (SHW-60): sekce „Stažené" — offline filmy/epizody v telefonu (na chatu bez wifi).
 * Nahoře místo (zabráno/volné) + varování při docházejícím místě; pak probíhající stahování
 * (progress) a níže dokončená stažení (tap = přehrát offline, koš = smazat).
 */
@Composable
fun DownloadsScreen(
    onPlay: (OfflineDownload) -> Unit,
    onOpenDetail: (OfflineDownload) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val allDownloads by viewModel.downloads.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val online by viewModel.isOnline.collectAsStateWithLifecycle()

    // LEVER L3: audio podcasty (Poslech) sem nepatří — jsou ve „Stažené" v Poslechu.
    val downloads = allDownloads.filterNot { it.type == OfflineRequest.TYPE_PODCAST }

    // Probíhající/čekající/selhalá stahování (z indexu ještě nejsou) — bez audio podcastů.
    val active = states.entries
        .filter { it.value.status == OfflineStatus.DOWNLOADING || it.value.status == OfflineStatus.QUEUED || it.value.status == OfflineStatus.FAILED }
        .filterNot { viewModel.isPodcast(it.key) }
        .sortedBy { it.key }

    val used = remember(downloads) { viewModel.usedBytes() }
    val free = remember(downloads) { viewModel.freeBytes() }
    val low = remember(downloads) { viewModel.isLowOnSpace() }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Zabráno ${formatBytes(used)} · volných ${formatBytes(free)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (downloads.isNotEmpty()) {
                TextButton(onClick = { viewModel.deleteAll() }) { Text("Smazat vše") }
            }
        }
        if (low) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Dochází místo v telefonu — zvaž smazání starších stažení.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (active.isEmpty() && downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("Zatím nic staženého", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "V detailu filmu zvol \"Stáhnout do telefonu\" a po stažení ho tu pustíš i bez sítě.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active.isNotEmpty()) {
                    items(active, key = { "active_${it.key}" }) { entry ->
                        ActiveRow(
                            title = viewModel.titleFor(entry.key),
                            status = entry.value.status,
                            progress = entry.value.progress,
                            downloadedBytes = entry.value.downloadedBytes,
                            totalBytes = entry.value.totalBytes,
                            error = entry.value.error,
                            onCancel = { viewModel.cancel(entry.key) },
                        )
                    }
                }
                items(downloads, key = { it.key }) { dl ->
                    // Online + film s TMDb → klik otevře KARTU obsahu (přehrát/na TV/prohlédnout).
                    // Offline (nebo bez karty) → klik pustí rovnou lokální kopii. ▶ hraje offline vždy.
                    val canOpenCard = online && dl.type == OfflineRequest.TYPE_MOVIE && dl.tmdb != null
                    DownloadRow(
                        download = dl,
                        onClick = { if (canOpenCard) onOpenDetail(dl) else onPlay(dl) },
                        onPlay = { onPlay(dl) },
                        onDelete = { viewModel.delete(dl.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveRow(
    title: String,
    status: OfflineStatus,
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    error: String?,
    onCancel: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            when (status) {
                OfflineStatus.FAILED -> Text(
                    error?.let { "Stažení selhalo: $it" } ?: "Stažení selhalo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OfflineStatus.QUEUED -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Čeká ve frontě…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    val pct = (progress * 100).toInt()
                    val sub = if (totalBytes > 0) "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}" else formatBytes(downloadedBytes)
                    Text("Stahuje se · $pct % · $sub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onCancel) {
            Icon(if (status == OfflineStatus.FAILED) Icons.Default.Delete else Icons.Default.Close, contentDescription = "Zrušit")
        }
    }
}

@Composable
private fun DownloadRow(
    download: OfflineDownload,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val poster = download.posterPath?.let { File(it) } ?: download.posterUrl
        AsyncImage(
            model = poster,
            contentDescription = download.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 54.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(download.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            download.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${download.sourceLabel} · ${formatBytes(download.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Přehrát offline", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    var value = b.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024.0
        i++
    }
    return String.format(Locale.getDefault(), if (i == 0) "%.0f %s" else "%.1f %s", value, units[i])
}
