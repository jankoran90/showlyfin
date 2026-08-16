package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
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
    /** User (2026-08-15 16:49) — odznak „hraje" (aktuálně načtená epizoda v přehrávači). */
    isPlaying: Boolean = false,
    /** PROFIL (2026-08-16) — dlouhý stisk → „Sdílet s…" (celý zdroj epizody). null = nic (zkratka). */
    onLongClick: (() -> Unit)? = null,
) {
    CoverCard(
        title = episode.title,
        subtitle = sourceTitle,
        imageUrl = episode.imageUrl,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
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
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Hraje",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
        },
    )
}
