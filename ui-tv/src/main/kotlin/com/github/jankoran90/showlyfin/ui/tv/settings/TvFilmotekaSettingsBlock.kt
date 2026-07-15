package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaSettingsViewModel

/**
 * CINEMATHEQUE (SHW-90) — blok „Filmotéka" v TV Nastavení: 4 toggly zdrojů, výchozí osa (Žánr | Země) a
 * rozbalovací seznam regionů pro osu Země (F2). Vzor [TvContentSettingsBlocks]. „Ostatní" fallback se
 * netogluje (zobrazuje se vždy).
 */
@Composable
fun TvFilmotekaSettingsBlock(vm: TvFilmotekaSettingsViewModel = hiltViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val axis by vm.defaultAxis.collectAsStateWithLifecycle()
    val allSort by vm.allSort.collectAsStateWithLifecycle()
    val enabledRegions by vm.enabledRegions.collectAsStateWithLifecycle()
    TvSettingsBlock(title = "Filmotéka") {
        TvToggleRow(
            label = "Jellyfin knihovna",
            subtitle = "Filmy a seriály z tvých Jellyfin knihoven",
            checked = FilmotekaSource.JELLYFIN in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.JELLYFIN, it) },
        )
        TvToggleRow(
            label = "Zapamatované zdroje",
            subtitle = "Tituly s uloženým zdrojem přehrávání",
            checked = FilmotekaSource.WORKING in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.WORKING, it) },
        )
        TvToggleRow(
            label = "Trakt watchlist",
            subtitle = "Filmy a seriály z tvého Trakt watchlistu",
            checked = FilmotekaSource.TRAKT_WATCHLIST in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.TRAKT_WATCHLIST, it) },
        )
        TvToggleRow(
            label = "Oblíbené",
            subtitle = "Filmy přidané mezi oblíbené",
            checked = FilmotekaSource.FAVORITES in sources,
            onCheckedChange = { vm.setSource(FilmotekaSource.FAVORITES, it) },
        )
        TvOptionStepperRow(
            label = "Výchozí osa",
            subtitle = "Podle čeho se Filmotéka po otevření přeskupí",
            options = listOf(FilmotekaAxis.ALL, FilmotekaAxis.GENRE, FilmotekaAxis.COUNTRY),
            selected = axis,
            labelOf = ::axisLabel,
            onSelect = vm::setDefaultAxis,
        )
        TvOptionStepperRow(
            label = "Řazení řady „Vše\"",
            subtitle = "Jak seřadit plochý výpis v ose Vše",
            options = listOf(FilmotekaAllSort.RECENT, FilmotekaAllSort.ALPHABETICAL),
            selected = allSort,
            labelOf = ::allSortLabel,
            onSelect = vm::setAllSort,
        )
        RegionSection(enabledRegions = enabledRegions, onToggle = vm::setRegion)
    }
}

/** Rozbalovací seznam regionů pro osu Země. „Ostatní" (fallback) se netogluje — zobrazuje se vždy. */
@Composable
private fun RegionSection(
    enabledRegions: Set<CinematographyRegion>,
    onToggle: (CinematographyRegion, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Kinematografie (osa Země)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Které regionální řady zobrazit v ose Země",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column {
            CinematographyRegion.entries
                .filter { it != CinematographyRegion.OSTATNI }
                .forEach { region ->
                    TvToggleRow(
                        label = region.label,
                        checked = region in enabledRegions,
                        onCheckedChange = { onToggle(region, it) },
                    )
                }
        }
    }
}

private fun axisLabel(axis: FilmotekaAxis): String = when (axis) {
    FilmotekaAxis.ALL -> "Vše"
    FilmotekaAxis.GENRE -> "Žánr"
    FilmotekaAxis.COUNTRY -> "Země"
}

private fun allSortLabel(sort: FilmotekaAllSort): String = when (sort) {
    FilmotekaAllSort.RECENT -> "Nedávno přidané"
    FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
}
