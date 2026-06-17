package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode

private val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 2f, 3f)
private val SLEEP_OPTIONS = listOf(5, 15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    itemId: String?,
    fromStart: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startSec: Double? = null,
    episodeId: String? = null,
    // PERCH (SHW-69): klik na cover → seznam dílů rodičovského pořadu/knihy (cíl odvodí VM ze zdroje).
    onOpenSource: (com.github.jankoran90.showlyfin.feature.listen.ListenSourceTarget) -> Unit = {},
    viewModel: AudiobookPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // itemId != null = otevřeno z detailu (spustit knihu / epizodu); null = expand z mini-playeru (už hraje).
    LaunchedEffect(itemId, episodeId, startSec) {
        if (itemId != null) viewModel.open(itemId, fromStart, startSec, episodeId)
    }

    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    // CADENCE Fáze E: tah dolů (z horní lišty nebo coveru) sbalí přehrávač do mini-playeru (jako Deezer/Qobuz).
    val collapseOffset = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val collapseThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val collapseDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                val newY = (collapseOffset.value + dragAmount).coerceAtLeast(0f)
                dragScope.launch { collapseOffset.snapTo(newY) }
                if (dragAmount > 0f) change.consume()
            },
            onDragEnd = {
                if (collapseOffset.value > collapseThresholdPx) onBack()
                else dragScope.launch { collapseOffset.animateTo(0f) }
            },
            onDragCancel = { dragScope.launch { collapseOffset.animateTo(0f) } },
        )
    }

    ListenExpressiveTheme {
        Column(
            modifier
                .fillMaxSize()
                .graphicsLayer { translationY = collapseOffset.value }
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Horní lišta = úchyt pro tah dolů (sbalit). IconButton = klasický „zpět".
            Row(
                Modifier.fillMaxWidth().then(collapseDrag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Stáhnout přehrávač",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            // Prázdný přehrávač (po „Vymazat vše včetně přehrávaného") — nic nehraje, fronta prázdná.
            if (!state.isActive && itemId == null && !state.isBuffering) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Přehrávač je prázdný.\nVyber epizodu nebo audioknihu a začni poslouchat.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            // Cover — menší, ať zbyde místo na seznam.
            // PERCH (SHW-69): klik na cover skočí na seznam dílů rodičovského pořadu/knihy (tah dolů
            // dál sbalí přehrávač — gesta koexistují: vertikální tah konzumuje collapseDrag, ťuk projde).
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .aspectRatio(1f)
                    .then(collapseDrag)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { viewModel.currentSourceTarget()?.let(onOpenSource) },
                contentAlignment = Alignment.Center,
            ) {
                if (state.coverUrl != null) {
                    AsyncImage(model = state.coverUrl, contentDescription = state.title, modifier = Modifier.fillMaxSize())
                }
                if (state.isBuffering) CircularProgressIndicator()
            }

            Spacer(Modifier.height(10.dp))
            // Host jako poutač nad titulkem i v now-playing (když je rozpoznán a zapnuto).
            state.guest?.takeIf { it.isNotBlank() && viewModel.episodeDisplay.highlightGuest }?.let { g ->
                Text(
                    g,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                state.title.ifBlank { "Načítám…" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            state.currentChapterTitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            val dur = state.durationMs.coerceAtLeast(1L)
            val sliderValue = if (scrubbing) scrubMs else state.positionMs.toFloat()
            Slider(
                value = sliderValue.coerceIn(0f, dur.toFloat()),
                onValueChange = { scrubbing = true; scrubMs = it },
                onValueChangeFinished = { viewModel.seekTo(scrubMs.toLong()); scrubbing = false },
                valueRange = 0f..dur.toFloat(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val rightLabel = if (state.showRemainingTime) {
                    "-" + fmt((state.durationMs - sliderValue.toLong()).coerceAtLeast(0L))
                } else fmt(state.durationMs)
                Text(rightLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(6.dp))

            // Jedna řada ovládání: rychlost · ◀ · −seek · play · +seek · ▶ · časovač
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.showSpeedButton) {
                    Box {
                        IconButton(onClick = { speedMenu = true }, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Speed, contentDescription = "Rychlost", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                                Text("${trimSpeed(state.speed)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            SPEEDS.forEach { sp ->
                                DropdownMenuItem(text = { Text("${trimSpeed(sp)}×") }, onClick = { viewModel.setSpeed(sp); speedMenu = false })
                            }
                        }
                    }
                }

                IconButton(onClick = { if (state.isPodcastEpisode) viewModel.playPrevInQueue() else viewModel.prevChapter() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = if (state.isPodcastEpisode) "Předchozí epizoda z fronty" else "Předchozí kapitola",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp),
                    )
                }

                SeekButton(seconds = state.skipSeconds, forward = false) { viewModel.seekBy(-state.skipSeconds * 1000L) }

                FilledIconButton(
                    onClick = { viewModel.playPause() },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pauza" else "Přehrát",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                SeekButton(seconds = state.skipSeconds, forward = true) { viewModel.seekBy(state.skipSeconds * 1000L) }

                val canSkipNext = if (state.isPodcastEpisode) queue.isNotEmpty() else true
                IconButton(
                    onClick = { if (state.isPodcastEpisode) viewModel.playNextInQueue() else viewModel.nextChapter() },
                    enabled = canSkipNext,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = if (state.isPodcastEpisode) "Další epizoda z fronty" else "Další kapitola",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (canSkipNext) 1f else 0.3f),
                        modifier = Modifier.size(28.dp),
                    )
                }

                if (state.showSleepButton) {
                    Box {
                        val sleepActive = state.sleepAtEnd || state.sleepMinutesLeft != null
                        IconButton(onClick = { sleepMenu = true }, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = "Časovač spánku",
                                    tint = if (sleepActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp),
                                )
                                state.sleepMinutesLeft?.let {
                                    Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                            SLEEP_OPTIONS.forEach { min ->
                                DropdownMenuItem(text = { Text("$min min") }, onClick = { viewModel.setSleepTimer(min); sleepMenu = false })
                            }
                            DropdownMenuItem(
                                text = { Text(if (state.isPodcastEpisode) "Do konce epizody" else "Do konce kapitoly") },
                                onClick = { viewModel.setSleepEndOfCurrent(); sleepMenu = false },
                            )
                            DropdownMenuItem(text = { Text("Vypnout") }, onClick = { viewModel.setSleepTimer(null); sleepMenu = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Subtilní seznam pod ovládáním: fronta (podcast) nebo kapitoly (audiokniha). Hrající zvýrazněno.
            if (state.isPodcastEpisode && queue.isNotEmpty()) {
                InlineQueueList(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    queue = queue,
                    currentEpisodeId = state.currentEpisodeId,
                    display = viewModel.episodeDisplay,
                    swipeAction = state.queueSwipeAction,
                    onPlay = { viewModel.playQueued(it) },
                    onRemove = { viewModel.removeFromQueue(it) },
                    onSwipeRight = { viewModel.onQueueSwipeAction(it, state.queueSwipeAction) },
                    onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                    onClear = { viewModel.clearAll() },
                )
            } else if (chapters.isNotEmpty()) {
                InlineChapterList(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    chapters = chapters,
                    currentIndex = state.currentChapterIndex,
                    onSeek = { viewModel.seekToChapter(it) },
                )
            }
        }
    }
}

@Composable
private fun InlineChapterList(
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
private fun InlineQueueList(
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
private fun SeekButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
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

private fun trimSpeed(s: Float): String =
    if (s == s.toLong().toFloat()) s.toLong().toString() else s.toString()

private fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
