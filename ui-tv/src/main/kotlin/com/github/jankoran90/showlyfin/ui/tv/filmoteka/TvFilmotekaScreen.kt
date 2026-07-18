package com.github.jankoran90.showlyfin.ui.tv.filmoteka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList
import com.github.jankoran90.showlyfin.ui.tv.components.TvSectionHeader

/**
 * CINEMATHEQUE (SHW-90) — sekce „Filmotéka": nahoře přepínač osy (Žánr | Země), pod ním immersive řady
 * ([TvRailList]) podle vybrané osy. Sjednocuje JF knihovnu, zapamatované zdroje, Trakt watchlist a Oblíbené
 * (dedup + věkový gate ve VM). Osa Žánr = řady dle žánru; osa Země (F2) = regionální „kinematografie".
 */
@Composable
fun TvFilmotekaScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (String) -> Unit,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvFilmotekaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // CONVERGE — při každém vstupu do sekce obnov výchozí osu (Nastavení → Filmotéka, default „Vše"). VM je
    // retained na úrovni shellu, takže bez tohoto by uvázlo runtime přepnutí osy (chip) z minulé návštěvy.
    LaunchedEffect(Unit) { viewModel.applyDefaultAxis() }

    // KÁNON (CONVERGE): osa Filmotéky (Vše | Žánr | Země) jako chipy VEDLE názvu sekce — ne ve vlastním Row
    // nad TvRailList (to tlačilo obsah dolů a osa byla vizuálně odtržená od titulku). V řadovém stavu je
    // hlavička uvnitř TvRailList (sectionActions), v prázdném/loading nad obsahem přes TvSectionHeader.
    val chips: @Composable () -> Unit = {
        AxisChips(
            axis = state.axis,
            allSort = state.allSort,
            onSelect = viewModel::setAxis,
            onAllSort = viewModel::setAllSort,
        )
    }

    if (state.rails.isNotEmpty()) {
        val rails = remember(state.rails) { state.rails.map { it.toTvRail() } }
        TvRailList(
            rails = rails,
            sectionTitle = "Filmotéka",
            immersive = immersive,
            immersiveHeader = immersiveHeader,
            onFocusItem = onFocusItem,
            onItemClick = { item ->
                val media = item.mediaItem
                if (media != null) onOpenDetail(media)
                else item.jellyfinId?.let(onOpenJellyfinDetail)
            },
            modifier = modifier.fillMaxSize(),
            sectionActions = { chips() },
        )
        return
    }

    Column(modifier.fillMaxSize().tvOverscan()) {
        TvSectionHeader(title = "Filmotéka", actions = { chips() })
        if (state.loading) {
            Centered { Text("Načítám…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Centered {
                Text(
                    text = "Zatím nic — zapni zdroje v Nastavení → Filmotéka, nebo přidej tituly do knihovny.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }
}

/**
 * Přepínač osy Filmotéky: Vše | Žánr | Země + pro osu „Vše" řazení Nedávno | Abecedně (parita s telefonem —
 * user 2026-07-18). Všechny chipy D-pad-fokusovatelné; přepnutí jen přeskupí bázi (bez fetch). Řazení se ukládá
 * per profil (sdílené s Nastavením přes [TvFilmotekaViewModel.setAllSort]).
 */
@Composable
private fun AxisChips(
    axis: FilmotekaAxis,
    allSort: FilmotekaAllSort,
    onSelect: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = axis == FilmotekaAxis.ALL,
            onClick = { onSelect(FilmotekaAxis.ALL) },
            label = { Text("Vše") },
            modifier = Modifier.tvFocusable(),
        )
        FilterChip(
            selected = axis == FilmotekaAxis.GENRE,
            onClick = { onSelect(FilmotekaAxis.GENRE) },
            label = { Text("Žánr") },
            modifier = Modifier.tvFocusable(),
        )
        FilterChip(
            selected = axis == FilmotekaAxis.COUNTRY,
            onClick = { onSelect(FilmotekaAxis.COUNTRY) },
            label = { Text("Země") },
            modifier = Modifier.tvFocusable(),
        )
        // Řazení jen dává smysl pro plochou osu „Vše" (Žánr/Země mají vlastní řazení řad).
        if (axis == FilmotekaAxis.ALL) {
            FilterChip(
                selected = allSort == FilmotekaAllSort.RECENT,
                onClick = { onAllSort(FilmotekaAllSort.RECENT) },
                label = { Text("Nedávno") },
                modifier = Modifier.tvFocusable(),
            )
            FilterChip(
                selected = allSort == FilmotekaAllSort.ALPHABETICAL,
                onClick = { onAllSort(FilmotekaAllSort.ALPHABETICAL) },
                label = { Text("Abecedně") },
                modifier = Modifier.tvFocusable(),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun FilmotekaRail.toTvRail(): TvRail = TvRail(
    id = id,
    title = title,
    style = HomeCardStyle.POSTER,
    items = items,
    configId = id,
    showTitles = true,
    immersiveHeader = false,
)
