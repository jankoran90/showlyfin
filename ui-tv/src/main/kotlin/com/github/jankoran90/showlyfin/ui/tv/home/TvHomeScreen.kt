package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams.boolParam
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.toHomeRowItem
import com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvRail
import com.github.jankoran90.showlyfin.ui.tv.components.TvRailList

/**
 * TENFOOT (SHW-87) — TV domov (Kodi Arctic Fuse styl). Skládá uživatelskou konfiguraci řad ([TvHomeViewModel])
 * + per-knihovna řady ([LibraryRowsViewModel]) do plochého seznamu [TvRail] a předává je sdílenému renderu
 * [TvRailList] (společný s ostatními sekcemi). Sidebar žije v [com.github.jankoran90.showlyfin.ui.tv.TvShell];
 * tady je jen tělo domova + editor řady (menu na řadě / long-press karty).
 */
@Composable
fun TvHomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    // LAPIDARY S4b: karta řady „Uloženo k přehrání" (playDirectly) → detail v režimu one-click (autoplay).
    onOpenDetailPlay: (MediaItem) -> Unit = onOpenDetail,
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
    // Lazy načtení řad (jen ZAPNUTÉ; mimo JF knihovny — ty jedou přes LibraryRowsViewModel). `store.rows`
    // vrací i vypnuté řady (konzument filtruje `enabled`) → bez filtru by se skryté řady načítaly i zobrazovaly.
    LaunchedEffect(rowConfigs) {
        rowConfigs.filter { it.enabled && it.source != HomeRowSourceType.JELLYFIN_LIBRARY && it.source != HomeRowSourceType.JELLYFIN_LIBRARIES }
            .forEach { homeVm.ensureRowLoaded(it) }
    }

    // Sestav ploché raily v pořadí rowConfigs, JEN ze ZAPNUTÝCH řad s obsahem. `store.rows` obsahuje i
    // vypnuté (skryté) řady — filtr `enabled` je nutný, jinak by se skrytá řada zobrazila. Prázdná/načítající
    // se řada se nezařadí (objeví se, až dorazí data). Skeleton (OTA 320) stažen — dělal „prázdné covery" u
    // pomalu načítaných řad a zviditelnil skryté řady; stabilnější je klasické „zobraz, až jsou data".
    val rails: List<TvRail> = buildList {
        rowConfigs.filter { it.enabled }.forEach { cfg ->
            when (cfg.source) {
                HomeRowSourceType.JELLYFIN_LIBRARY -> {
                    val libId = cfg.params[HomeRowParams.LIBRARY_ID]
                    val libRow = libraryState.rows.firstOrNull { it.libraryId == libId }
                    val items = libRow?.items?.map { it.toHomeRowItem() }?.applyConfig(cfg).orEmpty()
                    if (items.isNotEmpty()) {
                        add(TvRail(cfg.id, cfg.title.ifBlank { libRow?.libraryName.orEmpty() }, cfg.cardStyle, items, cfg.id, cfg.showTitles, cfg.immersiveHeader))
                    }
                }
                HomeRowSourceType.JELLYFIN_LIBRARIES -> Unit // deprecated meta — migrováno pryč
                else -> {
                    val st = states[cfg.id]
                    if (st != null && st.items.isNotEmpty()) {
                        add(TvRail(cfg.id, cfg.resolvedTitle(), cfg.cardStyle, st.items, cfg.id, cfg.showTitles, cfg.immersiveHeader))
                    }
                }
            }
        }
    }

    var editingId by remember { mutableStateOf<String?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }
    var pendingNewId by remember { mutableStateOf("") }
    // Seznam knihoven profilu pro picker „Přidat řadu" (konkrétní knihovna / Nejnovější v knihovně).
    val libraries = remember(libraryState.rows) {
        libraryState.rows.map { LibrarySummary(it.libraryId, it.libraryName, it.collectionType) }
    }

    fun clickItem(item: HomeRowItem) {
        val mi = item.mediaItem
        val jf = item.jellyfinId
        when {
            // Klik na kartu VŽDY jen otevře detail — žádný autoplay (user: nechci autoplay při otevření karty).
            // Přehrání se spouští až explicitně z detailu (tlačítko Přehrát / zdroj). `playDirectly`/one-click zrušen.
            mi != null -> onOpenDetail(mi)
            jf != null -> onOpenJellyfinDetail(jf)
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvRailList(
            rails = rails,
            sectionTitle = "Domů",
            immersive = immersive,
            immersiveHeader = immersiveHeader,
            onFocusItem = onFocusItem,
            onItemClick = ::clickItem,
            onRequestEditor = { configId -> editingId = configId },
            modifier = modifier,
        )

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
