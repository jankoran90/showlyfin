package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import kotlinx.coroutines.launch

/** Jedna řada telefonního domova (= jeden tab). Telefonní ekvivalent `TvRail` z ui-tv (varianta A). */
data class FilmyRail(
    val id: String,
    val title: String,
    val style: HomeCardStyle,
    val items: List<HomeRowItem>,
    val showTitles: Boolean = true,
)

/**
 * CELLULOID (SHW-98) Fáze 2 M2.2 — telefonní domov jako TRANSPOZICE TV.
 *
 * Klíč vize: TV řada (vodorovná) → telefonní TAB. Přepnutí na jinou řadu = swipe/tab (vodorovně),
 * obsah JEDNÉ řady = svislý seznam bohatých řádků ([MediaRow]: cover + název/rok/žánr/ČSFD + popis).
 * Taby splynou s lištou (transparentní [ScrollableTabRow]) → minimal chrome, obsah
 * dostane celý zbytek. Vzor tabů = audioman `HomeTabs` (ScrollableTabRow + HorizontalPager sdílí
 * PagerState). JF-only položka (bez `mediaItem`) → jednoduchý fallback řádek.
 */
@Composable
fun FilmyHomeTabbed(
    rails: List<FilmyRail>,
    onItemClick: (HomeRowItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { rails.size })
    val scope = rememberCoroutineScope()
    val selected = pagerState.currentPage.coerceIn(0, rails.lastIndex.coerceAtLeast(0))

    Column(modifier = modifier.fillMaxSize()) {
        // Taby = řady. Transparentní pruh → čte se jako součást horní lišty (splynutí = M2.2b doladění).
        ScrollableTabRow(
            selectedTabIndex = selected,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            divider = {},
        ) {
            rails.forEachIndexed { i, rail ->
                Tab(
                    selected = selected == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(rail.title, maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { rails[it].id },
        ) { page ->
            FilmyRailPage(rail = rails[page], onItemClick = onItemClick)
        }
    }
}

/** Obsah jednoho tabu = svislý seznam řádků jedné řady. */
@Composable
private fun FilmyRailPage(rail: FilmyRail, onItemClick: (HomeRowItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rail.items, key = { it.key }) { item ->
            val mi = item.mediaItem
            if (mi != null) {
                MediaRow(
                    item = mi,
                    onClick = { onItemClick(item) },
                    watched = item.watched,
                    genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                )
            } else {
                FilmyJfRow(item = item, onClick = { onItemClick(item) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        }
    }
}

/** JF-only položka (bez bohatého `mediaItem`) — cover + název + rok, bez ČSFD/popisu (nemáme data). */
@Composable
private fun FilmyJfRow(item: HomeRowItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .height(120.dp)
                .width(80.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.year?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
