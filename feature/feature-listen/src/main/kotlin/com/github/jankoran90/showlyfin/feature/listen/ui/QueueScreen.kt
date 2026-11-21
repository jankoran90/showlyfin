package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode

/**
 * AMPHORA (SHW-124) FR1 — Fronta na jeden dotek (místo ve spodní liště). Nahoře souhrn (počet dílů,
 * celková zbývající délka), co právě hraje (i kniha mimo frontu), pak fronta s tažením a gesty
 * (stejná jako v přehrávači, swipe podle Nastavení › Přehrávač) a akce Vyčistit přehrané / Vymazat vše.
 * Přidává se odkudkoli přes „Přehrát další“ / „Na konec fronty“ v nabídce dílu.
 */
@Composable
fun QueueScreen(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiobookPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val marks by viewModel.directMarks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // Zbývající délka každého dílu: hraný = živě z přehrávače, jinak uložená pozice, jinak celá délka.
    fun remainingMs(q: QueuedEpisode): Long? {
        if (state.isActive && q.episodeId == state.currentEpisodeId && state.durationMs > 0) {
            return (state.durationMs - state.positionMs).coerceAtLeast(0L)
        }
        val mark = marks[q.episodeId]
        if (mark != null && mark.durMs > 0) return if (mark.isFinished) 0L else (mark.durMs - mark.posMs).coerceAtLeast(0L)
        val dur = q.direct?.durationSec ?: 0.0
        return if (dur > 0) (dur * 1000).toLong() else null
    }

    val known = queue.mapNotNull { remainingMs(it) }
    val totalMs = known.sum()
    val unknown = queue.size - known.size
    val finishedCount = queue.count { it.episodeId != state.currentEpisodeId && marks[it.episodeId]?.isFinished == true }
    val playingOutsideQueue = state.isActive && queue.none { it.episodeId == state.currentEpisodeId }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    queue.isEmpty() -> "Prázdná"
                    else -> buildString {
                        append(dilu(queue.size))
                        if (totalMs > 0) append(" · zbývá ${durationLabel(totalMs)}")
                        if (unknown > 0 && totalMs > 0) append(" + ?")
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = queue.isNotEmpty()) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Akce fronty")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (finishedCount > 0) "Vyčistit přehrané ($finishedCount)" else "Vyčistit přehrané") },
                        leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
                        enabled = finishedCount > 0,
                        onClick = {
                            menuOpen = false
                            val n = viewModel.clearFinishedFromQueue()
                            android.widget.Toast.makeText(ctx, "Odebráno: ${dilu(n)}", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Vymazat celou frontu") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                        onClick = { menuOpen = false; confirmClear = true },
                    )
                }
            }
        }
        if (playingOutsideQueue) {
            SlovoMediaRow(
                model = SlovoMediaModel(
                    title = state.title,
                    subtitle = state.author,
                    coverUrl = state.coverUrl,
                    progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else null,
                    meta = remainingLabel(state.positionMs, state.durationMs),
                    playing = true,
                    placeholder = Icons.Rounded.Headphones,
                ),
                onClick = onOpenPlayer,
                trailing = {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Hraje", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Spacing.xl))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
        if (queue.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Spacing.xxl * 2))
                Text(
                    "Ve frontě nic není.\nDíl přidáš podržením nebo přes ⋮ → Přehrát další / Na konec fronty.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }
        } else {
            InlineQueueList(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s),
                queue = queue,
                currentEpisodeId = state.currentEpisodeId,
                display = viewModel.episodeDisplay,
                swipeLeft = state.queueSwipeLeft,
                swipeRight = state.queueSwipeRight,
                onPlay = { q -> if (q.episodeId == state.currentEpisodeId) onOpenPlayer() else viewModel.playQueued(q) },
                onSwipe = { ep, action -> viewModel.onQueueSwipe(ep, action) },
                onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                onClear = { confirmClear = true },
                showHeader = false,
                contentPadding = PaddingValues(bottom = Spacing.miniPlayerReserve),
                metaFor = { q ->
                    val mark = marks[q.episodeId]
                    when {
                        q.episodeId != state.currentEpisodeId && mark?.isFinished == true -> "přehráno"
                        else -> remainingMs(q)?.let { ms ->
                            val started = (q.episodeId == state.currentEpisodeId && state.positionMs > 0) || (mark?.posMs ?: 0L) > 0L
                            if (started) "zbývá ${durationLabel(ms)}" else durationLabel(ms)
                        }
                    }
                },
                progressFor = { q ->
                    if (state.isActive && q.episodeId == state.currentEpisodeId && state.durationMs > 0) {
                        state.positionMs.toFloat() / state.durationMs
                    } else marks[q.episodeId]?.takeIf { it.durMs > 0 && !it.isFinished }?.let { it.posMs.toFloat() / it.durMs }
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Vymazat frontu?") },
            text = { Text("Odebere ${dilu(queue.size)} z fronty. Stažené soubory ani pozice poslechu se nesmažou.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearQueue() }) { Text("Vymazat") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Zrušit") } },
        )
    }
}

/** „1 díl“, „3 díly“, „7 dílů“. */
internal fun dilu(n: Int): String = when {
    n == 1 -> "1 díl"
    n in 2..4 -> "$n díly"
    else -> "$n dílů"
}
