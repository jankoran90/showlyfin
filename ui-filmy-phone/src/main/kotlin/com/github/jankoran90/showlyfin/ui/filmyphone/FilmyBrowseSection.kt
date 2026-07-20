package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.ui.LocalDirectorProvider
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.cachedDirector
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.core.ui.warmDirector
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaUiState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * MIRROR (user 2026-07-20) — SDÍLENÁ telefonní plocha „procházení" (Filmotéka i „Pro tebe" appky Filmy).
 *
 * Bere hotový [FilmotekaUiState] (osy Vše/Žánr/Země, filtr žánru+země, řazení osy „Vše", počítadlo, řady) a sadu
 * akcí → renderuje identické nástroje: lišta chipů os (☰ + počet + lupa + přepínač zobrazení), řádek řazení u osy
 * „Vše", řádek pojmenovaných chipů aktivního filtru s křížkem, fulltext hledání (název + popis + REŽIE), mřížka /
 * seznam bohatých řádků, spodní sheety výběru žánru/země. Klik na tab „Žánr"/„Země" přepne osu A rovnou otevře
 * picker. Obě sekce tak vypadají a ovládají se 1:1 — jediný zdroj pravdy tohoto UI. Prázdný stav = [emptyContent].
 */
@Composable
fun FilmyBrowseSection(
    state: FilmotekaUiState,
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onToggleGenre: (String) -> Unit,
    onClearGenre: () -> Unit,
    onToggleCountry: (CinematographyRegion) -> Unit,
    onClearCountry: () -> Unit,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Výchozí = SEZNAM (bohaté řádky jako domov, přání usera 2026-07-17). Přepínač vpravo v liště. Per-session.
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    // GENRE/COUNTRY-FILTER — spodní sheety výběru. Otevřou se klikem na tab „Žánr"/„Země" (user 07-20).
    var showGenreFilter by remember { mutableStateOf(false) }
    var showCountryFilter by remember { mutableStateOf(false) }
    // SEARCH (user 07-19) — lupa → rozbalí input → živý fulltext filtr (case/diakritika insensitive) přes
    // název + popisek + REŽIE. Filtruje se render-time nad railama (bez fetch). Prázdný dotaz = beze změny.
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // SEARCH-REŽISÉR (user 07-19): index režisérů pro hledání — provider + verze (roste, jak se dotahují).
    val directorProvider = LocalDirectorProvider.current
    var directorVersion by remember { mutableStateOf(0) }

    // Při otevřeném hledání dotáhni režiséry VŠECH položek do procesní cache (bounded souběh), ať jde hledat
    // i podle režiséra napříč celou plochou, ne jen podle už zobrazených karet.
    LaunchedEffect(searchOpen, state.rails, directorProvider) {
        if (!searchOpen || directorProvider == null) return@LaunchedEffect
        val items = state.rails.asSequence().flatMap { it.items.asSequence() }
            .mapNotNull { it.mediaItem }
            .filter { it.tmdbId != null || !it.imdbId.isNullOrBlank() }
            .distinctBy { it.tmdbId ?: it.imdbId }
            .toList()
        val sem = Semaphore(6)
        coroutineScope {
            items.forEach { mi ->
                launch {
                    sem.withPermit {
                        val name = warmDirector(directorProvider, mi.imdbId, mi.tmdbId, mi.type, mi.title, mi.year)
                        if (name != null) directorVersion++
                    }
                }
            }
        }
    }

    val displayRails = remember(state.rails, query, directorVersion) {
        val q = normalizeSearch(query)
        if (q.isBlank()) state.rails
        else state.rails.map { rail ->
            rail.copy(items = rail.items.filter { item ->
                val mi = item.mediaItem
                val dir = mi?.let { cachedDirector(it.tmdbId, it.imdbId) } ?: ""
                q in normalizeSearch(item.title + " " + (mi?.overview ?: "") + " " + dir)
            })
        }.filter { it.items.isNotEmpty() }
    }

    Column(modifier.fillMaxSize()) {
        FilmotekaChips(
            axis = state.axis,
            allSort = state.allSort,
            viewMode = viewMode,
            total = state.total,
            genreFilter = state.genreFilter,
            countryFilter = state.countryFilter,
            onMenu = onMenu,
            onAxis = { a ->
                // user 07-20 — klik na „Žánr"/„Země" přepne osu A rovnou otevře picker filtru (výběr → chip s křížkem).
                onAxis(a)
                when (a) {
                    FilmotekaAxis.GENRE -> showGenreFilter = true
                    FilmotekaAxis.COUNTRY -> showCountryFilter = true
                    else -> {}
                }
            },
            onAllSort = onAllSort,
            onToggleView = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID },
            onRemoveGenre = onToggleGenre,
            onRemoveCountry = onToggleCountry,
            searchOpen = searchOpen,
            onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
        )
        if (searchOpen) {
            FilmotekaSearchField(query = query, onQuery = { query = it }, onClose = { searchOpen = false; query = "" })
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.rails.isEmpty() -> emptyContent()
                displayRails.isEmpty() -> FilmotekaNoResults(query)
                viewMode == ViewMode.LIST -> FilmotekaList(displayRails, onOpenDetail)
                else -> FilmotekaGrid(displayRails, onOpenDetail)
            }
        }
    }

    if (showGenreFilter) {
        GenreFilterSheet(
            available = state.availableGenres,
            selected = state.genreFilter,
            onToggle = onToggleGenre,
            onClear = onClearGenre,
            onDismiss = { showGenreFilter = false },
        )
    }
    if (showCountryFilter) {
        CountryFilterSheet(
            available = state.availableCountries,
            selected = state.countryFilter,
            onToggle = onToggleCountry,
            onClear = onClearCountry,
            onDismiss = { showCountryFilter = false },
        )
    }
}

/**
 * Lišta os splynulá s horní lištou (☰ + chipy os + přepínač zobrazení vpravo) a — jen pro osu „Vše" —
 * druhá lišta řazení pod ní. Max 2 lišty (přání usera). Vzor ovladačů = princip „lišta v každé sekci".
 */
@Composable
private fun FilmotekaChips(
    axis: FilmotekaAxis,
    allSort: FilmotekaAllSort,
    viewMode: ViewMode,
    total: Int,
    genreFilter: Set<String>,
    countryFilter: Set<CinematographyRegion>,
    onMenu: () -> Unit,
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onToggleView: () -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRemoveCountry: (CinematographyRegion) -> Unit,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ☰ + chipy os (scroll) + počet titulů + přepínač zobrazení vpravo — jeden pruh (splynutí s lištou).
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                if (total > 0) {
                    Text(
                        text = "$total filmů",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (searchOpen) "Zavřít hledání" else "Hledat",
                        tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleView) {
                    if (viewMode == ViewMode.GRID) {
                        Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = "Zobrazit jako seznam")
                    } else {
                        Icon(Icons.Rounded.GridView, contentDescription = "Zobrazit jako mřížku")
                    }
                }
            },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAxis.entries.forEach { a ->
                    FilterChip(selected = axis == a, onClick = { onAxis(a) }, label = { Text(a.chipLabel) })
                }
            }
        }
        if (axis == FilmotekaAxis.ALL) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAllSort.entries.forEach { s ->
                    FilterChip(selected = allSort == s, onClick = { onAllSort(s) }, label = { Text(s.chipLabel) })
                }
            }
        }
        // GENRE-FILTER (user 07-20) — druhý řádek u osy Žánr: JEN pojmenované chipy vybraných žánrů s křížkem
        // (klik = zrušit ten žánr). Vstup do výběru = klik na tab „Žánr" (otevře picker). Prázdný výběr = žádný řádek.
        if (axis == FilmotekaAxis.GENRE && genreFilter.isNotEmpty()) {
            SelectedFilterChips(
                labels = genreFilter.map { it to it },
                onRemove = { onRemoveGenre(it) },
            )
        }
        // COUNTRY-FILTER (user 07-20) — analogicky u osy Země: pojmenované chipy vybraných regionů s křížkem.
        if (axis == FilmotekaAxis.COUNTRY && countryFilter.isNotEmpty()) {
            SelectedFilterChips(
                labels = countryFilter.map { it.label to it },
                onRemove = onRemoveCountry,
            )
        }
    }
}

/**
 * SELECTED-FILTER (user 07-20) — řádek pojmenovaných chipů aktivního filtru (žánr/země) s křížkem na zrušení.
 * [labels] = dvojice (popisek → hodnota); klik na chip (i křížek) zavolá [onRemove] s hodnotou. Generický pro
 * žánr (String) i region (CinematographyRegion).
 */
@Composable
private fun <T> SelectedFilterChips(labels: List<Pair<String, T>>, onRemove: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { (label, value) ->
            FilterChip(
                selected = true,
                onClick = { onRemove(value) },
                label = { Text(label) },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Zrušit filtr $label",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

/** Mřížka plakátů se sekcemi. Víc řad (žánr/země) → full-width nadpis mezi sekcemi; jedna řada (Vše) → bez nadpisu. */
@Composable
private fun FilmotekaGrid(rails: List<FilmotekaRail>, onOpenDetail: (MediaItem) -> Unit) {
    val cols = rememberGridColumnPref()
    val showHeaders = rails.size > 1
    LazyVerticalGrid(
        columns = gridCellsFor(ViewMode.GRID, cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(rail.title)
                }
            }
            gridItems(rail.items, key = { it.key }) { row ->
                row.mediaItem?.let { mi -> MediaCard(item = mi, onClick = { onOpenDetail(mi) }) }
            }
        }
    }
}

/** Seznam bohatých řádků (cover + název + režie + rok · žánry + popis) — stejný řádek jako domov. */
@Composable
private fun FilmotekaList(rails: List<FilmotekaRail>, onOpenDetail: (MediaItem) -> Unit) {
    val showHeaders = rails.size > 1
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rails.forEach { rail ->
            if (showHeaders) {
                item(key = "hdr_${rail.id}") { SectionHeader(rail.title) }
            }
            listItems(rail.items, key = { it.key }) { row ->
                row.mediaItem?.let { mi ->
                    MediaRow(
                        item = mi,
                        onClick = { onOpenDetail(mi) },
                        watched = row.watched,
                        genreLine = mi.genres?.filter { it.isNotBlank() }?.take(3)?.joinToString(" · "),
                        showDirector = true,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private val FilmotekaAxis.chipLabel: String
    get() = when (this) {
        FilmotekaAxis.ALL -> "Vše"
        FilmotekaAxis.GENRE -> "Žánr"
        FilmotekaAxis.COUNTRY -> "Země"
    }

private val FilmotekaAllSort.chipLabel: String
    get() = when (this) {
        FilmotekaAllSort.RECENT -> "Nedávno přidané"
        FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
    }

/**
 * GENRE-FILTER — spodní sheet výběru žánrů (multi-select). Filtruje se dle HLAVNÍHO žánru (parita s osou Žánr);
 * prázdný výběr = vše. Nabídka [available] = žánry přítomné v bázi (dle četnosti). Sdílený VM = parita s TV.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GenreFilterSheet(
    available: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filtr žánrů",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Zrušit filtr") }
                }
            }
            if (available.isEmpty()) {
                Text(
                    text = "Žádné žánry k dispozici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { g ->
                        FilterChip(
                            selected = g in selected,
                            onClick = { onToggle(g) },
                            label = { Text(g) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * COUNTRY-FILTER (user 2026-07-20) — spodní sheet výběru zemí/regionů (multi-select), analogie [GenreFilterSheet].
 * Filtruje dle HLAVNÍ země s největší vahou (parita s osou Země); prázdný výběr = vše. Nabídka [available] =
 * regiony přítomné v bázi (dle četnosti). Sdílený VM = parita s TV.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CountryFilterSheet(
    available: List<CinematographyRegion>,
    selected: Set<CinematographyRegion>,
    onToggle: (CinematographyRegion) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filtr zemí",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (selected.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Zrušit filtr") }
                }
            }
            if (available.isEmpty()) {
                Text(
                    text = "Žádné země k dispozici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { region ->
                        FilterChip(
                            selected = region in selected,
                            onClick = { onToggle(region) },
                            label = { Text(region.label) },
                        )
                    }
                }
            }
        }
    }
}

/** SEARCH — normalizace pro fulltext: lowercase + odstranění diakritiky (case & diakritika insensitive). */
private fun normalizeSearch(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .trim()

/** SEARCH — rozbalený input pro fulltext filtr (auto-fokus, klávesnice hned). Filtruje živě přes onQuery. */
@Composable
private fun FilmotekaSearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .focusRequester(focus),
        singleLine = true,
        placeholder = { Text("Hledat v názvu, popisu i režii…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { if (query.isEmpty()) onClose() else onQuery("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Vymazat")
            }
        },
    )
}

/** SEARCH — prázdný výsledek hledání. */
@Composable
private fun FilmotekaNoResults(query: String) {
    Text(
        text = "Nic nenalezeno pro „$query\"",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
