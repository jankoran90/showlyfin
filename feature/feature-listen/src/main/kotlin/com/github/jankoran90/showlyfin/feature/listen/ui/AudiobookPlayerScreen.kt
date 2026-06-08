package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel

private val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 2f, 3f)
private val SLEEP_OPTIONS = listOf(5, 15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookPlayerScreen(
    itemId: String?,
    fromStart: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startSec: Double? = null,
    viewModel: AudiobookPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // itemId != null = otevřeno z detailu (spustit knihu); null = expand z mini-playeru (už hraje).
    // startSec != null = klik na kapitolu v detailu → skok na její začátek.
    androidx.compose.runtime.LaunchedEffect(itemId, startSec) {
        if (itemId != null) viewModel.open(itemId, fromStart, startSec)
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    ListenExpressiveTheme {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = MaterialTheme.colorScheme.onBackground)
            }

            if (error != null) {
                Text(error!!, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Box
            }

            Column(
                Modifier.fillMaxSize().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.coverUrl != null) {
                        AsyncImage(
                            model = state.coverUrl,
                            contentDescription = state.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (state.isBuffering) CircularProgressIndicator()
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    state.title.ifBlank { "Načítám…" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                state.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.currentChapterTitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

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
                    Text(fmt(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.prevChapter() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Předchozí kapitola", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { viewModel.seekBy(-30_000) }) {
                        Icon(Icons.Default.Replay30, contentDescription = "−30 s", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    FilledIconButton(
                        onClick = { viewModel.playPause() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pauza" else "Přehrát",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    IconButton(onClick = { viewModel.seekBy(30_000) }) {
                        Icon(Icons.Default.Forward30, contentDescription = "+30 s", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { viewModel.nextChapter() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Další kapitola", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        AssistChip(
                            onClick = { speedMenu = true },
                            label = { Text("${trimSpeed(state.speed)}×") },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            SPEEDS.forEach { sp ->
                                DropdownMenuItem(text = { Text("${trimSpeed(sp)}×") }, onClick = { viewModel.setSpeed(sp); speedMenu = false })
                            }
                        }
                    }
                    Box {
                        AssistChip(
                            onClick = { sleepMenu = true },
                            label = { Text(state.sleepMinutesLeft?.let { "$it min" } ?: "Časovač") },
                            leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                            SLEEP_OPTIONS.forEach { min ->
                                DropdownMenuItem(text = { Text("$min min") }, onClick = { viewModel.setSleepTimer(min); sleepMenu = false })
                            }
                            DropdownMenuItem(text = { Text("Vypnout") }, onClick = { viewModel.setSleepTimer(null); sleepMenu = false })
                        }
                    }
                }
            }
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
