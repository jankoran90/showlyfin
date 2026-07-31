package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.cardRatingKey
import com.github.jankoran90.showlyfin.feature.discover.trakt.WatchHistoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Telefonní sekce „Zhlédnuto" = historie sledování z Traktu (user 2026-07-31: „potřebuju někde v telefonu
 * vidět tu historii, abych mohl reagovat hned hodnocením — nechci hodnotit automaticky po koukání").
 *
 * Nejnověji sledované první. **Hodnotí se dlouhým stiskem** na kartě i na řádku (sdílený hvězdičkový
 * dialog přes `LocalUserRatingProvider`; hodnocení jde zároveň k nám i na Trakt). V liště je vidět,
 * kolik z historie už ohodnocené je — to je přesně ta smyčka, kvůli které obrazovka vznikla.
 */
@Composable
fun FilmyHistoryScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: WatchHistoryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    // Kolik z historie má vlastní hvězdy — provider zná jen UI vrstva, VM by na něj nedosáhl.
    // Náhradní prázdný tok drží počet composable volání stabilní i bez providera (podmíněné
    // `collectAsStateWithLifecycle` by porušilo pravidla Compose).
    val provider = LocalUserRatingProvider.current
    val fallbackRatings = remember { MutableStateFlow(emptyMap<String, Int>()) }
    val ratings by (provider?.ratings ?: fallbackRatings).collectAsStateWithLifecycle()
    val ratedCount = state.items.count { mi -> cardRatingKey(mi.tmdbId, mi.imdbId)?.let { ratings[it] } != null }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                FilmyViewToggle(viewMode) { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID }
            },
        ) {
            Column {
                Text(
                    text = "Zhlédnuto",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (state.items.isNotEmpty()) {
                    Text(
                        text = "${state.items.size} titulů · $ratedCount ohodnoceno · dlouhý stisk = hvězdy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading && state.items.isEmpty() -> CircularProgressIndicator()
                state.items.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.History,
                    title = "Historie je prázdná",
                    text = "Tady se objeví, cos dokoukal — historii vede Trakt. Aby do ní appka sama zapisovala, " +
                        "zapni v Nastavení → Přehrávač volbu \"Hlásit dokoukané filmy na Trakt\".",
                )
                viewMode == ViewMode.LIST -> FilmyMediaList(state.items, onOpenDetail)
                else -> FilmyMediaGrid(state.items, onOpenDetail)
            }
        }
    }
}
