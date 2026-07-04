package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.data.abs.model.Podcast

/** CHORUS Osa 2: delegát nad kanonickým [CoverCard] (odznak počtu nedoposlouchaných v overlay). */
@Composable
fun PodcastCard(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        title = podcast.title,
        subtitle = podcast.author,
        imageUrl = podcast.coverUrl,
        onClick = onClick,
        modifier = modifier,
        placeholder = Icons.Default.Podcasts,
        overlay = {
            if (podcast.numUnfinished > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(podcast.numUnfinished.toString())
                }
            }
        },
    )
}
