package com.github.jankoran90.showlyfin.core.domain.resume

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
 * REWIND (SHW-68): lokální paměť pozice přehrávání pro VIDEO (Jellyfin-item playback v [PlaybackScreen]).
 *
 * Showlyfin NEhlásí JF playback progress zpět na server, takže serverový `playbackPositionTicks` u videa
 * přehraného na telefonu zůstává ~0 → žádné „Pokračovat"/progres. Tenhle store dělá videu lokální
 * resume (ekvivalent [feature.listen.player.DirectResumeStore] pro audio): pozici ukládáme periodicky,
 * při dohrání mažeme. Klíč = stabilní `resumeKey` předaný do přehrávače.
 *
 * Sdílený přes `core-domain` → vidí ho `feature-playback` (zapisuje pozici videa) i `feature-listen`
 * (RSS řádek čte [marks] → progres + „Pokračovat" u video epizody, sdílený klíč s audiem = „poslední
 * vyhrává"). Reaktivní [marks] = seznam ukáže stav bez pollingu.
 */
@Singleton
class VideoResumeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** Pozice + (známá) délka v ms pro jednu video položku. */
    data class Mark(val posMs: Long, val durMs: Long)

    private val prefs = context.getSharedPreferences("video_resume", Context.MODE_PRIVATE)

    private val _marks = MutableStateFlow(load())
    /** key → [Mark]; reaktivní (seznamy ukáží progres/„Pokračovat" bez pollingu). */
    val marks: StateFlow<Map<String, Mark>> = _marks.asStateFlow()

    fun get(key: String): Mark? = _marks.value[key]

    /**
     * Ulož pozici. Blízko konce ([FINISH_TAIL_MS]) = dokoukáno → [clear] (žádné „Pokračovat" na 99 %).
     * Pod [MIN_RESUME_MS] od začátku neukládáme.
     */
    fun save(key: String, posMs: Long, durMs: Long) {
        if (key.isBlank()) return
        if (durMs > 0 && posMs >= durMs - FINISH_TAIL_MS) { clear(key); return }
        if (posMs < MIN_RESUME_MS) return
        val cur = _marks.value[key]
        if (cur != null && cur.posMs == posMs && cur.durMs == durMs) return
        _marks.update { it + (key to Mark(posMs, durMs)) }
        persist()
    }

    fun clear(key: String) {
        if (key.isBlank() || !_marks.value.containsKey(key)) return
        _marks.update { it - key }
        persist()
    }

    private fun persist() {
        val obj = JSONObject()
        _marks.value.forEach { (id, m) ->
            obj.put(id, JSONObject().put("p", m.posMs).put("d", m.durMs))
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
                    put(id, Mark(o.optLong("p", 0L), o.optLong("d", 0L)))
                }
            }
        }.getOrElse { emptyMap() }
    }

    companion object {
        private const val KEY_JSON = "marks"
        const val MIN_RESUME_MS = 5_000L
        const val FINISH_TAIL_MS = 20_000L
    }
}
