package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder

/**
 * TENFOOT (SHW-87) — jedna dlaždice v TV mřížce „Sleduj". `clickable` ji dělá D-pad-fokusovatelnou,
 * `tvFocusBorder` kreslí bílý prstenec ve fokusu (rukopis fleetu z přehrávače). Barvy/tvary čte z theme.
 */
@Composable
fun TvMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium   // tvar z theme (design guard: žádný inline RoundedCornerShape)
    Column(modifier = modifier.width(160.dp)) {
        AsyncImage(
            model = item.posterUrl("w342"),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clickable(onClick = onClick)
                .tvFocusBorder(shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // Celý titulek (až 2 řádky) + rok pod ním — na TV se dřív titulek na 1 řádek ořízl a rok chyběl.
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        item.year?.let { year ->
            Text(
                text = "$year",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 1.dp),
            )
        }
    }
}
