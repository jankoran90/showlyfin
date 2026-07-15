package com.github.jankoran90.showlyfin.ui.tv.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRow
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList
import com.github.jankoran90.showlyfin.ui.tv.components.toHomeRowItem

/**
 * TENFOOT (SHW-87) / COUCH Fáze B — sekce „Knihovna" na řádkovém modelu (jako Domů). Každá Jellyfin
 * filmová/seriálová knihovna = jedna immersive řada ([TvRailList], sdílený render). Na konci řady dlaždice
 * „Zobrazit vše" → drill do plné mřížky knihovny ([onOpenLibrary] → `TvDestination.LibraryItems`).
 * Klik na kartu: bohatý Trakt/TMDB detail (mediaItem) nebo jednoduchá Jellyfin karta (jellyfinId).
 */
@Composable
fun TvLibraryScreen(
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryRowsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.isLoading && state.rows.isEmpty() ->
            Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }

        state.rows.isEmpty() -> Centered {
            Text(
                text = state.error
                    ?: "Žádné knihovny. Přihlas se k Jellyfinu ve verzi appky na telefonu — profil se sesynchronizuje i na TV.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }

        else -> {
            val rows = state.rows
            val rails = remember(rows) { rows.map { it.toTvRail() } }
            TvRailList(
                rails = rails,
                sectionTitle = "Knihovna",
                immersive = immersive,
                immersiveHeader = immersiveHeader,
                onFocusItem = onFocusItem,
                onItemClick = { item ->
                    val media = item.mediaItem
                    if (media != null) onOpenDetail(media)
                    else item.jellyfinId?.let(onOpenJellyfinDetail)
                },
                // „Zobrazit vše" → drill do plné mřížky knihovny (configId = libraryId).
                onShowAll = { libraryId ->
                    rows.firstOrNull { it.libraryId == libraryId }?.let { row ->
                        onOpenLibrary(row.libraryId, row.libraryName, row.collectionType)
                    }
                },
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Jellyfin knihovní řada → sdílený [TvRail] (řádkový model). `showAll` = drill do plné mřížky. */
private fun LibraryRow.toTvRail(): TvRail = TvRail(
    id = libraryId,
    title = libraryName,
    style = HomeCardStyle.POSTER,
    items = items.map { it.toHomeRowItem() },
    configId = libraryId,
    showTitles = true,
    immersiveHeader = false,
    showAll = true,
)
