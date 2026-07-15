package com.github.jankoran90.showlyfin.ui.tv.lapidary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.feature.discover.lapidary.LapidaryRail
import com.github.jankoran90.showlyfin.feature.discover.lapidary.TvLapidaryViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList

/**
 * LAPIDARY (SHW-96) — sekce „Vzácné klenoty": immersive řady ([TvRailList]) po zemích (JP/KR/CN/HK/TW/TH/IR).
 * Kriticky ceněné art-house/festival/klasika. Klik → detail; přidání do „chci vidět"/„oblíbené" spustí
 * auto-cache zdroje (S2) → pak hraje instantně. Vzor [com.github.jankoran90.showlyfin.ui.tv.filmoteka.TvFilmotekaScreen].
 */
@Composable
fun TvLapidaryScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (String) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvLapidaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        when {
            state.loading && state.rails.isEmpty() ->
                Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.rails.isEmpty() -> Centered {
                Text(
                    text = "Zatím nic — vzácné klenoty se teprve sbírají. Zkus to za chvíli.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            else -> {
                val rails = remember(state.rails) { state.rails.map { it.toTvRail() } }
                TvRailList(
                    rails = rails,
                    sectionTitle = "Vzácné klenoty",
                    immersive = immersive,
                    immersiveHeader = immersiveHeader,
                    onFocusItem = onFocusItem,
                    onItemClick = { item ->
                        val media = item.mediaItem
                        if (media != null) onOpenDetail(media)
                        else item.jellyfinId?.let(onOpenJellyfinDetail)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun LapidaryRail.toTvRail(): TvRail = TvRail(
    id = id,
    title = title,
    style = HomeCardStyle.POSTER,
    items = items,
    configId = id,
    showTitles = true,
    immersiveHeader = false,
)
