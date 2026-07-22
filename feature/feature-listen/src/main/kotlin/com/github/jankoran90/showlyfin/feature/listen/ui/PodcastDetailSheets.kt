package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.abs.model.DownloadState
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import com.github.jankoran90.showlyfin.feature.listen.FindEpisodesState
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trailing badge stažení epizody: stáhnout / probíhá (klik = zrušit) / staženo / chyba (klik = znovu). */
@Composable
internal fun DownloadControl(
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when (state.status) {
            DownloadStatus.DOWNLOADED -> Icon(
                Icons.Default.DownloadDone,
                contentDescription = "Staženo (offline)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            DownloadStatus.DOWNLOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zrušit stahování", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DownloadStatus.FAILED -> IconButton(onClick = onDownload) {
                Icon(Icons.Default.ErrorOutline, contentDescription = "Stažení selhalo — zkusit znovu", tint = MaterialTheme.colorScheme.error)
            }
            DownloadStatus.NONE -> IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Stáhnout pro offline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeActionSheet(
    episode: PodcastEpisode,
    downloadStatus: DownloadStatus,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFinished: () -> Unit,
    onEnqueueNext: () -> Unit,
    onEnqueueEnd: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onShare: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            episode.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ActionRow(Icons.Default.PlayArrow, "Přehrát", onPlay)
        ActionRow(
            if (episode.isFinished) Icons.Outlined.Circle else Icons.Default.CheckCircle,
            if (episode.isFinished) "Označit jako nedokončené" else "Označit jako dokončené",
            onToggleFinished,
        )
        ActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Přidat do fronty (další)", onEnqueueNext)
        ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)", onEnqueueEnd)
        when (downloadStatus) {
            DownloadStatus.DOWNLOADED ->
                ActionRow(Icons.Default.Delete, "Smazat stažení", onDeleteDownload)
            DownloadStatus.DOWNLOADING ->
                ActionRow(Icons.Default.Close, "Zrušit stahování", onCancelDownload)
            DownloadStatus.FAILED ->
                ActionRow(Icons.Default.Download, "Stáhnout znovu", onDownload)
            DownloadStatus.NONE ->
                ActionRow(Icons.Default.Download, "Stáhnout pro offline", onDownload)
        }
        ActionRow(Icons.Default.Share, "Sdílet epizodu", onShare)
        Box(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<QueuedEpisode>,
    playingEpisodeId: String?,
    display: EpisodeDisplaySettings,
    onDismiss: () -> Unit,
    onPlay: (QueuedEpisode) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Fronta · ${queue.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Vymazat vše") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (queue.isEmpty()) {
            Text(
                "Fronta je prázdná.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn {
                items(queue, key = { it.episodeId }) { q ->
                    val isCur = q.episodeId == playingEpisodeId
                    val accent = MaterialTheme.colorScheme.primary
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isCur) accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPlay(q) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCur) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "Hraje", tint = accent, modifier = Modifier.size(18.dp).padding(end = 8.dp, top = 2.dp).align(Alignment.Top))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Přehrát", tint = accent, modifier = Modifier.size(18.dp).padding(end = 8.dp, top = 2.dp).align(Alignment.Top))
                        }
                        Column(Modifier.weight(1f)) {
                            GuestBanner(q.guest, display)
                            Text(
                                q.title,
                                style = episodeTitleStyle(display),
                                color = if (isCur) accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = display.titleLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                            q.podcastTitle?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            EpisodeDescriptionText(description = q.description, display = display)
                        }
                        IconButton(onClick = { onRemove(q.episodeId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Odebrat z fronty", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

/** Sheet „Prohledat epizody": dostupné RSS epizody (nestažené na serveru) + multi-select → stáhnout na server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FindEpisodesSheet(
    state: FindEpisodesState,
    display: EpisodeDisplaySettings,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Prohledat epizody", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Dostupné v RSS, zatím nestažené na serveru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.episodes.isNotEmpty()) {
                val allSelected = state.selectedIds.size == state.episodes.size
                TextButton(onClick = { if (allSelected) onClearSelection() else onSelectAll() }) {
                    Text(if (allSelected) "Zrušit výběr" else "Vybrat vše")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Text(
                state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp),
            )
            state.episodes.isEmpty() -> Text(
                "Žádné nové epizody — server má všechny z feedu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(state.episodes, key = { it.id }) { ep ->
                    val checked = ep.id in state.selectedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(ep.id) }
                            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(ep.id) })
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            GuestBanner(ep.guest, display)
                            Text(
                                ep.title,
                                style = episodeTitleStyle(display),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = display.titleLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildList {
                                formatEpisodeDate(ep.publishedAt)?.let { add(it) }
                                ep.durationSec?.takeIf { it > 0 }?.let { add(formatEpisodeDuration(it)) }
                            }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            EpisodeDescriptionText(description = ep.description, display = display)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        if (state.episodes.isNotEmpty()) {
            Button(
                onClick = onConfirm,
                enabled = state.selectedIds.isNotEmpty() && !state.submitting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Stáhnout na server (${state.selectedIds.size})")
                }
            }
        }
        Box(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

internal fun formatEpisodeDate(ms: Long?): String? {
    if (ms == null || ms <= 0L) return null
    return runCatching {
        SimpleDateFormat("d. M. yyyy", Locale("cs")).format(Date(ms))
    }.getOrNull()
}

internal fun formatEpisodeDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min"
        else -> "<1 min"
    }
}
