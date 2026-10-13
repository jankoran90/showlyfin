package com.github.jankoran90.showlyfin.ui.filmyphone

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

    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    // Na stránce vždy všechny řady (přepínače řad z Nastavení patří sekci Podcasty).
    val rows = remember { PodcastPrefs.rows(prefs).copy(continueRow = true, newRow = true, channelsRow = true) }
    val shelves = remember(state, marks, translated, translating, watched, rows) {
        podcastShelves(state, marks, translated, translating, rows, watched)
    }

    FilmyPageBar(
        onMenu = onMenu,
        trailing = {
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

    val list = if (tab == 0) shelves.newItems else shelves.channelItems
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
        if (shelves.continueItems.isNotEmpty()) {
            item(key = "dalsi") {
                Box(Modifier.padding(horizontal = 16.dp)) { FilmyNextUpRow(items = shelves.continueItems, onClick = onItem) }
            }
        }
        item(key = "taby") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Nové díly") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Kanály a pořady") })
            }
        }
        items(list, key = { "p-${it.key}" }) { item ->
            FilmyPodcastRow(item = item, onClick = { onItem(item) }, onLongClick = longPressOf(item))
        }
    }
}
