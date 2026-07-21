package com.github.jankoran90.showlyfin.feature.discover.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.RecommendationsStore
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaGrouping
import com.github.jankoran90.showlyfin.feature.discover.filmoteka.FilmotekaUiState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stav sekce „Pro tebe" — plochý seznam kurátorských doporučení + příznak načítání. Konzument = TV/sdílený screen. */
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
 *
 * **MIRROR (user 2026-07-20)** — telefon appky Filmy dostává stejné nástroje jako Filmotéka: osy (Vše/Žánr/Země),
 * filtr žánru+země, řazení osy „Vše", počítadlo, řady. Vystaveno zvlášť jako [filmotekaState] ([FilmotekaUiState]),
 * grupováno SDÍLENÝM [FilmotekaGrouping.build] nad TÝMIŽ akumulovanými tipy (parita 1:1, žádný drift). TV/sdílený
 * screen dál čte plochý [state]; filtry jsou živé (per-session), **akumulace beze změny**.
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val curatorLoader: CuratorLoader,
    private val profileRepository: ProfileRepository,
    private val viewModeStore: ViewModeStore,
    private val recommendationsStore: RecommendationsStore,
    private val settings: FilmotekaSettingsStore,
) : ViewModel() {

    /** MIRROR — živé nástroje browsingu (osa/řazení/filtry) telefonní plochy „Pro tebe". Per-session (neukládá se). */
    private data class Tools(
        val axis: FilmotekaAxis = FilmotekaAxis.ALL,
        val allSort: FilmotekaAllSort = FilmotekaAllSort.RECENT,
        val genreFilter: Set<String> = emptySet(),
        val countryFilter: Set<CinematographyRegion> = emptySet(),
    )

    private val _loading = MutableStateFlow(true)
    private val _tools = MutableStateFlow(Tools())

    /** Plochý stav (TV + sdílený ForYouScreen) — beze změny oproti původku. */
    val state: StateFlow<ForYouUiState> = combine(recommendationsStore.items, _loading) { items, loading ->
        ForYouUiState(items = items, loading = loading && items.isEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ForYouUiState())

    /**
     * MIRROR — stav ve stejném tvaru jako Filmotéka ([FilmotekaUiState]) → sdílený telefonní browse UI ho renderuje
     * 1:1. Přeskupuje se reaktivně na každou změnu akumulovaných tipů / nástrojů / zapnutých regionů (grouper je levý).
     */
    // RUBRIC (SHW-104) — enabledRegions + hybridGenres do jednoho flow (combine má typovaný strop 5 argumentů).
    private val filmotekaOptions: kotlinx.coroutines.flow.Flow<Pair<Set<CinematographyRegion>, Boolean>> =
        combine(settings.enabledRegions, settings.hybridGenres) { regions, hybrid -> regions to hybrid }

    val filmotekaState: StateFlow<FilmotekaUiState> =
        combine(recommendationsStore.items, _loading, _tools, filmotekaOptions) { items, loading, tools, opts ->
            val result = FilmotekaGrouping.build(
                all = items,
                axis = tools.axis,
                allSort = tools.allSort,
                genreFilter = tools.genreFilter,
                countryFilter = tools.countryFilter,
                enabledRegions = opts.first,
                hybridGenres = opts.second,
            )
            FilmotekaUiState(
                axis = tools.axis,
                rails = result.rails,
                loading = loading && items.isEmpty(),
                allSort = tools.allSort,
                total = result.total,
                genreFilter = tools.genreFilter,
                availableGenres = result.availableGenres,
                countryFilter = tools.countryFilter,
                availableCountries = result.availableCountries,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FilmotekaUiState())

    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { ViewMode.fromKey(it[ViewModeStore.SECTION_FOR_YOU]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    /**
     * MIRROR (user 2026-07-20) — PERZISTENTNÍ přepínač mřížka/seznam telefonní plochy „Pro tebe" ([ViewModeStore],
     * klíč `SECTION_FOR_YOU`; dřív jen per-session `remember`). Default = SEZNAM (telefon), na rozdíl od [viewMode]
     * výše (GRID default, immersive řada na TV) — obojí čte tentýž uložený klíč, liší se jen fallback při nevyplnění.
     */
    val browseViewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { modes -> modes[ViewModeStore.SECTION_FOR_YOU]?.let { ViewMode.fromKey(it) } ?: ViewMode.LIST }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.LIST)

    /** MIRROR — ulož volbu zobrazení telefonní plochy „Pro tebe" (perzistentní, per-zařízení). */
    fun setBrowseViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_FOR_YOU, mode.storeKey)

    private var loadJob: Job? = null

    init {
        // Změna aktivního profilu → dorovnej jeho serverový seznam a přimíchej čerstvý snímek kurátora.
        // Akumulace je per-profil na serveru → přepnutí profilu ukáže jeho vlastní rostoucí seznam.
        profileRepository.activeProfile
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    fun setViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_FOR_YOU, mode.storeKey)

    // ── MIRROR: nástroje browsingu (parita s TvFilmotekaViewModel) ──────────────────

    /** Přepnutí osy (Vše/Žánr/Země) — jen přeskupí (bez fetch). */
    fun setAxis(axis: FilmotekaAxis) = _tools.update { if (it.axis == axis) it else it.copy(axis = axis) }

    /** Řazení osy „Vše" (Nedávno / Abecedně). */
    fun setAllSort(sort: FilmotekaAllSort) = _tools.update { if (it.allSort == sort) it else it.copy(allSort = sort) }

    /** GENRE-FILTER — přepni žánr (multi-select, prázdný = vše). */
    fun toggleGenreFilter(genre: String) {
        val g = genre.trim()
        if (g.isBlank()) return
        _tools.update { it.copy(genreFilter = if (g in it.genreFilter) it.genreFilter - g else it.genreFilter + g) }
    }

    /** GENRE-FILTER — zruš filtr žánrů. */
    fun clearGenreFilter() = _tools.update { if (it.genreFilter.isEmpty()) it else it.copy(genreFilter = emptySet()) }

    /** COUNTRY-FILTER — přepni region (multi-select, prázdný = vše). */
    fun toggleCountryFilter(region: CinematographyRegion) =
        _tools.update { it.copy(countryFilter = if (region in it.countryFilter) it.countryFilter - region else it.countryFilter + region) }

    /** COUNTRY-FILTER — zruš filtr země. */
    fun clearCountryFilter() = _tools.update { if (it.countryFilter.isEmpty()) it else it.copy(countryFilter = emptySet()) }

    private fun reload() {
        loadJob?.cancel()
        _loading.value = true
        loadJob = viewModelScope.launch {
            // 1) Dorovnej store se serverem pro AKTUÁLNÍ profil (adopce při přepnutí / union při shodě).
            recommendationsStore.syncNow()
            // 2) Čerstvý snímek kurátora → merge (dedup + strop + push na server). Prázdný snímek nemaže.
            //    pollUntilReady=true: sekce „Pro tebe" na `pending` (mozek počítá) počká a re-polluje →
            //    tipy naskočí na této obrazovce bez zavření (SUBSTRATE F2c stale-while-revalidate).
            val fresh = curatorLoader.forYou(limit = 60, pollUntilReady = true)
            recommendationsStore.accumulate(fresh)
            _loading.value = false
        }
    }
}
