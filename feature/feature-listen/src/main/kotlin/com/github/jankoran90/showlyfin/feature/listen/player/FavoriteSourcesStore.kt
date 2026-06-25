package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AGORA F3: lokální (per-zařízení) „Moje oblíbené" podcasty/kanály = srdíčka, na rozdíl od sdíleného
 * serverového store [com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository] (= „Přidáno",
 * vidí celá rodina). Srdíčka jsou OSOBNÍ záložky, žijou jen na telefonu — stejný princip jako
 * [DirectResumeStore] (SharedPreferences + JSON + reaktivní StateFlow), bez nové Gson závislosti.
 *
 * Ukládá CELOU kartu ([SourceSearchResult]) → režim „Oblíbené" na obrazovce ji vykreslí přímo z paměti
 * (bez serverového dotazu) a karta drží popis/počet epizod/kategorii i offline.
 */
@Singleton
class FavoriteSourcesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("podcast_favorites", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(load())
    /** Oblíbené karty (nejnovější srdíčko první). Reaktivní → karty hned ukáží plné/prázdné srdce. */
    val favorites: StateFlow<List<SourceSearchResult>> = _favorites.asStateFlow()

    /** Stabilní klíč karty (shoda s „addedRefs" v [PodcastDiscoveryViewModel]). */
    private fun key(type: String, ref: String) = "$type:$ref"

    fun refsSnapshot(): Set<String> = _favorites.value.map { key(it.type, it.ref) }.toSet()

    fun isFavorite(type: String, ref: String): Boolean =
        _favorites.value.any { it.type == type && it.ref == ref }

    /** Přepne srdíčko (přidá nebo odebere). Nově přidané jde na začátek (nejnovější první). */
    fun toggle(card: SourceSearchResult) {
        val k = key(card.type, card.ref)
        _favorites.update { list ->
            if (list.any { key(it.type, it.ref) == k }) list.filterNot { key(it.type, it.ref) == k }
            else listOf(card) + list
        }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _favorites.value.forEach { r ->
            arr.put(
                JSONObject()
                    .put("type", r.type)
                    .put("ref", r.ref)
                    .put("title", r.title)
                    .apply {
                        r.subtitle?.let { put("subtitle", it) }
                        r.thumbnail?.let { put("thumbnail", it) }
                        r.summary?.let { put("summary", it) }
                        r.episodeCount?.let { put("episode_count", it) }
                        r.category?.let { put("category", it) }
                    },
            )
        }
        prefs.edit().putString(KEY_JSON, arr.toString()).apply()
    }

    private fun load(): List<SourceSearchResult> {
        val json = prefs.getString(KEY_JSON, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SourceSearchResult(
                            type = o.optString("type"),
                            ref = o.optString("ref"),
                            title = o.optString("title"),
                            subtitle = o.optString("subtitle").ifBlank { null },
                            thumbnail = o.optString("thumbnail").ifBlank { null },
                            summary = o.optString("summary").ifBlank { null },
                            episodeCount = if (o.has("episode_count")) o.optInt("episode_count") else null,
                            category = o.optString("category").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val KEY_JSON = "favorites"
    }
}
