package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel

/**
 * Mini-player — kompaktní lišta nad spodní navigací; zobrazí se jen když něco hraje.
 * Klik → fullscreen player. Sdílí stav přes singleton connection (AudiobookPlayerViewModel).
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isListenSection: Boolean = true,
    viewModel: AudiobookPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    // „Resume" režim (nic nehraje, jen uložená fronta) ukazuj JEN v sekci Poslech.
    // Aktivní přehrávání ukazuj všude (standardní mini-player).
    val resumeMode = !state.isActive
    if (resumeMode && (queue.isEmpty() || !isListenSection)) return

    val first = queue.firstOrNull()
    val cover = if (resumeMode) first?.coverUrl else state.coverUrl
    val guest = if (resumeMode) first?.guest else state.guest
    val mainTitle = if (resumeMode) (first?.title ?: "Fronta") else state.title
    val subLine = if (resumeMode) {
        listOfNotNull(first?.podcastTitle?.takeIf { it.isNotBlank() }, "Fronta · ${queue.size} — klepni pro přehrání").joinToString(" · ")
    } else state.currentChapterTitle
    val startOrToggle = {
        if (resumeMode) first?.let { viewModel.playQueued(it) } else viewModel.playPause()
    }

    ListenExpressiveTheme {
        Row(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = { if (resumeMode) startOrToggle() else onExpand() })
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                guest?.takeIf { it.isNotBlank() && viewModel.episodeDisplay.highlightGuest }?.let { g ->
                    Text(
                        g,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    mainTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { startOrToggle() }) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pauza" else "Přehrát",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (!resumeMode) {
            val dur = state.durationMs.coerceAtLeast(1L)
            LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / dur).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
