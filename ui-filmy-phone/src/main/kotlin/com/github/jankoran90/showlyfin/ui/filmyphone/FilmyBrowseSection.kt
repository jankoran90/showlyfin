package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.github.jankoran90.showlyfin.core.domain.formatRuntime
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.ui.LocalDirectorProvider
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.cachedDirector
import com.github.jankoran90.showlyfin.core.ui.warmDirector
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaCollectionGroup
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
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
    /**
     * VESTIBUL (SHW-120) — klik na kartu „Další díly". Default no-op, aby „Pro tebe" (sdílí tuhle
     * sekci) nemusela nic řešit — řada se jí stejně nezobrazuje.
     */
    onOpenNextUp: (HomeRowItem) -> Unit = {},
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onToggleGenre: (String) -> Unit,
    onClearGenre: () -> Unit,
    onToggleCountry: (CinematographyRegion) -> Unit,
    onClearCountry: () -> Unit,
    viewMode: ViewMode,
    onToggleView: () -> Unit,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * RAMPA (SHW-121) — co je v liště místo dřívějších chipů os: u Filmotéky názvy stránek
     * (Filmotéka / K přehrání), jinde prostý název sekce. Default = nic (lišta zůstane holá).
     */
    titleContent: @Composable () -> Unit = {},
    /** Volitelné akce navíc vlevo v liště (Pro tebe = přepínač kategorií). Default nic → Filmotéka beze změny. */
    extraActions: (@Composable () -> Unit)? = null,
) {
    // GENRE/COUNTRY-FILTER — spodní sheety výběru. Otevřou se klikem na tab „Žánr"/„Země" (user 07-20).
    var showGenreFilter by remember { mutableStateOf(false) }
    var showCountryFilter by remember { mutableStateOf(false) }
    // SEARCH (user 07-19) — živý fulltext filtr (case/diakritika insensitive) přes název + popisek +
    // REŽIE. Filtruje se render-time nad railama (bez fetch). Prázdný dotaz = beze změny.
    // RAMPA (SHW-121): pole už není samostatná lupa v liště, ale první kategorie v panelu ovladačů —
    // proto tu zůstává jen dotaz a příznak otevřeného panelu.
    var controlsOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // ATRIUM (SHW-118) — otevřená kolekce (překryv s jejími díly); null = zavřeno.
    var openCollection by remember { mutableStateOf<FilmotekaCollectionGroup?>(null) }
    // SEARCH-REŽISÉR (user 07-19): index režisérů pro hledání — provider + verze (roste, jak se dotahují).
    val directorProvider = LocalDirectorProvider.current
    var directorVersion by remember { mutableStateOf(0) }

    // Při otevřeném hledání dotáhni režiséry VŠECH položek do procesní cache (bounded souběh), ať jde hledat
    // i podle režiséra napříč celou plochou, ne jen podle už zobrazených karet.
    val searching = query.isNotBlank()
    LaunchedEffect(searching, state.rails, directorProvider) {
        if (!searching || directorProvider == null) return@LaunchedEffect
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
                // ATRIUM: karta kolekce se najde i podle názvu DÍLU — hledání „auta" nesmí přestat
                // fungovat jen proto, že díly teď zastupuje jedna karta (user 2026-08-24).
                val memberTitles = item.collectionKey
                    ?.let { key -> state.collectionGroups.firstOrNull { it.id == key } }
                    ?.members?.joinToString(" ") { it.displayTitle }
                    .orEmpty()
                q in normalizeSearch(item.title + " " + (mi?.overview ?: "") + " " + dir + " " + memberTitles)
            })
        }.filter { it.items.isNotEmpty() }
    }

    Column(modifier.fillMaxSize()) {
        FilmotekaChips(
            genreFilter = state.genreFilter,
            countryFilter = state.countryFilter,
            query = query,
            onMenu = onMenu,
            titleContent = titleContent,
            onOpenControls = { controlsOpen = true },
            extraActions = extraActions,
        )
        if (controlsOpen) {
            FilmyBrowseControlsSheet(
                axis = state.axis,
                allSort = state.allSort,
                viewMode = viewMode,
                total = state.total,
                query = query,
                genreFilter = state.genreFilter,
                countryFilter = state.countryFilter,
                onQuery = { query = it },
                onAxis = { a ->
                    // user 07-20 — klik na „Žánr"/„Země" přepne osu A rovnou otevře výběr filtru.
                    onAxis(a)
                    when (a) {
                        FilmotekaAxis.GENRE -> showGenreFilter = true
                        FilmotekaAxis.COUNTRY -> showCountryFilter = true
                        else -> {}
                    }
                },
                onAllSort = onAllSort,
                onToggleView = onToggleView,
                onRemoveGenre = onToggleGenre,
                onRemoveCountry = onToggleCountry,
                onDismiss = { controlsOpen = false },
            )
        }
        // Vypršelé přihlášení k Traktu se dřív projevilo JEN tím, že se tiše rozhodilo pořadí „Nedávno
        // přidané" (data „kdy jsem si film přidal do Chci vidět" prostě nedorazila). Řekni to nahlas.
        if (state.traktStale) {
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Přihlášení k Traktu vypršelo — „Chci vidět\" je ze zálohy. Přihlas se znovu v Nastavení.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        // 🔴 user 2026-08-27 (výpadek Hetzneru): sběr se nepovedl → na obrazovce je poslední známý
        // stav z disku, ne čerstvá data. Bez téhle hlášky to vypadá jako vypnuté zdroje.
        if (state.offlineSnapshot) {
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Server se neozval — ukazuju poslední známý stav. Jakmile bude zpátky, obsah se dorovná.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        // ATRIUM (SHW-118, user 2026-08-24 „slouč její děti do jedné mateřské karty kolekce"):
        // žádná extra řada nahoře — karta kolekce sedí PŘÍMO v seznamu na svém abecedním místě a
        // zastupuje své díly (sdružení řeší [FilmotekaGrouping]). Klik ji rozbalí překryvem.
        val openGroupOf: (String) -> Unit = { key ->
            openCollection = state.collectionGroups.firstOrNull { it.id == key }
        }
        // MERIDIAN (SHW-119, user 2026-08-24): co ukazuje bublina rychlého posuvníku, ŘÍDÍ AKTUÁLNÍ
        // ŘAZENÍ — jinak by lišta lhala (písmeno u seznamu řazeného podle data nikam nevede).
        // Osy Žánr/Země mají vlastní pořadí v rámci řad → tam náhled nedává smysl (null = bez bubliny).
        val scrollerLabel: (HomeRowItem) -> String? = remember(state.axis, state.allSort) {
            when {
                state.axis != FilmotekaAxis.ALL -> ({ _: HomeRowItem -> null })
                else -> when (state.allSort) {
                    FilmotekaAllSort.ALPHABETICAL -> ({ row: HomeRowItem ->
                        row.title.trimStart().firstOrNull()?.uppercaseChar()?.toString()
                    })
                    FilmotekaAllSort.RECENT -> ({ row: HomeRowItem ->
                        row.mediaItem?.addedAtMs?.let { ms ->
                            java.time.Instant.ofEpochMilli(ms)
                                .atZone(java.time.ZoneId.systemDefault())
                                .let { "%02d/%d".format(it.monthValue, it.year) }
                        }
                    })
                    FilmotekaAllSort.RUNTIME -> ({ row: HomeRowItem ->
                        formatRuntime(row.mediaItem?.runtimeMinutes)
                    })
                }
            }
        }
        // VESTIBUL (SHW-120, user 2026-08-24 „i pro telefon nějakou obdobu včetně toho nastavení") —
        // „Další díly" nad obsahem, v obou zobrazeních. Při hledání se skrývá: uživatel filtruje
        // filmotéku, ne rozkoukané díly, a řada by mu jen ujídala obrazovku.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                state.rails.isEmpty() -> emptyContent()
                displayRails.isEmpty() -> FilmotekaNoResults(query)
                viewMode == ViewMode.LIST -> FilmotekaList(
                    displayRails, onOpenDetail, openGroupOf, scrollerLabel,
                    nextUp = if (query.isBlank()) state.nextUp else emptyList(),
                    onOpenNextUp = onOpenNextUp,
                )
                else -> FilmotekaGrid(
                    displayRails, onOpenDetail, openGroupOf, scrollerLabel,
                    nextUp = if (query.isBlank()) state.nextUp else emptyList(),
                    onOpenNextUp = onOpenNextUp,
                )
            }
        }
    }

    openCollection?.let { group ->
        FilmyCollectionOverlay(
            group = group,
            onDismiss = { openCollection = null },
            onOpenDetail = { item -> openCollection = null; onOpenDetail(item) },
        )
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
 * Horní lišta procházení — JEDINÉ patro: ☰ + [titleContent] (názvy stránek) + ikona ovladačů úplně
 * vpravo. Chipy os, řazení, lupa i počet titulů se přestěhovaly do [FilmyBrowseControlsSheet]
 * (RAMPA SHW-121, user 2026-08-28) — dřív to byly dvě lišty a přetékaly.
 */
@Composable
private fun FilmotekaChips(
    genreFilter: Set<String>,
    countryFilter: Set<CinematographyRegion>,
    query: String,
    onMenu: () -> Unit,
    /** Co se kreslí místo dřívějších chipů os — názvy stránek (Filmotéka / K přehrání) nebo titulek sekce. */
    titleContent: @Composable () -> Unit,
    /** Otevře panel se VŠEMI ovladači (hledání → zobrazení → řazení → osa → filtry). */
    onOpenControls: () -> Unit,
    extraActions: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // RAMPA (SHW-121) — JEDNO patro lišty: ☰ + názvy stránek + jediná ikona ovladačů úplně vpravo
        // (user 2026-08-28: „komplet prepinace presun do jedne ikony… tim padem se tam vejde nazev tabu"
        // + „ikonu filtru dej uplne doprava, kdybych chtel pridat sekci do filmoteky dalsi").
        // Dřív se tu tísnily osy, počet titulů, lupa i přepínač zobrazení a POD tím druhá řada s řazením
        // — na userově snímku se chip „Země" překrýval s textem „127 filmů". Všechno je teď v panelu.
        FilmySectionBar(
            onMenu = onMenu,
            trailing = {
                extraActions?.invoke()
                // Aktivní hledání/filtr musí být poznat i se zavřeným panelem — jinak by uživatel
                // koukal na osekaný seznam a nevěděl proč.
                val tuned = query.isNotBlank() || genreFilter.isNotEmpty() || countryFilter.isNotEmpty()
                IconButton(onClick = onOpenControls) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "Ovladače (hledání, zobrazení, řazení, filtry)",
                        tint = if (tuned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            titleContent()
        }
        // RAMPA (SHW-121): chipy aktivního filtru tu bývaly jako DRUHÝ řádek pod lištou. Teď jsou
        // v panelu (kategorie „Filtry") a že je něco zapnuté, hlásí obarvená ikona ovladačů výš —
        // lišta tak zůstane jednopatrová, jak si user přál.
    }
}


@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

internal val FilmotekaAxis.chipLabel: String
    get() = when (this) {
        FilmotekaAxis.ALL -> "Vše"
        FilmotekaAxis.GENRE -> "Žánr"
        FilmotekaAxis.COUNTRY -> "Země"
    }

internal val FilmotekaAllSort.chipLabel: String
    get() = when (this) {
        FilmotekaAllSort.RECENT -> "Nedávno přidané"
        FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
        FilmotekaAllSort.RUNTIME -> "Od nejkratšího"
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
