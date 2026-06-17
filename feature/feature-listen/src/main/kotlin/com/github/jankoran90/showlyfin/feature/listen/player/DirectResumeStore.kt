package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LEVER (SHW-61) L2b: lokální paměť pozice přehrávání pro DIRECT epizody (RSS / YouTube).
 *
 * ABS podcasty mají progres/pozici na serveru (`currentTimeSec`/`progress`); direct epizody (přímá
 * audio URL přes [AudiobookPlayerConnection.playDirect]) žádný serverový stav nemají. Tenhle store
 * jim dělá ekvivalent: pozici ukládáme periodicky (přehrávání/pauza), při dohrání mažeme. Klíč =
 * stabilní `mediaId` = `episodeKey` z VM (`yt:<id>` / `rss:<id>`).
 *
 * Díky [marks] (reaktivní mapa) umí seznam epizod ukázat progres i „Pokračovat" u NEhrané epizody —
 * stejně jako ABS podcast řádek.
 */
@Singleton
class DirectResumeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** Pozice + (známá) délka v ms pro jednu direct epizodu. [updatedAt] = čas posledního zápisu
     *  (epoch ms) → CRUISE (SHW-70) řadí „Pokračovat" v Android Auto dle posledního poslechu. */
    data class Mark(val posMs: Long, val durMs: Long, val updatedAt: Long = 0L)

    private val prefs = context.getSharedPreferences("direct_resume", Context.MODE_PRIVATE)

    private val _marks = MutableStateFlow(load())
    /** mediaId → [Mark]; reaktivní (seznamy ukáží progres/„Pokračovat" bez pollingu). */
    val marks: StateFlow<Map<String, Mark>> = _marks.asStateFlow()

    fun get(mediaId: String): Mark? = _marks.value[mediaId]

    /**
     * Ulož pozici. Blízko konce ([FINISH_TAIL_MS]) = bereme jako dohrané → [clear] (žádné „Pokračovat"
     * na 99 %). Pod [MIN_RESUME_MS] od začátku neukládáme (nemá smysl resume pár vteřin).
     */
    fun save(mediaId: String, posMs: Long, durMs: Long) {
        if (mediaId.isBlank()) return
        if (durMs > 0 && posMs >= durMs - FINISH_TAIL_MS) { clear(mediaId); return }
        if (posMs < MIN_RESUME_MS) return
        val cur = _marks.value[mediaId]
        if (cur != null && cur.posMs == posMs && cur.durMs == durMs) return
        _marks.update { it + (mediaId to Mark(posMs, durMs, System.currentTimeMillis())) }
        persist()
    }

    fun clear(mediaId: String) {
        if (mediaId.isBlank() || !_marks.value.containsKey(mediaId)) return
        _marks.update { it - mediaId }
        persist()
    }

    private fun persist() {
        val obj = JSONObject()
        _marks.value.forEach { (id, m) ->
            obj.put(id, JSONObject().put("p", m.posMs).put("d", m.durMs).put("u", m.updatedAt))
        }
        prefs.edit().putString(KEY_JSON, obj.toString()).apply()
    }

    private fun load(): Map<String, Mark> {
        val json = prefs.getString(KEY_JSON, "").orEmpty()
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { id ->
                    val o = obj.getJSONObject(id)
                    put(id, Mark(o.optLong("p", 0L), o.optLong("d", 0L), o.optLong("u", 0L)))
                }
            }
        }.getOrElse { emptyMap() }
    }

    companion object {
        private const val KEY_JSON = "marks"
        const val MIN_RESUME_MS = 5_000L
        const val FINISH_TAIL_MS = 15_000L
    }
}
