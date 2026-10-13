package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastItemKey
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastPrefs
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastsViewModel
import com.github.jankoran90.showlyfin.feature.discover.podcasts.podcastShelves

/**
 * AMFITEÁTR F5 — Podcasty jako stránka VEDLE Filmotéky (user 2026-10-13 17:29: „jak je dělaná Filmotéka,
 * tak bude vedle další sekce"): nahoře „Další díly" (rozkoukané, i z hledání a z jiného zařízení), pod
 * tím Nové díly / Kanály a pořady. Lišta pageru: ⊕ Spravovat zdroje. Stejná data jako sekce Podcasty.
 */
@Composable
fun FilmyPodcastsPage(
    onMenu: () -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    onPlay: (PodcastsViewModel.PlayTarget) -> Unit,
    onManageSources: () -> Unit,
    vm: PodcastsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val translated by vm.translated.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onEnter() }
    var confirmFor by remember { mutableStateOf<Pair<PodcastSource, SourceEpisode>?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val layoutCtx = LocalContext.current
    var layout by remember { mutableStateOf(PodcastPrefs.phoneLayout(prefsOf(layoutCtx))) }

    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    // Na stránce vždy všechny řady (přepínače řad z Nastavení patří sekci Podcasty).
    val rows = remember { PodcastPrefs.rows(prefs).copy(continueRow = true, newRow = true, channelsRow = true) }
    val shelves = remember(state, marks, translated, translating, watched, rows) {
        podcastShelves(state, marks, translated, translating, rows, watched)
    }

    val ctx = LocalContext.current
    FilmyPageBar(
        onMenu = onMenu,
        trailing = {
            // Mřížka ↔ seznam (řady po kanálech jen z Nastavení → Pořady). Volba se pamatuje.
            if (layout != "channels") FilmyViewToggle(if (layout == "grid") com.github.jankoran90.showlyfin.core.ui.ViewMode.GRID else com.github.jankoran90.showlyfin.core.ui.ViewMode.LIST) {
                layout = if (layout == "grid") "list" else "grid"
                prefsOf(ctx).edit().putString(PodcastPrefs.PHONE_LAYOUT, layout).apply()
            }
            if (vm.canFollow()) IconButton(onClick = onManageSources) {
                Icon(Icons.Rounded.AddCircle, contentDescription = "Spravovat zdroje", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {}

    confirmFor?.let { (src, ep) ->
        FilmyTranslateConfirmDialog(
            title = ep.title,
            alreadyTranslated = "yt:${ep.id}" in translated,
            onConfirm = { vm.translate(src, ep); confirmFor = null },
            onDismiss = { confirmFor = null },
        )
    }

    val onItem: (HomeRowItem) -> Unit = { item ->
        when (val ref = PodcastItemKey.parse(item.key)) {
            is PodcastItemKey.Source -> vm.sourceOf(ref.sourceKey)?.let(onOpenSource)
            is PodcastItemKey.Episode -> {
                val src = vm.sourceOf(ref.sourceKey)
                val ep = vm.episodeOf(ref.sourceKey, ref.episodeId)
                if (src != null && ep != null) vm.playTarget(src, ep)?.let(onPlay)
            }
            null -> Unit
        }
    }
    val longPressOf: (HomeRowItem) -> (() -> Unit)? = { item ->
        val ref = PodcastItemKey.parse(item.key) as? PodcastItemKey.Episode
        val src = ref?.let { vm.sourceOf(it.sourceKey) }
        val ep = ref?.let { vm.episodeOf(it.sourceKey, it.episodeId) }
        if (src != null && ep != null && vm.canTranslate(src)) ({ confirmFor = src to ep }) else null
    }

    val list = when (tab) {
        1 -> shelves.channelItems
        2 -> shelves.newItems.filter { it.subtitle?.startsWith("CZ") == true }
        3 -> shelves.newItems.filter { !it.watched && it.progressPct == null }
        else -> shelves.newItems
    }
    if (shelves.continueItems.isEmpty() && shelves.newItems.isEmpty() && shelves.channelItems.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                if (state.loading) "Načítám podcasty…"
                else state.error ?: "Zatím tu nejsou žádné podcasty s videem. Přidej kanál nebo pořad tlačítkem ⊕ vpravo nahoře.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    val header: @Composable () -> Unit = {
        if (shelves.continueItems.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) { FilmyNextUpRow(items = shelves.continueItems, onClick = onItem) }
        }
    }
    val chips: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Nové díly", "Kanály a pořady", "Přeložené", "Nezhlédnuté").forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
            }
        }
    }
    when (layout) {
        "channels" -> PoradyChannelRows(state = state, header = header, onItem = onItem, onOpenSource = onOpenSource,
            itemOf = { src, ep -> com.github.jankoran90.showlyfin.feature.discover.podcasts.episodeItem(src, ep, marks[ep.resumeKey ?: ep.id], translated, translating, watched) },
            longPressOf = longPressOf)
        "list" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
            item(key = "dalsi") { header() }
            item(key = "taby") { chips() }
            items(list, key = { "p-${it.key}" }) { item ->
                FilmyPodcastRow(item = item, onClick = { onItem(item) }, onLongClick = longPressOf(item))
            }
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "dalsi", span = { GridItemSpan(maxLineSpan) }) { header() }
            item(key = "taby", span = { GridItemSpan(maxLineSpan) }) { chips() }
            gridItems(list, key = { "g-${it.key}" }) { item ->
                PoradyCard(item = item, onClick = { onItem(item) }, onLongClick = longPressOf(item))
            }
        }
    }
}

private fun prefsOf(ctx: android.content.Context) =
    ctx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)

/** Karta dílu v mřížce: náhled 16:9 s průběhem a štítkem (Zhlédnuto / CZ), název, kanál. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PoradyCard(item: HomeRowItem, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.clip(MaterialTheme.shapes.small).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            coil3.compose.AsyncImage(
                model = item.landscapeUrl ?: item.posterUrl, contentDescription = item.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            val pct = item.progressPct
            if (pct != null && !item.watched) {
                Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart).fillMaxWidth(pct / 100f).height(3.dp)
                    .background(MaterialTheme.colorScheme.primary))
            }
            if (item.watched) {
                Text(
                    "Zhlédnuto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        item.subtitle?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** Varianta C: každý kanál / pořad = vodorovná řada nejnovějších dílů + „vše ›" (karta kanálu). */
@Composable
private fun PoradyChannelRows(
    state: PodcastsViewModel.UiState,
    header: @Composable () -> Unit,
    onItem: (HomeRowItem) -> Unit,
    onOpenSource: (PodcastSource) -> Unit,
    itemOf: (PodcastSource, SourceEpisode) -> HomeRowItem,
    longPressOf: (HomeRowItem) -> (() -> Unit)?,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "dalsi") { header() }
        items(state.sources.filter { state.episodes[it.key].orEmpty().isNotEmpty() }, key = { "k-${it.key}" }) { src ->
            androidx.compose.foundation.layout.Column {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenSource(src) }.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(src.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("vše ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.episodes[src.key].orEmpty().take(12), key = { "e-${it.id}" }) { ep ->
                        val item = itemOf(src, ep)
                        PoradyCard(item = item, onClick = { onItem(item) }, onLongClick = longPressOf(item), modifier = Modifier.width(180.dp))
                    }
                }
            }
        }
    }
}
