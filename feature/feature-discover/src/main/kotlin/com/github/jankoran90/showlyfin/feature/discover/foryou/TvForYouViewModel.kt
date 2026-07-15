package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stav sekce „Pro tebe" — plochý seznam kurátorských doporučení + příznak načítání. */
data class ForYouUiState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
)

/**
 * BESPOKE (SHW-95) F1/T1 — VM sekce „Pro tebe" (nahrazuje Objevovat). Klon kostry
 * [com.github.jankoran90.showlyfin.feature.discover.filmoteka.TvFilmotekaViewModel], ale zdroj = sdílený
 * [CuratorLoader] (Singleton, tentýž, co plní Home řadu „Pro tebe") s vyšším limitem. `forYou()` už dělá
 * enrich + věkový gate interně → tady se NEopakuje. Přepínač zobrazení mřížka↔immersive řada přes
 * [ViewModeStore] (klíč `SECTION_FOR_YOU`), vzor [com.github.jankoran90.showlyfin.feature.discover.DiscoverViewModel].
 *
 * Pozn.: `forYou()` nestránkuje (backend vrací max ~60), takže mřížka je statická; perzistentní akumulace
 * napříč obměnami je F2 (Track 2).
 */
@HiltViewModel
class TvForYouViewModel @Inject constructor(
    private val curatorLoader: CuratorLoader,
    private val profileRepository: ProfileRepository,
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ForYouUiState())
    val state: StateFlow<ForYouUiState> = _state.asStateFlow()

    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { ViewMode.fromKey(it[ViewModeStore.SECTION_FOR_YOU]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    private var loadJob: Job? = null

    init {
        // Per-profil: změna aktivního profilu → přenačti doporučení (kurátor si prefs/profil řeší interně).
        profileRepository.activeProfile
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun setViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_FOR_YOU, mode.storeKey)

    private fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            val items = curatorLoader.forYou(limit = 60)
            _state.value = ForYouUiState(items = items, loading = false)
        }
    }
}
