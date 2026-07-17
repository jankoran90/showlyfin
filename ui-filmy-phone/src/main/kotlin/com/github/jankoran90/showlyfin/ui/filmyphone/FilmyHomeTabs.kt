package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { rails.size })
    val scope = rememberCoroutineScope()
    val selected = pagerState.currentPage.coerceIn(0, rails.lastIndex.coerceAtLeast(0))

    Column(modifier = modifier.fillMaxSize()) {
        // Taby = řady, SPLYNULÉ s lištou: ☰ + záložky v jednom tenkém pruhu (M2.2b vize, minimal chrome).
        FilmySectionBar(onMenu = onMenu) {
            ScrollableTabRow(
                selectedTabIndex = selected,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 4.dp, // ☰ vlevo už dává odstup
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
                    showDirector = true,
                )
            } else {
                FilmyJfRow(item = item, onClick = { onItemClick(item) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        }
    }
}
