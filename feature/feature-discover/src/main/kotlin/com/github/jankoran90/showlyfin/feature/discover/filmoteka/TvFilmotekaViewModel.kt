package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import com.github.jankoran90.showlyfin.data.uploader.TraktSyncSignal
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Jedna řada Filmotéky (hodnota osy → tituly). Neutrální model — UI (ui-tv) ho mapuje na `TvRail`. */
data class FilmotekaRail(
    val id: String,
    val title: String,
    val items: List<HomeRowItem>,
)

/** Stav sekce Filmotéka. */
data class FilmotekaUiState(
    val axis: FilmotekaAxis = FilmotekaAxis.ALL,
    val rails: List<FilmotekaRail> = emptyList(),
    val loading: Boolean = true,
    /** Aktuální řazení osy „Vše" — pro telefonní chip (Nedávno / Abecedně). TV ho ignoruje. */
    val allSort: FilmotekaAllSort = FilmotekaAllSort.RECENT,
    /** Počet unikátních titulů v bázi (po dedup+gate, napříč osami stejný) — pro telefonní ukazatel „N filmů". */
    val total: Int = 0,
    /**
     * GENRE-FILTER — aktivní filtr žánrů (multi-select). Prázdný = bez filtru (vše). Filtruje se dle HLAVNÍHO
     * žánru titulu (první = nejvyšší váha, stejně jako grupování osy Žánr) → titul projde, je-li jeho hlavní
     * žánr ve výběru. Karty/seznamy dál ukazují všechny žánry. Živý stav (neukládá se), reset při restartu.
     */
    val genreFilter: Set<String> = emptySet(),
    /** Všechny hlavní žánry přítomné v bázi (dle četnosti sestupně) — nabídka pro picker filtru. */
    val availableGenres: List<String> = emptyList(),
    /**
     * COUNTRY-FILTER (user 2026-07-20) — aktivní filtr zemí/regionů (multi-select), analogie genreFilter.
     * Prázdný = bez filtru. Filtruje se dle HLAVNÍ země/regionu titulu ([FilmotekaGrouping.mainRegionOf] = první
     * region s největší vahou, stejně jako [FilmotekaGrouping.mainGenreOf]). Živý stav, neukládá se.
     */
    val countryFilter: Set<CinematographyRegion> = emptySet(),
    /** Všechny hlavní regiony přítomné v bázi (dle četnosti sestupně) — nabídka pro picker filtru země. */
    val availableCountries: List<CinematographyRegion> = emptyList(),
)

/**
 * CINEMATHEQUE (SHW-90) — agregační VM Filmotéky. Sjednocuje 4 zdroje ([FilmotekaSource]) do jedné plochy
 * přeskupitelné podle osy ([FilmotekaAxis]). Konstrukce zrcadlí [com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel].
 *
 * Tok: JF knihovna + zapamatované zdroje + Trakt watchlist se jednorázově sloučí do báze (dedup podle
 * tmdb→imdb, precedence JELLYFIN>WORKING>TRAKT); Oblíbené se mergují REAKTIVNĚ (StateFlow). Vše se obohatí
 * ([MediaEnricher]) a projde věkovým gate ([ContentAgeGate]) PŘED grupováním. Přepnutí osy jen přeskupí
 * už-obohacenou bázi (bez fetch). Reload na změnu profilu.
 *
 * Osa GENRE = řady dle žánru; osa COUNTRY (F2) = řady dle regionální „kinematografie" ([CinematographyRegion]),
 * respektuje zapnuté regiony ([FilmotekaSettingsStore.enabledRegions]), OSTATNI vždy poslední.
 */
@HiltViewModel
class TvFilmotekaViewModel @Inject constructor(
    // FOYER (SHW-107): sběr báze (JF/working/Trakt/Oblíbené + enrich + věkový gate) žije ve sdíleném
    // [FilmotekaBaseLoader] — VM drží už jen stav sekce (osa, filtry, řady).
    private val filmotekaBase: FilmotekaBaseLoader,
    private val favorites: FavoritesRepository,
    private val workingSources: WorkingSourceStore,
    private val profileRepository: ProfileRepository,
    private val settings: FilmotekaSettingsStore,
    private val traktSyncSignal: TraktSyncSignal,
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(FilmotekaUiState())
    val state: StateFlow<FilmotekaUiState> = _state.asStateFlow()

    /**
     * MIRROR (user 2026-07-20) — PERZISTENTNÍ přepínač mřížka/seznam telefonní Filmotéky ([ViewModeStore],
     * klíč `SECTION_FILMOTEKA`; dřív jen per-session `remember` → po opuštění sekce se ztratil). Default =
     * SEZNAM (přání usera 2026-07-17), ne GRID jako [ViewMode.fromKey] null-fallback. TV render ho nepoužívá.
     */
    val browseViewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { modes -> modes[ViewModeStore.SECTION_FILMOTEKA]?.let { ViewMode.fromKey(it) } ?: ViewMode.LIST }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.LIST)

    /** MIRROR — ulož volbu zobrazení telefonní Filmotéky (perzistentní, per-zařízení). */
    fun setBrowseViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_FILMOTEKA, mode.storeKey)

    // Enrichnutá + věkově gatovaná báze (JF+Working+Trakt) a zvlášť Oblíbené (reaktivní). Grupování je merguje.
    @Volatile private var baseItems: List<MediaItem> = emptyList()
    @Volatile private var favoriteItems: List<MediaItem> = emptyList()

    /** GENRE-FILTER — živý výběr žánrů (viz [FilmotekaUiState.genreFilter]). Drží se napříč přeskupením os. */
    @Volatile private var genreFilter: Set<String> = emptySet()

    /** COUNTRY-FILTER — živý výběr zemí/regionů (viz [FilmotekaUiState.countryFilter]). Drží se napříč osami. */
    @Volatile private var countryFilter: Set<CinematographyRegion> = emptySet()

    private var loadJob: Job? = null

    init {
        // Per-profil: přepni nastavení Filmotéky na profil, pak přenačti obsah (jeden collector = pořadí).
        profileRepository.activeProfile
            .onEach { p ->
                settings.switchProfile(p?.id)
                reload()
            }
            .launchIn(viewModelScope)

        // Oblíbené reaktivně (per-profil sync běží async) → přemerguj bez plného reloadu.
        favorites.items
            .onEach { list -> refreshFavorites(list) }
            .launchIn(viewModelScope)

        // CONVERGE V1 — změna řazení osy „Vše" v Nastavení → přeskup (bez fetch). drop(1) = ignoruj
        // iniciální emit (base ještě nemusí být načtená; reload/rebuild ho pokryjí).
        settings.allSort
            .drop(1)
            .onEach { if (_state.value.axis == FilmotekaAxis.ALL) rebuild(FilmotekaAxis.ALL) }
            .launchIn(viewModelScope)

        // CONVERGE — přidání/odebrání „Chci vidět" v detailu ([DetailViewModel.toggleWatchlist]) bumpne sdílený
        // Trakt signál → přenačti bázi, aby čerstvý titul naskočil i ve Filmotéce (ne jen v sekci Trakt/Domů;
        // watchlist NENÍ reaktivní store, proto tenhle pull). drop(1) = ignoruj iniciální hodnotu. Oblíbené jsou
        // řešené zvlášť reaktivně přes favorites.items výše.
        traktSyncSignal.version
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)

        // CELLULOID M2.4 — auto-cache backend zapíše uložený zdroj (WorkingSource) → savedKeys se změní →
        // přenačti, ať se titul objeví ve Filmotéce ŽIVĚ (dřív nutný restart). Padne i na TV = bonus.
        // drop(1) = ignoruj iniciální emit (base pokryje reload z profilu výše).
        workingSources.savedKeys
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)

        // ORCHARD (user 07-19) — ŽIVÝ refresh Filmotéky při změně ZDROJŮ (zapnutí/vypnutí Jellyfin/Working/…)
        // a při změně výběru JF knihoven Filmotéky (filmotekaJfLibraries). Dřív se toggle projevil až po
        // restartu appky / vymazání cache (user hlásil na TV). drop(1) = ignoruj iniciální emit.
        settings.sources
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)

        // RUBRIC (SHW-104) — přepnutí hybridního seskupení žánrů jen PŘESKUPÍ už-obohacenou bázi (bez fetch),
        // aby se řady/nabídka filtru ose Žánr překreslily ŽIVĚ. drop(1) = ignoruj iniciální emit.
        settings.hybridGenres
            .drop(1)
            .onEach { rebuild(_state.value.axis) }
            .launchIn(viewModelScope)
        profileRepository.activeConfig
            .map { it.filmotekaJfLibraries }
            .distinctUntilChanged()
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    /** Přepnutí osy — jen přeskupí už-obohacenou bázi (bez fetch). Volá UI z přepínače osy. */
    fun setAxis(axis: FilmotekaAxis) {
        if (_state.value.axis != axis) rebuild(axis)
    }

    /**
     * CELLULOID M2.4 — telefonní chip řazení osy „Vše" (Nedávno / Abecedně). Uloží do Nastavení (per profil,
     * sdílené s TV) a hned přeskup, jsme-li na ose ALL. TV mění řazení v Nastavení, tady přímo z plochy.
     */
    fun setAllSort(sort: FilmotekaAllSort) {
        if (settings.allSort.value == sort) return
        settings.setAllSort(sort)
        if (_state.value.axis == FilmotekaAxis.ALL) rebuild(FilmotekaAxis.ALL)
    }

    /**
     * GENRE-FILTER — přepni žánr ve filtru (multi-select). Prázdný filtr = bez omezení. Sdílené telefon+TV;
     * hned přeskup na aktuální ose. Neukládá se (živý browsing filtr).
     */
    fun toggleGenreFilter(genre: String) {
        val g = genre.trim()
        if (g.isBlank()) return
        genreFilter = if (g in genreFilter) genreFilter - g else genreFilter + g
        rebuild(_state.value.axis)
    }

    /** GENRE-FILTER — nastav celý výběr žánrů najednou (prázdná množina = zrušit filtr). */
    fun setGenreFilter(genres: Set<String>) {
        val cleaned = genres.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (cleaned == genreFilter) return
        genreFilter = cleaned
        rebuild(_state.value.axis)
    }

    /** GENRE-FILTER — zruš filtr (zobraz vše). */
    fun clearGenreFilter() {
        if (genreFilter.isEmpty()) return
        genreFilter = emptySet()
        rebuild(_state.value.axis)
    }

    /**
     * COUNTRY-FILTER (user 2026-07-20) — přepni region ve filtru země (multi-select), analogie [toggleGenreFilter].
     * Prázdný filtr = bez omezení. Filtruje dle hlavního regionu titulu. Sdílené telefon+TV; hned přeskup.
     */
    fun toggleCountryFilter(region: CinematographyRegion) {
        countryFilter = if (region in countryFilter) countryFilter - region else countryFilter + region
        rebuild(_state.value.axis)
    }

    /** COUNTRY-FILTER — zruš filtr země (zobraz vše). */
    fun clearCountryFilter() {
        if (countryFilter.isEmpty()) return
        countryFilter = emptySet()
        rebuild(_state.value.axis)
    }

    /**
     * CONVERGE — vstup do sekce: obnov VÝCHOZÍ osu z Nastavení (default „Vše"). VM je retained na úrovni
     * shellu (TvShell přepíná sekce jen `when`em), takže bez tohoto by runtime přepnutí osy z minulé návštěvy
     * uvázlo. Iniciální reload nastaví osu sám (loading==true) → skip, ať neblikneme prázdnou bází.
     */
    fun applyDefaultAxis() {
        if (_state.value.loading) return
        val target = settings.defaultAxis.value
        if (_state.value.axis != target) rebuild(target)
    }

    /** Zahoď bázi a přenačti (po přepnutí profilu). */
    private fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            baseItems = filmotekaBase.loadBase()
            rebuild(settings.defaultAxis.value)
        }
    }

    /** Obohaď + gatuj Oblíbené (jen filmy) a přeskup. Vypnutý zdroj → prázdný bucket (řeší loader). */
    private suspend fun refreshFavorites(list: List<FavoriteItem>) {
        favoriteItems = filmotekaBase.loadFavorites(list)
        rebuild(_state.value.axis)
    }

    // ── Grupování (osa) ───────────────────────────────────────────────────────────

    /**
     * MIRROR (user 2026-07-20) — grupování/filtr delegováno na SDÍLENÝ [FilmotekaGrouping.build] (tentýž grouper
     * volá i „Pro tebe" → obě sekce filtrují/grupují 1:1, žádný drift). Zde jen posbírej bázi (base>favorites),
     * předej živé filtry + nastavení a výsledek promítni do stavu.
     */
    private fun rebuild(axis: FilmotekaAxis) {
        // FOYER (SHW-107): merge base>Oblíbené + dopočet data „přidáno" dělá SDÍLENÝ [FilmotekaBaseLoader]
        // (tentýž kód pohání i řadu domova „Filmotéka — nedávno přidané" → nemůžou se rozejít).
        val all = filmotekaBase.mergeWithFavorites(baseItems, favoriteItems)
        val result = FilmotekaGrouping.build(
            all = all,
            axis = axis,
            allSort = settings.allSort.value,
            genreFilter = genreFilter,
            countryFilter = countryFilter,
            enabledRegions = settings.enabledRegions.value,
            hybridGenres = settings.hybridGenres.value,
        )
        _state.value = FilmotekaUiState(
            axis = axis, rails = result.rails, loading = false,
            allSort = settings.allSort.value, total = result.total,
            genreFilter = genreFilter, availableGenres = result.availableGenres,
            countryFilter = countryFilter, availableCountries = result.availableCountries,
        )
    }

}
