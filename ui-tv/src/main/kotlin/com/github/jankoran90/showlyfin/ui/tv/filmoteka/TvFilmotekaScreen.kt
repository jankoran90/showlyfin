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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.onFocusChanged
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
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
        when {
            item.collection && jf != null -> onOpenCollection(jf, item.title)
            media != null -> onOpenDetail(media)
            jf != null -> onOpenJellyfinDetail(jf)
        }
    }

    // 🔒 2026-08-24 (user: „nechci žádné chipy kolekce, filmy z kolekce se musí zobrazovat mezi
    // ostatními, nevymýšlej nové sekce/view type") — samostatná karta kolekce ZRUŠENA i na TV
    // (telefon už tenhle den dřív). Jednotlivé filmy z kolekcí teď natahuje rovnou do `state.rails`
    // [FilmotekaBaseLoader.loadJellyfinLibrary], takže tahle extra karta by je jen DUPLIKOVALA
    // (přesně to user nahlásil: „zobrazují se jak v kolekci tak zvlášť"). VŽDY prázdné, dokud
    // nevznikne skutečné sdružování dílů pod jednu kartu (jiná feature, zatím nepotvrzeno).
    val collectionCards = emptyList<HomeRowItem>()

    Box(Modifier.fillMaxSize()) {
        // FOYER — plochá osa „Vše" v režimu MŘÍŽKA (TV default): jedna velká abecední mřížka místo jedné
        // dlouhé řady. Osy Žánr/Země a režim „Řada" jedou dál přes TvRailList (řady mají svůj smysl).
        val flatItems = remember(state.rails, state.axis, sort, collectionCards) {
            if (state.axis != FilmotekaAxis.ALL) emptyList()
            // Kolekce (když jsou zapnuté) drží pohromadě na začátku — jsou to rozcestníky, ne filmy.
            else collectionCards + state.rails.flatMap { it.items }.sortedBy(sort)
        }
        if (state.axis == FilmotekaAxis.ALL && viewMode == ViewMode.GRID && flatItems.isNotEmpty()) {
            TvFilmotekaGrid(
                items = flatItems,
                immersive = immersive,
                onFocusItem = onFocusItem,
                onItemClick = ::clickItem,
                header = { TvSectionHeader(title = "Filmotéka", actions = { chips() }) },
                modifier = modifier.fillMaxSize(),
            )
        } else if (state.rails.isNotEmpty()) {
            val rails = remember(state.rails, collectionCards) {
                val base = state.rails.map { it.toTvRail() }
                if (collectionCards.isEmpty()) base
                else listOf(collectionRail(collectionCards)) + base
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

/** FOYER — řada „Kolekce" nad ostatními řadami Filmotéky (jen když jsou karty kolekcí zapnuté). */
private fun collectionRail(cards: List<HomeRowItem>): TvRail = TvRail(
    id = "filmo_collections",
    title = "Kolekce",
    style = HomeCardStyle.POSTER,
    items = cards,
    configId = "filmo_collections",
    showTitles = true,
    immersiveHeader = false,
)

private fun FilmotekaRail.toTvRail(): TvRail = TvRail(
    id = id,
    title = title,
    style = HomeCardStyle.POSTER,
    items = items,
    configId = id,
    showTitles = true,
    immersiveHeader = false,
)
