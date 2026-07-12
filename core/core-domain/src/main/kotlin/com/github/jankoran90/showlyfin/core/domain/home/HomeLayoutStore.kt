package com.github.jankoran90.showlyfin.core.domain.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TENFOOT — TV DOMOV REDESIGN. Uživatelské rozvržení domova: seznam [HomeRowConfig] řad + [SidebarEntry]
 * sidebar. Dedikovaný prefs soubor (`tv_home_layout`) mimo `traktPreferences` → izolace + snadný reset,
 * nepodléhá odhlášení Traktu. Vzor: [core.domain.resume.VideoResumeStore] (kotlinx JSON → String,
 * reaktivní [StateFlow], tolerantní `load`).
 *
 * **Forward-compat merge:** uložené (validní) řady zůstávají v uživatelově pořadí, nové default řady
 * z novější OTA se doplní na konec. „Skrýt" = `enabled=false` (řada zůstává, aby se dala vrátit);
 * hard [removeRow] jen pro vlastní (custom_*) řady. Neznámá enum hodnota → default (`coerceInputValues`);
 * poškozená jednotlivá řada se přeskočí (per-element decode), zbytek přežije.
 */
@Singleton
class HomeLayoutStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("tv_home_layout", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val _rows = MutableStateFlow(loadRows())
    /** Všechny řady (i vypnuté) v pořadí; konzument si vyfiltruje `enabled`. */
    val rows: StateFlow<List<HomeRowConfig>> = _rows.asStateFlow()

    private val _sidebar = MutableStateFlow(loadSidebar())
    val sidebar: StateFlow<List<SidebarEntry>> = _sidebar.asStateFlow()

    // ── Řady ──────────────────────────────────────────────────────────────────

    /** Posun řady o jedno místo (nahoru = dřív). No-op na kraji. */
    fun move(id: String, up: Boolean) {
        _rows.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) return@update list
            val j = if (up) i - 1 else i + 1
            if (j < 0 || j >= list.size) return@update list
            list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        }
        persistRows()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _rows.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        persistRows()
    }

    /** Přepiš konfiguraci řady (styl/řazení/filtr/titulek). */
    fun updateRow(config: HomeRowConfig) {
        _rows.update { list -> list.map { if (it.id == config.id) config else it } }
        persistRows()
    }

    /** Přidej vlastní řadu na konec (id musí být unikátní). */
    fun addRow(config: HomeRowConfig) {
        _rows.update { list -> if (list.any { it.id == config.id }) list else list + config }
        persistRows()
    }

    /** Odeber řadu. Default řadu to jen skryje sémanticky ne — vrátí se při dalším merge; pro
     *  default používej [setEnabled]. Míněno pro vlastní `custom_*` řady. */
    fun removeRow(id: String) {
        _rows.update { list -> list.filterNot { it.id == id } }
        persistRows()
    }

    /** Obnovit výchozí sadu řad. */
    fun resetRows() {
        _rows.value = DEFAULT_ROWS
        persistRows()
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    fun moveSidebar(item: String, up: Boolean) {
        _sidebar.update { list ->
            val i = list.indexOfFirst { it.item == item }
            if (i < 0) return@update list
            val j = if (up) i - 1 else i + 1
            if (j < 0 || j >= list.size) return@update list
            list.toMutableList().also { it[i] = list[j]; it[j] = list[i] }
        }
        persistSidebar()
    }

    fun setSidebarEnabled(item: String, enabled: Boolean) {
        _sidebar.update { list -> list.map { if (it.item == item) it.copy(enabled = enabled) else it } }
        persistSidebar()
    }

    // ── Perzistence ─────────────────────────────────────────────────────────────

    private fun persistRows() {
        prefs.edit().putString(KEY_ROWS, json.encodeToString(_rows.value)).apply()
    }

    private fun persistSidebar() {
        prefs.edit().putString(KEY_SIDEBAR, json.encodeToString(_sidebar.value)).apply()
    }

    private fun loadRows(): List<HomeRowConfig> {
        val stored = decodeList(prefs.getString(KEY_ROWS, null)) { el ->
            json.decodeFromJsonElement<HomeRowConfig>(el)
        }
        if (stored.isEmpty()) return DEFAULT_ROWS
        // Merge: uložené v pořadí + nové default řady (podle id) na konec.
        val storedIds = stored.map { it.id }.toSet()
        return stored + DEFAULT_ROWS.filter { it.id !in storedIds }
    }

    private fun loadSidebar(): List<SidebarEntry> {
        val stored = decodeList(prefs.getString(KEY_SIDEBAR, null)) { el ->
            json.decodeFromJsonElement<SidebarEntry>(el)
        }.filter { SidebarItem.fromName(it.item) != null }
        if (stored.isEmpty()) return SidebarItem.DEFAULT
        val storedItems = stored.map { it.item }.toSet()
        return stored + SidebarItem.DEFAULT.filter { it.item !in storedItems }
    }

    /** Per-element tolerantní decode: poškozená položka se přeskočí, ne celý seznam. */
    private fun <T> decodeList(raw: String?, decode: (kotlinx.serialization.json.JsonElement) -> T): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { runCatching { decode(it) }.getOrNull() }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val KEY_ROWS = "rows_json"
        private const val KEY_SIDEBAR = "sidebar_json"

        /** Výchozí domov: vzdušná Kodi-like sada. Obsah hned nahoře (Pokračovat), pak Trakt + Jellyfin. */
        val DEFAULT_ROWS: List<HomeRowConfig> = listOf(
            HomeRowConfig(
                id = "continue",
                source = HomeRowSourceType.CONTINUE_WATCHING,
                title = "Pokračovat ve sledování",
                cardStyle = HomeCardStyle.LANDSCAPE,
            ),
            HomeRowConfig(
                id = "next_up",
                source = HomeRowSourceType.NEXT_UP,
                title = "Další díly",
                cardStyle = HomeCardStyle.LANDSCAPE,
            ),
            HomeRowConfig(
                id = "trending_movies",
                source = HomeRowSourceType.DISCOVER,
                title = "Trendy filmy",
                cardStyle = HomeCardStyle.POSTER,
                params = mapOf(HomeRowParams.TAB to "movies", HomeRowParams.FILTER to "trending"),
            ),
            HomeRowConfig(
                id = "popular_shows",
                source = HomeRowSourceType.DISCOVER,
                title = "Populární seriály",
                cardStyle = HomeCardStyle.POSTER,
                params = mapOf(HomeRowParams.TAB to "shows", HomeRowParams.FILTER to "popular"),
            ),
            HomeRowConfig(
                id = "favorites",
                source = HomeRowSourceType.FAVORITES,
                title = "Oblíbené",
                cardStyle = HomeCardStyle.POSTER,
            ),
            HomeRowConfig(
                id = "jellyfin_libraries",
                source = HomeRowSourceType.JELLYFIN_LIBRARIES,
                title = "Knihovny",
                cardStyle = HomeCardStyle.LANDSCAPE,
            ),
        )
    }
}
