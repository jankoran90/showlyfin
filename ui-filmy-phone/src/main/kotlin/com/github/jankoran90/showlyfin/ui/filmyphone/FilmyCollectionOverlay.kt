package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaCollectionGroup
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.dilyLabel

/**
 * ATRIUM (SHW-118) — OBSAH sdružené kolekce. Karta kolekce v seznamu zastupuje své díly; klik na ni
 * otevře tohle: široká hlavička (backdrop + název) a mřížka dílů. Klik na díl pokračuje normálním
 * detailem s hledáním zdroje, takže se cesta k přehrání nikde nerozchází.
 *
 * PARITA s webem (`static/filmy/flatgrid.js` → `collectionCard`), který kolekci otevírá jako překryv
 * nad seznamem — ne jako další obrazovka v navigaci. Díly jsou už načtené v [FilmotekaCollectionGroup],
 * takže se nic nedotahuje a překryv naskočí okamžitě.
 */
@Composable
fun FilmyCollectionOverlay(
    group: FilmotekaCollectionGroup,
    onDismiss: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                CollectionHeader(group = group, onDismiss = onDismiss)
                CollectionMembers(group = group, onOpenDetail = onOpenDetail)
            }
        }
    }
}

/** Hlavička: široká grafika kolekce se ztmavením zdola, název + počet dílů, křížek na zavření. */
@Composable
private fun CollectionHeader(group: FilmotekaCollectionGroup, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 7f)
    ) {
        val art = group.backdropUrl ?: group.posterUrl
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrim zdola — text musí být čitelný i nad světlým plakátem.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    )
                )
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Zavřít")
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = dilyLabel(group.members.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Mřížka dílů — stejné karty jako ve Filmotéce, ať kolekce nevypadá jako cizí obrazovka. */
@Composable
private fun CollectionMembers(group: FilmotekaCollectionGroup, onOpenDetail: (MediaItem) -> Unit) {
    val cols = rememberGridColumnPref()
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(group.members, key = { it.tmdbId ?: it.imdbId ?: it.title }) { item ->
            MediaCard(item = item, onClick = { onOpenDetail(item) })
        }
    }
}
