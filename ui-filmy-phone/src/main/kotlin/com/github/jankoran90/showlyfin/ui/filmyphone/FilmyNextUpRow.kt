package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/**
 * VESTIBUL (SHW-120, user 2026-08-24) — telefonní obdoba řady „Další díly" nad obsahem Filmotéky.
 * Široké karty (16:9 still dílu) s názvem seriálu a označením epizody, vodorovně rolovatelné —
 * stejná informace jako na webu i na TV, jen v proporcích telefonu.
 *
 * Karta ukazuje ikonu přehrání: cílem řady je pustit další díl, ne otevřít detail.
 */
@Composable
internal fun FilmyNextUpRow(
    items: List<HomeRowItem>,
    onClick: (HomeRowItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            text = "Další díly",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(items, key = { it.key }) { item -> NextUpCard(item = item, onClick = { onClick(item) }) }
        }
    }
}

@Composable
private fun NextUpCard(item: HomeRowItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(CardWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val art = item.landscapeUrl ?: item.posterUrl
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Ztmavení zdola drží ikonu čitelnou i nad světlým stillem.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
                    )
            )
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        item.subtitle?.let { sub ->
            Text(
                text = sub,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Šířka karty — na běžný telefon se vejdou dvě a kus třetí, takže je vidět, že řada pokračuje. */
private val CardWidth = 210.dp
