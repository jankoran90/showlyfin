package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/**
 * TENFOOT — Netflix/Kodi immersive pozadí. Fokusovaná karta řídí fanart přes CELOU obrazovku (za
 * sidebarem i obsahem); nahoře volitelný metadata header (název/žánr/hodnocení/synopse). Sdílené pro
 * Domů + Objevovat. Zapínatelné ([HomeLayoutStore.immersiveBackground]).
 */
data class ImmersiveInfo(
    val backdropUrl: String?,
    val title: String,
    /** Řádek metadat: „2024 · Sci-fi · ★ 8.2". */
    val meta: String?,
    val overview: String?,
)

/** Full-screen fanart + čitelnostní scrim (vlevo tmavý → doprava průhledný, dole tmavý). Crossfade při změně. */
@Composable
fun TvImmersiveBackground(info: ImmersiveInfo?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Crossfade(
            targetState = info?.backdropUrl,
            animationSpec = tween(durationMillis = 450),
            label = "immersiveBackdrop",
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val bg = MaterialTheme.colorScheme.background
        // Levý gradient (text vlevo čitelný) + spodní gradient (řady dole čitelné).
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(bg.copy(alpha = 0.92f), bg.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, bg.copy(alpha = 0.55f), bg)),
            ),
        )
    }
}

/** Metadata header (Netflix hero) — vlevo nahoře nad řadami. Prázdné [info] → nic. */
@Composable
fun TvImmersiveHeader(info: ImmersiveInfo?, modifier: Modifier = Modifier) {
    info ?: return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = info.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        info.meta?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 760.dp).padding(top = 2.dp),
            )
        }
    }
}

/** Sestav [ImmersiveInfo] z položky řady domova (Trakt/TMDB [MediaItem] preferováno, jinak Jellyfin data). */
fun HomeRowItem.toImmersiveInfo(): ImmersiveInfo {
    val mi = mediaItem
    return ImmersiveInfo(
        backdropUrl = landscapeUrl ?: mi?.backdropUrl() ?: posterUrl,
        title = mi?.let { it.titleCz?.takeIf { t -> t.isNotBlank() } ?: it.title } ?: title,
        meta = mi?.metaLine() ?: year?.toString(),
        overview = mi?.let { it.overviewCz?.takeIf { o -> o.isNotBlank() } ?: it.overview },
    )
}

/** Sestav [ImmersiveInfo] z [MediaItem] (Objevovat). */
fun MediaItem.toImmersiveInfo(): ImmersiveInfo = ImmersiveInfo(
    backdropUrl = backdropUrl() ?: posterUrl(),
    title = titleCz?.takeIf { it.isNotBlank() } ?: title,
    meta = metaLine(),
    overview = overviewCz?.takeIf { it.isNotBlank() } ?: overview,
)

private fun MediaItem.metaLine(): String = listOfNotNull(
    year?.toString(),
    genres?.firstOrNull()?.replaceFirstChar { it.uppercase() },
    rating?.takeIf { it > 0f }?.let { "★ %.1f".format(it) },
).joinToString(" · ")
