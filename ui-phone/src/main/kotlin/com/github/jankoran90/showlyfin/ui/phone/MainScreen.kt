package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.feature.discover.ui.DiscoverScreen
import com.github.jankoran90.showlyfin.feature.discover.ui.RdLibraryScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.LibraryRowsScreen
import com.github.jankoran90.showlyfin.feature.watchlist.ui.WatchlistScreen
import kotlinx.coroutines.launch

/** Pořadí + popisky podsekcí „Hlavní". Klíče = [ProfileConfig.Sections]. */
private val ALL_SUBSECTIONS = listOf(
    ProfileConfig.Sections.KNIHOVNA to "Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Objevit",
    ProfileConfig.Sections.NA_RD to "Na RD",
)

/**
 * Sekce „Hlavní" — horizontálně swipovatelné podsekce: Knihovna (Trakt pohled na Jellyfin),
 * Chci vidět (Watchlist), Objevit (Discover), Na RD (filmy uložené na RealDebrid).
 *
 * @param visibleSubsections klíče viditelných podsekcí (Plan PROFILES 1E). Prázdné = všechny.
 * @param onTraktItemClick otevři bohatý Trakt/TMDB detail
 * @param onJellyfinItemClick otevři Jellyfin kartu (fallback pro položky bez TMDB matche)
 * @param onOpenLibrary otevři celou Jellyfin knihovnu (drill-down z řady Knihovny)
 */
@Composable
fun MainScreen(
    onTraktItemClick: (MediaItem) -> Unit,
    onJellyfinItemClick: (jellyfinId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    modifier: Modifier = Modifier,
    visibleSubsections: List<String> = emptyList(),
) {
    val tabs = ALL_SUBSECTIONS
        .filter { visibleSubsections.isEmpty() || it.first in visibleSubsections }
        .ifEmpty { ALL_SUBSECTIONS }
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color(0xFF1A1A2E),
            contentColor = Color.White,
        ) {
            tabs.forEachIndexed { index, (_, title) ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (tabs[page].first) {
                ProfileConfig.Sections.KNIHOVNA -> LibraryRowsScreen(
                    onItemClick = { media, jellyfinId ->
                        if (media != null) onTraktItemClick(media) else onJellyfinItemClick(jellyfinId)
                    },
                    onOpenLibrary = onOpenLibrary,
                    modifier = Modifier.fillMaxSize(),
                )
                ProfileConfig.Sections.CHCI_VIDET -> WatchlistScreen(
                    onItemClick = { item, jellyfinId ->
                        if (jellyfinId != null) onJellyfinItemClick(jellyfinId) else onTraktItemClick(item)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ProfileConfig.Sections.OBJEVIT -> DiscoverScreen(
                    onItemClick = { item, jellyfinId ->
                        if (jellyfinId != null) onJellyfinItemClick(jellyfinId) else onTraktItemClick(item)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> RdLibraryScreen(
                    onItemClick = onTraktItemClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
