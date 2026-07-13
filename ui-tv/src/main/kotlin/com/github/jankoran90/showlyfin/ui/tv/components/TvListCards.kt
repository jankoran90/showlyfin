package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CsfdMiniBadge
import com.github.jankoran90.showlyfin.core.ui.WatchedBadge
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/** Šířky pro řádkové/detailní styly řady (širší než plakátové). */
val TV_LIST_WIDTH = 360.dp
val TV_DETAIL_WIDTH = 440.dp

/**
 * TENFOOT — styl řady „Seznam" (LIST): malý plakát vlevo + název/rok/ČSFD vpravo. Kompaktní řádek pro
 * husté řady. ČSFD % je přímo v textu (řádek má místo), proto dispatcher u tohoto stylu overlay badge nekreslí.
 * Vzor `tvFocusBorder`-PŘED-`clickable` (fokus lift/záře) shodný s ostatními TV kartami.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvListCard(
    item: HomeRowItem,
    csfd: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(84.dp)
            .tvFocusBorder(shape = shape)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.year?.let {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                csfd?.let { CsfdMiniBadge(it) }
            }
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * TENFOOT — styl řady „Fanart + popis" (FANART_DETAIL): fanart 16:9 vlevo + název/rok/ČSFD/popis vpravo.
 * Širší editorial karta (Kodi wide list). Popis ze statického overviewCz/overview položky.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvDetailCard(
    item: HomeRowItem,
    csfd: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val overview = item.mediaItem?.let { it.overviewCz?.takeIf { o -> o.isNotBlank() } ?: it.overview }
        ?: item.subtitle
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(120.dp)
            .tvFocusBorder(shape = shape)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = item.landscapeUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
            )
            if (item.watched) WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.year?.let {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                csfd?.let { CsfdMiniBadge(it) }
            }
            overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
