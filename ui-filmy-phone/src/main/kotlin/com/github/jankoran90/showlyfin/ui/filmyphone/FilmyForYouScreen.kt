package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.feature.discover.foryou.ForYouViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Pro tebe" appky Filmy.
 *
 * MIRROR (user 2026-07-20) — 1:1 s Filmotékou: tentýž SDÍLENÝ [FilmyBrowseSection] (osy Vše/Žánr/Země, filtr
 * žánru+země, řazení osy „Vše", hledání vč. režie, mřížka/seznam, počítadlo) nad [ForYouViewModel.filmotekaState]
 * (kurátorská doporučení + per-profil akumulace přes backend). Filtry živé; akumulace beze změny. Klik → sdílený
 * DetailScreen (shell stack).
 */
@Composable
fun FilmyForYouScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: ForYouViewModel = hiltViewModel(),
) {
    val state by vm.filmotekaState.collectAsStateWithLifecycle()
    val viewMode by vm.browseViewMode.collectAsStateWithLifecycle()

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
    )
}
