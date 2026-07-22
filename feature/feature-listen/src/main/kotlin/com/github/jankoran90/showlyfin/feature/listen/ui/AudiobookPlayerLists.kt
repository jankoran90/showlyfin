package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode

@Composable
internal fun InlineChapterList(
    modifier: Modifier,
    chapters: List<com.github.jankoran90.showlyfin.data.abs.model.Chapter>,
    currentIndex: Int?,
    onSeek: (Double) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentPos = chapters.indexOfFirst { it.index == currentIndex }
    LaunchedEffect(currentPos) {
        if (currentPos >= 0) runCatching { listState.scrollToItem(currentPos) }
    }
    Column(modifier) {
        ListHeader("Kapitoly · ${chapters.size}")
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(chapters, key = { _, ch -> ch.index }) { _, ch ->
                val isCur = ch.index == currentIndex
                val accent = MaterialTheme.colorScheme.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(ch.startSec) }
                        .background(if (isCur) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isCur) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        ch.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCur) accent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        fmt((ch.startSec * 1000).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InlineQueueList(
    modifier: Modifier,
    queue: List<QueuedEpisode>,
    currentEpisodeId: String?,
    display: EpisodeDisplaySettings,
    swipeAction: Int,
    onPlay: (QueuedEpisode) -> Unit,
    onRemove: (String) -> Unit,
    onSwipeRight: (QueuedEpisode) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    val queueState = rememberUpdatedState(queue)
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableStateOf(0f) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ListHeader("Fronta · ${queue.size}", Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Vymazat vše") }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(queue, key = { _, q -> q.episodeId }) { _, q ->
                val isCur = q.episodeId == currentEpisodeId
                val isDragging = q.episodeId == draggingId
                val accent = MaterialTheme.colorScheme.primary
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { v ->
                        when (v) {
                            SwipeToDismissBoxValue.EndToStart -> { onRemove(q.episodeId); true }
                            SwipeToDismissBoxValue.StartToEnd -> { onSwipeRight(q); false }
                            else -> false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragAccum else 0f },
                    backgroundContent = { QueueSwipeBackground(dismissState, swipeAction) },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isCur) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background)
                            .clickable { onPlay(q) }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCur) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "Hraje", tint = accent, modifier = Modifier.size(18.dp))
                        } else {
                            QueueThumb(q.coverUrl, size = 36)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
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
                        // Velký úchyt pro přetažení (změna pořadí) — long-press + tah nahoru/dolů.
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Přetáhnout pro změnu pořadí",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(start = 4.dp)
                                .pointerInput(q.episodeId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingId = q.episodeId; dragAccum = 0f },
                                        onDragEnd = { draggingId = null; dragAccum = 0f },
                                        onDragCancel = { draggingId = null; dragAccum = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragAccum += amount.y
                                            val itemH = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                                            if (itemH > 0) {
                                                val cur = queueState.value.indexOfFirst { it.episodeId == draggingId }
                                                if (dragAccum > itemH && cur in 0 until queueState.value.lastIndex) {
                                                    onMove(cur, cur + 1); dragAccum -= itemH
                                                } else if (dragAccum < -itemH && cur > 0) {
                                                    onMove(cur, cur - 1); dragAccum += itemH
                                                }
                                            }
                                        },
                                    )
                                },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}

/** Pozadí swipe gesta na položce fronty: vlevo (StartToEnd) = volitelná akce, vpravo (EndToStart) = odebrat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSwipeBackground(state: SwipeToDismissBoxState, swipeAction: Int) {
    when (state.dismissDirection) {
        SwipeToDismissBoxValue.EndToStart -> SwipeBg(
            align = Alignment.CenterEnd,
            color = MaterialTheme.colorScheme.errorContainer,
            icon = Icons.Default.Delete,
            label = "Odebrat",
            onColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        SwipeToDismissBoxValue.StartToEnd -> {
            val (icon, label) = when (swipeAction) {
                1 -> Icons.Default.PlayArrow to "Přehrát"
                2 -> Icons.Default.ArrowUpward to "Na začátek"
                else -> Icons.Default.CloudDownload to "Stáhnout"
            }
            SwipeBg(
                align = Alignment.CenterStart,
                color = MaterialTheme.colorScheme.primaryContainer,
                icon = icon,
                label = label,
                onColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        else -> Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun SwipeBg(
    align: Alignment,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onColor: Color,
) {
    Box(Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = onColor, modifier = Modifier.size(20.dp))
            Text(label, color = onColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 6.dp, horizontal = 4.dp),
    )
}

/** Přeskok ◀▶ s konfigurovatelnou velikostí — generická ikona Replay (forward = zrcadlená) + číslo. */
@Composable
internal fun SeekButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Replay,
                contentDescription = (if (forward) "+" else "−") + "$seconds s",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = (if (forward) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier).size(38.dp),
            )
            Text(
                "$seconds",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun QueueThumb(coverUrl: String?, size: Int = 44) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl != null) {
            AsyncImage(model = coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

internal fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
