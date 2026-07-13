package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.feature.detail.DetailActionsPlacement
import com.github.jankoran90.showlyfin.feature.detail.TvDetailLayout
import com.github.jankoran90.showlyfin.ui.phone.DetailPrefsState
import com.github.jankoran90.showlyfin.ui.phone.DetailPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.ParentalPrefsViewModel

/** Styly karet nabízené pro sekce detailu (plakát/fanart/fanart+popis). */
internal val DETAIL_SECTION_STYLES = listOf(
    HomeCardStyle.POSTER,
    HomeCardStyle.LANDSCAPE,
    HomeCardStyle.FANART_DETAIL,
)

/** Řazení nabízené pro sekce režisér/studio v detailu. */
private val DETAIL_SECTION_SORTS = listOf(
    HomeRowSort.DEFAULT,
    HomeRowSort.RATING,
    HomeRowSort.YEAR_DESC,
    HomeRowSort.ALPHA,
)

/**
 * TENFOOT — blok „Detail obsahu" TV Nastavení (extrahováno z TvSettingsScreen kvůli stropu 600ř).
 * COUCH (SHW-88) přidává řazení sekcí + filtr „jen vydané".
 */
@Composable
fun TvDetailContentBlock(detail: DetailPrefsState, detailPrefs: DetailPrefsViewModel) {
    TvSettingsBlock(title = "Detail obsahu") {
        TvOptionStepperRow(
            label = "Rozvržení detailu",
            subtitle = "Immersive (blok přes fanart) nebo klasický hero pruh nahoře",
            options = TvDetailLayout.entries.toList(),
            selected = detail.tvLayout,
            labelOf = { it.label },
            onSelect = detailPrefs::setTvLayout,
        )
        TvToggleRow(
            label = "Auto-kompakt popisu",
            subtitle = "Zkrátit popis tak, aby první řada obsahu byla vidět bez scrollu",
            checked = detail.plotAutoCompact,
            onCheckedChange = detailPrefs::setPlotAutoCompact,
        )
        TvOptionStepperRow(
            label = "Počet řádků popisu",
            subtitle = "Když je auto-kompakt vypnutý — pevný počet řádků",
            options = DetailPrefsViewModel.PLOT_LINE_OPTIONS,
            selected = detail.plotLines,
            labelOf = { if (it <= 0) "Bez omezení" else "$it řádků" },
            onSelect = detailPrefs::setPlotLines,
        )
        TvOptionStepperRow(
            label = "Umístění tlačítek",
            subtitle = "Blok akcí nad popisem, nebo pod ním (immersive layout)",
            options = DetailActionsPlacement.entries.toList(),
            selected = detail.actionsPlacement,
            labelOf = { it.label },
            onSelect = detailPrefs::setActionsPlacement,
        )
        TvToggleRow(
            label = "Tvůrci",
            subtitle = "Pás herců a režie + Scénář/Kamera/Žánry",
            checked = detail.showCreators,
            onCheckedChange = detailPrefs::setCreators,
        )
        TvToggleRow(
            label = "Sezóny a epizody",
            subtitle = "U seriálu výběr sezóny a seznam epizod v detailu",
            checked = detail.showSeasons,
            onCheckedChange = detailPrefs::setSeasons,
        )
        TvToggleRow(
            label = "Kolekce",
            subtitle = "Další díly ságy / kolekce",
            checked = detail.showCollections,
            onCheckedChange = detailPrefs::setCollections,
        )
        TvToggleRow(
            label = "Od stejného režiséra",
            checked = detail.showDirector,
            onCheckedChange = detailPrefs::setDirector,
        )
        TvToggleRow(
            label = "Od stejného studia",
            checked = detail.showStudio,
            onCheckedChange = detailPrefs::setStudio,
        )
        TvOptionStepperRow(
            label = "Styl karet sekcí",
            subtitle = "Kolekce / režisér / studio: plakát, fanart nebo fanart s popisem",
            options = DETAIL_SECTION_STYLES,
            selected = detail.sectionStyle,
            labelOf = { it.label },
            onSelect = detailPrefs::setSectionStyle,
        )
        TvOptionStepperRow(
            label = "Řazení sekcí",
            subtitle = "Režisér / studio: pořadí titulů v pásu (projeví se po znovuotevření detailu)",
            options = DETAIL_SECTION_SORTS,
            selected = detail.sectionSort,
            labelOf = { it.label },
            onSelect = detailPrefs::setSectionSort,
        )
        TvToggleRow(
            label = "Jen vydané tituly",
            subtitle = "V sekcích režisér/studio skryj filmy s premiérou v budoucnu",
            checked = detail.releasedOnly,
            onCheckedChange = detailPrefs::setReleasedOnly,
        )
    }
}

/**
 * COUCH (SHW-88) — blok „Rodičovská kontrola": věkový strop obsahu pro OBJEVOVACÍ plochy dětského
 * profilu (doporučení / trendy / populární / Trakt / hledání). Jellyfin knihovna se NEfiltruje. Strop
 * se automaticky odvodí i z Jellyfin parental rating; tady je explicitní per-profil volba (nejpřísnější
 * z obou platí).
 */
@Composable
fun TvParentalSettingsBlock(parental: ParentalPrefsViewModel = hiltViewModel()) {
    val state by parental.state.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Rodičovská kontrola") {
        TvOptionStepperRow(
            label = "Věkový strop obsahu",
            subtitle = effectiveCapSubtitle(state.explicitCap, state.effectiveCap),
            options = ParentalPrefsViewModel.AGE_CAP_OPTIONS,
            selected = state.explicitCap,
            labelOf = { if (it <= 0) "Vypnuto (dle Jellyfinu)" else "do $it let" },
            onSelect = parental::setCap,
        )
        TvToggleRow(
            label = "Skrýt i neohodnocené",
            subtitle = "Přísný režim: skryj i tituly bez věkové certifikace (jen když je strop aktivní)",
            checked = state.hideUnrated,
            onCheckedChange = parental::setHideUnrated,
        )
    }
}

private fun effectiveCapSubtitle(explicit: Int, effective: Int?): String = when {
    effective == null -> "Vypnuto — zobrazí se vše"
    explicit > 0 && effective == explicit -> "Aktivní: do $effective let (skrývá se napříč doporučeními a hledáním)"
    else -> "Aktivní: do $effective let (řízeno Jellyfinem)"
}
