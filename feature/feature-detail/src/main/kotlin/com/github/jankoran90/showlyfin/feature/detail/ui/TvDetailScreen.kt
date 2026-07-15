package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RateReview
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.github.jankoran90.showlyfin.feature.detail.TvDetailLayout

private fun tvLooksCzech(t: String?): Boolean =
    !t.isNullOrBlank() && t.any { it in "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" }

/** Popis (fallback: český TMDB → ČSFD → TMDB → item). */
private fun resolveTvPlot(uiState: DetailUiState, displayItem: MediaItem): String? {
    val tmdbCz = uiState.tmdbCzOverview
    return tmdbCz?.takeIf { tvLooksCzech(it) }
        ?: uiState.csfdPlot?.takeIf { it.isNotBlank() }
        ?: tmdbCz?.takeIf { it.isNotBlank() }
        ?: uiState.movieDetails?.overview
        ?: uiState.showDetails?.overview
        ?: displayItem.overview
}

/**
 * TENFOOT (SHW-87) — nativní 10-foot tělo karty filmu/seriálu na TV. Volá se z [DetailScreen] ve TV
 * form-factoru; sdílené sheety (výběr zdroje, galerie, recenze, tvorba osoby) + playback signaling
 * zůstávají v DetailScreen (společné pro telefon i TV).
 *
 * TV DETAIL REDESIGN (OTA 299): dvě rozvržení podle `uiState.tvDetailLayout`:
 *  - IMMERSIVE_OVERLAY (default) = blok název→popis→akce PŘES fanart vlevo + první řada obsahu bez scrollu.
 *  - CLASSIC_HERO = původní fixní hero pruh nahoře + scrollovatelné sekce pod ním.
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
    onOpenSettings: (() -> Unit)? = null,
) {
    // 4K TV: w1280 se na immersive fanartu roztaženém přes celou plochu pixeluje → plné rozlišení.
    val backdropUrl = displayItem.backdropUrl("original")
    val genres = uiState.movieDetails?.genres?.map { it.name }
        ?: uiState.showDetails?.genres?.map { it.name }
        ?: displayItem.genres
    val endTime = endTimeLabel(uiState.movieDetails?.runtime)
    var plotExpanded by remember { mutableStateOf(false) }

    val hasReviews = uiState.csfdReviews.isNotEmpty()
    val hasGallery = uiState.csfdId != null && uiState.uploaderConfigured
    val plot = resolveTvPlot(uiState, displayItem)

    // Jsou pod hero nějaké sekce? Pokud ano, dej jim víc místa (nižší hero); jinak plný immersive hero.
    val hasSeasons = uiState.showSeasons && displayItem.type == MediaType.SHOW && uiState.seasons.isNotEmpty()
    val hasCreators = uiState.showCreators &&
        (uiState.cast.isNotEmpty() || uiState.directors.isNotEmpty() || uiState.writers.isNotEmpty() ||
            uiState.cinematographers.isNotEmpty() || !genres.isNullOrEmpty())
    val hasContentRows = hasSeasons ||
        (uiState.showCollections && (uiState.mergedCollection != null || uiState.collection != null)) ||
        (uiState.showDirector && uiState.directorMovies != null) ||
        (uiState.showStudio && uiState.studioMovies != null)
    val hasSections = hasCreators || hasContentRows || !plot.isNullOrBlank()

    when (uiState.tvDetailLayout) {
        TvDetailLayout.IMMERSIVE_OVERLAY -> ImmersiveOverlayLayout(
            displayItem = displayItem,
            displayTitle = displayTitle,
            uiState = uiState,
            viewModel = viewModel,
            genres = genres,
            plot = plot,
            plotExpanded = plotExpanded,
            onTogglePlot = { plotExpanded = !plotExpanded },
            hasReviews = hasReviews,
            hasGallery = hasGallery,
            onOpenReviews = onOpenReviews,
            onPlayJellyfin = onPlayJellyfin,
            onCollectionPartClick = onCollectionPartClick,
            backdropUrl = backdropUrl,
            endTime = endTime,
            hasContentRows = hasContentRows,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        TvDetailLayout.CLASSIC_HERO -> ClassicHeroLayout(
            displayItem = displayItem,
            displayTitle = displayTitle,
            uiState = uiState,
            viewModel = viewModel,
            onBack = onBack,
            onPlayJellyfin = onPlayJellyfin,
            onOpenReviews = onOpenReviews,
            onCollectionPartClick = onCollectionPartClick,
            onOpenSettings = onOpenSettings,
            genres = genres,
            plot = plot,
            plotExpanded = plotExpanded,
            onTogglePlot = { plotExpanded = !plotExpanded },
            hasReviews = hasReviews,
            hasGallery = hasGallery,
            backdropUrl = backdropUrl,
            endTime = endTime,
            hasSections = hasSections,
            modifier = modifier,
        )
    }
}

/**
 * Původní layout (device-ověřený): immersive hero FIXNÍ nahoře (fanart nemizí při fokusu akce) + POD ním
 * scrollovatelné sekce plné parity. Zachováno jako přepínatelná varianta (Nastavení → Detail obsahu → Rozvržení).
 */
@Composable
private fun ClassicHeroLayout(
    displayItem: MediaItem,
    displayTitle: String,
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlayJellyfin: ((String) -> Unit)?,
    onOpenReviews: () -> Unit,
    onCollectionPartClick: ((CollectionPart) -> Unit)?,
    onOpenSettings: (() -> Unit)? = null,
    genres: List<String>?,
    plot: String?,
    plotExpanded: Boolean,
    onTogglePlot: () -> Unit,
    hasReviews: Boolean,
    hasGallery: Boolean,
    backdropUrl: String?,
    endTime: String?,
    hasSections: Boolean,
    modifier: Modifier = Modifier,
) {
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
                    TvDetailTitleRow(
                        title = displayTitle,
                        year = displayItem.year,
                        csfdRating = uiState.csfdRating,
                        fallbackRating = displayItem.rating,
                        hasReviews = hasReviews,
                        onOpenReviews = onOpenReviews,
                        hasGallery = hasGallery,
                        onOpenGallery = { viewModel.openGallery() },
                        onWhite = true,
                    )
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
                        onOpenSettings = onOpenSettings,
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
                    onTogglePlot = onTogglePlot,
                    onCollectionPartClick = onCollectionPartClick,
                    onPlayJellyfin = onPlayJellyfin,
                    showPlot = true,
                )
            }
        }
    }
}

/**
 * Sdílený řádek: název + rok + ČSFD hodnocení + fokusovatelné akce „Recenze" (F3 — viditelný spouštěč,
 * ne jen skrytý klik na badge) a „Galerie" (F3 — dřív jen skrytý klik na fanart). `onWhite` = text na fanartu.
 */
@Composable
internal fun TvDetailTitleRow(
    title: String,
    year: Int?,
    csfdRating: Int?,
    fallbackRating: Float?,
    hasReviews: Boolean,
    onOpenReviews: () -> Unit,
    hasGallery: Boolean,
    onOpenGallery: () -> Unit,
    onWhite: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (onWhite) Color.White else MaterialTheme.colorScheme.onBackground
    // KOLO2 (L): chipy Recenze/Galerie jako fokusová skupina → D-pad dolů z nich míří jednotně do akční
    // skupiny (která má enter=primární CTA), místo geometrického přeskoku na náhodné tlačítko.
    Row(
        modifier.focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        year?.let {
            Text("$it", style = MaterialTheme.typography.titleLarge, color = titleColor.copy(alpha = 0.85f))
        }
        if (csfdRating != null) {
            CsfdRatingBadge(rating = csfdRating, big = true)
        } else {
            fallbackRating?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                    Text("%.1f".format(rating), style = MaterialTheme.typography.titleLarge, color = titleColor)
                }
            }
        }
        // F3: viditelné fokusovatelné akce místo skrytých kliků.
        if (hasReviews) {
            DetailInlineChip(icon = Icons.Filled.RateReview, label = "Recenze", onClick = onOpenReviews, onWhite = onWhite)
        }
        if (hasGallery) {
            DetailInlineChip(icon = Icons.Filled.PhotoLibrary, label = "Galerie", onClick = onOpenGallery, onWhite = onWhite)
        }
    }
}

/** Malý fokusovatelný chip (ikona + text) pro sekundární akce v title-row (Recenze/Galerie). */
@Composable
private fun DetailInlineChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onWhite: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (onWhite) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (onWhite) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .tvFocusBorder(shape = shape)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}
