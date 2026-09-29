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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Podcasts
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
    // Sdílený (activity-scoped) přehrávač — stejný jako MiniPlayer; Play/Pauza z karty bez navigace.
    val playerVm: AudiobookPlayerViewModel = hiltViewModel()
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
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> LazyVerticalGrid(
                // User (2026-09-29) — Nastavení → Zobrazení Domů: mřížka (default) nebo jeden sloupec.
                columns = if (layout.list) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                // BUG (2026-09-29, user screenshot): bez spodní rezervy poslední řada zůstala napořád
                // schovaná pod mini-playerem — 96.dp stejně jako YoutubeChannelScreen aj.
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(if (layout.list) 18.dp else 14.dp),
            ) {
                items(items, key = ::continueItemKey) { item ->
                    when (item) {
                        is HomeViewModel.ContinueItem.Book -> {
                            val isPlaying = playerState.isActive && playerState.currentItemId == item.book.id
                            // User (2026-08-16 14:42, „Sdílet s Nel u dětské knihy nedává smysl") —
                            // vlastnictví/sdílení se knih z dětské knihovny netýká, dlouhý stisk pro ně nic nenabídne.
                            val onLong: (() -> Unit)? =
                                if (otherAdultProfiles.isNotEmpty() && item.book.libraryId !in kidsLibraryIds) {
                                    { shareBook = item }
                                } else null
                            if (layout.list) {
                                val b = item.book
                                HomeListCard(
                                    title = b.title,
                                    subtitle = listOfNotNull(b.author, b.narrator?.let { "čte $it" }).joinToString(" · "),
                                    description = b.seriesName?.let { s -> b.seriesSequence?.let { "$s #$it" } ?: s },
                                    imageUrl = b.coverUrl,
                                    wideImage = false,
                                    // Hrající položka = živá pozice z přehrávače (uložená se obnoví až refreshem).
                                    progress = if (isPlaying && playerState.isPlaying && playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else b.progress.toFloat(),
                                    posMs = if (isPlaying && playerState.isPlaying && playerState.durationMs > 0) playerState.positionMs else (b.currentTimeSec * 1000).toLong(),
                                    durMs = if (isPlaying && playerState.isPlaying && playerState.durationMs > 0) playerState.durationMs else (b.durationSec * 1000).toLong(),
                                    layout = layout,
                                    placeholder = Icons.Default.Headphones,
                                    onClick = { onOpenBook(b.id) },
                                    isPlaying = isPlaying,
                                    onLongClick = onLong,
                                    onEndListening = { vm.resetBookProgress(b) },
                                    // User (2026-09-29): Play/Pauza přímo na kartě — Domů zůstává, nic se neotevírá.
                                    playing = isPlaying && playerState.isPlaying,
                                    onPlayPause = { if (isPlaying) playerVm.playPause() else playerVm.open(b.id, fromStart = false) },
                                )
                            } else {
                                AudiobookCard(
                                    book = item.book,
                                    onClick = { onOpenBook(item.book.id) },
                                    isPlaying = isPlaying,
                                    onLongClick = onLong,
                                    onEndListening = { vm.resetBookProgress(item.book) },
                                )
                            }
                        }
                        is HomeViewModel.ContinueItem.Episode -> {
                            val loadedInQueue = playerState.isActive &&
                                playerState.currentEpisodeId == (item.episode.resumeKey ?: item.episode.id)
                            // BUG (2026-09-04, user „dej možnost zobrazit je a rovnou naskočit"): ťuk
                            // rovnou přehraje, dřív otvíral jen zdrojovou obrazovku (parita s knihami
                            // zůstává jinde — epizoda na rozdíl od knihy nepotřebuje kapitolní kontext).
                            // ADAPT (2026-09-04): spustí SPRÁVNÝ režim (video, pokud vede) — dřív vždy jen audio.
                            // BUG (2026-09-29, user „Cukrfree na Domů nutí video, mám rozposlouchané audio"):
                            // režim = jak se epizoda naposled hrála ([HomeViewModel.videoLaunch] → LastPlaybackMode),
                            // neznámo = audio. (Dřívější „načtená ve frontě → audio" zrušeno: po „Přepnout na
                            // video" zůstává audio načtené v pauze a přebilo by naposled sledované video.)
                            val onClick = {
                                when (val launch = vm.videoLaunch(item)) {
                                    is HomeViewModel.VideoLaunch.External -> onPlayVideo(launch.url, launch.title, launch.posterUrl)
                                    is HomeViewModel.VideoLaunch.Jellyfin -> onPlayJfVideo(launch.jfItemId, launch.title, launch.resumeKey)
                                    null -> vm.playEpisode(item)
                                }
                            }
                            val onLong: (() -> Unit)? = if (otherAdultProfiles.isNotEmpty()) ({ shareEpisode = item }) else null
                            if (layout.list) {
                                HomeListCard(
                                    title = item.episode.title,
                                    subtitle = item.sourceTitle,
                                    description = homeDescription(item.episode.description),
                                    imageUrl = item.episode.imageUrl,
                                    wideImage = item.sourceType == "youtube" || item.sourceType == "ctv",
                                    progress = if (loadedInQueue && playerState.isPlaying && playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else item.progress,
                                    posMs = if (loadedInQueue && playerState.isPlaying && playerState.durationMs > 0) playerState.positionMs else item.posMs,
                                    durMs = if (loadedInQueue && playerState.isPlaying && playerState.durationMs > 0) playerState.durationMs else item.durMs,
                                    layout = layout,
                                    placeholder = Icons.Default.Podcasts,
                                    onClick = onClick,
                                    isPlaying = loadedInQueue,
                                    onLongClick = onLong,
                                    onEndListening = { vm.resetEpisodeProgress(item) },
                                    // Play na kartě = vždy POSLECH (user: „hlavně se spustí audio, na video
                                    // si přepnu ve frontě"); načtená epizoda jen přepne pauzu.
                                    playing = loadedInQueue && playerState.isPlaying,
                                    onPlayPause = { if (loadedInQueue) playerVm.playPause() else vm.playEpisode(item) },
                                )
                            } else {
                                ContinueEpisodeCard(
                                    episode = item.episode,
                                    sourceTitle = item.sourceTitle,
                                    progress = item.progress,
                                    isPlaying = loadedInQueue,
                                    onClick = onClick,
                                    onLongClick = onLong,
                                    onEndListening = { vm.resetEpisodeProgress(item) },
                                )
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

private fun continueItemKey(item: HomeViewModel.ContinueItem): String = when (item) {
    is HomeViewModel.ContinueItem.Book -> "book:${item.book.id}"
    is HomeViewModel.ContinueItem.Episode -> "ep:${item.sourceType}:${item.sourceRef}:${item.episode.id}"
}
