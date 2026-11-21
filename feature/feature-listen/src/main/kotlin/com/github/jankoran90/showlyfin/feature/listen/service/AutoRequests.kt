package com.github.jankoran90.showlyfin.feature.listen.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.github.jankoran90.showlyfin.feature.listen.bookmarks.Bookmark
import java.text.Normalizer

/**
 * Android Auto: požadavek na přehrání bez adresy (kniha `abs:`, díl pořadu `direct:`, záložka `bmk:`,
 * hlasové „pusť Počítám do tří“) → hotový playlist se startovní pozicí. Vyčleněno ze služby.
 * AMPHORA (SHW-124) F5 / IN2 přidalo záložky a hlas; knihy a díly pořadů tu jsou z CRUISE (SHW-70).
 */

/** true = položku musí doplnit [resolveAutoRequest] (služba ji sama přehrát neumí). */
internal fun isAutoRequest(item: MediaItem): Boolean {
    if (item.localConfiguration != null) return false
    val id = item.mediaId
    return id.startsWith(AudiobookPlayerService.PREFIX_BOOK) || id.startsWith(AudiobookPlayerService.PREFIX_DIRECT) ||
        id.startsWith(AudiobookPlayerService.PREFIX_BOOKMARK) || (id.isBlank() && item.requestMetadata.searchQuery != null)
}

/** null = nejde vyřídit (neznámá záložka, prázdný pořad, nic nenalezeno). */
internal suspend fun AudiobookPlayerService.resolveAutoRequest(item: MediaItem): MediaSession.MediaItemsWithStartPosition? {
    val id = item.mediaId
    return when {
        id.startsWith(AudiobookPlayerService.PREFIX_BOOK) -> bookAt(id.removePrefix(AudiobookPlayerService.PREFIX_BOOK), null)
        id.startsWith(AudiobookPlayerService.PREFIX_DIRECT) -> {
            val rest = id.removePrefix(AudiobookPlayerService.PREFIX_DIRECT).split("|", limit = 2)
            if (rest.size == 2) directAt(rest[0], id) else null
        }
        id.startsWith(AudiobookPlayerService.PREFIX_BOOKMARK) ->
            bookmarkStore.bookmarks.value.firstOrNull { it.id == id.removePrefix(AudiobookPlayerService.PREFIX_BOOKMARK) }
                ?.let { playBookmark(it) }
        else -> item.requestMetadata.searchQuery?.let { voiceRequest(it) }
    }
}

/** Kniha od [posMs] (null = kde skončil poslech). */
private suspend fun AudiobookPlayerService.bookAt(itemId: String, posMs: Long?): MediaSession.MediaItemsWithStartPosition {
    val pb = repo.startPlayback(itemId).also { currentPlayback = it }
    val (idx, pos) = trackPositionForBookMs(pb, posMs ?: (pb.startPositionSec * 1000).toLong())
    autoShowKey = "book:$itemId"
    return MediaSession.MediaItemsWithStartPosition(trackItems(pb, autoShowKey), idx, pos)
}

/**
 * Celá řada dílů pořadu (⏮⏭ = předchozí / další díl, samo navazuje), start od [mediaId] nebo od
 * naposledy poslouchaného nedohraného dílu; pozice z uloženého poslechu (dohraný díl od začátku).
 */
private suspend fun AudiobookPlayerService.directAt(sourceId: String, mediaId: String?): MediaSession.MediaItemsWithStartPosition? {
    val items = directSourceEpisodes(sourceId)
    if (items.isEmpty()) return null
    currentPlayback = null
    fun key(i: MediaItem) = i.mediaMetadata.extras?.getString(AudiobookPlayerService.KEY_DIRECT_KEY)
    val idx = if (mediaId != null) {
        items.indexOfFirst { it.mediaId == mediaId }.coerceAtLeast(0)
    } else {
        items.withIndex()
            .mapNotNull { (i, it) -> key(it)?.let { k -> directResume.get(k) }?.takeUnless { m -> m.isFinished }?.let { m -> i to m.updatedAt } }
            .maxByOrNull { it.second }?.first ?: 0
    }
    val resumeMs = key(items[idx])?.let { directResume.get(it)?.takeUnless { m -> m.isFinished }?.posMs } ?: 0L
    return MediaSession.MediaItemsWithStartPosition(items.toMutableList(), idx, resumeMs)
}

/** Záložka: kniha / ABS díl přes ABS, díl RSS/YouTube/ČT přímo z uložené adresy. */
private suspend fun AudiobookPlayerService.playBookmark(b: Bookmark): MediaSession.MediaItemsWithStartPosition? {
    if (b.isBook) return bookAt(b.itemId, b.posMs)
    val url = b.url
    val ep = b.episodeId ?: return null
    if (url.isNullOrBlank()) {
        val pb = repo.startEpisodePlayback(b.itemId, ep).also { currentPlayback = it }
        autoShowKey = "show:${b.itemId}"
        val (idx, pos) = trackPositionForBookMs(pb, b.posMs)
        return MediaSession.MediaItemsWithStartPosition(trackItems(pb, autoShowKey), idx, pos)
    }
    currentPlayback = null
    val extras = Bundle().apply {
        putString(AudiobookPlayerService.KEY_DIRECT_KEY, ep)
        putDouble(AudiobookPlayerService.KEY_DURATION_SEC, b.durationMs / 1000.0)
        putDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC, 0.0)
        putString(AudiobookPlayerService.KEY_BOOK_TITLE, b.title)
        b.subtitle?.let { putString(AudiobookPlayerService.KEY_BOOK_AUTHOR, it) }
        b.itemId.takeIf { it.isNotBlank() }?.let { putString(PlayerAudioExtras.KEY_SHOW_KEY, "show:$it") }
    }
    val item = MediaItem.Builder()
        .setUri(url)
        .setMediaId(ep)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(b.title).setArtist(b.subtitle).setArtworkUri(b.coverUrl?.let(Uri::parse))
                .setExtras(extras).build(),
        )
        .build()
    return MediaSession.MediaItemsWithStartPosition(mutableListOf(item), 0, b.posMs)
}

/**
 * Hlas („pusť Počítám do tří“): nejlépe odpovídající pořad (navázat na rozposlouchaný díl, jinak nejnovější)
 * nebo kniha. Prázdný dotaz („pusť Slovo“) = poslední rozposlouchané.
 */
private suspend fun AudiobookPlayerService.voiceRequest(query: String): MediaSession.MediaItemsWithStartPosition? {
    val q = normalizeVoice(query)
    if (q.isBlank()) return continueItems().firstOrNull { it.mediaMetadata.isPlayable == true }?.let { resolveAutoRequest(it) }
    sourcesRepo.refresh()
    val src = visibleSources().map { it to voiceScore(q, it.title) }.filter { it.second > 0 }.maxByOrNull { it.second }
    val book = if (repo.isConfigured) {
        runCatching { visibleBooks(visibleLibraries(repo.getAudiobookLibraries()).flatMap { repo.getAudiobooks(it.id) }) }
            .getOrDefault(emptyList())
            .map { it to voiceScore(q, it.title) }.filter { it.second > 0 }.maxByOrNull { it.second }
    } else null
    timber.log.Timber.i("[AUTO] hlas '%s' → pořad=%s kniha=%s", query, src?.first?.title, book?.first?.title)
    return when {
        src != null && (book == null || src.second >= book.second) -> directAt(src.first.id, null)
        book != null -> bookAt(book.first.id, null)
        else -> null
    }
}

private val VOICE_FILLERS = setOf("pust", "pustit", "prehraj", "prehrat", "audiokniha", "audioknihu", "podcast", "porad", "na", "ve", "slovu", "slovo")

private fun normalizeVoice(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(' ').filter { it.isNotBlank() && it !in VOICE_FILLERS }.joinToString(" ")

/** 3 = shoda, 2 = jedno obsahuje druhé, 1 = všechna slova dotazu v názvu, 0 = nic. */
private fun voiceScore(q: String, title: String): Int {
    val t = normalizeVoice(title)
    if (t.isBlank()) return 0
    return when {
        t == q -> 3
        t.contains(q) || q.contains(t) -> 2
        q.split(' ').all { w -> t.split(' ').any { it.startsWith(w) } } -> 1
        else -> 0
    }
}
