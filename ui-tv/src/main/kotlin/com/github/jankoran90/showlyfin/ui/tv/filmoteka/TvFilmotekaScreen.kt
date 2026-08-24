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
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import kotlin.math.roundToInt
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaRail
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel
import com.github.jankoran90.showlyfin.core.ui.TvSectionSort
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.tv.TvSectionViewModel
import com.github.jankoran90.showlyfin.feature.discover.tv.sortedBy
import com.github.jankoran90.showlyfin.ui.tv.components.TvViewChips
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.onFocusChanged
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaCollectionGroup
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.ui.tv.components.AutoFocusFirst
import com.github.jankoran90.showlyfin.ui.tv.components.TvHomeCard
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList
import com.github.jankoran90.showlyfin.ui.tv.components.TvGenreFilterDialog
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
    // FOYER (SHW-107): klik na kartu KOLEKCE → mřížka jejího obsahu (ne detail filmu).
    onOpenCollection: (collectionId: String, name: String) -> Unit = { _, _ -> },
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvFilmotekaViewModel = hiltViewModel(),
    sectionVm: TvSectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // FOYER (SHW-107, user 2026-07-26): na TV je vstup do Filmotéky MŘÍŽKA + ABECEDNĚ (osa „Vše"); přepnutí
    // se uloží. Osy Žánr/Země zůstávají řadové — mřížka by u nich smazala samotné dělení na řady.
    val modes by sectionVm.modes.collectAsStateWithLifecycle()
    val viewMode = sectionVm.viewModeOf(modes, ViewModeStore.SECTION_FILMOTEKA)
    val sort = sectionVm.sortOf(modes, ViewModeStore.SECTION_FILMOTEKA)

    // CONVERGE — při každém vstupu do sekce obnov výchozí osu (Nastavení → Filmotéka, default „Vše"). VM je
    // retained na úrovni shellu, takže bez tohoto by uvázlo runtime přepnutí osy (chip) z minulé návštěvy.
    LaunchedEffect(Unit) { viewModel.applyDefaultAxis() }

    // GENRE-FILTER — overlay dialog výběru žánrů (parita s telefonem). Otevře „Filtr žánrů" chip nebo 2. klik
    // na už aktivní osu Žánr.
    var showGenreFilter by remember { mutableStateOf(false) }

    // ATRIUM (SHW-118) — otevřená sdružená kolekce (překryv s díly); null = zavřeno.
    var openCollection by remember { mutableStateOf<FilmotekaCollectionGroup?>(null) }

    // KÁNON (CONVERGE): osa Filmotéky (Vše | Žánr | Země) jako chipy VEDLE názvu sekce — ne ve vlastním Row
    // nad TvRailList (to tlačilo obsah dolů a osa byla vizuálně odtržená od titulku). V řadovém stavu je
    // hlavička uvnitř TvRailList (sectionActions), v prázdném/loading nad obsahem přes TvSectionHeader.
    val chips: @Composable () -> Unit = {
        AxisChips(
            axis = state.axis,
            genreFilterCount = state.genreFilter.size,
            onSelect = { a ->
                if (a == FilmotekaAxis.GENRE && state.axis == FilmotekaAxis.GENRE) showGenreFilter = true
                else viewModel.setAxis(a)
            },
            onOpenGenreFilter = { showGenreFilter = true },
        )
        // Zobrazení + řazení ploché osy „Vše" (na ostatních osách řadí grouper podle své logiky).
        if (state.axis == FilmotekaAxis.ALL) {
            TvViewChips(
                viewMode = viewMode,
                sort = sort,
                onViewMode = { sectionVm.setViewMode(ViewModeStore.SECTION_FILMOTEKA, it) },
                onSort = { sectionVm.setSort(ViewModeStore.SECTION_FILMOTEKA, it) },
            )
        }
    }

    fun clickItem(item: HomeRowItem) {
        val media = item.mediaItem
        val jf = item.jellyfinId
        val collectionKey = item.collectionKey
        when {
            // ATRIUM (SHW-118): sdružená kolekce se otevře VLASTNÍM překryvem, ne obsahem BoxSetu —
            // kolekce může mít díl mimo Jellyfin (sdilej.cz) a ten by v JF prohlížeči chyběl.
            collectionKey != null ->
                openCollection = state.collectionGroups.firstOrNull { it.id == collectionKey }
            item.collection && jf != null -> onOpenCollection(jf, item.title)
            media != null -> onOpenDetail(media)
            jf != null -> onOpenJellyfinDetail(jf)
        }
    }

    // ATRIUM (SHW-118): žádná samostatná řada „Kolekce" nahoře (user: „žádná extra řada, to už jsem
    // říkal") — karty kolekcí sedí přímo mezi filmy na svém místě, sdružení řeší [FilmotekaGrouping].

    Box(Modifier.fillMaxSize()) {
        // FOYER — plochá osa „Vše" v režimu MŘÍŽKA (TV default): jedna velká abecední mřížka místo jedné
        // dlouhé řady. Osy Žánr/Země a režim „Řada" jedou dál přes TvRailList (řady mají svůj smysl).
        val flatItems = remember(state.rails, state.axis, sort) {
            if (state.axis != FilmotekaAxis.ALL) emptyList()
            else state.rails.flatMap { it.items }.sortedBy(sort)
        }
        if (state.axis == FilmotekaAxis.ALL && viewMode == ViewMode.GRID && flatItems.isNotEmpty()) {
            TvFilmotekaGrid(
                items = flatItems,
                immersive = immersive,
                onFocusItem = onFocusItem,
                onItemClick = ::clickItem,
                header = {
                    // Horní sekce zůstává beze změny (user: „zachovejme horní sekci jak je") — řada
                    // „Další díly" se řadí POD ni, nad samotnou mřížku.
                    TvSectionHeader(title = "Filmotéka", actions = { chips() })
                    if (state.nextUp.isNotEmpty()) {
                        TvNextUpRow(
                            items = state.nextUp,
                            immersive = immersive,
                            onFocusItem = onFocusItem,
                            onItemClick = ::clickItem,
                        )
                    }
                },
                modifier = modifier.fillMaxSize(),
            )
        } else if (state.rails.isNotEmpty()) {
            // VESTIBUL (SHW-120): „Další díly" jako PRVNÍ řada — na velké TV se tak vejde spolu s řadou
            // filmotéky na jednu obrazovku (user 2026-08-24). Immersive i popisky jedou jako u ostatních.
            val rails = remember(state.rails, state.nextUp) {
                val base = state.rails.map { it.toTvRail() }
                if (state.nextUp.isEmpty()) base else listOf(nextUpRail(state.nextUp)) + base
            }
            TvRailList(
                rails = rails,
                sectionTitle = "Filmotéka",
                immersive = immersive,
                immersiveHeader = immersiveHeader,
                onFocusItem = onFocusItem,
                onItemClick = ::clickItem,
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

        openCollection?.let { group ->
            TvFilmotekaCollectionOverlay(
                group = group,
                onDismiss = { openCollection = null },
                onItemClick = { item ->
                    openCollection = null
                    item.mediaItem?.let(onOpenDetail) ?: item.jellyfinId?.let(onOpenJellyfinDetail)
                },
            )
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
 * Přepínač osy Filmotéky: Vše | Žánr | Země. Všechny chipy D-pad-fokusovatelné; přepnutí jen přeskupí bázi
 * (bez fetch). FOYER (SHW-107): řazení ploché osy „Vše" na TV řídí `TvViewChips` (TV default = abecedně),
 * ne telefonní `FilmotekaAllSort` — proto tu chipy Nedávno/Abecedně už nejsou.
 */
@Composable
private fun AxisChips(
    axis: FilmotekaAxis,
    genreFilterCount: Int,
    onSelect: (FilmotekaAxis) -> Unit,
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
 * FOYER (SHW-107) — plochá mřížka Filmotéky (osa „Vše"). Karty = tentýž [TvHomeCard] jako v řadách
 * (plakát + ČSFD badge + odznak „hraje hned"), aby sekce vypadala konzistentně s domovem. Hlavička
 * (název + chipy) jde dovnitř jako [header], ať nad mřížkou není druhý pás.
 */
@Composable
private fun TvFilmotekaGrid(
    items: List<HomeRowItem>,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    onItemClick: (HomeRowItem) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardScale = LocalTvCardScale.current
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    AutoFocusFirst(
        focusRequester = firstFocus,
        enabled = items.isNotEmpty(),
        isTargetPlaced = { gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 } },
    )
    Column(modifier.tvOverscan()) {
        header()
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed((6f / cardScale.widthScale).roundToInt().coerceIn(3, 9)),
            horizontalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
            verticalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp)),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                Box(
                    Modifier.onFocusChanged { if (it.hasFocus && immersive) onFocusItem(item.toImmersiveInfo()) },
                ) {
                    TvHomeCard(
                        item = item,
                        style = HomeCardStyle.POSTER,
                        onClick = { onItemClick(item) },
                        focusRequester = if (index == 0) firstFocus else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** VESTIBUL (SHW-120) — „Další díly" jako TV řada: široké karty s popiskem, immersive zapnuté. */
private fun nextUpRail(items: List<HomeRowItem>): TvRail = TvRail(
    id = "filmo_nextup",
    title = "Další díly",
    // Epizoda se ukazuje na ŠIROKÉ kartě (still dílu), ne na plakátu — stejně jako na webu i na domově.
    style = HomeCardStyle.LANDSCAPE,
    items = items,
    configId = "filmo_nextup",
    showTitles = true,
    immersiveHeader = false,
)

/**
 * Vodorovná řada „Další díly" pro MŘÍŽKOVÝ režim (v řadovém ji kreslí `TvRailList` sama).
 * Fokus hlásí immersive pozadí, aby se karta chovala stejně jako v řadách.
 */
@Composable
private fun TvNextUpRow(
    items: List<HomeRowItem>,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    onItemClick: (HomeRowItem) -> Unit,
) {
    val cardScale = LocalTvCardScale.current
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            text = "Další díly",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(cardScale.spacing(16.dp))) {
            items(items, key = { it.key }) { item ->
                Box(
                    Modifier.onFocusChanged {
                        if (it.hasFocus && immersive) onFocusItem(item.toImmersiveInfo())
                    },
                ) {
                    TvHomeCard(
                        item = item,
                        style = HomeCardStyle.LANDSCAPE,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
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
