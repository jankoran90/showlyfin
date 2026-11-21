package com.github.jankoran90.showlyfin.feature.listen.player

import com.github.jankoran90.showlyfin.feature.listen.stats.ListenItem
import com.github.jankoran90.showlyfin.feature.listen.stats.ListenStat

/**
 * AMPHORA (SHW-124) F8 / CH2 — co právě hraje, pro statistiky poslechu (vyčleněno, spojení je
 * na hraně velikosti). Díl se počítá pod svůj pořad (`show:<id>`), kniha pod sebe (`book:<id>`).
 */
internal fun AudiobookPlayerConnection.listenItemNow(): ListenItem? {
    val st = _state.value
    val ep = currentEpisode
    if (ep != null) {
        val key = showKeyFor(ep, null) ?: "ep:${ep.episodeId}"
        val show = ep.podcastTitle?.takeIf { it.isNotBlank() } ?: ep.direct?.author?.takeIf { it.isNotBlank() } ?: ep.title
        return ListenItem(key, ListenStat.KIND_EPISODE, show, ep.coverUrl, unitId = "ep:${ep.episodeId}")
    }
    if (!st.isActive) return null
    val itemId = st.currentItemId?.takeIf { it.isNotBlank() } ?: return null
    // Po znovupřipojení ke službě (appka spuštěná znovu během hraní) díl nemá frontovou položku.
    return if (st.isPodcastEpisode) {
        ListenItem("show:$itemId", ListenStat.KIND_EPISODE, st.author?.takeIf { it.isNotBlank() } ?: st.title, st.coverUrl,
            unitId = "ep:${st.currentEpisodeId ?: itemId}")
    } else {
        ListenItem("book:$itemId", ListenStat.KIND_BOOK, st.title, st.coverUrl, unitId = "book:$itemId")
    }
}
