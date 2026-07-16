package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.foryou.ForYouAccumulationStore
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
 * F2 (Track 2) — perzistentní AKUMULACE: `forYou()` vrací jen aktuální snímek (~60 dle vkusu), ten se přes
 * [ForYouAccumulationStore] MERGuje s dřívějšími (dedup, strop, per-profil). Sekce tak roste místo aby se
 * přepisovala; akumulace přežívá restart (načítá se v [reload] z prefs ještě před čerstvým dotazem).
 */
@HiltViewModel
class TvForYouViewModel @Inject constructor(
    private val curatorLoader: CuratorLoader,
    private val profileRepository: ProfileRepository,
    private val viewModeStore: ViewModeStore,
    private val accumulationStore: ForYouAccumulationStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ForYouUiState())
    val state: StateFlow<ForYouUiState> = _state.asStateFlow()

    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { ViewMode.fromKey(it[ViewModeStore.SECTION_FOR_YOU]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    private var loadJob: Job? = null

    init {
        // Per-profil: změna aktivního profilu → přenačti doporučení (kurátor si prefs/profil řeší interně).
        // Akumulace je klíčovaná profilem → přepnutí profilu ukáže jeho vlastní rostoucí seznam (nemíchá se).
        profileRepository.activeProfile
            .onEach { profile -> reload(profile?.id) }
            .launchIn(viewModelScope)
    }

    fun setViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_FOR_YOU, mode.storeKey)

    /** Klíč akumulace pro daný profil (null = žádný aktivní profil → oddělený „none" kbelík). */
    private fun profileKey(id: Long?): String = id?.let { "p$it" } ?: "none"

    private fun reload(profileId: Long?) {
        loadJob?.cancel()
        val key = profileKey(profileId)
        // Nejdřív ukaž akumulované z perzistence (přežije restart) — než doteče čerstvý snímek kurátora.
        _state.value = ForYouUiState(items = accumulationStore.load(key), loading = true)
        loadJob = viewModelScope.launch {
            val fresh = curatorLoader.forYou(limit = 60)
            // Merge s dřívějšími (dedup + strop + per-profil). Prázdný `fresh` akumulaci NEMAŽE (viz store).
            val merged = accumulationStore.accumulate(key, fresh)
            _state.value = ForYouUiState(items = merged, loading = false)
        }
    }
}
