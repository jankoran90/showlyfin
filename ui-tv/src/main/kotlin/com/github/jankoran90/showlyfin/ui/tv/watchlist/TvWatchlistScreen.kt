package com.github.jankoran90.showlyfin.ui.tv.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistTab
import com.github.jankoran90.showlyfin.feature.watchlist.WatchlistViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.TvMediaCard

/**
 * TENFOOT (SHW-87) F3 — nativní 10-foot „Oblíbené" (Trakt watchlist) na TV. Sdílí telefonní
 * [WatchlistViewModel] (žádný TV-specifický wiring), výsledky = plakátová mřížka ze sdílené
 * [TvMediaCard] (stejný vzhled + fokusová záře jako domov/hledání); ťuk → nativní TV karta obsahu
 * (`WatchlistViewModel` dává plný [MediaItem] vč. tmdbId, takže se předá přímo bez stubu).
 *
 * Přepínač Filmy/Seriály = `selectTab`. Odebrání z watchlistu tu neřešíme (stejně jako telefon —
 * běží přes kartu obsahu).
 */
@Composable
fun TvWatchlistScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    // Autofokus na obsah: jakmile jsou položky, přeskoč fokus rovnou na první plakát (ne na Zpět/přepínač).
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(ui.items.size, ui.isLoading) {
        if (!ui.isLoading && ui.items.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().tvOverscan()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zpět",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .tvFocusBorder(shape = CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Text(
                text = "Oblíbené",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Přepínač Filmy / Seriály (jen když je uživatel přihlášen k Traktu — jinak nemá watchlist).
        if (ui.isLoggedIn) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                WatchlistTab.entries.forEach { tab ->
                    FilterChip(
                        selected = ui.activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = {
                            Text(
                                tabLabel(tab),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (ui.activeTab == tab) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        modifier = Modifier.tvFocusable(),
                    )
                }
            }
        }

        when {
            !ui.isLoggedIn -> CenteredHint("Přihlas se k Trakt v Nastavení / Účty a uvidíš tady své oblíbené")
            ui.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            ui.items.isEmpty() -> CenteredHint(
                if (ui.activeTab == WatchlistTab.MOVIES) "Zatím žádné filmy v oblíbených"
                else "Zatím žádné seriály v oblíbených",
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(ui.items, key = { _, it -> "${it.type}_${it.traktId}" }) { index, item ->
                    TvMediaCard(
                        item = item,
                        onClick = { onOpenDetail(item) },
                        focusRequester = if (index == 0) firstItemFocus else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CenteredHint(text: String) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun tabLabel(tab: WatchlistTab): String = when (tab) {
    WatchlistTab.MOVIES -> "Filmy"
    WatchlistTab.SHOWS -> "Seriály"
}
