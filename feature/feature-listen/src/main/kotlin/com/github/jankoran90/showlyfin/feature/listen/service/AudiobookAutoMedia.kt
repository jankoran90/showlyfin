package com.github.jankoran90.showlyfin.feature.listen.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.Podcast
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode

// Buildery MediaItemů + čistí pomocníci pro Android Auto strom — vyříznuto z AudiobookPlayerService
// (anti-monolit). Bezstavové (jen doménové argumenty + companion konstanty Service), proto top-level
// `internal`. Konstanty prefixů/klíčů zůstávají v AudiobookPlayerService.Companion (referují je i jiné
// soubory), tady se na ně odkazuje kvalifikovaně.

/** Browsable (neplayable) uzel pro Android Auto menu. */
internal fun browsableNode(id: String, title: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .build(),
        )
        .build()

/** Playable položka knihy (mediaId `abs:<itemId>`) — resolver ji expanduje na tracky. */
internal fun bookItem(b: Audiobook): MediaItem =
    MediaItem.Builder()
        .setMediaId("${AudiobookPlayerService.PREFIX_BOOK}${b.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(b.title)
                .setArtist(b.author)
                .setArtworkUri(b.coverUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                .build(),
        )
        .build()

/** Browsable uzel podcastu (mediaId `pod:<itemId>`) — klik vylistuje epizody. */
internal fun podcastNode(p: Podcast): MediaItem =
    MediaItem.Builder()
        .setMediaId("${AudiobookPlayerService.PREFIX_PODCAST}${p.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(p.title)
                .setArtist(p.author)
                .setArtworkUri(p.coverUrl?.let(Uri::parse))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                .build(),
        )
        .build()

/** Playable epizoda (mediaId `epi:<itemId>|<episodeId>`) — resolver ji expanduje na single track. */
internal fun episodeItem(itemId: String, ep: PodcastEpisode, coverUrl: String?): MediaItem =
    MediaItem.Builder()
        .setMediaId("${AudiobookPlayerService.PREFIX_EPISODE}$itemId|${ep.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(ep.title)
                .setSubtitle(ep.guest)   // host (+profese) jako podtitul i v Android Auto
                .setArtist(ep.guest)
                .setArtworkUri(coverUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .build(),
        )
        .build()

/** CRUISE: browsable uzel custom zdroje (`src:<id>`) — klik vylistuje epizody. */
internal fun sourceNode(s: PodcastSource): MediaItem =
    MediaItem.Builder()
        .setMediaId("${AudiobookPlayerService.PREFIX_SRC}${s.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtworkUri(s.thumbnail?.let(Uri::parse))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                .build(),
        )
        .build()

/**
 * CRUISE: přehratelná direct epizoda (`direct:<sourceId>|<episodeId>`) S URI = přímou stream URL
 * (YT proxy / RSS enclosure). Cachuje se v itemCache → onSetMediaItems ji při tapnutí najde i po
 * stripnutí URI (AA přenáší jen mediaId). Bez ABS session → syncNow() ji přeskočí.
 */
internal fun directEpisodeItem(sourceId: String, ep: SourceEpisode): MediaItem =
    MediaItem.Builder()
        .setUri(ep.streamUrl)
        .setMediaId("${AudiobookPlayerService.PREFIX_DIRECT}$sourceId|${ep.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(ep.title)
                .setSubtitle(ep.subtitle)
                .setArtist(ep.subtitle)
                .setArtworkUri(ep.imageUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                // CRUISE: resume klíč (sdílený s in-app) → zápis pozice z auta + „Pokračovat".
                .setExtras(Bundle().apply { ep.resumeKey?.let { putString(AudiobookPlayerService.KEY_DIRECT_KEY, it) } })
                .build(),
        )
        .build()

/** Multi-track playlist z play session (shodné s AudiobookPlayerConnection). */
internal fun trackItems(pb: AbsPlayback): List<MediaItem> {
    val artwork = pb.coverUrl?.let(Uri::parse)
    return pb.tracks.map { t ->
        val extras = Bundle().apply {
            putString(AudiobookPlayerService.KEY_SESSION_ID, pb.sessionId)
            putDouble(AudiobookPlayerService.KEY_DURATION_SEC, pb.durationSec)
            putDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC, t.startOffsetSec)
            putString(AudiobookPlayerService.KEY_BOOK_TITLE, pb.title)
            pb.author?.let { putString(AudiobookPlayerService.KEY_BOOK_AUTHOR, it) }
        }
        MediaItem.Builder()
            .setUri(t.url)
            .setMediaId("${pb.sessionId}_${t.index}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(pb.title)
                    .setArtist(pb.author)
                    .setArtworkUri(artwork)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}

/** Čas celé knihy [ms] → (index tracku, pozice v tracku ms). */
internal fun trackPositionForBookMs(pb: AbsPlayback, bookMs: Long): Pair<Int, Long> {
    val target = bookMs.coerceAtLeast(0L)
    if (pb.tracks.size <= 1) return 0 to target
    var idx = 0
    for (i in pb.tracks.indices) {
        val offMs = (pb.tracks[i].startOffsetSec * 1000).toLong()
        if (offMs <= target + 1) idx = i else break
    }
    val idxOffMs = (pb.tracks[idx].startOffsetSec * 1000).toLong()
    return idx to (target - idxOffMs).coerceAtLeast(0L)
}

/** CRUISE: pevný label audioknihovní záložky v AA dle obecenstva (Dětské/Dospělý), fallback = název knihovny. */
internal fun audiobookTabLabel(name: String): String {
    val n = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return when {
        n.contains("det") -> "Dětské"      // „Děti" / „Dětské" / „Pro děti" …
        n.contains("dospel") -> "Dospělý"  // „Dospělí" / „Dospělý" …
        else -> name
    }
}
