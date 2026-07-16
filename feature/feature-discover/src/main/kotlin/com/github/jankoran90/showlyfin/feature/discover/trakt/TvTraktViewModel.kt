package com.github.jankoran90.showlyfin.feature.discover.trakt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** COUCH Fáze C — jedna Trakt řada (kategorie / konkrétní seznam). Prázdná řada se do UI nedostane. */
data class TraktRail(
    val id: String,
    val title: String,
    val items: List<HomeRowItem>,
)

data class TvTraktUiState(
    val rows: List<TraktRail> = emptyList(),
    val isLoading: Boolean = true,
    // Progres načítání (2026-07-16): kolik řad z celku už doběhlo, ať uživatel nekouká na slepé „Načítám…".
    val loadingDone: Int = 0,
    val loadingTotal: Int = 0,
)

/**
 * COUCH (SHW-88) Fáze C — sekce Trakt na ŘÁDKOVÉM modelu (jako Domů/Knihovna): místo chip výběru jedné
 * kategorie + mřížky vykreslí VÍC immersive řad naráz — Watchlist, Zhlédnuto, Doporučeno (couchmonkey) a
 * KAŽDÝ userův Trakt seznam jako vlastní řadu. Data přes sdílený [TraktRowLoader]. Vše OAuth; nepřihlášený /
 * prázdná kategorie → řada se vynechá (viz filtr prázdných). Věkový gate řeší loader (dětský profil).
 */
@HiltViewModel
class TvTraktViewModel @Inject constructor(
    private val loader: TraktRowLoader,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TvTraktUiState())
    val state: StateFlow<TvTraktUiState> = _state.asStateFlow()

    private var lastProfileId: Long? = null

    init {
        // FIX C (2026-07-16): na přepnutí profilu (jiný Trakt účet) přenačti řady — jinak sekce Trakt drží
        // watchlist/hodnocení/seznamy STARÉHO účtu, dokud se VM nevytvoří znovu (je retained na shellu).
        // Vzor = RatingViewModel/TvFilmotekaViewModel (observují activeProfile). Iniciální emit = první load.
        profileRepository.activeProfile
            .onEach { p -> if (p?.id != lastProfileId) { lastProfileId = p?.id; load() } }
            .launchIn(viewModelScope)
    }

    fun load() {
        _state.update { it.copy(isLoading = true, rows = emptyList(), loadingDone = 0, loadingTotal = 0) }
        viewModelScope.launch {
            val cfg = profileRepository.activeConfig.value
            // Seznam userových Trakt seznamů zvlášť (může selhat / být prázdný) — bez něj jedou pevné kategorie.
            val myLists = runCatching { loader.myLists() }.getOrDefault(emptyList())
            // Každá řada = (id, titulek, suspend loader). Watchlist/Zhlédnuto/Doporučeno + userovy seznamy.
            val specs: List<Triple<String, String, suspend () -> List<MediaItem>>> = buildList {
                add(Triple("watchlist", "Watchlist") { loader.watchlist("all") })
                add(Triple("history", "Zhlédnuto") { loader.history("all") })
                add(Triple("recommended", "Doporučeno") { loader.couchmonkeyRecommendations() })
                myLists.forEach { l -> add(Triple("list_${l.ids.trakt}", l.name) { loader.list(l.ids.trakt) }) }
            }
            val total = specs.size
            _state.update { it.copy(loadingTotal = total) }
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            // Načti VŠE paralelně, ale KAŽDOU řadu vypublikuj hned jak doběhne (ne až celý balík) — první
            // řada naskočí za pár vteřin místo čekání na nejpomalejší (~3 min u velkého účtu). Řazení + skrývání
            // (Nastavení → Řady Traktu) se aplikuje průběžně, takže pořadí sedí i během načítání.
            coroutineScope {
                specs.forEach { (id, title, loadRow) ->
                    launch {
                        val r = rail(id, title, runCatching { loadRow() }.getOrDefault(emptyList()))
                        val d = done.incrementAndGet()
                        _state.update { st ->
                            val merged = if (r != null) st.rows.filter { it.id != r.id } + r else st.rows
                            st.copy(rows = orderRows(cfg, merged), loadingDone = d, isLoading = d < total)
                        }
                    }
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    /** CONVERGE V1 — per-profil řazení + skrývání řad sekce Trakt (Nastavení → Řady Traktu). */
    private fun orderRows(cfg: ProfileConfig, rows: List<TraktRail>): List<TraktRail> =
        cfg.orderedTraktRows(rows.map { it.id })
            .mapNotNull { id -> rows.firstOrNull { it.id == id } }
            .filter { cfg.isTraktRowVisible(it.id) }

    /** Řada z Trakt [MediaItem]; prázdná → null (odfiltruje se, řada se nezobrazí). */
    private fun rail(id: String, title: String, items: List<MediaItem>): TraktRail? =
        items.takeIf { it.isNotEmpty() }?.let { list ->
            TraktRail(id = id, title = title, items = list.map { it.toHomeRowItem(id) })
        }

    /** Obohacené Trakt [MediaItem] → [HomeRowItem] (mirror `TvHomeViewModel` mapperu). */
    private fun MediaItem.toHomeRowItem(railId: String) = HomeRowItem(
        key = "trakt_${railId}_${type}_${tmdbId ?: traktId}",
        title = displayTitle,
        year = year,
        posterUrl = posterUrl("w342"),
        landscapeUrl = backdropUrl("w780"),
        mediaItem = this,
    )
}
