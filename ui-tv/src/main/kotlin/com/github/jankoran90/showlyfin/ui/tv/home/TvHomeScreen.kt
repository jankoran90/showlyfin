package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.core.domain.home.SidebarItem
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.TvHomeCard
import kotlinx.coroutines.flow.first

/** Jedna vykreslená řada (HomeVM řada NEBO expandovaná Jellyfin knihovna). */
private data class Rail(
    val id: String,
    val title: String,
    val style: HomeCardStyle,
    val items: List<HomeRowItem>,
    val configId: String,
)

/**
 * TENFOOT (SHW-87) — TV DOMOV REDESIGN (Kodi Arctic Fuse styl). Levý [TvHomeSidebar] + vertikální
 * seznam horizontálních railů dle uživatelské konfigurace ([TvHomeViewModel] + [LibraryRowsViewModel]
 * pro Jellyfin knihovny). Obsah je VŽDY hned fokusovaný (první karta první řady). Menu na řadě =
 * inline editor ([TvHomeRowEditor]). Vzdušné, konfigurovatelné, soustředěné na obsah.
 */
@Composable
fun TvHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
    homeVm: TvHomeViewModel = hiltViewModel(),
    libraryVm: LibraryRowsViewModel = hiltViewModel(),
) {
    val rowConfigs by homeVm.rowConfigs.collectAsStateWithLifecycle()
    val allRows by homeVm.allRows.collectAsStateWithLifecycle()
    val states by homeVm.states.collectAsStateWithLifecycle()
    val sidebarEntries by homeVm.sidebar.collectAsStateWithLifecycle()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { libraryVm.load() }
    // Lazy načtení řad (mimo JF knihovny — ty jede LibraryRowsViewModel).
    LaunchedEffect(rowConfigs) {
        rowConfigs.filter { it.source != HomeRowSourceType.JELLYFIN_LIBRARIES }
            .forEach { homeVm.ensureRowLoaded(it) }
    }

    // Sestav ploché raily (jen neprázdné → domov je vzdušný, obsah hned).
    val rails: List<Rail> = buildList {
        rowConfigs.forEach { cfg ->
            if (cfg.source == HomeRowSourceType.JELLYFIN_LIBRARIES) {
                libraryState.rows.forEach { lib ->
                    add(
                        Rail(
                            id = "lib_${lib.libraryId}",
                            title = lib.libraryName,
                            style = cfg.cardStyle,
                            items = lib.items.map { it.toHomeRowItem() },
                            configId = cfg.id,
                        ),
                    )
                }
            } else {
                val st = states[cfg.id]
                if (st != null && st.items.isNotEmpty()) {
                    add(Rail(id = cfg.id, title = cfg.title, style = cfg.cardStyle, items = st.items, configId = cfg.id))
                }
            }
        }
    }

    val sidebarItems = sidebarEntries.filter { it.enabled }.mapNotNull { SidebarItem.fromName(it.item) }
    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<String?>(null) }

    // Anti-bump: fokusovaná řada se plynule odscrolluje na stabilní pozici (žádný „bump" u 2. řady).
    LaunchedEffect(focusedIndex) {
        runCatching { listState.animateScrollToItem(focusedIndex.coerceAtLeast(0)) }
    }

    // Autofokus na PRVNÍ kartu PRVNÍ řady (obsah, ne sidebar/nadpis). Čeká na umístění uzlu.
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
        Row(Modifier.fillMaxSize()) {
            TvHomeSidebar(
                items = sidebarItems,
                active = SidebarItem.DOMU,
                onSelect = { item ->
                    when (item) {
                        SidebarItem.DOMU -> runCatching { firstFocus.requestFocus() }
                        SidebarItem.HLEDAT -> onOpenSearch()
                        SidebarItem.NASTAVENI -> onOpenSettings()
                        SidebarItem.OBLIBENE -> onOpenWatchlist()
                        SidebarItem.KNIHOVNA -> {
                            val libIdx = rails.indexOfFirst { it.configId == "jellyfin_libraries" }
                            if (libIdx >= 0) focusedIndex = libIdx
                        }
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
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
                Text(
                    text = "Domů",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(rails, key = { _, r -> r.id }) { index, rail ->
                        RailSection(
                            rail = rail,
                            firstCardFocus = if (index == 0) firstFocus else null,
                            onItemClick = ::clickItem,
                            onFocused = { focusedIndex = index },
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
                onUpdate = { homeVm.updateRow(it) },
                onMove = { up -> homeVm.moveRow(id, up) },
                onHide = { homeVm.setRowEnabled(id, false); editingId = null },
                onAddRow = {
                    val newId = "custom_${allRows.size}_${cfg.hashCode()}"
                    homeVm.addRow(
                        HomeRowConfig(
                            id = newId,
                            source = HomeRowSourceType.DISCOVER,
                            title = "Nová řada",
                            cardStyle = HomeCardStyle.POSTER,
                            params = mapOf(HomeRowParams.TAB to "movies", HomeRowParams.FILTER to "trending"),
                        ),
                    )
                    editingId = newId
                },
                onDismiss = { editingId = null },
            )
        }
    }
}

@Composable
private fun RailSection(
    rail: Rail,
    firstCardFocus: FocusRequester?,
    onItemClick: (HomeRowItem) -> Unit,
    onFocused: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onFocused() },
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
                TvHomeCard(
                    item = item,
                    style = rail.style,
                    onClick = { onItemClick(item) },
                    focusRequester = if (firstCardFocus != null && item.key == rail.items.first().key) firstCardFocus else null,
                )
            }
        }
    }
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
