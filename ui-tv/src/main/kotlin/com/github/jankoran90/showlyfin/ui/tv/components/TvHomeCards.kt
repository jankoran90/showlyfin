package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.CsfdMiniBadge
import com.github.jankoran90.showlyfin.core.ui.WatchedBadge
import com.github.jankoran90.showlyfin.core.ui.rememberCsfdCardRating
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/** Šířky karet na TV (10-foot, o něco větší než telefon). */
val TV_POSTER_WIDTH = 132.dp
val TV_LANDSCAPE_WIDTH = 300.dp

/**
 * TENFOOT — TV DOMOV REDESIGN. Karta jedné položky řady dle [HomeCardStyle]. Manuální
 * `tvFocusBorder`-PŘED-`clickable` vzor (ověřený z [TvPosterCard]; Material3 `Card` neobaluje fokus
 * spolehlivě). [focusRequester] = autofokus na první kartu první neprázdné řady.
 */
@Composable
fun TvHomeCard(
    item: HomeRowItem,
    style: HomeCardStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    // ČSFD % badge (parita s telefonem): líně přes sdílený provider zapojený v ShowlyfinTvApp; bez id/providera
    // = null → badge se neukáže. Overlay v pravém horním rohu plakátu (jako telefonní PosterCard).
    val csfd = rememberCsfdCardRating(item.mediaItem?.imdbId, item.mediaItem?.tmdbId, item.title, item.year)
    Box(modifier) {
        when (style) {
            HomeCardStyle.LANDSCAPE -> TvLandscapeCard(item, onClick, Modifier.width(TV_LANDSCAPE_WIDTH), focusRequester, showLabel, onLongClick)
            HomeCardStyle.COVER -> TvCoverCard(item, onClick, Modifier.width(TV_POSTER_WIDTH), focusRequester, showLabel, onLongClick)
            HomeCardStyle.POSTER -> TvPosterCard(
                posterUrl = item.posterUrl,
                title = item.title,
                year = item.year,
                onClick = onClick,
                modifier = Modifier.width(TV_POSTER_WIDTH),
                focusRequester = focusRequester,
                showLabel = showLabel,
                onLongClick = onLongClick,
            )
            HomeCardStyle.LIST -> TvListCard(item, csfd, onClick, Modifier.width(TV_LIST_WIDTH), focusRequester, onLongClick)
            HomeCardStyle.FANART_DETAIL -> TvDetailCard(item, csfd, onClick, Modifier.width(TV_DETAIL_WIDTH), focusRequester, onLongClick)
        }
        // Overlay ČSFD badge jen pro plakátové styly; LIST/FANART_DETAIL mají ČSFD přímo v textu.
        if (style == HomeCardStyle.POSTER || style == HomeCardStyle.COVER || style == HomeCardStyle.LANDSCAPE) {
            csfd?.let { CsfdMiniBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        }
    }
}

/** Fanart 16:9 (Netflix/Kodi styl): landscape → fallback poster; scrim název + (u epizody) S×E · popis + progress. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvLandscapeCard(
    item: HomeRowItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .aspectRatio(16f / 9f)
            .tvFocusBorder(shape = shape)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = item.landscapeUrl ?: item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (item.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
        if (showLabel) {
            CardScrim(Modifier.align(Alignment.BottomStart))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        CardProgress(item.progressPct, Modifier.align(Alignment.BottomStart))
    }
}

/** Čistý plakát 2:3 (COVER): název jen ve scrimu, vzdušná mřížka. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvCoverCard(
    item: HomeRowItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .aspectRatio(2f / 3f)
            .tvFocusBorder(shape = shape)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (item.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
        if (showLabel) {
            CardScrim(Modifier.align(Alignment.BottomStart))
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        CardProgress(item.progressPct, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun CardScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
    )
}

@Composable
private fun CardProgress(pct: Int?, modifier: Modifier = Modifier) {
    pct?.takeIf { it > 0 }?.let { p ->
        LinearProgressIndicator(
            progress = { p / 100f },
            modifier = modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
    }
}
