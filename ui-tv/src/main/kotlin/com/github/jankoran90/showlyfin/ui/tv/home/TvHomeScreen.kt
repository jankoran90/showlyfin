package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams.boolParam
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvHomeCard
import com.github.jankoran90.showlyfin.ui.tv.components.TvImmersiveHeader
import com.github.jankoran90.showlyfin.ui.tv.components.toImmersiveInfo
import kotlinx.coroutines.flow.first

/** Jedna vykreslená řada (HomeVM řada NEBO konkrétní Jellyfin knihovna). */
private data class Rail(
    val id: String,
    val title: String,
    val style: HomeCardStyle,
    val items: List<HomeRowItem>,
    val configId: String,
    /** Popisky pod/na kartách (immersive Netflix styl je skrývá — per řada). */
    val showTitles: Boolean = true,
    /** KOLO2 (M): immersive hlavička (název/rok/popis fokusované karty nahoře) — per řada. */
    val immersiveHeader: Boolean = false,
)

/**
 * BringIntoViewSpec, který na vertikální ose NIKDY nescrolluje (vždy 0). Nasazen na LazyColumn domova:
 * fokus karty (a jeho případná změna bounds) tak nemůže hnout svislým seznamem — svislý „bump" při vodorovném
 * projíždění karet je konstrukčně vyloučen. Svislé pozicování řad řeší VÝHRADNĚ explicitní
 * `animateScrollToItem(focusedIndex)` (klíčovaný jen na změnu ŘADY). Uvnitř řad se obnoví DEFAULT biv, aby
 * horizontální LazyRow scrolloval normálně.
 */
@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScrollBiv = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * TENFOOT (SHW-87) — TV domov (Kodi Arctic Fuse styl). Vertikální seznam horizontálních railů dle
 * uživatelské konfigurace ([TvHomeViewModel]) + per-knihovna řady ([LibraryRowsViewModel]). Sidebar žije
 * v [com.github.jankoran90.showlyfin.ui.tv.TvShell]; tady je jen tělo domova. Fokusovaná karta hlásí
 * [onFocusItem] nahoru (immersive pozadí) a řídí lokální hero header. Menu na řadě = inline editor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    immersiveHeader: Boolean = true,
    homeVm: TvHomeViewModel = hiltViewModel(),
    libraryVm: LibraryRowsViewModel = hiltViewModel(),
) {
    val rowConfigs by homeVm.rowConfigs.collectAsStateWithLifecycle()
    val allRows by homeVm.allRows.collectAsStateWithLifecycle()
    val states by homeVm.states.collectAsStateWithLifecycle()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()

    // COUCH R2: JF knihovní řady přenačti i při PŘEPNUTÍ profilu (nejen jednou) — jiný profil = jiné knihovny.
    val activeProfileId by homeVm.activeProfileId.collectAsStateWithLifecycle()
    LaunchedEffect(activeProfileId) { libraryVm.load() }
    // Seed-once řad per knihovna, jakmile známe seznam (neprázdné) knihoven.
    LaunchedEffect(libraryState.rows) {
        val libs = libraryState.rows.map { LibrarySummary(it.libraryId, it.libraryName, it.collectionType) }
        if (libs.isNotEmpty()) homeVm.syncLibraries(libs)
    }
    // Lazy načtení řad (mimo JF knihovny — ty jedou přes LibraryRowsViewModel).
    LaunchedEffect(rowConfigs) {
        rowConfigs.filter { it.source != HomeRowSourceType.JELLYFIN_LIBRARY && it.source != HomeRowSourceType.JELLYFIN_LIBRARIES }
            .forEach { homeVm.ensureRowLoaded(it) }
    }

    // Sestav ploché raily (jen neprázdné → domov je vzdušný, obsah hned).
    val rails: List<Rail> = buildList {
        rowConfigs.forEach { cfg ->
            when (cfg.source) {
                HomeRowSourceType.JELLYFIN_LIBRARY -> {
                    val libId = cfg.params[HomeRowParams.LIBRARY_ID]
                    val libRow = libraryState.rows.firstOrNull { it.libraryId == libId }
                    val items = libRow?.items?.map { it.toHomeRowItem() }?.applyConfig(cfg).orEmpty()
                    if (items.isNotEmpty()) {
                        add(Rail(cfg.id, cfg.title.ifBlank { libRow?.libraryName.orEmpty() }, cfg.cardStyle, items, cfg.id, cfg.showTitles, cfg.immersiveHeader))
                    }
                }
                HomeRowSourceType.JELLYFIN_LIBRARIES -> Unit // deprecated meta — migrováno pryč
                else -> {
                    val st = states[cfg.id]
                    if (st != null && st.items.isNotEmpty()) {
                        add(Rail(cfg.id, cfg.resolvedTitle(), cfg.cardStyle, st.items, cfg.id, cfg.showTitles, cfg.immersiveHeader))
                    }
                }
            }
        }
    }

    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(0) }
    var focusedItem by remember { mutableStateOf<HomeRowItem?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }
    var pendingNewId by remember { mutableStateOf("") }
    // Seznam knihoven profilu pro picker „Přidat řadu" (konkrétní knihovna / Nejnovější v knihovně).
    val libraries = remember(libraryState.rows) {
        libraryState.rows.map { LibrarySummary(it.libraryId, it.libraryName, it.collectionType) }
    }

    // Immersive: fokusovaná karta → info nahoru (pozadí, gated `immersive` v TvShell) + lokální hero header
    // (gated `immersiveHeader`). Info počítej, když je aspoň jedno zapnuté; pozadí posílej jen když `immersive`.
    val focusedInfo: ImmersiveInfo? =
        if (immersive || immersiveHeader) focusedItem?.toImmersiveInfo() else null
    LaunchedEffect(focusedInfo, immersive) { onFocusItem(if (immersive) focusedInfo else null) }

    // DEFINITIVNÍ fix bumpu (user 2026-07-13, kolo 2): vertikální scroll je 100 % pod explicitní kontrolou —
    // zarovnání fokusované řady se děje JEN při změně ŘADY (`focusedIndex`), nikdy z bring-into-view. Auto
    // vertikální bring-into-view je totiž zdrojem bumpu: fokus-scale/lift karty mění její bounds, což
    // `BringIntoViewSpec` na LazyColumn interpretuje jako potřebu svislého scrollu i při čistě VODOROVNÉM
    // projíždění karet. Řešení = spec, který na vertikální ose vrací VŽDY 0 (viz `NoAutoScrollBiv` níže), takže
    // vodorovný pohyb NIKDY nehne seznamem; svislé pozicování obstará tento efekt (klíčovaný jen na `focusedIndex`).
    LaunchedEffect(focusedIndex) {
        runCatching { listState.animateScrollToItem(focusedIndex.coerceAtLeast(0)) }
    }

    // Autofokus na PRVNÍ kartu PRVNÍ řady. Čeká na umístění řady, pak pár snímků a request.
    val firstFocus = remember { FocusRequester() }
    val firstRailId = rails.firstOrNull()?.id
    LaunchedEffect(firstRailId) {
        if (firstRailId == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == firstRailId } }.first { it }
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    fun clickItem(item: HomeRowItem) {
        val mi = item.mediaItem
        val jf = item.jellyfinId
        when {
            mi != null -> onOpenDetail(mi)
            jf != null -> onOpenJellyfinDetail(jf)
        }
    }

    fun openEditorForFocused() {
        rails.getOrNull(focusedIndex)?.let { editingId = it.configId }
    }

    val defaultBiv = LocalBringIntoViewSpec.current
    Box(Modifier.fillMaxSize()) {
        Column(modifier.fillMaxSize().tvOverscan()) {
            // Titulek sekce NAHOŘE VLEVO — konzistentní s ostatními sekcemi (Objevovat/Hledat/Nastavení). VŽDY.
            Text(
                text = "Domů",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
            )
            // Immersive hero = info PRÁVĚ fokusované karty (název/rok/žánr/popis). Master toggle `immersiveHeader`;
            // STABILNÍ výška (bez per-řada gate → žádné prázdné zóny ani přeblikávání). OFF → rovnou řady.
            // Immersive POZADÍ (fanart) je full-screen za vším v TvShell. Text se mění recompose (bez fade blikání).
            if (immersiveHeader) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.32f),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    focusedInfo?.let {
                        TvImmersiveHeader(info = it, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    }
                }
            }
            // Vertikální list řad. `NoAutoScrollBiv` = žádný svislý scroll z fokusu karet (anti-bump); svislé
            // pozicování řeší explicitní `animateScrollToItem(focusedIndex)`. Uvnitř řad DEFAULT biv (horizontála).
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScrollBiv) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onPreviewKeyEvent { e ->
                            // Menu/Info (ovladače, které je mají) → editor. Root cause fix pro ovladače BEZ Menu =
                            // podržení OK na kartě (combinedClickable onLongClick v RailSection).
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Menu || e.key == Key.Info)) {
                                openEditorForFocused(); true
                            } else {
                                false
                            }
                        },
                ) {
                    itemsIndexed(rails, key = { _, r -> r.id }) { index, rail ->
                        RailSection(
                            rail = rail,
                            firstCardFocus = if (index == 0) firstFocus else null,
                            onItemClick = ::clickItem,
                            onItemFocused = { item -> focusedItem = item; focusedIndex = index },
                            onItemLongPress = { editingId = rail.configId },
                            showLabel = rail.showTitles,
                            rowBringIntoViewSpec = defaultBiv,
                        )
                    }
                }
            }
        }

        editingId?.let { id ->
            val cfg = allRows.firstOrNull { it.id == id } ?: return@let
            val pos = allRows.indexOfFirst { it.id == id }
            TvHomeRowEditor(
                config = cfg,
                canMoveUp = pos > 0,
                canMoveDown = pos in 0 until allRows.lastIndex,
                hiddenRows = allRows.filter { !it.enabled },
                onUpdate = { homeVm.updateRow(it) },
                onMove = { up -> homeVm.moveRow(id, up) },
                onHide = { homeVm.setRowEnabled(id, false); editingId = null },
                onUnhide = { unhideId -> homeVm.setRowEnabled(unhideId, true) },
                onAddRow = {
                    // Otevři výběr zdroje (řeší mezeru: dřív šla přidat jen Trakt řada).
                    pendingNewId = "custom_${System.currentTimeMillis()}"
                    editingId = null
                    showAddPicker = true
                },
                onDismiss = { editingId = null },
            )
        }

        if (showAddPicker) {
            TvAddRowPicker(
                libraries = libraries,
                newId = pendingNewId,
                onPick = { newRow ->
                    homeVm.addRow(newRow)
                    showAddPicker = false
                    editingId = newRow.id // rovnou otevři editaci nové řady (styl/řazení/limit)
                },
                onDismiss = { showAddPicker = false },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RailSection(
    rail: Rail,
    firstCardFocus: FocusRequester?,
    onItemClick: (HomeRowItem) -> Unit,
    onItemFocused: (HomeRowItem) -> Unit,
    onItemLongPress: () -> Unit,
    showLabel: Boolean = true,
    rowBringIntoViewSpec: BringIntoViewSpec,
) {
    // Vstup do řady (vertikální navigací i initial autofokus) směřuj VŽDY na 1. kartu. Bez tohoto default
    // directional focus search + souběžný snap-scroll (animateScrollToItem) přistál na 2. sloupci. Na první
    // řadě je enterFocus = firstCardFocus (slouží i initial requestFocus z parenta), jinde lokální requester.
    val enterFocus = firstCardFocus ?: remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxWidth()
            .focusGroup()
            .focusProperties { enter = { enterFocus } },
    ) {
        Text(
            text = rail.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        // Uvnitř řady obnov DEFAULT BringIntoViewSpec (parent list má ScrollToTop) → vodorovný scroll LazyRow
        // funguje normálně a nepropaguje svislé cukání do LazyColumn.
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringIntoViewSpec) {
        // COUCH DA4: rozestup karet z uživatelské volby (jeden multiplier pro všechny řady). Horizontální
        // contentPadding NEškálujeme (drží ořez u sidebaru), škáluje se jen mezera mezi kartami.
        val cardScale = LocalTvCardScale.current
        LazyRow(
            // vertical 10dp: prostor pro fokus lift (1.08×) + záři. horizontal 14dp: totéž vodorovně — LazyRow
            // ořezává na své bounds, tak PRVNÍ (a poslední) karta potřebuje odsazení ≥ scale přesah (~5dp) + záře
            // (~6dp), jinak se levý sloupec u sidebaru ořízne (user feedback OTA 297).
            horizontalArrangement = Arrangement.spacedBy(cardScale.spacing(12.dp)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            items(rail.items, key = { it.key }) { item ->
                Box(
                    Modifier.onFocusChanged { if (it.hasFocus) onItemFocused(item) },
                ) {
                    TvHomeCard(
                        item = item,
                        style = rail.style,
                        onClick = { onItemClick(item) },
                        // 1. karta nese enterFocus (cíl focusProperties.enter + initial autofokus první řady).
                        focusRequester = if (item.key == rail.items.first().key) enterFocus else null,
                        showLabel = showLabel,
                        onLongClick = onItemLongPress,
                    )
                }
            }
        }
        }
    }
}

/** Klientské operace pro řadu knihovny (skryj zhlédnuté + řazení + limit). */
private fun List<HomeRowItem>.applyConfig(cfg: HomeRowConfig): List<HomeRowItem> {
    var r = this
    if (cfg.params.boolParam(HomeRowParams.HIDE_WATCHED)) r = r.filter { !it.watched }
    r = when (cfg.sort) {
        HomeRowSort.YEAR_DESC -> r.sortedByDescending { it.year ?: 0 }
        HomeRowSort.ALPHA -> r.sortedBy { it.title.lowercase() }
        HomeRowSort.RATING -> r.sortedByDescending { it.mediaItem?.rating ?: -1f }
        HomeRowSort.RANDOM -> r.shuffled()
        HomeRowSort.RECENT, HomeRowSort.DEFAULT -> r
    }
    return r.take(cfg.limit.coerceIn(1, 60))
}

/** Jellyfin řadová položka → sjednocený [HomeRowItem]. */
private fun LibraryRowItem.toHomeRowItem() = HomeRowItem(
    key = "jf_$jellyfinId",
    title = name,
    year = year,
    posterUrl = imageUrl,
    landscapeUrl = landscapeUrl,
    progressPct = progressPct,
    watched = watched,
    mediaItem = mediaItem,
    jellyfinId = jellyfinId,
)
