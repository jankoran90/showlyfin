package com.github.jankoran90.showlyfin.feature.discover.trakt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val rows = coroutineScope {
                // Pevné kategorie + seznamy načti paralelně; každou řadu obohať a odfiltruj prázdné.
                val watchlist = async { rail("watchlist", "Watchlist", loader.watchlist("all")) }
                val history = async { rail("history", "Zhlédnuto", loader.history("all")) }
                val recommended = async { rail("recommended", "Doporučeno", loader.couchmonkeyRecommendations()) }
                val lists = async {
                    loader.myLists().map { l ->
                        async { rail("list_${l.ids.trakt}", l.name, loader.list(l.ids.trakt)) }
                    }.awaitAll()
                }
                // Pořadí: Watchlist (má data, default), Zhlédnuto, Doporučeno, pak userovy seznamy v pořadí z API.
                (listOf(watchlist.await(), history.await(), recommended.await()) + lists.await()).filterNotNull()
            }
            // CONVERGE V1 — aplikuj per-profil řazení + skrývání řad (Nastavení → Řady Traktu).
            val cfg = profileRepository.activeConfig.value
            val ordered = cfg.orderedTraktRows(rows.map { it.id })
                .mapNotNull { id -> rows.firstOrNull { it.id == id } }
                .filter { cfg.isTraktRowVisible(it.id) }
            _state.update { it.copy(rows = ordered, isLoading = false) }
        }
    }

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
