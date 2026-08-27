package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.foryou.CuratorRail
import com.github.jankoran90.showlyfin.feature.discover.foryou.ForYouBucketsViewModel
import com.github.jankoran90.showlyfin.feature.discover.foryou.ForYouViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Pro tebe" appky Filmy.
 *
 * MIRROR (user 2026-07-20) — 1:1 s Filmotékou: tentýž SDÍLENÝ [FilmyBrowseSection] (osy Vše/Žánr/Země, filtr
 * žánru+země, řazení osy „Vše", hledání vč. režie, mřížka/seznam, počítadlo) nad [ForYouViewModel.filmotekaState]
 * (kurátorská doporučení + per-profil akumulace přes backend). Filtry živé; akumulace beze změny. Klik → sdílený
 * DetailScreen (shell stack).
 *
 * **Kategorie (user 2026-07-31: „třídění podle filmů nedávno viděných a líbených filmů a nebo celkově vysoce
 * kladně hodnocených"):** ikona v liště přepne na rozdělení doporučení do řad, z nichž každá říká PROČ. Každá
 * řada dostane od mozku jen tu výseč vkusu, která ji definuje ([ForYouBucketsViewModel]) — „nedávno viděné"
 * tedy opravdu vychází z posledních filmů, ne z celého profilu. Volba přežije otočení displeje.
 */
@Composable
fun FilmyForYouScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: ForYouViewModel = hiltViewModel(),
    bucketsVm: ForYouBucketsViewModel = hiltViewModel(),
) {
    val state by vm.filmotekaState.collectAsStateWithLifecycle()
    val viewMode by vm.browseViewMode.collectAsStateWithLifecycle()
    var byCategory by rememberSaveable { mutableStateOf(false) }

    val categoryToggle: @Composable () -> Unit = {
        IconButton(onClick = { byCategory = !byCategory }) {
            Icon(
                if (byCategory) Icons.Rounded.ViewModule else Icons.Rounded.ViewAgenda,
                contentDescription = if (byCategory) "Zobrazit vše dohromady" else "Rozdělit podle kategorií",
                tint = if (byCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (byCategory) {
        FilmyForYouCategories(
            onMenu = onMenu,
            onOpenDetail = onOpenDetail,
            categoryToggle = categoryToggle,
            modifier = modifier,
            vm = bucketsVm,
        )
        return
    }

    FilmyBrowseSection(
        state = state,
        onMenu = onMenu,
        onOpenDetail = onOpenDetail,
        onAxis = vm::setAxis,
        onAllSort = vm::setAllSort,
        onToggleGenre = vm::toggleGenreFilter,
        onClearGenre = vm::clearGenreFilter,
        onToggleCountry = vm::toggleCountryFilter,
        onClearCountry = vm::clearCountryFilter,
        viewMode = viewMode,
        onToggleView = { vm.setBrowseViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) },
        emptyContent = {
            FilmyEmpty(
                icon = Icons.Rounded.Recommend,
                title = "Zatím žádná doporučení",
                text = "Kurátor tu skládá tipy podle tvého Trakt vkusu. Jestli je prázdno i po chvíli, přihlas se k Traktu v Nastavení a ohodnoť pár filmů.",
            )
        },
        modifier = modifier,
        extraActions = categoryToggle,
    )
}

/** Doporučení rozdělená do řad podle DŮVODU. Řady doskakují postupně — mozek je LLM, počítá desítky sekund. */
@Composable
private fun FilmyForYouCategories(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    categoryToggle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    vm: ForYouBucketsViewModel = hiltViewModel(),
    blocklistVm: FilmyBlocklistViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                // user 2026-08-27 („Jak tohle aktualizuji?") — do dneška to nešlo nijak. Tlačítko
                // vědomě obchází serverovou paměť, jinak by vrátilo tytéž tituly (v týdnu je výběr
                // stabilní). Stará dávka se nezahazuje, sesune se do historie pod čerstvou.
                IconButton(
                    enabled = !state.loading && !state.refreshing,
                    onClick = { vm.load(force = true) },
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Vybrat znovu",
                        tint = if (state.refreshing) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                categoryToggle()
            },
        ) {
            Column {
                Text(
                    text = "Pro tebe — po kategoriích",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val sub = when {
                    state.refreshing -> "Vybírám znovu… ${state.done}/${state.total}"
                    state.loading && state.total > 0 -> "Počítám… ${state.done}/${state.total} kategorií"
                    state.rails.isNotEmpty() -> "${state.rails.size} kategorií"
                    state.history.isNotEmpty() -> "dřívější výběr"
                    else -> null
                }
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.rails.isEmpty() && state.history.isEmpty() && state.loading -> CircularProgressIndicator()
                state.rails.isEmpty() && state.history.isEmpty() -> FilmyEmpty(
                    icon = Icons.Rounded.Recommend,
                    title = "Kategorie zatím nemají z čeho vyjít",
                    text = "Každá řada potřebuje svůj signál — něco zhlédnutého, ohodnoceného nebo často přehrávaného. " +
                        "Ohodnoť pár filmů v sekci Zhlédnuto a zkus to znovu.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rails, key = { it.id }) { rail ->
                        CuratorRailRow(rail = rail, onOpenDetail = onOpenDetail, onBlock = blocklistVm::block)
                    }
                    // user 2026-08-27 („ale ukladejme historii") — dřívější dávky nezmizí, jen se
                    // sesunou sem pod čerstvou. Tituly, co jsou právě nahoře, se tu už neopakují.
                    if (state.history.isNotEmpty()) {
                        item(key = "history_header") {
                            Text(
                                text = "Dřívější výběr",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 2.dp),
                            )
                        }
                        items(state.history, key = { it.id }) { rail ->
                            CuratorRailRow(rail = rail, onOpenDetail = onOpenDetail, onBlock = blocklistVm::block)
                        }
                    }
                    if (state.loading) {
                        item(key = "progress") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Jedna řada doporučení. Křížek v rohu karty = „tohle mi nenabízej" (user 2026-08-27).
 * ZÁMĚRNĚ křížek, ne dlouhý stisk: ten už na kartě znamená hodnocení hvězdami a přebít ho by
 * uživateli sebral zavedené gesto.
 */
@Composable
private fun CuratorRailRow(
    rail: CuratorRail,
    onOpenDetail: (MediaItem) -> Unit,
    onBlock: (MediaItem) -> Unit,
) {
    Text(
        text = rail.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(rail.items, key = { it.stableKey() }) { mi ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(118.dp)
                    .height(215.dp),
            ) {
                MediaCard(item = mi, onClick = { onOpenDetail(mi) })
                if (mi.tmdbId != null) {
                    IconButton(
                        onClick = { onBlock(mi) },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Tohle mi nenabízej",
                            tint = Color.White,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .padding(3.dp)
                                .size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
