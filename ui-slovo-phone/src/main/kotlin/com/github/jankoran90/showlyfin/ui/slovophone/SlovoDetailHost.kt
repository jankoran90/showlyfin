package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.feature.listen.ListenSourceTarget
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookDetailScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookPlayerScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.CtvProgramScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.MergedPodcastScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDetailScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.RssPodcastScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.YoutubeChannelScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen

/**
 * Slovo (EXCISE/SHW-103 Krok 2) — lehký back-stack detailů poslechu. Zrcadlo poslechové části
 * `Destination` z `ShowlyfinPhoneApp`, ale bez filmových cílů: audiokniha/podcast detail + přehrávač +
 * zdrojové obrazovky (YouTube/RSS/ČT/sloučený pořad) + video přehrávač epizod. Navigace = seznam entrů
 * ve [SlovoShellContent]: push = přidej, back = odeber poslední; „rodič" = předchozí prvek stacku.
 */
sealed interface SlovoDetailEntry {
    data class AudiobookDetail(val itemId: String) : SlovoDetailEntry
    data class PodcastDetail(val itemId: String) : SlovoDetailEntry
    data class AudiobookPlayer(
        val itemId: String?,
        val fromStart: Boolean,
        val startSec: Double? = null,
        val episodeId: String? = null,
    ) : SlovoDetailEntry
    data class YoutubeChannel(val handle: String, val title: String, val highlightEpisodeKey: String? = null) : SlovoDetailEntry
    data class RssPodcast(val feedUrl: String, val title: String, val highlightEpisodeKey: String? = null) : SlovoDetailEntry
    data class CtvProgram(val ctvId: String, val title: String, val highlightEpisodeKey: String? = null) : SlovoDetailEntry
    data class MergedPodcast(val groupId: String, val title: String, val highlightEpisodeKey: String? = null) : SlovoDetailEntry
    /** Video verze epizody (RSS/YouTube/ČT „na výbornou") → sdílený přehrávač z :feature:feature-playback. */
    data class VideoPlayer(
        val itemId: String? = null,
        val externalUrl: String? = null,
        val title: String,
        val posterUrl: String? = null,
        val resumeKey: String? = null,
    ) : SlovoDetailEntry
}

/** True = tento cíl je fullscreen přehrávač (skryj MiniPlayer overlay nad ním). */
internal fun SlovoDetailEntry.isFullscreenPlayer(): Boolean =
    this is SlovoDetailEntry.AudiobookPlayer || this is SlovoDetailEntry.VideoPlayer

/**
 * Zdroj epizody → SLOUČENÁ obrazovka (TWINE), pokud je pořad propojený, jinac samostatný zdroj podle typu.
 * Sdílené shellem (onOpenSourceEpisode z Poslechu) i přehrávačem (onOpenSource). [type] = "youtube"/"ctv"/rss.
 */
internal fun linkedOrPlain(
    podcastLinkLookup: PodcastLinkLookupViewModel,
    type: String,
    ref: String,
    title: String,
    epKey: String?,
): SlovoDetailEntry {
    val group = podcastLinkLookup.groupFor(type, ref)
    return when {
        group != null -> SlovoDetailEntry.MergedPodcast(group.id, group.title ?: title, highlightEpisodeKey = epKey)
        type == "youtube" -> SlovoDetailEntry.YoutubeChannel(ref, title, highlightEpisodeKey = epKey)
        type == "ctv" -> SlovoDetailEntry.CtvProgram(ref, title, highlightEpisodeKey = epKey)
        else -> SlovoDetailEntry.RssPodcast(ref, title, highlightEpisodeKey = epKey)
    }
}

/**
 * Vykreslí aktuální detail. [onPush] přidá nový cíl na stack, [onPop] se vrátí o krok zpět,
 * [onGoToPoslech] vyprázdní stack a přepne shell na sekci Poslech (pro offline epizody).
 */
@Composable
internal fun SlovoDetail(
    entry: SlovoDetailEntry,
    onPush: (SlovoDetailEntry) -> Unit,
    onPop: () -> Unit,
    onGoToPoslech: () -> Unit,
    listenVm: ListenViewModel,
    podcastLinkLookup: PodcastLinkLookupViewModel,
) {
    // Přehrávač → klik na cover → seznam dílů rodiče (napříč zdroji); offline → přepni na Poslech.
    val onOpenSource: (ListenSourceTarget) -> Unit = { target ->
        when (target) {
            is ListenSourceTarget.Offline -> {
                listenVm.openOfflinePodcast(target.showTitle, target.episodeKey)
                onGoToPoslech()
            }
            is ListenSourceTarget.Audiobook -> onPush(SlovoDetailEntry.AudiobookDetail(target.itemId))
            is ListenSourceTarget.Podcast -> onPush(SlovoDetailEntry.PodcastDetail(target.itemId))
            is ListenSourceTarget.Rss -> onPush(linkedOrPlain(podcastLinkLookup, "rss", target.feedUrl, target.title, target.episodeKey))
            is ListenSourceTarget.Youtube -> onPush(linkedOrPlain(podcastLinkLookup, "youtube", target.handle, target.title, target.episodeKey))
        }
    }

    when (entry) {
        is SlovoDetailEntry.AudiobookDetail -> AudiobookDetailScreen(
            itemId = entry.itemId,
            onBack = onPop,
            onPlay = { itemId, fromStart, startSec ->
                onPush(SlovoDetailEntry.AudiobookPlayer(itemId, fromStart, startSec))
            },
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.PodcastDetail -> PodcastDetailScreen(
            itemId = entry.itemId,
            onBack = onPop,
            onPlayEpisode = { itemId, episodeId, fromStart, startSec ->
                onPush(SlovoDetailEntry.AudiobookPlayer(itemId, fromStart, startSec, episodeId))
            },
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.AudiobookPlayer -> AudiobookPlayerScreen(
            itemId = entry.itemId,
            fromStart = entry.fromStart,
            startSec = entry.startSec,
            episodeId = entry.episodeId,
            onBack = onPop,
            onOpenSource = onOpenSource,
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.YoutubeChannel -> YoutubeChannelScreen(
            channel = entry.handle,
            channelTitle = entry.title,
            highlightEpisodeKey = entry.highlightEpisodeKey,
            onBack = onPop,
            onPlayVideo = { url, title, poster ->
                onPush(SlovoDetailEntry.VideoPlayer(externalUrl = url, title = title, posterUrl = poster))
            },
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.RssPodcast -> RssPodcastScreen(
            feedUrl = entry.feedUrl,
            title = entry.title,
            highlightEpisodeKey = entry.highlightEpisodeKey,
            onBack = onPop,
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            onPlayVideo = { jfItemId, videoTitle, resumeKey ->
                onPush(SlovoDetailEntry.VideoPlayer(itemId = jfItemId, title = videoTitle, resumeKey = resumeKey))
            },
            onPlayYoutubeVideo = { url, videoTitle, poster ->
                onPush(SlovoDetailEntry.VideoPlayer(externalUrl = url, title = videoTitle, posterUrl = poster))
            },
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.CtvProgram -> CtvProgramScreen(
            ctvId = entry.ctvId,
            title = entry.title,
            highlightEpisodeKey = entry.highlightEpisodeKey,
            onBack = onPop,
            onPlayVideo = { url, title, poster ->
                onPush(SlovoDetailEntry.VideoPlayer(externalUrl = url, title = title, posterUrl = poster))
            },
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.MergedPodcast -> MergedPodcastScreen(
            groupId = entry.groupId,
            title = entry.title,
            highlightEpisodeKey = entry.highlightEpisodeKey,
            onBack = onPop,
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            onPlayVideo = { url, videoTitle, poster ->
                onPush(SlovoDetailEntry.VideoPlayer(externalUrl = url, title = videoTitle, posterUrl = poster))
            },
            onUnlinked = onPop,
        )
        is SlovoDetailEntry.VideoPlayer -> PlaybackScreen(
            itemId = entry.itemId ?: "",
            externalUrl = entry.externalUrl,
            externalTitle = entry.title,
            externalPosterUrl = entry.posterUrl,
            resumeKey = entry.resumeKey,
            onBack = onPop,
            onPlaybackFailed = { onPop() },
        )
    }
}
