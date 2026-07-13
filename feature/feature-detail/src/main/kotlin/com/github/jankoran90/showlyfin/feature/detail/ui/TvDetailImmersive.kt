package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.detail.DetailActionsPlacement
import com.github.jankoran90.showlyfin.feature.detail.DetailUiState
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

/**
 * TV DETAIL REDESIGN (OTA 299) — immersive overlay layout (default).
 *
 * Fanart přes celou plochu; vlevo blok název → rok/ČSFD → žánry → popis (~50 % šířky, AUTO-KOMPAKT) → akce.
 * Levý horizontální gradient + spodní scrim pro čitelnost na světlém fanartu. ŽÁDNÁ šipka zpět (Back na
 * ovladači stačí). Pod hero blokem začíná první řada obsahu tak, aby padla do viewportu BEZ scrollu —
 * priorita = viditelnost první řady, proto se popis dynamicky zkracuje dle dostupné výšky.
 */
@Composable
internal fun ImmersiveOverlayLayout(
    displayItem: MediaItem,
    displayTitle: String,
    uiState: DetailUiState,
    viewModel: DetailViewModel,
    genres: List<String>?,
    plot: String?,
    plotExpanded: Boolean,
    onTogglePlot: () -> Unit,
    hasReviews: Boolean,
    hasGallery: Boolean,
    onOpenReviews: () -> Unit,
    onPlayJellyfin: ((String) -> Unit)?,
    onCollectionPartClick: ((CollectionPart) -> Unit)?,
    backdropUrl: String?,
    endTime: String?,
    hasContentRows: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Hero drží prostor pro fanart+blok; zbytek viewportu (0.40) je na první řadu obsahu bez scrollu.
        // Bez obsahových řad (samotný popis) dej hero víc plochy.
        val heroHeight = maxHeight * (if (hasContentRows) 0.60f else 0.72f)

        // AUTO-KOMPAKT: kolik řádků popisu se vejde do bloku, aby blok nepřerostl hero (a první řada zůstala vidět).
        // Rezerva = název (~56) + žánry (~28) + akce (~64) + spacing/padding (~84). lineHeight bodyLarge ~24 dp.
        val autoLines = if (uiState.plotAutoCompact) {
            val availDp = (heroHeight.value - 232f).coerceAtLeast(24f)
            (availDp / 24f).toInt().coerceIn(1, 14)
        } else {
            uiState.plotCollapsedLines.takeIf { it > 0 } ?: 5
        }

        // Fanart jako pozadí CELÉ plochy — NEOŘEZÁVAT (user feedback OTA 299): zachovat poměr stran,
        // zarovnat nahoře a do stran (FillWidth), klidně přesahuje dolů (sekce ho níž překryjí pozadím).
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            )
        }

        // Hero blok je FIXNÍ (mimo scroll) — fokus na akce (Přehrát…) NESMÍ scrollovat obrazovku (user
        // feedback OTA 299). Scrolluje jen část se sekcemi pod ním.
        Column(Modifier.fillMaxSize()) {
            // ── Hero stránka: gradienty (přes fanart) + overlay blok (roste jen při rozbaleném popisu) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = heroHeight),
            ) {
                // Levý horizontální gradient — čitelnost textu na světlém fanartu (bílé pozadí u animáků).
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(
                            0.0f to MaterialTheme.colorScheme.background,
                            0.35f to MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                            0.6f to Color.Transparent,
                        )
                    )
                )
                // Spodní scrim → plynulý přechod do pozadí (pod blok a k první řadě).
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            1.0f to MaterialTheme.colorScheme.background,
                        )
                    )
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

                // Blok nahoru-doleva (zarovnaný dolů v hero → popis končí na hraně hero, první řada hned pod ním).
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 20.dp),
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
                        modifier = Modifier.fillMaxWidth(0.62f),
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

                    val actions: @Composable () -> Unit = {
                        TvDetailActions(uiState = uiState, viewModel = viewModel, onPlayJellyfin = onPlayJellyfin)
                    }
                    val plotBlock: @Composable () -> Unit = {
                        if (!plot.isNullOrBlank()) {
                            ImmersivePlot(
                                plot = plot,
                                collapsedLines = autoLines,
                                expanded = plotExpanded,
                                onToggle = onTogglePlot,
                            )
                        }
                    }

                    if (uiState.actionsPlacement == DetailActionsPlacement.ABOVE_PLOT) {
                        actions()
                        plotBlock()
                    } else {
                        plotBlock()
                        actions()
                    }
                }
            }

            // ── Sekce POD hero — JEN tahle část scrolluje (popis je v hero bloku → showPlot=false).
            // První sekce = první řada obsahu, padne do zbytku viewportu bez scrollu.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
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
                    showPlot = false,
                )
            }
        }
    }
}

/** Popis v immersim bloku — ~50 % šířky, kompaktní; rozbalení (šipka) zůstává jen pro čtení celého textu. */
@Composable
private fun ImmersivePlot(
    plot: String,
    collapsedLines: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(0.5f)) {
        Text(
            text = plot,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = if (expanded) 30 else collapsedLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Sbalit" else "Zobrazit víc",
            tint = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onToggle)
                .tvFocusBorder(shape = CircleShape)
                .padding(6.dp)
                .size(28.dp),
        )
    }
}
