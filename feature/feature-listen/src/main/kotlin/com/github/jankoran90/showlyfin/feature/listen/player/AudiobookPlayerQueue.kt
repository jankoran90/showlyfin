package com.github.jankoran90.showlyfin.feature.listen.player

import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import org.json.JSONArray
import org.json.JSONObject

// Fronta epizod (perzistence + čisté list-operace) — vyříznuto z AudiobookPlayerConnection (anti-monolit).
// Bez coroutine/controlleru: JSON (de)serializace fronty jako top-level `internal`, list-operace jako
// extension nad Connection (přístup k internal _queue/currentEpisode/prefs). Přehrávací jádro (playBook/
// playDirect/startEpisode/onPlaybackEnded) zůstává v Connection.

internal fun loadPersistedQueue(prefs: AbsPreferences): List<QueuedEpisode> {
    if (!prefs.persistQueue) return emptyList()
    val json = prefs.queueJson.ifBlank { return emptyList() }
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val directObj = o.optJSONObject("direct")
            QueuedEpisode(
                itemId = o.getString("itemId"),
                episodeId = o.getString("episodeId"),
                title = o.optString("title"),
                coverUrl = if (o.isNull("coverUrl")) null else o.optString("coverUrl").takeIf { it.isNotBlank() },
                guest = if (o.isNull("guest")) null else o.optString("guest").takeIf { it.isNotBlank() },
                description = if (o.isNull("description")) null else o.optString("description").takeIf { it.isNotBlank() },
                podcastTitle = if (o.isNull("podcastTitle")) null else o.optString("podcastTitle").takeIf { it.isNotBlank() },
                direct = directObj?.let {
                    DirectAudio(
                        url = it.optString("url"),
                        durationSec = it.optDouble("durationSec", 0.0),
                        author = if (it.isNull("author")) null else it.optString("author").takeIf { a -> a.isNotBlank() },
                    )
                }?.takeIf { it.url.isNotBlank() },
            )
        }
    }.getOrElse { emptyList() }
}

internal fun persistQueue(prefs: AbsPreferences, queue: List<QueuedEpisode>) {
    if (!prefs.persistQueue) { prefs.queueJson = ""; return }
    val arr = JSONArray()
    queue.forEach { q ->
        arr.put(
            JSONObject()
                .put("itemId", q.itemId)
                .put("episodeId", q.episodeId)
                .put("title", q.title)
                .put("coverUrl", q.coverUrl ?: JSONObject.NULL)
                .put("guest", q.guest ?: JSONObject.NULL)
                .put("description", q.description ?: JSONObject.NULL)
                .put("podcastTitle", q.podcastTitle ?: JSONObject.NULL)
                .put(
                    "direct",
                    q.direct?.let {
                        JSONObject()
                            .put("url", it.url)
                            .put("durationSec", it.durationSec)
                            .put("author", it.author ?: JSONObject.NULL)
                    } ?: JSONObject.NULL,
                ),
        )
    }
    prefs.queueJson = arr.toString()
}

internal fun AudiobookPlayerConnection.setQueue(list: List<QueuedEpisode>) {
    _queue.value = list
    persistQueue(prefs, _queue.value)
}

internal fun AudiobookPlayerConnection.currentIndex(): Int =
    _queue.value.indexOfFirst { it.episodeId == currentEpisode?.episodeId }

/** Přidá epizodu do fronty. [atFront] = hned ZA aktuálně hranou, jinak na konec. Bez duplicit. */
fun AudiobookPlayerConnection.enqueue(episode: QueuedEpisode, atFront: Boolean) {
    val without = _queue.value.filterNot { it.episodeId == episode.episodeId }
    if (!atFront) { setQueue(without + episode); return }
    val curIdx = without.indexOfFirst { it.episodeId == currentEpisode?.episodeId }
    setQueue(
        if (curIdx >= 0) without.toMutableList().apply { add(curIdx + 1, episode) }
        else listOf(episode) + without,
    )
}

fun AudiobookPlayerConnection.removeFromQueue(episodeId: String) {
    setQueue(_queue.value.filterNot { it.episodeId == episodeId })
}

fun AudiobookPlayerConnection.clearQueue() = setQueue(emptyList())

/** Přesun položky ve frontě (drag reorder). */
fun AudiobookPlayerConnection.moveQueueItem(fromIndex: Int, toIndex: Int) {
    val q = _queue.value.toMutableList()
    if (fromIndex !in q.indices || toIndex !in q.indices || fromIndex == toIndex) return
    q.add(toIndex, q.removeAt(fromIndex))
    setQueue(q)
}

/** Přesun epizody na začátek fronty (swipe akce). */
fun AudiobookPlayerConnection.moveToFront(episodeId: String) {
    val q = _queue.value
    val idx = q.indexOfFirst { it.episodeId == episodeId }
    if (idx > 0) moveQueueItem(idx, 0)
}
