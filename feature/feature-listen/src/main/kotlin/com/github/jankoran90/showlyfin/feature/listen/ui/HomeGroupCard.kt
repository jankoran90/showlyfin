package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.core.ui.dilyCz
import com.github.jankoran90.showlyfin.feature.listen.HomeLayout
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel.ContinueItem

/**
 * Hlavní karta skupiny rozposlouchaných ([HomeEntry.Group]): obrázek série / pořadu, počet rozposlouchaných a
 * naposled poslouchaná položka. Ťuk rozbalí / sbalí, ▶ pokračuje naposled poslouchanou položkou.
 */
@Composable
internal fun HomeGroupCard(
    group: HomeEntry.Group,
    expanded: Boolean,
    layout: HomeLayout,
    /** Naposled poslouchaná položka skupiny je načtená v přehrávači. */
    latestLoaded: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val count = group.items.size
    val countLine = "Rozposlouchané: " + if (group.isBooks) knihyCz(count) else dilyCz(count)
    val latestTitle = when (val l = group.latest) {
        is ContinueItem.Book -> l.book.title
        is ContinueItem.Episode -> l.episode.title
    }
    val progress = when (val l = group.latest) {
        is ContinueItem.Book -> l.book.progress.toFloat()
        is ContinueItem.Episode -> l.progress
    }
    val placeholder = if (group.isBooks) Icons.Default.Headphones else Icons.Default.Podcasts
    if (layout.list) Column {
        HomeListCard(
            title = group.title,
            subtitle = countLine,
            description = "Naposled: $latestTitle",
            imageUrl = group.imageUrl,
            wideImage = group.wideImage,
            progress = progress,
            posMs = 0L,
            durMs = 0L,
            layout = layout,
            placeholder = placeholder,
            onClick = onToggle,
            isPlaying = latestLoaded,
            playing = playing,
            onPlayPause = onPlayPause,
        )
        ExpandHint(expanded, onToggle)
    } else {
        CoverCard(
            title = group.title,
            subtitle = "$countLine · naposled $latestTitle",
            imageUrl = group.imageUrl,
            onClick = onToggle,
            placeholder = placeholder,
            overlay = {
                if (progress > 0.001f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                }
                // Počet rozposlouchaných + šipka = je to skupina, ťuk ji rozbalí.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Sbalit" else "Rozbalit",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pauza" else "Pokračovat naposled poslouchaným",
                    )
                }
            },
        )
    }
}

@Composable
private fun ExpandHint(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        TextButton(onClick = onToggle) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            Text(if (expanded) "Sbalit" else "Rozbalit")
        }
    }
}

private fun knihyCz(count: Int): String = when {
    count == 1 -> "1 kniha"
    count in 2..4 -> "$count knihy"
    else -> "$count knih"
}
