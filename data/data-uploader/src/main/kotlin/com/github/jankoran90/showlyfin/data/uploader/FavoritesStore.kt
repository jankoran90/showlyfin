package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** COMPASS C2 (SHW-44) — kategorie Oblíbených (dle zadání usera; WRITER = scénáristé doplněn C3). */
enum class FavoriteKind { MOVIE, ACTOR, DIRECTOR, WRITER, PRODUCER, COMPOSER, COMPANY }

/**
 * COMPASS C2 (SHW-44) — jedna položka v Oblíbených. [id] = tmdbId (film / osoba / vydavatelství),
 * [imageUrl] = plná TMDB URL (poster filmu / profil osoby / logo studia), [year] jen u filmu.
 */
data class FavoriteItem(
    val kind: FavoriteKind = FavoriteKind.MOVIE,
    val id: Long = 0L,
    val name: String = "",
    val imageUrl: String? = null,
    val year: Int? = null,
    val addedAtMs: Long = 0L,
)

/**
 * COMPASS C2 (SHW-44) — vlastní úložiště Oblíbených. Stejný princip jako [WorkingSourceStore]:
 * VLASTNÍ SharedPreferences (`compass_favorites`), NE sdílené `trakt_prefs` (ty smete odhlášení
 * Traktu přes `revokeToken().clear()`). Reaktivní [items] (StateFlow) → obrazovka se aktualizuje
 * okamžitě po přidání/odebrání kdekoli (detail, ENSEMBLE, hledání).
 *
 * ZATÍM GLOBÁLNÍ (ne per-profil) — jako WorkingSourceStore; per-profil = follow-up (známý gap C2).
 */
@Singleton
class FavoritesStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val prefs = context.getSharedPreferences("compass_favorites", Context.MODE_PRIVATE)
    private val storeKey = "favorites"

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<FavoriteItem>> = _items.asStateFlow()

    private fun load(): List<FavoriteItem> {
        val raw = prefs.getString(storeKey, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<FavoriteItem>>(raw, object : TypeToken<List<FavoriteItem>>() {}.type)
        }.onFailure { Timber.w(it, "[COMPASS] favorites parse failed") }.getOrNull() ?: emptyList()
    }

    private fun persist(list: List<FavoriteItem>) {
        _items.value = list
        prefs.edit().putString(storeKey, gson.toJson(list)).apply()
    }

    fun isFavorite(kind: FavoriteKind, id: Long): Boolean =
        _items.value.any { it.kind == kind && it.id == id }

    fun add(item: FavoriteItem) {
        if (item.id <= 0L) return
        if (isFavorite(item.kind, item.id)) return
        persist(_items.value + item.copy(addedAtMs = System.currentTimeMillis()))
        Timber.i("[COMPASS] +oblíbené %s #%d %s", item.kind, item.id, item.name)
    }

    fun remove(kind: FavoriteKind, id: Long) {
        persist(_items.value.filterNot { it.kind == kind && it.id == id })
    }

    /** @return true = po přepnutí je v oblíbených, false = odebráno. */
    fun toggle(item: FavoriteItem): Boolean =
        if (isFavorite(item.kind, item.id)) {
            remove(item.kind, item.id); false
        } else {
            add(item); true
        }
}
