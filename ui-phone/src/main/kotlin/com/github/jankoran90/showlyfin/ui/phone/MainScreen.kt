package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.feature.discover.ui.ForYouScreen
import com.github.jankoran90.showlyfin.feature.discover.ui.RdLibraryScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.LibraryRowsScreen
import com.github.jankoran90.showlyfin.feature.watchlist.history.HistoryScreen
import com.github.jankoran90.showlyfin.feature.watchlist.ui.WatchlistScreen
import kotlinx.coroutines.launch

/** Pořadí + popisky podsekcí „Hlavní". Klíče = [ProfileConfig.Sections]. */
private val ALL_SUBSECTIONS = listOf(
    ProfileConfig.Sections.KNIHOVNA to "Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Pro tebe",  // BESPOKE: interní klíč „objevit" ponechán (uložené layouty), obsah = kurátorská sekce
    ProfileConfig.Sections.HISTORIE to "Historie",
    ProfileConfig.Sections.NA_RD to "Na RD",
)

/**
 * Sekce „Hlavní" — horizontálně swipovatelné podsekce: Knihovna (Trakt pohled na Jellyfin),
 * Chci vidět (Watchlist), Pro tebe (kurátorská doporučení), Na RD (filmy uložené na RealDebrid).
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
    initialSubsection: String? = null,
    onSubsectionChange: (String) -> Unit = {},
) {
    // GLIDE: respektuj POŘADÍ podsekcí z profilu — `visibleSubsections` je už seřazené dle
    // subsectionOrder (ShowlyfinPhoneApp → orderedSubsections). Dřív se tu pořadí ZAHODILO
    // (filtr nad natvrdo seřazeným ALL_SUBSECTIONS) → uživatelský reorder se nikdy neprojevil.
    val labels = ALL_SUBSECTIONS.toMap()
    val tabs: List<Pair<String, String>> = visibleSubsections
        .mapNotNull { key -> labels[key]?.let { key to it } }
        .ifEmpty { ALL_SUBSECTIONS }
    // Plan PROFILES Fáze 4: „hlavní" podsekce profilu otevřená jako první (jinak Knihovna).
    val initialPage = initialSubsection
        ?.let { key -> tabs.indexOfFirst { it.first == key } }
        ?.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    val scope = rememberCoroutineScope()
    // GLIDE: hlas nahoru aktivní podsekci → Zpět z detailu přistane zpátky na téhle záložce
    // (dřív se MainScreen znovu vytvořil a spadl na první/výchozí podsekci).
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage, tabs) {
        tabs.getOrNull(pagerState.currentPage)?.let { onSubsectionChange(it.first) }
    }

    Column(modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                ProfileConfig.Sections.OBJEVIT -> ForYouScreen(
                    onItemClick = { item, jellyfinId ->
                        if (jellyfinId != null) onJellyfinItemClick(jellyfinId) else onTraktItemClick(item)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ProfileConfig.Sections.HISTORIE -> HistoryScreen(
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
