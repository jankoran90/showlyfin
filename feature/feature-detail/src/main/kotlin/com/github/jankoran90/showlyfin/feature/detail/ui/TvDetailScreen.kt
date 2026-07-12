package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.detail.DetailUiState
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

private fun looksCzech(t: String?): Boolean =
    !t.isNullOrBlank() && t.any { it in "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" }

/**
 * TENFOOT (SHW-87) Fáze 2 — nativní 10-foot tělo karty filmu na TV. Volá se z [DetailScreen] ve TV
 * form-factoru; sdílené sheety (výběr zdroje, download menu, galerie) + playback signaling zůstávají
 * v DetailScreen (společné pro telefon i TV). Zde jen 10-foot vizuál: immersive fanart + akce + popis.
 *
 * Immersive hero (user 2026-07-12): full-bleed 16:9 fanart (ne úzká „nudle"); přes spodní gradient
 * titul+meta a HNED pod ním akční řada (auto-fokus na primární CTA); popis až pod fanartem.
 */
@Composable
internal fun TvDetailBody(
    displayItem: MediaItem,
    displayTitle: String,
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlayJellyfin: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val backdropUrl = displayItem.backdropUrl("w1280")
    val genres = uiState.movieDetails?.genres?.map { it.name }
        ?: uiState.showDetails?.genres?.map { it.name }
        ?: displayItem.genres
    val endTime = endTimeLabel(uiState.movieDetails?.runtime)
    var plotExpanded by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll),
    ) {
        // ── Immersive hero: fanart 16:9 přes celou šířku ──
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Spodní scrim → text čitelný a plynulý přechod do pozadí.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        1.0f to MaterialTheme.colorScheme.background,
                    )
                )
            )

            // Zpět (vlevo nahoře)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zpět",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 27.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .tvFocusBorder(shape = CircleShape)
                    .padding(8.dp)
                    .size(28.dp),
            )
            // „Skončí ve 21:00" (vpravo nahoře) — místo přeplácané lišty tlačítek na telefonu.
            endTime?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 48.dp, top = 27.dp),
                )
            }

            // Dole: titul + rok + ČSFD, žánry, akce (uvnitř fanart framu, pod názvem).
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    displayItem.year?.let {
                        Text("$it", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.85f))
                    }
                    val csfdRating = uiState.csfdRating
                    if (csfdRating != null) {
                        CsfdRatingBadge(rating = csfdRating, big = true)
                    } else {
                        displayItem.rating?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                                Text("%.1f".format(rating), style = MaterialTheme.typography.titleLarge, color = Color.White)
                            }
                        }
                    }
                }
                if (!genres.isNullOrEmpty()) {
                    Text(
                        text = genres.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TvDetailActions(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlayJellyfin = onPlayJellyfin,
                )
            }
        }

        // ── Popis pod fanartem (sbalený na N řádků + rozklik) ──
        val tmdbCz = uiState.tmdbCzOverview
        val plot = tmdbCz?.takeIf { looksCzech(it) }
            ?: uiState.csfdPlot?.takeIf { it.isNotBlank() }
            ?: tmdbCz?.takeIf { it.isNotBlank() }
            ?: uiState.movieDetails?.overview
            ?: uiState.showDetails?.overview
            ?: displayItem.overview
        if (!plot.isNullOrBlank()) {
            val collapsedLines = uiState.plotCollapsedLines.takeIf { it > 0 } ?: 3
            Column(Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (plotExpanded) Int.MAX_VALUE else collapsedLines,
                    overflow = if (plotExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Icon(
                    imageVector = if (plotExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (plotExpanded) "Sbalit" else "Zobrazit víc",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { plotExpanded = !plotExpanded }
                        .tvFocusBorder(shape = CircleShape)
                        .padding(6.dp)
                        .size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
