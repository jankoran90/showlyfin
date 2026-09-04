package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val otherAdultProfiles by vm.otherAdultProfiles.collectAsStateWithLifecycle()
    val kidsLibraryIds by vm.kidsLibraryIds.collectAsStateWithLifecycle()
    // PROFIL (2026-08-16) — dlouhý stisk epizody na Domů → „Sdílet s…" (celý zdroj epizody, ne jen tuhle).
    var shareEpisode by remember { mutableStateOf<HomeViewModel.ContinueItem.Episode?>(null) }
    var shareBook by remember { mutableStateOf<HomeViewModel.ContinueItem.Book?>(null) }

    Box(modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            items.isEmpty() -> Text(
                "Zatím nic rozposloucháno.\nZačni poslouchat audioknihu nebo epizodu a najdeš ji tady.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = ::continueItemKey) { item ->
                    when (item) {
                        is HomeViewModel.ContinueItem.Book -> AudiobookCard(
                            book = item.book,
                            onClick = { onOpenBook(item.book.id) },
                            isPlaying = playerState.isActive && playerState.currentItemId == item.book.id,
                            // User (2026-08-16 14:42, „Sdílet s Nel u dětské knihy nedává smysl") —
                            // vlastnictví/sdílení se knih z dětské knihovny netýká, dlouhý stisk pro ně nic nenabídne.
                            onLongClick = if (otherAdultProfiles.isNotEmpty() && item.book.libraryId !in kidsLibraryIds) {
                                { shareBook = item }
                            } else null,
                            onEndListening = { vm.resetBookProgress(item.book) },
                        )
                        is HomeViewModel.ContinueItem.Episode -> ContinueEpisodeCard(
                            episode = item.episode,
                            sourceTitle = item.sourceTitle,
                            progress = item.progress,
                            isPlaying = playerState.isActive &&
                                playerState.currentEpisodeId == (item.episode.resumeKey ?: item.episode.id),
                            // BUG (2026-09-04, user „dej možnost zobrazit je a rovnou naskočit"): ťuk
                            // rovnou přehraje, dřív otvíral jen zdrojovou obrazovku (parita s knihami
                            // zůstává jinde — epizoda na rozdíl od knihy nepotřebuje kapitolní kontext).
                            // ADAPT (2026-09-04): spustí SPRÁVNÝ režim (video, pokud vede) — dřív vždy jen audio.
                            onClick = {
                                when (val launch = vm.videoLaunch(item)) {
                                    is HomeViewModel.VideoLaunch.External -> onPlayVideo(launch.url, launch.title, launch.posterUrl)
                                    is HomeViewModel.VideoLaunch.Jellyfin -> onPlayJfVideo(launch.jfItemId, launch.title, launch.resumeKey)
                                    null -> vm.playEpisode(item)
                                }
                            },
                            onLongClick = if (otherAdultProfiles.isNotEmpty()) ({ shareEpisode = item }) else null,
                            onEndListening = { vm.resetEpisodeProgress(item) },
                        )
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

private fun continueItemKey(item: HomeViewModel.ContinueItem): String = when (item) {
    is HomeViewModel.ContinueItem.Book -> "book:${item.book.id}"
    is HomeViewModel.ContinueItem.Episode -> "ep:${item.sourceType}:${item.sourceRef}:${item.episode.id}"
}
