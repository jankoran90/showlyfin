package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource

private val SourceCoverShape = RoundedCornerShape(16.dp)

/**
 * PRESET (SHW-65): karta vlastního zdroje (YouTube kanál / RSS podcast) v sekci Poslech → Podcasty.
 * Vizuálně se od ABS podcastů odliší odznakem typu v rohu obálky. Tap → otevře epizody zdroje.
 */
@Composable
fun SourceCard(
    source: PodcastSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeIcon: ImageVector = if (source.type == "youtube") Icons.Default.PlayArrow else Icons.Default.Podcasts
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SourceCoverShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SourceCoverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (source.thumbnail != null) {
                AsyncImage(
                    model = source.thumbnail,
                    contentDescription = source.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Odznak typu (rozlišení od ABS podcastů).
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) {
                Icon(typeIcon, contentDescription = null, modifier = Modifier.padding(3.dp).size(16.dp))
            }
        }
        Text(
            text = source.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = if (source.type == "youtube") "YouTube kanál" else "Podcast",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
