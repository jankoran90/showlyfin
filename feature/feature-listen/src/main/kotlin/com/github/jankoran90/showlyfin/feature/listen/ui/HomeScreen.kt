package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel

/**
 * Domů (user 2026-08-15: „home obrazovka, co se vždy otevře, naposledy přehráno + pokračovat,
 * hezky v mřížce"). Sjednocené rozposlouchané napříč audioknihami i podcastovými epizodami
 * (viz [HomeViewModel]), seřazené podle posledního poslechu. Tap = otevře detail/kontext (stejná
 * konvence jako zbytek appky — nespouští playback rovnou z dlaždice).
 */
@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    modifier: Modifier = Modifier,
    // ADAPT (2026-09-04, user „když spustím video a ukončím to, návratem z Domů se na video
    // nenaváže, jakoby náš přehrávač fronty neuměl spouštět video"): dřív „Pokračovat" VŽDY pustilo
    // jen audio frontu, i když video vedlo (delší pozice). Teď [HomeViewModel.videoLaunch] rozhodne
    // podle [HomeViewModel.ContinueItem.Episode.mode] — externí URL (YouTube/ČT) nebo JF video (RSS).
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit = { _, _, _ -> },
    onPlayJfVideo: (jfItemId: String, title: String, resumeKey: String) -> Unit = { _, _, _ -> },
) {
    val vm: HomeViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    // Slovo 2026-10-13 — díl ze série = karta knihy (ruční knihy z pravidel, ČT řady, N/M, prefix).
    val bookVm: com.github.jankoran90.showlyfin.feature.listen.SeriesBookViewModel = hiltViewModel()
    val seriesRules by bookVm.seriesRules.collectAsStateWithLifecycle()
    // Sdílený (activity-scoped) přehrávač — stejný jako MiniPlayer; Play/Pauza z karty bez navigace.
    val playerVm: AudiobookPlayerViewModel = hiltViewModel()
    val otherAdultProfiles by vm.otherAdultProfiles.collectAsStateWithLifecycle()
    val kidsLibraryIds by vm.kidsLibraryIds.collectAsStateWithLifecycle()
    // PROFIL (2026-08-16) — dlouhý stisk epizody na Domů → „Sdílet s…" (celý zdroj epizody, ne jen tuhle).
    var shareEpisode by remember { mutableStateOf<HomeViewModel.ContinueItem.Episode?>(null) }
    var shareBook by remember { mutableStateOf<HomeViewModel.ContinueItem.Book?>(null) }
    // User 2026-11-19: rozposlouchané jednoho pořadu / série knih pod jednou hlavní kartou (Nastavení → Zobrazení Domů).
    val entries = remember(items, layout.groupContinue) { groupHomeItems(items, layout.groupContinue) }
    var expandedGroups by rememberSaveable { mutableStateOf(emptySet<String>()) }

    Box(modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            items.isEmpty() -> Text(
                "Zatím nic rozposloucháno.\nZačni poslouchat audioknihu nebo epizodu a najdeš ji tady.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> {
              // Jedna karta položky (samostatně i v rozbalené skupině) — tělo v [HomeContinueItemCard].
              val card: @Composable (HomeViewModel.ContinueItem) -> Unit = { item ->
                HomeContinueItemCard(
                    item = item, vm = vm, playerVm = playerVm, bookVm = bookVm, playerState = playerState,
                    layout = layout, seriesRules = seriesRules, otherAdultProfiles = otherAdultProfiles,
                    kidsLibraryIds = kidsLibraryIds, onOpenBook = onOpenBook, onOpenSourceEpisode = onOpenSourceEpisode,
                    onPlayVideo = onPlayVideo, onPlayJfVideo = onPlayJfVideo,
                    onShareBook = { shareBook = it }, onShareEpisode = { shareEpisode = it },
                )
              }
              LazyVerticalGrid(
                // User (2026-09-29) — Nastavení → Zobrazení Domů: mřížka (default) nebo jeden sloupec.
                columns = if (layout.list) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                // BUG (2026-09-29, user screenshot): bez spodní rezervy poslední řada zůstala napořád
                // schovaná pod mini-playerem — 96.dp stejně jako YoutubeChannelScreen aj.
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(if (layout.list) 18.dp else 14.dp),
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is HomeEntry.Single -> item(key = entry.key) { card(entry.item) }
                        is HomeEntry.Group -> {
                            val expanded = entry.key in expandedGroups
                            item(key = "group:${entry.key}") {
                                val latest = entry.latest
                                val latestLoaded = playerState.isActive && when (latest) {
                                    is HomeViewModel.ContinueItem.Book -> playerState.currentItemId == latest.book.id
                                    is HomeViewModel.ContinueItem.Episode ->
                                        playerState.currentEpisodeId == (latest.episode.resumeKey ?: latest.episode.id)
                                }
                                HomeGroupCard(
                                    group = entry,
                                    expanded = expanded,
                                    layout = layout,
                                    latestLoaded = latestLoaded,
                                    playing = latestLoaded && playerState.isPlaying,
                                    playerState = playerState,
                                    onToggle = {
                                        expandedGroups = if (expanded) expandedGroups - entry.key else expandedGroups + entry.key
                                    },
                                    onPlayPause = {
                                        when {
                                            latestLoaded -> playerVm.playPause()
                                            latest is HomeViewModel.ContinueItem.Book -> playerVm.open(latest.book.id, fromStart = false)
                                            latest is HomeViewModel.ContinueItem.Episode -> vm.playEpisode(latest)
                                        }
                                    },
                                )
                            }
                            if (expanded) entry.items.forEach { child ->
                                item(key = continueItemKey(child)) { card(child) }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    shareEpisode?.let { item ->
        val key = "${item.sourceType}:${item.sourceRef}"
        val owner = vm.ownerOfSourceKey(key)
        ListenEpisodeActionSheet(
            title = item.sourceTitle,
            infoLine = vm.ownershipInfoLine(owner, otherAdultProfiles.filter { vm.isSourceSharedWith(setOf(key), it) }),
            actions = otherAdultProfiles.map { target ->
                val shared = vm.isSourceSharedWith(setOf(key), target)
                ListenEpisodeAction(
                    if (shared) Icons.Default.Visibility else Icons.Default.Share,
                    if (shared) "Přestat sdílet s ${target.name}" else "Sdílet s ${target.name}",
                ) { vm.setSourceSharedWith(setOf(key), target.id, !shared) }
            },
            onDismiss = { shareEpisode = null },
        )
    }

    shareBook?.let { item ->
        val owner = vm.ownerOfBook(item.book.id)
        ListenEpisodeActionSheet(
            title = item.book.title,
            infoLine = vm.ownershipInfoLine(owner, otherAdultProfiles.filter { vm.isBookSharedWith(item.book.id, it) }),
            actions = otherAdultProfiles.map { target ->
                val shared = vm.isBookSharedWith(item.book.id, target)
                ListenEpisodeAction(
                    if (shared) Icons.Default.Visibility else Icons.Default.Share,
                    if (shared) "Přestat sdílet s ${target.name}" else "Sdílet s ${target.name}",
                ) { vm.setBookSharedWith(item.book.id, target.id, !shared) }
            },
            onDismiss = { shareBook = null },
        )
    }
}
