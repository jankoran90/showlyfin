package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.ui.tv.TvCardSize
import com.github.jankoran90.showlyfin.ui.tv.TvJellyfinItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvItemCard(
    item: TvJellyfinItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inLibrary: Boolean = true,
    cardSize: TvCardSize = TvCardSize.MEDIUM,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "tv-card-scale",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (focused) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "tv-card-title-alpha",
    )
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = if (focused) accent else Color.Transparent
    val elevation = if (focused) 18.dp else 4.dp

    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardSize.widthDp.dp)
            .aspectRatio(2f / 3f)
            .scale(scale)
            .shadow(elevation = elevation, shape = RoundedCornerShape(12.dp), clip = false)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .border(width = 3.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = item.name,
                    color = Color.White.copy(alpha = titleAlpha),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (inLibrary) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .background(accent.copy(alpha = titleAlpha), CircleShape)
                            .padding(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "V knihovně",
                            tint = Color.Black.copy(alpha = titleAlpha),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            if (inLibrary) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(accent, CircleShape)
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "V knihovně",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            item.progressPct?.let { pct ->
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}
