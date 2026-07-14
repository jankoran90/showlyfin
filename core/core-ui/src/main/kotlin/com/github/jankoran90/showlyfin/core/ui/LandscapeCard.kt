package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType

// ── PANORAMA (SHW-78): široké „Netflix" karty (UNISON) ──────────────────────────────
// LandscapeCard = kompaktní karta na šířku (16:9 backdrop + scrim s [titulek · rok · ČSFD]).
// LandscapeDetailCard = širší řádek s vždy viditelným krátkým popisem (plot) vedle obrázku.
// Obě čtou z theme (colorScheme/typography), obraz = backdrop → fallback poster.

private val LandscapeCardShape = RoundedCornerShape(14.dp)

/** Obrázek na šířku: preferuj backdrop (fanart), jinak poster (na výšku v landscape rámečku). */
private fun MediaItem.landscapeImageUrl(): String? = backdropUrl() ?: posterUrl("w500")

/**
 * Kompaktní karta na šířku (16:9). Backdrop vyplní kartu, spodní scrim nese [titulek · rok · ČSFD].
 * Drop-in vedle [PosterCard]/[MediaCard] — bere stejný [MediaItem].
 */
@Composable
fun LandscapeCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    csfdRating: Int? = null,
    enableCsfd: Boolean = true,
    inLibrary: Boolean = false,
    watched: Boolean = false,
    progress: Float? = null,
) {
    val lazyRating = rememberCsfdCardRating(item.imdbId, item.tmdbId, item.title, item.year)
    val rating = csfdRating ?: (if (enableCsfd) lazyRating else null)
    val image = item.landscapeImageUrl()
    val title = item.displayTitle
    val isShow = item.type != MediaType.MOVIE

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(LandscapeCardShape)
            .tvFocusable(shape = LandscapeCardShape),
        shape = LandscapeCardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                PosterShimmer(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isShow) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (inLibrary) InLibraryTitleBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            if (watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    item.year?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    if (rating != null) {
                        Spacer(Modifier.width(4.dp))
                        CsfdMiniBadge(rating = rating)
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Široká karta s vždy viditelným krátkým popisem. Vlevo backdrop (16:9), vpravo titulek + meta
 * (rok · ČSFD · stav) + český popis (líně přes [rememberCzechOverview]). Bere stejný [MediaItem].
 */
@Composable
fun LandscapeDetailCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    csfdRating: Int? = null,
    enableCsfd: Boolean = true,
    inLibrary: Boolean = false,
    watched: Boolean = false,
    progressText: String? = null,
) {
    val lazyRating = rememberCsfdCardRating(item.imdbId, item.tmdbId, item.title, item.year)
    val rating = csfdRating ?: (if (enableCsfd) lazyRating else null)
    val image = item.landscapeImageUrl()
    val title = item.displayTitle
    val isShow = item.type != MediaType.MOVIE

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(LandscapeCardShape)
            .tvFocusable(shape = LandscapeCardShape),
        shape = LandscapeCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(150.dp)
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    PosterShimmer(Modifier.fillMaxSize())
                    Icon(
                        imageVector = if (isShow) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
                if (progressText != null) {
                    // progress jako text v meta řádku; zde nic
                }
            }
            Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.year?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (rating != null) {
                        if (item.year != null) Text(" · ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "ČSFD $rating %",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (inLibrary) {
                        Text("  •  v knihovně", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (progressText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(progressText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                val fallback = item.overviewCz?.takeIf { it.isNotBlank() } ?: item.overview?.takeIf { it.isNotBlank() }
                val description = rememberCzechOverview(item.imdbId, item.tmdbId, item.title, item.titleCz, item.year, fallback)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
