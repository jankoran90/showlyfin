package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Slovo 2026-10-13 (user „vybrané pořady nebo balíčky v nich dodatečně převést na kapitoly série" →
 * „Chci to") — RUČNÍ kniha v pořadu: díly, jejichž název obsahuje [SeriesRule.match], tvoří knihu
 * [SeriesRule.title]. Pravidlo místo seznamu dílů → sama pobere i budoucí (a později dotažené starší) díly.
 * Ruční pravidlo má přednost před automatickou detekcí série. Ukládá se v telefonu (traktPreferences).
 */
data class SeriesRule(val title: String, val match: String)

@Singleton
class SeriesRuleStore @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private val _rules = MutableStateFlow(load())
    /** sourceKey (`rss:…`, `youtube:…`, `ctv:…`) → pravidla knih toho pořadu. */
    val rules: StateFlow<Map<String, List<SeriesRule>>> = _rules.asStateFlow()

    /** Název ruční knihy pro díl [episodeTitle] ve zdroji [sourceKey], nebo null. */
    fun titleFor(sourceKey: String, episodeTitle: String): String? {
        val hay = fold(episodeTitle)
        return _rules.value[sourceKey]?.firstOrNull { it.match.isNotBlank() && hay.contains(fold(it.match)) }?.title
    }

    /** Přidá nebo přepíše (stejný název) pravidlo knihy. */
    fun put(sourceKey: String, rule: SeriesRule) = update(sourceKey) { list ->
        list.filterNot { it.title.equals(rule.title, ignoreCase = true) } + rule
    }

    fun remove(sourceKey: String, title: String) = update(sourceKey) { list ->
        list.filterNot { it.title.equals(title, ignoreCase = true) }
    }

    private fun update(sourceKey: String, change: (List<SeriesRule>) -> List<SeriesRule>) {
        val next = _rules.value.toMutableMap()
        val list = change(next[sourceKey].orEmpty())
        if (list.isEmpty()) next.remove(sourceKey) else next[sourceKey] = list
        _rules.value = next
        prefs.edit().putString(KEY, serialize(next)).apply()
    }

    private fun load(): Map<String, List<SeriesRule>> = runCatching {
        val root = JSONObject(prefs.getString(KEY, null) ?: return emptyMap())
        root.keys().asSequence().associateWith { key ->
            val arr = root.getJSONArray(key)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SeriesRule(o.optString("title"), o.optString("match"))
            }.filter { it.title.isNotBlank() && it.match.isNotBlank() }
        }
    }.getOrElse { emptyMap() }

    private fun serialize(map: Map<String, List<SeriesRule>>): String {
        val root = JSONObject()
        map.forEach { (key, list) ->
            root.put(key, JSONArray().apply { list.forEach { put(JSONObject().put("title", it.title).put("match", it.match)) } })
        }
        return root.toString()
    }

    /** Porovnání bez ohledu na velikost písmen a diakritiku („Mezi svety" najde „Mezi světy"). */
    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private companion object {
        const val KEY = "slovo_series_rules"
    }
}
