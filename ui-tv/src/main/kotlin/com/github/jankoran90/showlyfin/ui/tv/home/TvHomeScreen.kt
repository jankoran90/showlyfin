package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.focus.FocusRequester
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
)

/**
 * TENFOOT (SHW-87) — TV domov (Kodi Arctic Fuse styl). Vertikální seznam horizontálních railů dle
 * uživatelské konfigurace ([TvHomeViewModel]) + per-knihovna řady ([LibraryRowsViewModel]). Sidebar žije
 * v [com.github.jankoran90.showlyfin.ui.tv.TvShell]; tady je jen tělo domova. Fokusovaná karta hlásí
 * [onFocusItem] nahoru (immersive pozadí) a řídí lokální hero header. Menu na řadě = inline editor.
 */
@Composable
fun TvHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    immersive: Boolean,
    onFocusItem: (ImmersiveInfo?) -> Unit,
    modifier: Modifier = Modifier,
    homeVm: TvHomeViewModel = hiltViewModel(),
    libraryVm: LibraryRowsViewModel = hiltViewModel(),
) {
    val rowConfigs by homeVm.rowConfigs.collectAsStateWithLifecycle()
    val allRows by homeVm.allRows.collectAsStateWithLifecycle()
    val states by homeVm.states.collectAsStateWithLifecycle()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { libraryVm.load() }
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
                        add(Rail(cfg.id, cfg.title.ifBlank { libRow?.libraryName.orEmpty() }, cfg.cardStyle, items, cfg.id, cfg.showTitles))
                    }
                }
                HomeRowSourceType.JELLYFIN_LIBRARIES -> Unit // deprecated meta — migrováno pryč
                else -> {
                    val st = states[cfg.id]
                    if (st != null && st.items.isNotEmpty()) {
                        add(Rail(cfg.id, cfg.resolvedTitle(), cfg.cardStyle, st.items, cfg.id, cfg.showTitles))
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

    // Immersive: fokusovaná karta → info nahoru (pozadí) + lokální hero header.
    val focusedInfo: ImmersiveInfo? = if (immersive) focusedItem?.toImmersiveInfo() else null
    LaunchedEffect(focusedInfo) { onFocusItem(focusedInfo) }

    // Anti-bump: scrolluj JEN u řad pod první (index 0 je nahoře) → žádná pračka s počátečním fokusem.
    LaunchedEffect(focusedIndex) {
        if (focusedIndex > 0) runCatching { listState.animateScrollToItem(focusedIndex) }
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

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .tvOverscan()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Menu || e.key == Key.Info)) {
                        openEditorForFocused(); true
                    } else {
                        false
                    }
                },
        ) {
            if (immersive) {
                // Rezervuj hero prostor vždy (stabilní layout, žádný skok po autofokusu); header sedí dole.
                // 0.34 (dřív 0.42) — méně mrtvého místa nahoře, víc řad vidět; osamocený titulek u resume řad nepřevažuje.
                Box(Modifier.weight(0.34f).fillMaxWidth()) {
                    TvImmersiveHeader(
                        info = focusedInfo,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 8.dp),
                    )
                }
            } else {
                Text(
                    text = "Domů",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(rails, key = { _, r -> r.id }) { index, rail ->
                    RailSection(
                        rail = rail,
                        firstCardFocus = if (index == 0) firstFocus else null,
                        onItemClick = ::clickItem,
                        onItemFocused = { item -> focusedItem = item; focusedIndex = index },
                        showLabel = rail.showTitles,
                    )
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

@Composable
private fun RailSection(
    rail: Rail,
    firstCardFocus: FocusRequester?,
    onItemClick: (HomeRowItem) -> Unit,
    onItemFocused: (HomeRowItem) -> Unit,
    showLabel: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .focusGroup(),
    ) {
        Text(
            text = rail.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            items(rail.items, key = { it.key }) { item ->
                Box(
                    Modifier.onFocusChanged { if (it.hasFocus) onItemFocused(item) },
                ) {
                    TvHomeCard(
                        item = item,
                        style = rail.style,
                        onClick = { onItemClick(item) },
                        focusRequester = if (firstCardFocus != null && item.key == rail.items.first().key) firstCardFocus else null,
                        showLabel = showLabel,
                    )
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
