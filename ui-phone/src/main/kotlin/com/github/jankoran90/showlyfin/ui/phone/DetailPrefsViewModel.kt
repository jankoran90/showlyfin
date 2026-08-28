package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.feature.detail.DETAIL_ACTION_KEYS
import com.github.jankoran90.showlyfin.feature.detail.DetailActionsPlacement
import com.github.jankoran90.showlyfin.feature.detail.TvDetailLayout
import com.github.jankoran90.showlyfin.feature.detail.parseActionOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.github.jankoran90.showlyfin.feature.detail.DETAIL_ROW_ORDER_DEFAULT
import javax.inject.Inject
import javax.inject.Named

data class DetailPrefsState(
    val rich: Boolean = true,
    val showCollections: Boolean = true,
    val showDirector: Boolean = true,
    val showStudio: Boolean = true,
    val showSimilar: Boolean = true,    // SIMILAR (user 2026-08-27): pruh Podobne (Trakt related)
    // SUMÁŘ (SHW-122, user 2026-08-28): kurátorský popis z ohlasů diváků + jestli je HLAVNÍ stránkou.
    val showCurated: Boolean = true,
    val curatedFirst: Boolean = true,
    val rowOrder: List<String> = DETAIL_ROW_ORDER_DEFAULT,  // poradi pruhu pod kartou
    val showCreators: Boolean = true,   // ENSEMBLE (SHW-45): sekce „Tvůrci"
    val showSeasons: Boolean = true,    // TENFOOT WS-C (SHW-87): sezóny/epizody seriálu v detailu
    val sectionStyle: HomeCardStyle = HomeCardStyle.POSTER,   // styl karet sekcí (plakát/fanart/fanart+popis)
    // COUCH (SHW-88): řazení + filtr sekcí režisér/studio (kolekce se neřadí — má vlastní logiku dle data).
    val sectionSort: HomeRowSort = HomeRowSort.DEFAULT,
    val releasedOnly: Boolean = false,
    val plotLines: Int = 5,   // počet řádků popisu ve sbaleném stavu (0 = bez omezení)
    val actionOrder: List<String> = DETAIL_ACTION_KEYS,   // CANVAS A: pořadí akčních tlačítek
    // TV DETAIL REDESIGN (OTA 299): rozvržení TV detailu + auto-kompakt popisu + umístění tlačítek.
    val tvLayout: TvDetailLayout = TvDetailLayout.IMMERSIVE_OVERLAY,
    val plotAutoCompact: Boolean = true,
    val actionsPlacement: DetailActionsPlacement = DetailActionsPlacement.BELOW_PLOT,
)

/** Nastavení detailu obsahu z knihovny (jednoduchý vs bohatý + volitelné sekce). */
@HiltViewModel
class DetailPrefsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<DetailPrefsState> = _state.asStateFlow()

    private fun read() = DetailPrefsState(
        rich = prefs.getBoolean(KEY_RICH, true),
        showCollections = prefs.getBoolean(KEY_COLLECTIONS, true),
        showDirector = prefs.getBoolean(KEY_DIRECTOR, true),
        showStudio = prefs.getBoolean(KEY_STUDIO, true),
        showCreators = prefs.getBoolean(KEY_CREATORS, true),
        showSimilar = prefs.getBoolean(KEY_SIMILAR, true),
        showCurated = prefs.getBoolean(KEY_CURATED, true),
        curatedFirst = prefs.getBoolean(KEY_CURATED_FIRST, true),
        rowOrder = parseRowOrder(prefs.getString(KEY_ROW_ORDER, null)),
        showSeasons = prefs.getBoolean(KEY_SEASONS, true),
        sectionStyle = prefs.getString(KEY_SECTION_STYLE, null)
            ?.let { runCatching { HomeCardStyle.valueOf(it) }.getOrNull() } ?: HomeCardStyle.POSTER,
        sectionSort = prefs.getString(KEY_SECTION_SORT, null)
            ?.let { runCatching { HomeRowSort.valueOf(it) }.getOrNull() } ?: HomeRowSort.DEFAULT,
        releasedOnly = prefs.getBoolean(KEY_RELEASED_ONLY, false),
        plotLines = prefs.getInt(KEY_PLOT_LINES, 5),
        actionOrder = parseActionOrder(prefs.getString(KEY_ACTION_ORDER, null)),
        tvLayout = prefs.getString(KEY_TV_LAYOUT, null)
            ?.let { runCatching { TvDetailLayout.valueOf(it) }.getOrNull() } ?: TvDetailLayout.IMMERSIVE_OVERLAY,
        plotAutoCompact = prefs.getBoolean(KEY_PLOT_AUTOCOMPACT, true),
        actionsPlacement = prefs.getString(KEY_ACTIONS_PLACEMENT, null)
            ?.let { runCatching { DetailActionsPlacement.valueOf(it) }.getOrNull() } ?: DetailActionsPlacement.BELOW_PLOT,
    )

    fun setRich(value: Boolean) = put(KEY_RICH) { _state.update { s -> s.copy(rich = value) }; value }
    fun setCollections(value: Boolean) = put(KEY_COLLECTIONS) { _state.update { s -> s.copy(showCollections = value) }; value }
    fun setDirector(value: Boolean) = put(KEY_DIRECTOR) { _state.update { s -> s.copy(showDirector = value) }; value }
    fun setStudio(value: Boolean) = put(KEY_STUDIO) { _state.update { s -> s.copy(showStudio = value) }; value }
    fun setCreators(value: Boolean) = put(KEY_CREATORS) { _state.update { s -> s.copy(showCreators = value) }; value }
    fun setSimilar(value: Boolean) = put(KEY_SIMILAR) { _state.update { s -> s.copy(showSimilar = value) }; value }
    fun setCurated(value: Boolean) = put(KEY_CURATED) { _state.update { s -> s.copy(showCurated = value) }; value }
    fun setCuratedFirst(value: Boolean) = put(KEY_CURATED_FIRST) { _state.update { s -> s.copy(curatedFirst = value) }; value }

    /** Posun pruhu o [delta] (−1 nahoru, +1 dolu). Mimo rozsah = nic (tlacitko je stejne disabled). */
    fun moveRow(key: String, delta: Int) {
        val list = _state.value.rowOrder.toMutableList()
        val i = list.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        _state.update { s -> s.copy(rowOrder = list) }
        prefs.edit().putString(KEY_ROW_ORDER, list.joinToString(",")).apply()
    }
    fun setSeasons(value: Boolean) = put(KEY_SEASONS) { _state.update { s -> s.copy(showSeasons = value) }; value }
    fun setSectionStyle(value: HomeCardStyle) {
        _state.update { s -> s.copy(sectionStyle = value) }
        prefs.edit().putString(KEY_SECTION_STYLE, value.name).apply()
    }
    fun setPlotLines(value: Int) {
        _state.update { s -> s.copy(plotLines = value) }
        prefs.edit().putInt(KEY_PLOT_LINES, value).apply()
    }
    fun setSectionSort(value: HomeRowSort) {
        _state.update { s -> s.copy(sectionSort = value) }
        prefs.edit().putString(KEY_SECTION_SORT, value.name).apply()
    }
    fun setReleasedOnly(value: Boolean) = put(KEY_RELEASED_ONLY) { _state.update { s -> s.copy(releasedOnly = value) }; value }

    /** CANVAS A/E: změna pořadí akčních tlačítek detailu (Nastavení, šipky ▲▼). */
    fun setActionOrder(order: List<String>) {
        _state.update { s -> s.copy(actionOrder = order) }
        prefs.edit().putString(KEY_ACTION_ORDER, order.joinToString(",")).apply()
    }

    /** TV DETAIL REDESIGN (OTA 299): rozvržení TV detailu (immersive overlay vs klasický hero). */
    fun setTvLayout(value: TvDetailLayout) {
        _state.update { s -> s.copy(tvLayout = value) }
        prefs.edit().putString(KEY_TV_LAYOUT, value.name).apply()
    }
    fun setPlotAutoCompact(value: Boolean) = put(KEY_PLOT_AUTOCOMPACT) { _state.update { s -> s.copy(plotAutoCompact = value) }; value }
    fun setActionsPlacement(value: DetailActionsPlacement) {
        _state.update { s -> s.copy(actionsPlacement = value) }
        prefs.edit().putString(KEY_ACTIONS_PLACEMENT, value.name).apply()
    }

    /** Ulozene poradi -> platny seznam. Nezname klice zahodi, chybejici doplni na konec, takze
     *  starsi ulozene poradi nova verze (novy pruh) nerozbije. */
    private fun parseRowOrder(raw: String?): List<String> {
        val ulozene = raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val out = ulozene.filter { it in DETAIL_ROW_ORDER_DEFAULT }.toMutableList()
        DETAIL_ROW_ORDER_DEFAULT.forEach { if (it !in out) out += it }
        return out
    }

    private inline fun put(key: String, block: () -> Boolean) {
        prefs.edit().putBoolean(key, block()).apply()
    }

    companion object {
        private const val KEY_RICH = "detail_mode_rich"
        private const val KEY_COLLECTIONS = "detail_show_collections"
        private const val KEY_DIRECTOR = "detail_show_director"
        private const val KEY_STUDIO = "detail_show_studio"
        private const val KEY_CREATORS = "detail_show_creators"
        private const val KEY_SIMILAR = "detail_show_similar"

        // SUMÁŘ (SHW-122) — musí sedět s DetailViewModel.KEY_CURATED_* (jiný modul, sdílený string).
        private const val KEY_CURATED = "detail_curated_enabled"
        private const val KEY_CURATED_FIRST = "detail_curated_first"
        private const val KEY_ROW_ORDER = "detail_row_order"
        private const val KEY_SEASONS = "detail_show_seasons"
        private const val KEY_SECTION_STYLE = "detail_section_style"
        private const val KEY_SECTION_SORT = "detail_section_sort"
        private const val KEY_RELEASED_ONLY = "detail_section_released_only"
        private const val KEY_PLOT_LINES = "detail_plot_lines"
        private const val KEY_ACTION_ORDER = "detail_action_order"
        // TV DETAIL REDESIGN (OTA 299)
        private const val KEY_TV_LAYOUT = "detail_tv_layout"
        private const val KEY_PLOT_AUTOCOMPACT = "detail_plot_autocompact"
        private const val KEY_ACTIONS_PLACEMENT = "detail_actions_placement"
        // CANVAS A5: větší rozsah řádků popisu (3–25) + „bez omezení" (0).
        val PLOT_LINE_OPTIONS = listOf(3, 5, 8, 10, 15, 20, 25, 0)   // 0 = bez omezení
    }
}
