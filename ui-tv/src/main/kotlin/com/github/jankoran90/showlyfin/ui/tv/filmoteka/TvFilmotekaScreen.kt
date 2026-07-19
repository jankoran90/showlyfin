package com.github.jankoran90.showlyfin.ui.tv.filmoteka

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

    // GENRE-FILTER — overlay dialog výběru žánrů (parita s telefonem). Otevře „Filtr žánrů" chip nebo 2. klik
    // na už aktivní osu Žánr.
    var showGenreFilter by remember { mutableStateOf(false) }

    // KÁNON (CONVERGE): osa Filmotéky (Vše | Žánr | Země) jako chipy VEDLE názvu sekce — ne ve vlastním Row
    // nad TvRailList (to tlačilo obsah dolů a osa byla vizuálně odtržená od titulku). V řadovém stavu je
    // hlavička uvnitř TvRailList (sectionActions), v prázdném/loading nad obsahem přes TvSectionHeader.
    val chips: @Composable () -> Unit = {
        AxisChips(
            axis = state.axis,
            allSort = state.allSort,
            genreFilterCount = state.genreFilter.size,
            onSelect = { a ->
                if (a == FilmotekaAxis.GENRE && state.axis == FilmotekaAxis.GENRE) showGenreFilter = true
                else viewModel.setAxis(a)
            },
            onAllSort = viewModel::setAllSort,
            onOpenGenreFilter = { showGenreFilter = true },
        )
    }

    Box(Modifier.fillMaxSize()) {
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
        } else {
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

        if (showGenreFilter) {
            TvGenreFilterDialog(
                available = state.availableGenres,
                selected = state.genreFilter,
                onToggle = viewModel::toggleGenreFilter,
                onClear = viewModel::clearGenreFilter,
                onDismiss = { showGenreFilter = false },
            )
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
    genreFilterCount: Int,
    onSelect: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onOpenGenreFilter: () -> Unit,
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
        // GENRE-FILTER — u osy Žánr chip pro multi-select filtr žánrů (parita s telefonem).
        if (axis == FilmotekaAxis.GENRE) {
            FilterChip(
                selected = genreFilterCount > 0,
                onClick = onOpenGenreFilter,
                label = { Text(if (genreFilterCount > 0) "Filtr žánrů ($genreFilterCount)" else "Filtrovat žánry") },
                modifier = Modifier.tvFocusable(),
            )
        }
    }
}

/**
 * GENRE-FILTER — TV overlay pro multi-select filtr žánrů (parita s telefonním [GenreFilterSheet]). D-pad
 * fokusovatelné chipy; Back zavře. Filtruje dle hlavního žánru (sdílený VM). Barvy/tvary z motivu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvGenreFilterDialog(
    available: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstChipFocus = remember { FocusRequester() }
    LaunchedEffect(available) { if (available.isNotEmpty()) runCatching { firstChipFocus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .padding(32.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Filtr žánrů",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (available.isEmpty()) {
                    Text(
                        text = "Žádné žánry k dispozici.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        available.forEachIndexed { idx, g ->
                            FilterChip(
                                selected = g in selected,
                                onClick = { onToggle(g) },
                                label = { Text(g) },
                                modifier = (if (idx == 0) Modifier.focusRequester(firstChipFocus) else Modifier)
                                    .tvFocusable(),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = onClear, modifier = Modifier.tvFocusable()) { Text("Zrušit filtr") }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.tvFocusable()) { Text("Zavřít") }
                }
            }
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
