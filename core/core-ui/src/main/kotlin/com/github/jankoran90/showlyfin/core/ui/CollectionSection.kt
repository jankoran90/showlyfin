package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class CollectionPart(
    val key: String,
    val tmdbId: Long?,
    val jellyfinId: String?,
    val title: String,
    val posterUrl: String?,
    val year: String?,
    val watched: Boolean = false,
    // CANVAS (SHW-47) D: pro řazení karet (hodnocení = TMDB vote, oblíbenost = TMDB popularity)
    // + ≤4 žánrové štítky na kartě (z genre_ids přes statickou mapu).
    val rating: Float? = null,
    val popularity: Float? = null,
    val genres: List<String> = emptyList(),
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
            CollectionPartCard(part = part, onClick = { onPartClick(part) }, modifier = Modifier.width(110.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * CANVAS (SHW-47) C: vertikální mřížka karet kolekce — pro celoobrazovkovou „Tvorba" (filmografie),
 * která musí jít scrollovat (na rozdíl od bottom-sheetu). Reuse kanonické [CollectionPartCard].
 */
@Composable
fun CollectionGrid(
    parts: List<CollectionPart>,
    onPartClick: (CollectionPart) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    if (parts.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Pro tuto osobu nemáme žádné filmy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(parts, key = { it.key }) { part ->
            CollectionPartCard(part = part, onClick = { onPartClick(part) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CollectionPartCard(part: CollectionPart, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PosterCard(
        posterUrl = part.posterUrl,
        title = part.title,
        year = part.year,
        onClick = onClick,
        modifier = modifier,
        tmdbId = part.tmdbId,
        csfdYear = part.year?.take(4)?.toIntOrNull(),
        inLibrary = part.jellyfinId != null,
        watched = part.watched,
    )
}
