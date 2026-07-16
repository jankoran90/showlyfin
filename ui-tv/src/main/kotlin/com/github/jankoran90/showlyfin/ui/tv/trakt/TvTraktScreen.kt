package com.github.jankoran90.showlyfin.ui.tv.trakt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRail
import com.github.jankoran90.showlyfin.feature.discover.trakt.TvTraktViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList

/**
 * COUCH (SHW-88) Fáze C — sekce „Trakt" na řádkovém modelu (sdílený [TvRailList], jako Domů/Knihovna):
 * immersive řady Watchlist / Zhlédnuto / Doporučeno + každý userův Trakt seznam. Klik na kartu → bohatý
 * Trakt/TMDB detail. Nepřihlášený / prázdné kategorie → jen prázdný stav (řady se vynechají ve VM).
 */
@Composable
fun TvTraktScreen(
    onOpenDetail: (MediaItem) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvTraktViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading && state.rows.isEmpty() ->
            Centered {
                Text(
                    text = if (state.loadingTotal > 0)
                        "Načítám Trakt… (${state.loadingDone} z ${state.loadingTotal} řad)"
                    else "Načítám Trakt…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        state.rows.isEmpty() -> Centered {
            Text(
                text = "Nic k zobrazení — přihlaš se k Traktu (Nastavení → Účty), nebo tu zatím nic není.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }

        else -> {
            val rails = remember(state.rows) { state.rows.map { it.toTvRail() } }
            TvRailList(
                rails = rails,
                sectionTitle = "Trakt",
                immersive = immersive,
                immersiveHeader = immersiveHeader,
                onFocusItem = onFocusItem,
                onItemClick = { item -> item.mediaItem?.let(onOpenDetail) },
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Trakt řada → sdílený [TvRail] (řádkový model). Styl POSTER, immersive hlavička řídí sekce. */
private fun TraktRail.toTvRail(): TvRail = TvRail(
    id = id,
    title = title,
    style = HomeCardStyle.POSTER,
    items = items,
    configId = id,
    showTitles = true,
    immersiveHeader = false,
)
