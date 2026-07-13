package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.feature.detail.DetailUiState
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

private fun looksCzech(t: String?): Boolean =
    !t.isNullOrBlank() && t.any { it in "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" }

/**
 * TENFOOT (SHW-87) — nativní 10-foot tělo karty filmu/seriálu na TV. Volá se z [DetailScreen] ve TV
 * form-factoru; sdílené sheety (výběr zdroje, galerie, recenze, tvorba osoby) + playback signaling
 * zůstávají v DetailScreen (společné pro telefon i TV).
 *
 * Immersive hero (fixní nahoře — WS fáze: fanart nemizí při fokusu akce) + POD ním scrollovatelné
 * sekce plné parity ([TvDetailSections]: popis, Tvůrci, sezóny/epizody, kolekce, od režiséra/studia).
 */
@Composable
internal fun TvDetailBody(
    displayItem: MediaItem,
    displayTitle: String,
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlayJellyfin: ((String) -> Unit)?,
    onOpenReviews: () -> Unit,
    onCollectionPartClick: ((CollectionPart) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val backdropUrl = displayItem.backdropUrl("w1280")
    val genres = uiState.movieDetails?.genres?.map { it.name }
        ?: uiState.showDetails?.genres?.map { it.name }
        ?: displayItem.genres
    val endTime = endTimeLabel(uiState.movieDetails?.runtime)
    var plotExpanded by remember { mutableStateOf(false) }

    val hasReviews = uiState.csfdReviews.isNotEmpty()
    val hasGallery = uiState.csfdId != null && uiState.uploaderConfigured

    // Popis (fallback: český TMDB → ČSFD → TMDB → item).
    val tmdbCz = uiState.tmdbCzOverview
    val plot = tmdbCz?.takeIf { looksCzech(it) }
        ?: uiState.csfdPlot?.takeIf { it.isNotBlank() }
        ?: tmdbCz?.takeIf { it.isNotBlank() }
        ?: uiState.movieDetails?.overview
        ?: uiState.showDetails?.overview
        ?: displayItem.overview

    // Jsou pod hero nějaké sekce? Pokud ano, dej jim víc místa (nižší hero); jinak plný immersive hero.
    val hasSeasons = uiState.showSeasons && displayItem.type == MediaType.SHOW && uiState.seasons.isNotEmpty()
    val hasCreators = uiState.showCreators &&
        (uiState.cast.isNotEmpty() || uiState.directors.isNotEmpty() || uiState.writers.isNotEmpty() ||
            uiState.cinematographers.isNotEmpty() || !genres.isNullOrEmpty())
    val hasSections = hasCreators || hasSeasons ||
        (uiState.showCollections && (uiState.mergedCollection != null || uiState.collection != null)) ||
        (uiState.showDirector && uiState.directorMovies != null) ||
        (uiState.showStudio && uiState.studioMovies != null) ||
        !plot.isNullOrBlank()

    val scroll = rememberScrollState()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Fanart NESMÍ zabrat celou výšku — jinak fokus na sekce dole odscrolluje hero pryč. S obsahem
        // pod hero dej sekcím prostor (0.58), jinak plný immersive hero (0.70). Hero je FIXNÍ (mimo scroll).
        val heroHeight = maxHeight * (if (hasSections) 0.58f else 0.70f)
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                // Fanart (klik = ČSFD galerie, pokud dostupná) — fokusovatelný D-padem.
                Box(
                    Modifier
                        .matchParentSize()
                        .then(
                            if (hasGallery) Modifier
                                .clickable { viewModel.openGallery() }
                                .tvFocusable()
                            else Modifier,
                        ),
                ) {
                    if (backdropUrl != null) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = if (hasGallery) "Galerie" else null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
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

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zpět",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 48.dp, top = 27.dp)
                        .tvFocusBorder(shape = CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                        .size(28.dp),
                )
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
                            // Klik na ČSFD badge → recenze (sdílený sheet v DetailScreen).
                            CsfdRatingBadge(
                                rating = csfdRating,
                                big = true,
                                modifier = if (hasReviews) Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenReviews() }
                                    .tvFocusable(shape = RoundedCornerShape(6.dp)) else Modifier,
                            )
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

            // ── Sekce pod hero — JEN tahle část scrolluje (hero nahoře zůstává fixní) ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll),
            ) {
                TvDetailSections(
                    displayItem = displayItem,
                    uiState = uiState,
                    viewModel = viewModel,
                    genres = genres,
                    plot = plot,
                    plotExpanded = plotExpanded,
                    onTogglePlot = { plotExpanded = !plotExpanded },
                    onCollectionPartClick = onCollectionPartClick,
                )
            }
        }
    }
}
