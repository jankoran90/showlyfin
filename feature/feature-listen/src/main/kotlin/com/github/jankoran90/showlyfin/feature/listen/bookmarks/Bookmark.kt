package com.github.jankoran90.showlyfin.feature.listen.bookmarks

/**
 * AMPHORA (SHW-124) F5 / PR2 — záložka s poznámkou: místo v knize nebo dílu pořadu.
 *
 * Ukládá se per profil a synchronizuje na server (doména `bookmarks`, identita [id]) → záložky jsou
 * vidět i v jiném telefonu a v autě. Nese vše, co je potřeba k přehrání odtud i bez načtení pořadu
 * (u dílu RSS/YouTube/ČT i přímou adresu zvuku [url]).
 */
data class Bookmark(
    val id: String,
    /** [KIND_BOOK] nebo [KIND_EPISODE]. */
    val kind: String,
    /** Kniha = ABS itemId; díl = itemId pořadu (zdroj) nebo ABS podcastu. */
    val itemId: String,
    /** Díl: klíč dílu (`rss:`/`yt:`/`ctv:` = zároveň klíč pozice) nebo ABS episodeId. Kniha null. */
    val episodeId: String? = null,
    /** Název knihy nebo dílu. */
    val title: String,
    /** Autor knihy nebo název pořadu. */
    val subtitle: String? = null,
    val coverUrl: String? = null,
    /** Místo v čase celé knihy / dílu. */
    val posMs: Long,
    val durationMs: Long = 0L,
    /** Kapitola v době uložení (jen kniha). */
    val chapter: String? = null,
    val note: String = "",
    /** Díl RSS/YouTube/ČT: přímá adresa zvuku (přehrání bez ABS). */
    val url: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Klíč položky, ke které záložka patří (shodný s [bookmarkKeyFor]). */
    val itemKey: String get() = bookmarkKeyFor(kind, itemId, episodeId)

    val isBook: Boolean get() = kind == KIND_BOOK

    companion object {
        const val KIND_BOOK = "book"
        const val KIND_EPISODE = "episode"
    }
}

/** Klíč položky: kniha `book:<itemId>`, díl `ep:<episodeId>`. */
fun bookmarkKeyFor(kind: String, itemId: String, episodeId: String?): String =
    if (kind == Bookmark.KIND_EPISODE && !episodeId.isNullOrBlank()) "ep:$episodeId" else "book:$itemId"

/** Čas záložky pro lidi: `1:02:05` nebo `12:40`. */
fun formatBookmarkTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
