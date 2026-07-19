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
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.ui.MediaCard
import com.github.jankoran90.showlyfin.core.ui.MediaRow
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.gridCellsFor
import com.github.jankoran90.showlyfin.core.ui.rememberGridColumnPref
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.4 — telefonní Filmotéka appky „Filmy".
 *
 * Transpozice TV Filmotéky: sdílený datový mozek [TvFilmotekaViewModel] (feature vrstva, bez TV závislosti),
 * telefonní render = MŘÍŽKA plakátů NEBO SEZNAM bohatých řádků (přepínač vpravo v liště os — žádná další lišta).
 * Nahoře chipy osy (Vše / Žánr / Země) + pro osu „Vše" chipy řazení (Nedávno / Abecedně). Klik → sdílený
 * DetailScreen (přes shell back-stack). Live registrace uloženého zdroje řeší VM (savedKeys reload).
 *
 * View-styl (mřížka/seznam) je zatím lokální (per-session); per-profil uložení s presety = M2.5 (Nastavení).
 */
@Composable
fun FilmyFilmotekaScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    vm: TvFilmotekaViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Výchozí = SEZNAM (bohaté řádky jako domov, přání usera 2026-07-17). Přepínač vpravo v liště.
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    // GENRE-FILTER — bottom sheet výběru žánrů (multi-select). Otevře se druhým klikem na „Žánr" (user 07-19).
    var showGenreFilter by remember { mutableStateOf(false) }
    // SEARCH (user 07-19) — lupa → rozbalí input → živý fulltext filtr (case/diakritika insensitive) přes
    // název + popisek. Filtruje se render-time nad railama (bez fetch). Prázdný dotaz = beze změny.
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // VM je retained na úrovni shellu (sekce se přepínají when-em) → při vstupu obnov výchozí osu z Nastavení.
    LaunchedEffect(Unit) { vm.applyDefaultAxis() }

    val displayRails = remember(state.rails, query) {
        val q = normalizeSearch(query)
        if (q.isBlank()) state.rails
        else state.rails.map { rail ->
            rail.copy(items = rail.items.filter { q in normalizeSearch(it.title + " " + (it.mediaItem?.overview ?: "")) })
        }.filter { it.items.isNotEmpty() }
    }

    Column(modifier.fillMaxSize()) {
        FilmotekaChips(
            axis = state.axis,
            allSort = state.allSort,
            viewMode = viewMode,
            total = state.total,
            genreFilterCount = state.genreFilter.size,
            onMenu = onMenu,
            onAxis = { a ->
                // 2. klik na už aktivní osu Žánr = otevři filtr žánrů (jinak přepni osu).
                if (a == FilmotekaAxis.GENRE && state.axis == FilmotekaAxis.GENRE) showGenreFilter = true
                else vm.setAxis(a)
            },
            onAllSort = vm::setAllSort,
            onToggleView = { viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID },
            onOpenGenreFilter = { showGenreFilter = true },
            onClearGenreFilter = vm::clearGenreFilter,
            searchOpen = searchOpen,
            onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) query = "" },
        )
        if (searchOpen) {
            FilmotekaSearchField(query = query, onQuery = { query = it }, onClose = { searchOpen = false; query = "" })
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.rails.isEmpty() -> FilmotekaEmpty()
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
            onToggle = vm::toggleGenreFilter,
            onClear = vm::clearGenreFilter,
            onDismiss = { showGenreFilter = false },
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
    genreFilterCount: Int,
    onMenu: () -> Unit,
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onToggleView: () -> Unit,
    onOpenGenreFilter: () -> Unit,
    onClearGenreFilter: () -> Unit,
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
        // GENRE-FILTER — druhý řádek u osy Žánr: otevři filtr (multi-select) + zrušit, když aktivní.
        if (axis == FilmotekaAxis.GENRE) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = genreFilterCount > 0,
                    onClick = onOpenGenreFilter,
                    label = { Text(if (genreFilterCount > 0) "Filtr žánrů ($genreFilterCount)" else "Filtrovat žánry") },
                )
                if (genreFilterCount > 0) {
                    FilterChip(selected = false, onClick = onClearGenreFilter, label = { Text("Zrušit filtr") })
                }
            }
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

/** Prázdná Filmotéka — žádné zdroje/přihlášení. */
@Composable
private fun FilmotekaEmpty() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Filmotéka je zatím prázdná",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Přihlas se k Traktu nebo Jellyfinu a přidej zdroje — objeví se tu tvoje filmy seřazené podle žánru, země i abecedy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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
        placeholder = { Text("Hledat v názvu a popisu…") },
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
