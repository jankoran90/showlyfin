package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode

/** Domů/„Pokračovat" — dlaždice pro rozposlouchanou direct epizodu (RSS/YouTube/ČT). Zrcadlo [AudiobookCard]. */
@Composable
fun ContinueEpisodeCard(
    episode: SourceEpisode,
    sourceTitle: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        title = episode.title,
        subtitle = sourceTitle,
        imageUrl = episode.imageUrl,
        onClick = onClick,
        modifier = modifier,
        placeholder = Icons.Default.Podcasts,
        overlay = {
            if (progress > 0.001f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                )
            }
        },
    )
}
