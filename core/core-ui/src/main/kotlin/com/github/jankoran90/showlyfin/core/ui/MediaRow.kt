package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType

/** VANTAGE (SHW-48) — výška coveru řádku; výška textového sloupce = výška coveru (popis se ořízne). */
private val RowCoverHeight = 120.dp
private val RowCoverWidth = 80.dp
private val RowCoverShape = RoundedCornerShape(10.dp)

/**
 * VANTAGE (SHW-48) — kanonická ŘÁDKOVÁ karta (UNISON), vedle [PosterCard] (mřížka). Cover vlevo,
 * vpravo titulek + meta (rok · ČSFD · stav) + český popis omezený výškou coveru. Sdílí ji list-mode
 * sekcí Objevit / Chci vidět / Historie. ČSFD se líně dotáhne přes [LocalCsfdRatingProvider]
 * (jako [PosterCard]), pokud není předané [csfdRating].
 */
@Composable
fun MediaRow(
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowCoverShape)
            .clickable(onClick = onClick)
            .tvFocusable()
            .padding(vertical = 2.dp),
    ) {
        val posterUrl = item.posterUrl("w342")
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .height(RowCoverHeight)
                .width(RowCoverWidth)
                .clip(RowCoverShape),
            contentAlignment = Alignment.Center,
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = if (item.type == MediaType.MOVIE) Icons.Default.Movie else Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.height(RowCoverHeight).fillMaxWidth()) {
            Text(
                text = item.titleCz?.takeIf { it.isNotBlank() } ?: item.title,
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
                    if (item.year != null) DotSeparator()
                    Text(
                        text = "ČSFD $rating %",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (progressText != null) {
                    DotSeparator()
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (watched) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Zhlédnuto",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (inLibrary) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "V knihovně",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val description = item.overviewCz?.takeIf { it.isNotBlank() }
                ?: item.overview?.takeIf { it.isNotBlank() }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun DotSeparator() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
