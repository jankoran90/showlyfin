package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.StreamTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TICKS_PER_MS = 10_000L

/** RELAY — sekce „Ovladač": real-time sledování + dálkové ovládání běžící Jellyfin TV session. */
@Composable
fun OvladacScreen(
    onOpenDetail: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: OvladacViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    // Polling jen dokud je sekce na obrazovce.
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Ovladač",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        // Přepínač zařízení (když je víc remote-control TV session).
        if (state.sessions.size > 1) {
            DeviceSwitcher(state.sessions, state.selectedId, vm::selectDevice)
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.loading && state.current == null -> Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.noCreds -> Hint("Jellyfin není přihlášen", "Přihlas se v Nastavení.")

            state.current == null -> Hint(
                "Nic nehraje na TV",
                if (state.sessions.isEmpty()) "Žádná aktivní TV session."
                else "Dostupné: " + state.sessions.joinToString { it.deviceName },
            )

            else -> NowPlaying(state.current!!, state.coverUrl, onOpenDetail, vm)
        }
    }
}

@Composable
private fun DeviceSwitcher(
    sessions: List<JellyfinSessionSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sessions.forEach { s ->
            FilterChip(
                selected = s.sessionId == selectedId,
                onClick = { onSelect(s.sessionId) },
                label = { Text(s.deviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Tv, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun NowPlaying(
    s: JellyfinSessionSummary,
    coverUrl: String?,
    onOpenDetail: (String) -> Unit,
    vm: OvladacViewModel,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            val coverItemId = s.itemId
            Row {
                // Cover → proklik na detail v knihovně.
                if (coverUrl != null && coverItemId != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = s.nowPlayingTitle,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDetail(coverItemId) },
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            s.deviceName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.nowPlayingTitle ?: "Nic nehraje",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = s.nowPlayingSubtitle
                    if (!sub.isNullOrBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (s.runtimeTicks > 0L) {
                        Text(
                            buildString {
                                append(formatDuration(s.runtimeTicks))
                                endClock(s)?.let { append(" · skončí ve $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val overview = s.overview
            if (!overview.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Progress + seek slider.
            Spacer(Modifier.height(12.dp))
            ProgressSeek(s, vm)

            // Hlavní ovládání.
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.seekBy(-10_000L) }) {
                    Icon(Icons.Filled.Replay10, "Zpět 10 s", Modifier.size(34.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.playPause() }) {
                    Icon(
                        if (s.isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                        "Přehrát/Pauza",
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.seekBy(30_000L) }) {
                    Icon(Icons.Filled.Forward30, "Vpřed 30 s", Modifier.size(34.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.stopPlayback() }) {
                    Icon(Icons.Filled.Stop, "Stop", Modifier.size(30.dp))
                }
            }

            // Hlasitost.
            Spacer(Modifier.height(4.dp))
            VolumeRow(s, vm)

            // Titulky / audio / info.
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubtitlePicker(s, vm, Modifier.weight(1f))
                AudioPicker(s, vm, Modifier.weight(1f))
                if (s.mediaInfoLines.isNotEmpty()) InfoButton(s.mediaInfoLines)
            }
        }
    }
}

@Composable
private fun ProgressSeek(s: JellyfinSessionSummary, vm: OvladacViewModel) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val liveFraction =
        if (s.runtimeTicks > 0L) (s.positionTicks.toFloat() / s.runtimeTicks).coerceIn(0f, 1f) else 0f
    val fraction = if (scrubbing) scrubFraction else liveFraction

    if (s.runtimeTicks > 0L && s.canSeek) {
        Slider(
            value = fraction,
            onValueChange = { scrubbing = true; scrubFraction = it },
            onValueChangeFinished = { vm.seekToFraction(scrubFraction); scrubbing = false },
        )
    } else if (s.runtimeTicks > 0L) {
        LinearProgressIndicator(progress = { liveFraction }, modifier = Modifier.fillMaxWidth())
    }
    if (s.runtimeTicks > 0L) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((fraction * s.runtimeTicks).toLong()), style = MaterialTheme.typography.labelSmall)
            Text("-" + formatDuration(((1f - fraction) * s.runtimeTicks).toLong()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VolumeRow(s: JellyfinSessionSummary, vm: OvladacViewModel) {
    var local by remember(s.volumeLevel) { mutableFloatStateOf((s.volumeLevel ?: 50).toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.toggleMute() }) {
            Icon(if (s.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Ztlumit")
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { vm.setVolume(local.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("${local.toInt()}", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SubtitlePicker(s: JellyfinSessionSummary, vm: OvladacViewModel, modifier: Modifier) {
    if (s.subtitleTracks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val current = s.subtitleTracks.firstOrNull { it.index == s.currentSubtitleIndex }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(current?.label ?: "Titulky vyp.", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Subtitles, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Vypnuto") }, onClick = { vm.setSubtitle(-1); open = false })
            s.subtitleTracks.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.setSubtitle(t.index); open = false })
            }
        }
    }
}

@Composable
private fun AudioPicker(s: JellyfinSessionSummary, vm: OvladacViewModel, modifier: Modifier) {
    if (s.audioTracks.size <= 1) return
    var open by remember { mutableStateOf(false) }
    val current = s.audioTracks.firstOrNull { it.index == s.currentAudioIndex }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(current?.label ?: "Audio", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.VolumeUp, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            s.audioTracks.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.setAudio(t.index); open = false })
            }
        }
    }
}

@Composable
private fun InfoButton(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.Info, "Informace o stopách") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Zavřít") } },
            title = { Text("Audio / Video") },
            text = { Column { lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } },
        )
    }
}

@Composable
private fun Hint(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Délka v ticks → „1 h 24 min" / „14 min". */
private fun formatDuration(ticks: Long): String {
    val totalMin = (ticks / TICKS_PER_MS / 1000 / 60).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** Hodina, ve kterou přehrávání skončí (teď + zbývající runtime), formát HH:mm. */
private fun endClock(s: JellyfinSessionSummary): String? {
    if (s.runtimeTicks <= 0L) return null
    val remainingMs = ((s.runtimeTicks - s.positionTicks).coerceAtLeast(0L)) / TICKS_PER_MS
    val end = Date(System.currentTimeMillis() + remainingMs)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(end)
}
