package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.abs.model.EpisodeDownload
import com.github.jankoran90.showlyfin.feature.listen.ListenMode
import com.github.jankoran90.showlyfin.feature.listen.ListenUiState
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel

/**
 * Poslechová sekce — přepínač Audioknihy ↔ Podcasty, knihovní chips + grid obálek s progressem.
 * Vše v Material 3 Expressive tématu (ListenExpressiveTheme). ABS login je v Nastavení.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    onOpenBook: (itemId: String) -> Unit,
    onOpenPodcast: (itemId: String) -> Unit,
    onPlayEpisode: (itemId: String, episodeId: String) -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showDownloads by remember { mutableStateOf(false) }

    // PRESET (SHW-65) — po návratu z Nastavení převezmi případně změněné pořadí v Poslechu.
    LaunchedEffect(Unit) { viewModel.reloadOrderPrefs() }

    ListenExpressiveTheme {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (!state.isConfigured) {
                CenteredMessage(
                    "Poslech zatím není nastaven.\nPřihlas se k Audiobookshelf serveru v Nastavení → Poslech (Audiobookshelf).",
                )
            } else {
                // PRESET (SHW-65) — pořadí záložek dle nastavení (Audioknihy / Podcasty první).
                val modes = remember(state.booksFirst) {
                    if (state.booksFirst) listOf(ListenMode.BOOKS, ListenMode.PODCASTS)
                    else listOf(ListenMode.PODCASTS, ListenMode.BOOKS)
                }
                key(state.booksFirst) {
                    val pagerState = rememberPagerState(
                        initialPage = modes.indexOf(state.mode).coerceAtLeast(0),
                    ) { modes.size }
                    val scope = rememberCoroutineScope()
                    // Swipe ⇄ přepíná režim (a tím i načítání dat).
                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.setMode(modes[pagerState.currentPage])
                    }
                    Column(Modifier.fillMaxSize()) {
                        if (state.isOffline) OfflineBanner()
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            modes.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = pagerState.currentPage == i,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                                ) { Text(if (m == ListenMode.BOOKS) "Audioknihy" else "Podcasty") }
                            }
                        }

                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            when (modes[page]) {
                                ListenMode.BOOKS -> BooksContent(state, viewModel, onOpenBook)
                                ListenMode.PODCASTS -> PodcastsContent(
                                    state, viewModel, onOpenPodcast,
                                    downloadCount = downloads.size,
                                    onOpenDownloads = { showDownloads = true },
                                    onOpenSource = onOpenSource,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloads) {
        DownloadsSheet(
            downloads = downloads,
            onDismiss = { showDownloads = false },
            onPlay = { dl -> onPlayEpisode(dl.itemId, dl.episodeId); showDownloads = false },
            onDelete = { viewModel.deleteDownload(it) },
            onDeleteAll = { viewModel.deleteAllDownloads(); showDownloads = false },
        )
    }
}

@Composable
private fun BooksContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenBook: (String) -> Unit,
) {
    when {
        state.isLoading && state.books.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.books.isEmpty() -> CenteredMessage(state.error)

        else -> Column(Modifier.fillMaxSize()) {
            if (state.libraries.size > 1) {
                LibraryChips(
                    libraries = state.libraries.map { it.id to it.name },
                    selectedId = state.selectedLibraryId,
                    onSelect = viewModel::selectLibrary,
                )
            }
            if (state.books.isEmpty() && !state.isLoading) {
                CenteredMessage(
                    if (state.isOffline) {
                        "Jsi offline a nemáš žádné stažené audioknihy.\nStáhni je v detailu knihy, dokud jsi připojený."
                    } else {
                        "V této knihovně zatím nejsou žádné audioknihy."
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        AudiobookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            downloaded = book.id in state.downloadedBookIds,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastsContent(
    state: ListenUiState,
    viewModel: ListenViewModel,
    onOpenPodcast: (String) -> Unit,
    downloadCount: Int,
    onOpenDownloads: () -> Unit,
    onOpenSource: (com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource) -> Unit,
) {
    // Plan CASTAWAY — „Stažené epizody" musí zůstat dostupné i offline / při chybě načtení podcastů,
    // proto je chip nad obsahovou částí (ne uvnitř úspěšné větve).
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // PRESET (SHW-65): YouTube i RSS jsou teď zdroje v Podcastech (SourceCard) — samostatný
            // chip „YouTube podcasty" zrušen jako redundantní. YouTube kanál se otevře přes svůj zdroj.
            if (downloadCount > 0) {
                AssistChip(
                    onClick = onOpenDownloads,
                    label = { Text("Stažené · $downloadCount") },
                    leadingIcon = { Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        // PRESET (SHW-65) — vlastní zdroje (YouTube/RSS) splynou s Podcasty: jsou nezávislé na ABS,
        // proto se obsah ukáže, i když ABS podcasty chybí (offline / prázdná knihovna).
        val hasContent = state.podcasts.isNotEmpty() || state.customSources.isNotEmpty()
        when {
            state.isLoading && !hasContent ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            state.isOffline && !hasContent ->
                CenteredMessage(
                    if (downloadCount > 0) {
                        "Offline — online seznam podcastů není dostupný.\nStažené epizody najdeš nahoře."
                    } else {
                        "Jsi offline a nemáš žádné stažené epizody."
                    },
                )

            state.error != null && !hasContent -> CenteredMessage(state.error)

            else -> {
                if (state.podcastLibraries.size > 1) {
                    LibraryChips(
                        libraries = state.podcastLibraries.map { it.id to it.name },
                        selectedId = state.selectedPodcastLibraryId,
                        onSelect = viewModel::selectPodcastLibrary,
                    )
                }
                if (!hasContent && !state.isLoading) {
                    CenteredMessage("V této knihovně zatím nejsou žádné podcasty.\nVlastní zdroje přidáš v postranním menu → Zdroje podcastů.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Vlastní zdroje (YouTube/RSS) nahoře — sdílené ze serveru, nezávislé na vybrané knihovně.
                        items(state.customSources, key = { "src:${it.id}" }) { src ->
                            SourceCard(source = src, onClick = { onOpenSource(src) })
                        }
                        items(state.podcasts, key = { it.id }) { podcast ->
                            PodcastCard(podcast = podcast, onClick = { onOpenPodcast(podcast.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Plan CASTAWAY — proužek „Offline", když není síť; vysvětlí, proč jsou vidět jen stažené věci. */
@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Offline — zobrazeny jen stažené položky.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryChips(
    libraries: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        libraries.forEach { (id, name) ->
            FilterChip(
                selected = id == selectedId,
                onClick = { onSelect(id) },
                label = { Text(name) },
            )
        }
    }
}

/** Správa offline stažení: seznam stažených epizod (klik = přehrát offline) + smazat / smazat vše. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsSheet(
    downloads: List<EpisodeDownload>,
    onDismiss: () -> Unit,
    onPlay: (EpisodeDownload) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Stažené epizody · ${downloads.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (downloads.isNotEmpty()) {
                TextButton(onClick = onDeleteAll) { Text("Smazat vše") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (downloads.isEmpty()) {
            Text(
                "Žádné stažené epizody.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(Modifier.height(420.dp)) {
                columnItems(downloads, key = { it.episodeId }) { dl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(dl) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                dl.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildList {
                                dl.podcastTitle?.let { add(it) }
                                if (dl.sizeBytes > 0) add(formatSize(dl.sizeBytes))
                            }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(dl.episodeId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Smazat stažení", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}

/** Vystředěná zpráva — funguje v Column i Box (vlastní fillMaxSize Box). */
@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
