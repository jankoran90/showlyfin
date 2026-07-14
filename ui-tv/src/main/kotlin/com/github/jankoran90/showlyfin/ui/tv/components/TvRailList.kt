package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
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
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.LocalTvCardScale
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import kotlinx.coroutines.flow.first

/**
 * TENFOOT/COUCH — SDÍLENÝ řádkový render TV sekcí. Jedna vykreslená řada (řada HomeVM, konkrétní Jellyfin
 * knihovna, Trakt kategorie…) reprezentovaná [TvRail]. Extrahováno z `home/TvHomeScreen.kt`, aby stejný Kodi
 * Arctic Fuse „vertikální seznam horizontálních railů" pohánělo Domů i ostatní sekce (Knihovna/Trakt) — z jednoho
 * modelu vyplývá immersive hlavička, autofokus i long-press editor konzistentně napříč shellem.
 */
data class TvRail(
    val id: String,
    val title: String,
    val style: HomeCardStyle,
    val items: List<HomeRowItem>,
    /** Identita řady pro editor (u Domů = `HomeRowConfig.id`). */
    val configId: String,
    /** Popisky pod/na kartách (immersive Netflix styl je skrývá — per řada). */
    val showTitles: Boolean = true,
    /** KOLO2 (M): immersive hlavička (název/rok/popis fokusované karty nahoře) — per řada. */
    val immersiveHeader: Boolean = false,
    /**
     * A1 fix: řada se ještě NAČÍTÁ (data nedorazila). Drží pozici skeletonem místo aby chyběla a pak
     * naskočila mezi ostatní (posun/„přehazování"). Prázdná + `loading=false` = doběhlo prázdné → řadu
     * volající do seznamu vůbec nezařadí (skryje se).
     */
    val loading: Boolean = false,
)

/**
 * BringIntoViewSpec, který na vertikální ose NIKDY nescrolluje (vždy 0). Nasazen na LazyColumn: fokus karty
 * (a jeho případná změna bounds) tak nemůže hnout svislým seznamem — svislý „bump" při vodorovném projíždění
 * karet je konstrukčně vyloučen. Svislé pozicování řad řeší VÝHRADNĚ explicitní `animateScrollToItem(focusedIndex)`
 * (klíčovaný jen na změnu ŘADY). Uvnitř řad se obnoví DEFAULT biv, aby horizontální LazyRow scrolloval normálně.
 */
@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScrollBiv = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * Vertikální seznam horizontálních railů. [sectionTitle] = nadpis sekce vlevo nahoře. Fokusovaná karta hlásí
 * [onFocusItem] nahoru (immersive pozadí, gated `immersive`) a řídí lokální hero header (gated [immersiveHeader]).
 * [onRequestEditor] (nepovinné) = otevři editor řady (long-press karty NEBO Menu/Info ovladače); `null` =
 * sekce zatím bez editoru. [onItemClick] dostane vybranou položku (dispatch na detail řeší volající).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvRailList(
    rails: List<TvRail>,
    sectionTitle: String,
    immersive: Boolean,
    immersiveHeader: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    onItemClick: (HomeRowItem) -> Unit,
    modifier: Modifier = Modifier,
    onRequestEditor: ((configId: String) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    // A2 fix: fokus vázán na STABILNÍ identitu řady (`configId`/`id`), ne na index do proměnlivého `rails`.
    // Když async doběhne řada NAD fokusovanou, index by zůstal stálý a scroll skočil jinam; s id se pozice
    // dopočítá vždy aktuálně (scroll-follow drží fokusovanou řadu na místě).
    var focusedRailId by remember { mutableStateOf<String?>(null) }
    var focusedItem by remember { mutableStateOf<HomeRowItem?>(null) }

    // Immersive: fokusovaná karta → info nahoru (pozadí, gated `immersive` v TvShell) + lokální hero header
    // (gated `immersiveHeader`). Info počítej, když je aspoň jedno zapnuté; pozadí posílej jen když `immersive`.
    val focusedInfo: ImmersiveInfo? =
        if (immersive || immersiveHeader) focusedItem?.toImmersiveInfo() else null
    LaunchedEffect(focusedInfo, immersive) { onFocusItem(if (immersive) focusedInfo else null) }

    // DEFINITIVNÍ fix bumpu (user 2026-07-13, kolo 2): vertikální scroll je 100 % pod explicitní kontrolou —
    // zarovnání fokusované řady se děje JEN při změně ŘADY (`focusedIndex`), nikdy z bring-into-view. Auto
    // vertikální bring-into-view je totiž zdrojem bumpu: fokus-scale/lift karty mění její bounds, což
    // `BringIntoViewSpec` na LazyColumn interpretuje jako potřebu svislého scrollu i při čistě VODOROVNÉM
    // projíždění karet. Řešení = spec, který na vertikální ose vrací VŽDY 0 (viz `NoAutoScrollBiv`), takže
    // vodorovný pohyb NIKDY nehne seznamem; svislé pozicování obstará tento efekt (klíčovaný jen na `focusedIndex`).
    LaunchedEffect(focusedRailId, rails) {
        val idx = rails.indexOfFirst { it.id == focusedRailId }
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
    }

    // Autofokus na PRVNÍ kartu první řady S OBSAHEM (skeleton/loading řady přeskoč — nemají fokusovatelnou
    // kartu). A2 fix: spustí se JEN JEDNOU (`didInitialFocus`), ne při každé změně první řady — jinak
    // naskakující async řady opakovaně přebíjely fokus zpět nahoru („přehazování").
    val firstFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    val firstContentRailId = rails.firstOrNull { it.items.isNotEmpty() }?.id
    LaunchedEffect(firstContentRailId) {
        if (firstContentRailId == null || didInitialFocus) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == firstContentRailId } }.first { it }
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
        didInitialFocus = true
    }

    fun openEditorForFocused() {
        rails.firstOrNull { it.id == focusedRailId }?.let { onRequestEditor?.invoke(it.configId) }
    }

    val defaultBiv = LocalBringIntoViewSpec.current
    Column(modifier.fillMaxSize().tvOverscan()) {
        // Titulek sekce NAHOŘE VLEVO — konzistentní napříč sekcemi (Domů/Objevovat/Hledat/Nastavení). VŽDY.
        Text(
            text = sectionTitle,
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
                itemsIndexed(rails, key = { _, r -> r.id }) { _, rail ->
                    RailSection(
                        rail = rail,
                        firstCardFocus = if (rail.id == firstContentRailId) firstFocus else null,
                        onItemClick = onItemClick,
                        onItemFocused = { item -> focusedItem = item; focusedRailId = rail.id },
                        onItemLongPress = { onRequestEditor?.invoke(rail.configId) },
                        showLabel = rail.showTitles,
                        rowBringIntoViewSpec = defaultBiv,
                    )
                }
            }
        }
    }
}

/**
 * A1 fix — placeholder řady, která se ještě načítá: pár šedých karet drží výšku/pozici, aby řada
 * po doběhnutí dat nenaskočila náhle a neposunula ostatní. Landscape styl = širší dlaždice.
 */
@Composable
private fun SkeletonRow(style: HomeCardStyle) {
    val landscape = style == HomeCardStyle.LANDSCAPE
    val w = if (landscape) 180.dp else 108.dp
    val h = if (landscape) 104.dp else 156.dp
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        repeat(6) {
            Box(
                Modifier
                    .width(w)
                    .height(h)
                    .background(color, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RailSection(
    rail: TvRail,
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
    val firstKey = rail.items.firstOrNull()?.key
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
            // A1 fix: řada se ještě načítá (data nedorazila) → skeleton karty drží pozici/výšku, aby se
            // řada neobjevila náhle a neposunula ostatní. Po doběhnutí se buď naplní obsahem, nebo (prázdná)
            // ji volající ze seznamu vypustí.
            if (rail.items.isEmpty() && rail.loading) {
                SkeletonRow(style = rail.style)
                return@CompositionLocalProvider
            }
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
                            focusRequester = if (item.key == firstKey) enterFocus else null,
                            showLabel = showLabel,
                            onLongClick = onItemLongPress,
                        )
                    }
                }
            }
        }
    }
}
