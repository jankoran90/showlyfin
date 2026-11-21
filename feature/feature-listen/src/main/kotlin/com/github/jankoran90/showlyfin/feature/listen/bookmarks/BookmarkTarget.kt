package com.github.jankoran90.showlyfin.feature.listen.bookmarks

import androidx.media3.common.MediaItem
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.service.AudiobookPlayerService
import com.github.jankoran90.showlyfin.feature.listen.service.PlayerAudioExtras

/**
 * AMPHORA F5 / PR2 — co právě hraje, převedené na budoucí záložku. Dva zdroje: stav přehrávače
 * v appce ([currentBookmarkTarget]) a samotná položka přehrávače ve službě ([bookmarkTargetFromItem],
 * dvojklik sluchátek a tlačítko v Android Auto, i když hraní spustilo auto).
 */
data class BookmarkTarget(
    val kind: String,
    val itemId: String,
    val episodeId: String?,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val posMs: Long,
    val durationMs: Long,
    val chapter: String?,
    val url: String?,
) {
    val itemKey: String get() = bookmarkKeyFor(kind, itemId, episodeId)
}

fun BookmarkStore.addAt(t: BookmarkTarget, note: String = ""): Bookmark = add(
    kind = t.kind, itemId = t.itemId, episodeId = t.episodeId, title = t.title, subtitle = t.subtitle,
    coverUrl = t.coverUrl, posMs = t.posMs, durationMs = t.durationMs, chapter = t.chapter, url = t.url, note = note,
)

/** Právě hraná kniha / díl v appce; null = nic nehraje nebo nejde určit. */
fun AudiobookPlayerConnection.currentBookmarkTarget(): BookmarkTarget? {
    val s = state.value
    if (!s.isActive) return null
    val ep = currentEpisode
    if (ep != null && ep.episodeId.isNotBlank()) {
        return BookmarkTarget(
            kind = Bookmark.KIND_EPISODE, itemId = ep.itemId, episodeId = ep.episodeId,
            title = ep.title.ifBlank { s.title }, subtitle = ep.podcastTitle ?: ep.direct?.author ?: s.author,
            coverUrl = ep.coverUrl ?: s.coverUrl, posMs = s.positionMs, durationMs = s.durationMs,
            chapter = null, url = ep.direct?.url,
        )
    }
    val itemId = s.currentItemId?.takeIf { it.isNotBlank() } ?: return null
    return BookmarkTarget(
        kind = Bookmark.KIND_BOOK, itemId = itemId, episodeId = null, title = s.title, subtitle = s.author,
        coverUrl = s.coverUrl, posMs = s.positionMs, durationMs = s.durationMs,
        chapter = s.currentChapterTitle, url = null,
    )
}

/** Klíč právě hrané položky (pro značky na ose a seznam v přehrávači). */
fun AudiobookPlayerConnection.currentBookmarkKey(): String? {
    val ep = currentEpisode
    if (ep != null && ep.episodeId.isNotBlank()) return bookmarkKeyFor(Bookmark.KIND_EPISODE, ep.itemId, ep.episodeId)
    return state.value.currentItemId?.takeIf { it.isNotBlank() }?.let { bookmarkKeyFor(Bookmark.KIND_BOOK, it, null) }
}

/** Díl ze záložky jako položka fronty (přímá adresa); null = ABS díl nebo kniha (ty jdou přes ABS). */
fun Bookmark.toDirectEpisode(): QueuedEpisode? {
    val u = url?.takeIf { it.isNotBlank() } ?: return null
    val ep = episodeId?.takeIf { it.isNotBlank() } ?: return null
    return QueuedEpisode(
        itemId = itemId, episodeId = ep, title = title, coverUrl = coverUrl, podcastTitle = subtitle,
        direct = DirectAudio(url = u, durationSec = durationMs / 1000.0, author = subtitle),
    )
}

/**
 * Záložka z položky přehrávače ve službě. [posInItemMs] = pozice v aktuálním souboru (u knihy se přičte
 * posun souboru). Díl RSS/YouTube/ČT = položka bez ABS session (klíč dílu v extras z Auta, jinak mediaId
 * z appky); kniha = klíč `book:` z extras. ABS díl bez klíče dílu → null (nejde spolehlivě určit).
 */
internal fun bookmarkTargetFromItem(item: MediaItem, posInItemMs: Long, playerDurationMs: Long, chapter: String?): BookmarkTarget? {
    val meta = item.mediaMetadata
    val extras = meta.extras
    val showKey = extras?.getString(PlayerAudioExtras.KEY_SHOW_KEY)
    val sessionId = extras?.getString(AudiobookPlayerService.KEY_SESSION_ID)
    val title = extras?.getString(AudiobookPlayerService.KEY_BOOK_TITLE)?.takeIf { it.isNotBlank() }
        ?: meta.title?.toString().orEmpty()
    val subtitle = extras?.getString(AudiobookPlayerService.KEY_BOOK_AUTHOR) ?: meta.artist?.toString()
    val durMs = ((extras?.getDouble(AudiobookPlayerService.KEY_DURATION_SEC) ?: 0.0) * 1000).toLong()
        .takeIf { it > 0 } ?: playerDurationMs.coerceAtLeast(0L)
    val cover = meta.artworkUri?.toString()
    val directKey = extras?.getString(AudiobookPlayerService.KEY_DIRECT_KEY)?.takeIf { it.isNotBlank() }
        ?: item.mediaId.takeIf {
            sessionId.isNullOrBlank() && it.isNotBlank() && !it.startsWith(AudiobookPlayerService.PREFIX_DIRECT)
        }
    if (directKey != null) {
        return BookmarkTarget(
            kind = Bookmark.KIND_EPISODE, itemId = showKey?.removePrefix("show:").orEmpty(), episodeId = directKey,
            title = title, subtitle = subtitle, coverUrl = cover, posMs = posInItemMs.coerceAtLeast(0L),
            durationMs = durMs, chapter = null,
            url = item.localConfiguration?.uri?.toString()?.takeIf { it.startsWith("http") },
        )
    }
    // ABS díl (podcast z Audiobookshelfu): klíč pořadu `show:<itemId>` + ID dílu v extras; přehrává se přes ABS.
    val absEpisodeId = extras?.getString(AudiobookPlayerService.KEY_ABS_EPISODE_ID)?.takeIf { it.isNotBlank() }
    if (absEpisodeId != null && showKey?.startsWith("show:") == true) {
        val offMs = ((extras?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
        return BookmarkTarget(
            kind = Bookmark.KIND_EPISODE, itemId = showKey.removePrefix("show:"), episodeId = absEpisodeId,
            title = title, subtitle = subtitle, coverUrl = cover, posMs = offMs + posInItemMs.coerceAtLeast(0L),
            durationMs = durMs, chapter = null, url = null,
        )
    }
    val bookId = showKey?.takeIf { it.startsWith("book:") }?.removePrefix("book:")?.takeIf { it.isNotBlank() } ?: return null
    val offsetMs = ((extras?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
    return BookmarkTarget(
        kind = Bookmark.KIND_BOOK, itemId = bookId, episodeId = null, title = title, subtitle = subtitle,
        coverUrl = cover, posMs = offsetMs + posInItemMs.coerceAtLeast(0L), durationMs = durMs,
        // Přehrávač v appce přepisuje titulek položky na právě hranou kapitolu (titul knihy je v extras).
        chapter = chapter ?: meta.title?.toString()?.takeIf { it.isNotBlank() && it != title }, url = null,
    )
}
