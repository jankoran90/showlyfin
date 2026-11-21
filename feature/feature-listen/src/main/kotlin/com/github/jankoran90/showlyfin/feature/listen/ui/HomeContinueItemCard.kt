package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel
import com.github.jankoran90.showlyfin.feature.listen.HomeLayout
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.PlayerState

/** Jedna rozposlouchaná položka na Domů (samostatně i uvnitř rozbalené skupiny, viz [HomeEntry]). */
@Composable
internal fun HomeContinueItemCard(
    item: HomeViewModel.ContinueItem,
    vm: HomeViewModel,
    playerVm: AudiobookPlayerViewModel,
    bookVm: SeriesBookViewModel,
    playerState: PlayerState,
    layout: HomeLayout,
    seriesRules: Any?,
    otherAdultProfiles: List<ProfileEntity>,
    kidsLibraryIds: Set<String>,
    onOpenBook: (String) -> Unit,
    onOpenSourceEpisode: (sourceType: String, ref: String, title: String, episodeKey: String) -> Unit,
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    onPlayJfVideo: (jfItemId: String, title: String, resumeKey: String) -> Unit,
    onShareBook: (HomeViewModel.ContinueItem.Book) -> Unit,
    onShareEpisode: (HomeViewModel.ContinueItem.Episode) -> Unit,
) {
        when (item) {
            is HomeViewModel.ContinueItem.Book -> {
                val isPlaying = playerState.isActive && playerState.currentItemId == item.book.id
                // User (2026-08-16 14:42, „Sdílet s Nel u dětské knihy nedává smysl") —
                // vlastnictví/sdílení se knih z dětské knihovny netýká, dlouhý stisk pro ně nic nenabídne.
                val onLong: (() -> Unit)? =
                    if (otherAdultProfiles.isNotEmpty() && item.book.libraryId !in kidsLibraryIds) {
                        { onShareBook(item) }
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
                        placeholder = Icons.Rounded.Headphones,
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
                val play = {
                    when (val launch = vm.videoLaunch(item)) {
                        is HomeViewModel.VideoLaunch.External -> onPlayVideo(launch.url, launch.title, launch.posterUrl)
                        is HomeViewModel.VideoLaunch.Jellyfin -> onPlayJfVideo(launch.jfItemId, launch.title, launch.resumeKey)
                        null -> vm.playEpisode(item)
                    }
                }
                // User (2026-10-12): ťuk = otevřít pořad s dílem mezi ostatními (parita s knihou),
                // přehrání na ▶; staré „ťuk = přehrát" volitelně v Nastavení → Zobrazení Domů.
                // Starší YouTube díl mimo načtené okno nemá zdroj (sourceRef "") → nelze otevřít pořad,
                // ťuk ho přehraje jako dřív (Codex review).
                val opensShow = layout.episodeTapOpensShow && item.sourceRef.isNotBlank()
                val onClick = if (opensShow) {
                    {
                        onOpenSourceEpisode(
                            item.sourceType, item.sourceRef, item.sourceTitle,
                            item.episode.resumeKey ?: item.episode.id,
                        )
                    }
                } else play
                val onPlayPause = { if (loadedInQueue) playerVm.playPause() else vm.playEpisode(item) }
                val onLong: (() -> Unit)? = if (otherAdultProfiles.isNotEmpty()) ({ onShareEpisode(item) }) else null
                val bookLabel = remember(item, seriesRules, layout.seriesAsBook, layout.episodeAsBook) {
                    if (!layout.seriesAsBook || !layout.episodeAsBook) null
                    else homeBookLabel(
                        episodeTitle = item.episode.title,
                        sourceTitle = item.sourceTitle,
                        seriesTitle = item.episode.seriesTitle,
                        ruleTitle = bookVm.ruleTitle("${item.sourceType}:${item.sourceRef}", item.episode.title),
                    )
                }
                if (layout.list) {
                    HomeListCard(
                        title = bookLabel?.bookTitle ?: item.episode.title,
                        subtitle = bookLabel?.chapterLine ?: item.sourceTitle,
                        description = homeDescription(item.episode.description),
                        imageUrl = item.episode.imageUrl,
                        wideImage = item.sourceType == "youtube" || item.sourceType == "ctv",
                        progress = if (loadedInQueue && playerState.isPlaying && playerState.durationMs > 0) playerState.positionMs.toFloat() / playerState.durationMs else item.progress,
                        posMs = if (loadedInQueue && playerState.isPlaying && playerState.durationMs > 0) playerState.positionMs else item.posMs,
                        durMs = if (loadedInQueue && playerState.isPlaying && playerState.durationMs > 0) playerState.durationMs else item.durMs,
                        layout = layout,
                        placeholder = Icons.Rounded.Podcasts,
                        onClick = onClick,
                        isPlaying = loadedInQueue,
                        onLongClick = onLong,
                        onEndListening = { vm.resetEpisodeProgress(item) },
                        // Play na kartě = vždy POSLECH (user: „hlavně se spustí audio, na video
                        // si přepnu ve frontě"); načtená epizoda jen přepne pauzu.
                        playing = loadedInQueue && playerState.isPlaying,
                        onPlayPause = onPlayPause,
                    )
                } else {
                    ContinueEpisodeCard(
                        episode = bookLabel?.let { item.episode.copy(title = it.bookTitle) } ?: item.episode,
                        sourceTitle = bookLabel?.chapterLine ?: item.sourceTitle,
                        progress = item.progress,
                        isPlaying = loadedInQueue,
                        onClick = onClick,
                        onLongClick = onLong,
                        onEndListening = { vm.resetEpisodeProgress(item) },
                        // ▶ jen když ťuk otevírá pořad (jinak ťuk sám přehrává).
                        onPlayPause = if (opensShow) onPlayPause else null,
                        playing = loadedInQueue && playerState.isPlaying,
                    )
                }
            }
        }
}
