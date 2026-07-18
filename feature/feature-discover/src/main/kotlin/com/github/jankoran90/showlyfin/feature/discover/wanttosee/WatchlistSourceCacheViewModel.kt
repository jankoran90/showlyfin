package com.github.jankoran90.showlyfin.feature.discover.wanttosee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CELLULOID (SHW-98) — dávkové dohledání zdrojů pro CELÝ watchlist (user 2026-07-18 „ano chci to").
 * Dnes se auto-cache zdroje pouští jen při PŘIDÁNÍ do „Chci vidět" ([DetailViewModel.toggleWatchlist]).
 * Stávající tituly bez zdroje zůstaly prázdné. Tenhle job projde Trakt watchlist (filmy), najde ty BEZ
 * uloženého zdroje a na každý pošle `triggerAutoCache` (backend `/gems/cache-one` → RD na pozadí) S ROZESTUPEM,
 * ať nezahltí Real-Debrid. Backend cachuje na pozadí (fire-and-forget), WorkingSource se propíše sám.
 *
 * Sdílený mezi telefonem (Nastavení blok + sekce „Chci vidět") a TV (sekce „Chci vidět") — parita shellů.
 */
@HiltViewModel
class WatchlistSourceCacheViewModel @Inject constructor(
    private val traktRowLoader: TraktRowLoader,
    private val workingSourceStore: WorkingSourceStore,
    private val parentalControls: ParentalControlsRepository,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        data class Done(val requested: Int, val already: Int) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Rozestup mezi požadavky — RD/backend nesmí dostat 100 dotazů naráz. */
    private val gapMs = 4000L

    fun runBackfill() {
        if (_state.value is State.Running) return
        viewModelScope.launch {
            _state.value = State.Running(0, 0)
            val watchlist = runCatching { traktRowLoader.watchlist("movies") }.getOrElse {
                _state.value = State.Error("Nepodařilo se načíst watchlist z Traktu — přihlášen?")
                return@launch
            }
            // Jen filmy s imdb, které ještě NEMAJÍ uložený zdroj.
            val missing = watchlist.filter { it.imdbId != null && workingSourceStore.get(it.imdbId, it.tmdbId) == null }
            val already = watchlist.size - missing.size
            if (missing.isEmpty()) {
                _state.value = State.Done(requested = 0, already = already)
                return@launch
            }
            val policy = cachePolicy()
            missing.forEachIndexed { i, item ->
                workingSourceStore.triggerAutoCache(item.imdbId, item.tmdbId, item.title, item.year, policy)
                _state.value = State.Running(i + 1, missing.size)
                if (i < missing.lastIndex) delay(gapMs)
            }
            _state.value = State.Done(requested = missing.size, already = already)
        }
    }

    private fun cachePolicy(): String = when (parentalControls.profile.value.effectiveAgeRating) {
        AgeRating.CHILDREN, AgeRating.FAMILY -> "child"
        else -> "original"
    }
}
