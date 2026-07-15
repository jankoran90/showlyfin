package com.github.jankoran90.showlyfin.feature.discover.wanttosee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stav sekce „Chci vidět" — Trakt watchlist (nejnověji přidané první) + příznak načítání. */
data class WantToSeeUiState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
)

/**
 * BESPOKE (SHW-95) F1/T5a — VM sekce „Chci vidět" (Trakt watchlist) do sidebaru. Skutečný watchlist (dosud
 * na TV neměl vlastní sekci — `TvSection.WATCHLIST` renderuje Oblíbené). Zdroj = sdílený [TraktRowLoader]
 * `watchlist("all")` (enrich + věkový gate uvnitř loaderu). Per-profil reload při přepnutí profilu.
 * Nepřihlášený k Traktu → prázdný list (sekci sidebar skryje jako Trakt).
 */
@HiltViewModel
class TvWantToSeeViewModel @Inject constructor(
    private val traktLoader: TraktRowLoader,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WantToSeeUiState())
    val state: StateFlow<WantToSeeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        profileRepository.activeProfile
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun refresh() = reload()

    private fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            val items = traktLoader.watchlist("all")
            _state.value = WantToSeeUiState(items = items, loading = false)
        }
    }
}
