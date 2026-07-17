package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.ui.phone.DetailPrefsViewModel

/**
 * CELLULOID (SHW-98) M2.7 Settings parita vlna 2 — blok „Detail obsahu" v Nastavení Filmy.
 * Reuse SDÍLENÉHO [DetailPrefsViewModel] (respektuje ho sdílený DetailScreen) → volby 1:1 s TV
 * `TvDetailContentBlock` MINUS TV-only `tvLayout`/`actionsPlacement` (telefon má vlastní rozvržení).
 * Touch ovladače ([FilmySettingRows]).
 */
private val SECTION_STYLES = listOf(HomeCardStyle.POSTER, HomeCardStyle.LANDSCAPE, HomeCardStyle.FANART_DETAIL)
private val SECTION_SORTS = listOf(HomeRowSort.DEFAULT, HomeRowSort.RATING, HomeRowSort.YEAR_DESC, HomeRowSort.ALPHA)

@Composable
fun FilmyDetailContentSection(vm: DetailPrefsViewModel = hiltViewModel()) {
    val detail by vm.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Detail obsahu")
        SettingSwitchRow(
            title = "Auto-kompakt popisu",
            subtitle = "Zkrátit popis tak, aby první řada obsahu byla vidět bez scrollu",
            checked = detail.plotAutoCompact,
            onCheckedChange = vm::setPlotAutoCompact,
        )
        SettingChips(
            label = "Počet řádků popisu",
            subtitle = "Když je auto-kompakt vypnutý — pevný počet řádků",
            options = DetailPrefsViewModel.PLOT_LINE_OPTIONS,
            selected = detail.plotLines,
            labelOf = { if (it <= 0) "Bez omezení" else "$it řádků" },
            onSelect = vm::setPlotLines,
        )
        SettingSwitchRow(
            title = "Tvůrci",
            subtitle = "Pás herců a režie + Scénář/Kamera/Žánry",
            checked = detail.showCreators,
            onCheckedChange = vm::setCreators,
        )
        SettingSwitchRow(
            title = "Sezóny a epizody",
            subtitle = "U seriálu výběr sezóny a seznam epizod v detailu",
            checked = detail.showSeasons,
            onCheckedChange = vm::setSeasons,
        )
        SettingSwitchRow(
            title = "Kolekce",
            subtitle = "Další díly ságy / kolekce",
            checked = detail.showCollections,
            onCheckedChange = vm::setCollections,
        )
        SettingSwitchRow(
            title = "Od stejného režiséra",
            checked = detail.showDirector,
            onCheckedChange = vm::setDirector,
        )
        SettingSwitchRow(
            title = "Od stejného studia",
            checked = detail.showStudio,
            onCheckedChange = vm::setStudio,
        )
        SettingChips(
            label = "Styl karet sekcí",
            subtitle = "Kolekce / režisér / studio: plakát, fanart nebo fanart s popisem",
            options = SECTION_STYLES,
            selected = detail.sectionStyle,
            labelOf = { it.label },
            onSelect = vm::setSectionStyle,
        )
        SettingChips(
            label = "Řazení sekcí",
            subtitle = "Režisér / studio: pořadí titulů v pásu (projeví se po znovuotevření detailu)",
            options = SECTION_SORTS,
            selected = detail.sectionSort,
            labelOf = { it.label },
            onSelect = vm::setSectionSort,
        )
        SettingSwitchRow(
            title = "Jen vydané tituly",
            subtitle = "V sekcích režisér/studio skryj filmy s premiérou v budoucnu",
            checked = detail.releasedOnly,
            onCheckedChange = vm::setReleasedOnly,
        )
    }
}
