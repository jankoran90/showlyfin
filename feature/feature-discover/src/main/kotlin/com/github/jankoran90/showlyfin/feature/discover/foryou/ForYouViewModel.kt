package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.RecommendationsStore
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * BESPOKE (SHW-95) — VM sekce „Pro tebe" (nahrazuje Objevovat), **sdílená TV i telefonem** (dřív `TvForYouViewModel`).
 * Zdroj = sdílený [CuratorLoader] (Singleton, tentýž, co plní Home řadu „Pro tebe"). `forYou()` už dělá enrich +
 * věkový gate interně → tady se NEopakuje. Přepínač zobrazení mřížka↔immersive řada přes [ViewModeStore]
 * (klíč `SECTION_FOR_YOU`; telefon přepínač nepoužívá, jede grid).
 *
 * **Perzistentní AKUMULACE (F2) přes [RecommendationsStore]** — synchronizovaná přes backend (per-profil, sdílená
 * TV↔telefon, přežije reinstall). `forYou()` vrací jen aktuální snímek (~60 dle vkusu); ten se MERGuje s
 * akumulovaným ([RecommendationsStore.accumulate]). Sekce roste místo aby se přepisovala. Reaktivní [state] čte
 * přímo ze [RecommendationsStore.items] → akumulované se ukáže okamžitě (i před doběhnutím čerstvého snímku).
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val curatorLoader: CuratorLoader,
    private val profileRepository: ProfileRepository,
    private val viewModeStore: ViewModeStore,
    private val recommendationsStore: RecommendationsStore,
) : ViewModel() {

    private val _loading = MutableStateFlow(true)

    val state: StateFlow<ForYouUiState> = combine(recommendationsStore.items, _loading) { items, loading ->
        ForYouUiState(items = items, loading = loading && items.isEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ForYouUiState())

    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { ViewMode.fromKey(it[ViewModeStore.SECTION_FOR_YOU]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    private var loadJob: Job? = null

    init {
        // Změna aktivního profilu → dorovnej jeho serverový seznam a přimíchej čerstvý snímek kurátora.
        // Akumulace je per-profil na serveru → přepnutí profilu ukáže jeho vlastní rostoucí seznam.
        profileRepository.activeProfile
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun setViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_FOR_YOU, mode.storeKey)

    private fun reload() {
        loadJob?.cancel()
        _loading.value = true
        loadJob = viewModelScope.launch {
            // 1) Dorovnej store se serverem pro AKTUÁLNÍ profil (adopce při přepnutí / union při shodě).
            recommendationsStore.syncNow()
            // 2) Čerstvý snímek kurátora → merge (dedup + strop + push na server). Prázdný snímek nemaže.
            val fresh = curatorLoader.forYou(limit = 60)
            recommendationsStore.accumulate(fresh)
            _loading.value = false
        }
    }
}
