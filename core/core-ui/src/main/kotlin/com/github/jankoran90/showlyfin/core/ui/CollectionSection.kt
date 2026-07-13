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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle

data class CollectionPart(
    val key: String,
    val tmdbId: Long?,
    val jellyfinId: String?,
    val title: String,
    val posterUrl: String?,
    val year: String?,
    val watched: Boolean = false,
    // Fanart (16:9) pro landscape/detail styl sekcí; null → fallback na poster. Aditivní, telefon (POSTER) ignoruje.
    val backdropUrl: String? = null,
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
    // Styl karet sekce (kolekce/režisér/studio). Default POSTER = telefon i dosavadní vzhled beze změny;
    // TV nabízí LANDSCAPE (fanart) a FANART_DETAIL (fanart + popis). COVER/LIST degradují na plakát v pásu.
    style: HomeCardStyle = HomeCardStyle.POSTER,
) {
    val parts = collection.parts.filter { it.key != excludeKey }
    if (parts.isEmpty()) return
    // Šířka karty dle stylu (LazyRow = horizontální pás): plakát úzký, fanart širší, fanart+popis nejširší.
    val cardWidth = when (style) {
        HomeCardStyle.LANDSCAPE -> 210.dp
        HomeCardStyle.FANART_DETAIL -> 320.dp
        else -> 110.dp
    }
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
        items(parts, key = { it.key }) { part ->
            val mod = Modifier.width(cardWidth)
            when (style) {
                HomeCardStyle.LANDSCAPE ->
                    CollectionLandscapeCard(part = part, showPlot = false, onClick = { onPartClick(part) }, modifier = mod)
                HomeCardStyle.FANART_DETAIL ->
                    CollectionLandscapeCard(part = part, showPlot = true, onClick = { onPartClick(part) }, modifier = mod)
                else ->
                    CollectionPartCard(part = part, onClick = { onPartClick(part) }, modifier = mod)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

private val CollectionCardShape = RoundedCornerShape(14.dp)

/**
 * Karta sekce na šířku (16:9 fanart + scrim s titulkem/rokem/ČSFD). [showPlot] = pod obrázkem krátký český
 * popis (líně přes [rememberCzechOverview] dle tmdbId). Obraz = backdrop → fallback poster. Reuse core-ui
 * helperů (tvFocusable glow, CsfdMiniBadge, ČSFD/CZ providery), aby styl seděl s kartami domova.
 */
@Composable
private fun CollectionLandscapeCard(
    part: CollectionPart,
    showPlot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val year = part.year?.take(4)?.toIntOrNull()
    val rating = rememberCsfdCardRating(imdbId = null, tmdbId = part.tmdbId, title = part.title, year = year)
    val image = part.backdropUrl ?: part.posterUrl
    Column(modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CollectionCardShape)
                .tvFocusable(shape = CollectionCardShape),
            shape = CollectionCardShape,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = part.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    PosterShimmer(Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (part.jellyfinId != null) InLibraryTitleBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                if (part.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
                // COUCH (SHW-88): TMDB hodnocení ve volném rohu (okamžité, na rozdíl od líného ČSFD ve scrimu).
                part.rating?.takeIf { it > 0f }?.let {
                    TmdbMiniBadge(rating = it, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = part.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = true),
                        )
                        part.year?.let {
                            Spacer(Modifier.width(4.dp))
                            Text(it.take(4), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                        }
                        if (rating != null) {
                            Spacer(Modifier.width(4.dp))
                            CsfdMiniBadge(rating = rating)
                        }
                    }
                }
            }
        }
        if (showPlot) {
            val plot = rememberCzechOverview(null, part.tmdbId, part.title, null, year, null)
            if (!plot.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 0.dp),
                )
            }
        }
    }
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
