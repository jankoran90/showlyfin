package com.github.jankoran90.showlyfin.feature.discover.trakt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** COUCH T-SEKCE — kategorie sekce Trakt (chip lišta, stejně jako Objevovat má Doporučené/Trendy/…). */
enum class TraktCategory(val label: String) {
    RECOMMENDED("Doporučeno"),
    WATCHLIST("Watchlist"),
    HISTORY("Zhlédnuto"),
    MY_LISTS("Moje seznamy"),
}

/** Jeden Trakt seznam jako chip v „Moje seznamy". */
data class TraktListChip(val id: Long, val name: String)

data class TvTraktUiState(
    // COUCH R2: default WATCHLIST (má data) místo RECOMMENDED (couchmonkey — prázdné bez nastavených listů →
    // vypadalo to jako „nic k zobrazení"). Doporučeno zůstává jako chip.
    val category: TraktCategory = TraktCategory.WATCHLIST,
    val lists: List<TraktListChip> = emptyList(),
    val selectedListId: Long? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * COUCH (SHW-88) — sekce Trakt (strukturálně jako Objevovat: chip lišta kategorií + mřížka). Data přes
 * sdílený [TraktRowLoader]. „Moje seznamy" = všechny userovy Trakt listy jako chipy (v pořadí z API),
 * výběr chipu → položky listu. Vše OAuth; nepřihlášený → prázdno.
 */
@HiltViewModel
class TvTraktViewModel @Inject constructor(
    private val loader: TraktRowLoader,
) : ViewModel() {

    private val _state = MutableStateFlow(TvTraktUiState())
    val state: StateFlow<TvTraktUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { selectCategory(TraktCategory.WATCHLIST) }

    fun selectCategory(cat: TraktCategory) {
        job?.cancel()
        android.util.Log.i("COUCH_TraktSec", "selectCategory: $cat")
        _state.update { it.copy(category = cat, isLoading = true, items = emptyList()) }
        job = viewModelScope.launch {
            when (cat) {
                TraktCategory.RECOMMENDED -> emitItems(loader.couchmonkeyRecommendations())
                TraktCategory.WATCHLIST -> emitItems(loader.watchlist("all"))
                TraktCategory.HISTORY -> emitItems(loader.history("all"))
                TraktCategory.MY_LISTS -> {
                    val lists = loader.myLists().map { TraktListChip(it.ids.trakt, it.name) }
                    val first = lists.firstOrNull()
                    val items = if (first != null) loader.list(first.id) else emptyList()
                    android.util.Log.i("COUCH_TraktSec", "MY_LISTS: ${lists.size} seznamů, první='${first?.name}' → ${items.size} položek")
                    _state.update { it.copy(lists = lists, selectedListId = first?.id, items = items, isLoading = false) }
                }
            }
        }
    }

    fun selectList(listId: Long) {
        job?.cancel()
        _state.update { it.copy(selectedListId = listId, isLoading = true, items = emptyList()) }
        job = viewModelScope.launch { emitItems(loader.list(listId)) }
    }

    private fun emitItems(items: List<MediaItem>) {
        android.util.Log.i("COUCH_TraktSec", "emitItems: ${items.size} položek")
        _state.update { it.copy(items = items, isLoading = false) }
    }
}
