package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

data class CollectionPart(
    val key: String,
    val tmdbId: Long?,
    val jellyfinId: String?,
    val title: String,
    val posterUrl: String?,
    val year: String?,
    val watched: Boolean = false,
)

data class MediaCollection(
    val name: String,
    val parts: List<CollectionPart>,
)

@Composable
fun CollectionSection(
    collection: MediaCollection,
    excludeKey: String?,
    onPartClick: (CollectionPart) -> Unit,
) {
    val parts = collection.parts.filter { it.key != excludeKey }
    if (parts.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Text(
        text = collection.name,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(parts) { part ->
            CollectionPartCard(part = part, onClick = { onPartClick(part) })
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CollectionPartCard(part: CollectionPart, onClick: () -> Unit) {
    val inLibrary = part.jellyfinId != null
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
            .tvFocusable(shape = RoundedCornerShape(8.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (part.posterUrl != null) {
                AsyncImage(
                    model = part.posterUrl,
                    contentDescription = part.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (inLibrary) {
                InLibraryBadge(modifier = Modifier.align(Alignment.TopEnd))
            }
            if (part.watched) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = part.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (part.watched) {
                InLibraryTitleBadgeSpacer()
                WatchedTitleBadge()
            }
            if (inLibrary) {
                InLibraryTitleBadgeSpacer()
                InLibraryTitleBadge()
            }
        }
        part.year?.takeIf { it.isNotBlank() }?.let { year ->
            Text(
                text = year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
