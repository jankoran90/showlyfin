package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.feature.listen.ListenSourceTarget
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookDetailScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookEditScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookPlayerScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.CtvProgramScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.MergedPodcastScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDetailScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.RssPodcastScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.UploadAudiobookScreen
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
    /** DROPSHIP F2c — úprava metadata + cover u stávající audioknihy (long press v seznamu). */
    data class AudiobookEdit(val itemId: String, val title: String, val author: String? = null) : SlovoDetailEntry
    data class PodcastDetail(val itemId: String) : SlovoDetailEntry
    data class AudiobookPlayer(
        val itemId: String?,
        val fromStart: Boolean,
        val startSec: Double? = null,
        val episodeId: String? = null,
    ) : SlovoDetailEntry
    data class YoutubeChannel(
        val handle: String,
        val title: String,
        val highlightEpisodeKey: String? = null,
        /** SLOVO-KIDS-EPISODE — non-null = dětská cesta, jen tahle AUTO-detekovaná série (celý zdroj skrytý). */
        val seriesFilter: String? = null,
    ) : SlovoDetailEntry
    data class RssPodcast(
        val feedUrl: String,
        val title: String,
        val highlightEpisodeKey: String? = null,
        /** SLOVO-KIDS-EPISODE — non-null = dětská cesta, jen tahle AUTO-detekovaná série (celý zdroj skrytý). */
        val seriesFilter: String? = null,
    ) : SlovoDetailEntry
    data class CtvProgram(
        val ctvId: String,
        val title: String,
        val highlightEpisodeKey: String? = null,
        /** SLOVO-KIDS-EPISODE — non-null = dětská cesta, jen tahle AUTO-detekovaná série (celý zdroj skrytý). */
        val seriesFilter: String? = null,
    ) : SlovoDetailEntry
    data class MergedPodcast(val groupId: String, val title: String, val highlightEpisodeKey: String? = null) : SlovoDetailEntry
    /** Video verze epizody (RSS/YouTube/ČT „na výbornou") → sdílený přehrávač z :feature:feature-playback. */
    data class VideoPlayer(
        val itemId: String? = null,
        val externalUrl: String? = null,
        val title: String,
        val posterUrl: String? = null,
        val resumeKey: String? = null,
        /** User (2026-08-22) — YouTube video zpátky do ExoPlayer: nese synteticky "yt:<id>" imdb,
         *  ať přehrávač nabídne AI překlad titulků stejným mechanismem jako u filmů (LINGUA). */
        val subtitleQuery: SubtitleQuery? = null,
        /** 🔴 INCIDENT (2026-08-22, živě chyceno logcatem po nasazení) — spousta YouTube videí NEMÁ
         *  HLS formát (jen progresivní itag 18), backend na to `/api/yt/hls/…` odpovídá schválně 404
         *  („HLS pro toto video nedostupné" — dokumentováno v `routes/youtube.py`, ale klient tenhle
         *  fallback nikdy neimplementoval, dřív to řešila externí YouTube appka sama). Progresivní
         *  360p URL jako záložka — [onPlaybackFailed] u této entry ji jednou zkusí místo pádu na seznam. */
        val fallbackUrl: String? = null,
    ) : SlovoDetailEntry
    /** DROPSHIP F2 — Nahrát audioknihu z telefonu do ABS knihovny. */
    data object UploadAudiobook : SlovoDetailEntry
}

/** True = tento cíl je fullscreen přehrávač (skryj MiniPlayer overlay nad ním). */
internal fun SlovoDetailEntry.isFullscreenPlayer(): Boolean =
    this is SlovoDetailEntry.AudiobookPlayer || this is SlovoDetailEntry.VideoPlayer

/**
 * Zdroj epizody → SLOUČENÁ obrazovka (TWINE), pokud je pořad propojený, jinac samostatný zdroj podle typu.
 * Sdílené shellem (onOpenSourceEpisode z Poslechu) i přehrávačem (onOpenSource). [type] = "youtube"/"ctv"/rss.
 */
/**
 * Proxy stream URL (`.../api/yt/stream/{id}?...` nebo `.../api/yt/hls/{id}.m3u8?...`, viz
 * `UploaderApi.ytVideoUrl`) má syrové YouTube ID v cestě — vytáhneme ho odsud, ať se nesahá na
 * sdílený `feature-listen` kód (stejný modul používá i app-filmy). Slouží k sestavení syntetického
 * "yt:<id>" klíče pro AI překlad titulků (viz [youtubeSubtitleQuery]).
 */
private fun youtubeIdFromProxyUrl(url: String): String? =
    Regex("""/yt/(?:stream|hls)/([^/?.]+)""").find(url)?.groupValues?.get(1)

/**
 * 720p HLS proxy URL → progresivní 360p záložka (stejný base+key, jiná cesta/kvalita). Backend
 * `/api/yt/hls/…` schválně 404uje, když video HLS formát nemá (jen itag 18 progresiv) — 360p
 * přes `/api/yt/stream/…?kind=video&quality=360` funguje prakticky vždy. `null` = url nesedí na
 * očekávaný HLS proxy tvar (žádná záložka, ať se nic nerozbije na neznámém formátu URL).
 */
private fun youtube360Fallback(hlsUrl: String): String? {
    val m = Regex("""^(.*)/api/yt/hls/([^/?.]+)\.m3u8\?(.*)$""").find(hlsUrl) ?: return null
    val (base, id, query) = m.destructured
    val key = Regex("""key=([^&]+)""").find(query)?.value ?: return null
    return "$base/api/yt/stream/$id?kind=video&quality=360&$key"
}

/**
 * User (2026-08-22) — „youtube videa ze slova musíme zpět do exoplayer": AI titulky bez IMDb (běžný
 * LINGUA gate čeká na `tt…` id) fungují přes stejné pole `SubtitleQuery.imdb`, jen synteticky "yt:<id>"
 * — backend na tenhle prefix v `/api/subtitles/{imdb}` (žádné CZ hledání, rovnou 0 kandidátů) a
 * `/api/subtitles/translate/{imdb}` (anglické titulky z YouTube místo OpenSubtitles) reaguje zvlášť.
 * Bez rozpoznatelného id (neproxovaná/cizí URL) vrací null → přehraje se beze titulků, beze pádu.
 */
private fun youtubeSubtitleQuery(url: String, title: String): SubtitleQuery? =
    youtubeIdFromProxyUrl(url)?.let { id -> SubtitleQuery(imdb = "yt:$id", title = title, origTitle = title) }

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
    // SLOVO-KIDS-EPISODE (2026-08-15) — dětský profil: vždy jen audio, video volby v RSS/YouTube/ČT/
    // sloučeném pořadu se úplně skryjí (i u TWINE-propojeného páru, nikdy YouTube odkaz dětem).
    val activeProfile by listenVm.activeProfile.collectAsStateWithLifecycle()
    val audioOnly = activeProfile?.isAdmin == false
    // User (2026-08-22) — „youtube videa ze slova musíme zpět do exoplayer": dřív šlo VŽDY ven do
    // YouTube appky/prohlížeče (2026-08-15 rozhodnutí), teď hraje ROVNOU ve sdíleném přehrávači
    // (:feature:feature-playback — stejný, co používá Filmy), ať appka nabídne stejný styl titulků
    // + AI překlad (viz [youtubeSubtitleQuery]).
    val playYoutubeVideo: (url: String, title: String, poster: String?) -> Unit = { url, title, poster ->
        onPush(
            SlovoDetailEntry.VideoPlayer(
                externalUrl = url, title = title, posterUrl = poster,
                subtitleQuery = youtubeSubtitleQuery(url, title),
                fallbackUrl = youtube360Fallback(url),
            ),
        )
    }
    // SLOVO-KIDS-EPISODE — admin dlouhý stisk na AUTO-detekované sérii (RssPodcastScreen) potřebuje
    // vědět, které série jsou dětskému profilu už schválené (toggle stav v action sheetu).
    val kidsVisibleSourceKeys by listenVm.kidsVisibleSourceKeys.collectAsStateWithLifecycle()

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
            audioOnly = audioOnly,
            seriesFilter = entry.seriesFilter,
            isAdmin = activeProfile?.isAdmin == true,
            kidsVisibleSeriesKeys = kidsVisibleSourceKeys,
            onSetSeriesVisibleForKids = { key, visible -> listenVm.setSourceVisibleForKids(setOf(key), visible) },
            onBack = onPop,
            onPlayVideo = playYoutubeVideo,
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.RssPodcast -> RssPodcastScreen(
            feedUrl = entry.feedUrl,
            title = entry.title,
            highlightEpisodeKey = entry.highlightEpisodeKey,
            audioOnly = audioOnly,
            seriesFilter = entry.seriesFilter,
            isAdmin = activeProfile?.isAdmin == true,
            kidsVisibleSeriesKeys = kidsVisibleSourceKeys,
            onSetSeriesVisibleForKids = { key, visible -> listenVm.setSourceVisibleForKids(setOf(key), visible) },
            onBack = onPop,
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            onPlayVideo = { jfItemId, videoTitle, resumeKey ->
                onPush(SlovoDetailEntry.VideoPlayer(itemId = jfItemId, title = videoTitle, resumeKey = resumeKey))
            },
            onPlayYoutubeVideo = playYoutubeVideo,
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.CtvProgram -> CtvProgramScreen(
            ctvId = entry.ctvId,
            title = entry.title,
            highlightEpisodeKey = entry.highlightEpisodeKey,
            audioOnly = audioOnly,
            seriesFilter = entry.seriesFilter,
            isAdmin = activeProfile?.isAdmin == true,
            kidsVisibleSeriesKeys = kidsVisibleSourceKeys,
            onSetSeriesVisibleForKids = { key, visible -> listenVm.setSourceVisibleForKids(setOf(key), visible) },
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
            audioOnly = audioOnly,
            onBack = onPop,
            onOpenAudioPlayer = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) },
            onPlayVideo = playYoutubeVideo,
            onUnlinked = onPop,
        )
        is SlovoDetailEntry.AudiobookEdit -> AudiobookEditScreen(
            itemId = entry.itemId,
            initialTitle = entry.title,
            initialAuthor = entry.author,
            onBack = onPop,
            modifier = Modifier.fillMaxSize(),
        )
        is SlovoDetailEntry.VideoPlayer -> PlaybackScreen(
            itemId = entry.itemId ?: "",
            externalUrl = entry.externalUrl,
            externalTitle = entry.title,
            externalPosterUrl = entry.posterUrl,
            resumeKey = entry.resumeKey,
            subtitleQuery = entry.subtitleQuery,
            onBack = onPop,
            // 🔴 INCIDENT (2026-08-22) — HLS 404 (viz VideoPlayer.fallbackUrl) → zkus JEDNOU 360p
            // progresiv místo pádu na seznam. NAHRADÍ vršek stacku (pop pak push), ne pushne navrch —
            // jinak by druhé selhání (360p taky nejde) spadlo zpátky na PRVNÍ (už známě mrtvou) entry
            // a zacyklilo se. `fallbackUrl = null` v nové entry = druhé selhání už nemá kam couvnout.
            onPlaybackFailed = {
                val fb = entry.fallbackUrl
                if (fb != null) {
                    onPop()
                    onPush(entry.copy(externalUrl = fb, fallbackUrl = null))
                } else {
                    onPop()
                }
            },
        )
        SlovoDetailEntry.UploadAudiobook -> UploadAudiobookScreen(
            onBack = onPop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
