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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber
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
    /**
     * Přihlášení k Traktu vypršelo → „Chci vidět" jede ze serverového zrcadla (zmrazená data).
     * UI to musí říct nahlas, jinak se jen tiše rozhodí pořadí „Nedávno přidané" (user 2026-07-30).
     */
    val traktStale: Boolean = false,
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
    /**
     * ATRIUM (SHW-118) — sdružené kolekce: karta kolekce ZASTUPUJE své díly přímo v řadách (nahoře žádná
     * extra řada — user 2026-08-24). Prázdné při vypnutém přepínači „Karty kolekcí" → díly se pak zobrazí
     * jednotlivě. Obrazovka si tu podle `HomeRowItem.collectionKey` najde členy k zobrazení obsahu.
     */
    val collectionGroups: List<FilmotekaCollectionGroup> = emptyList(),
    /**
     * VESTIBUL (SHW-120) — „Další díly" NAD obsahem Filmotéky (Jellyfin NextUp ∪ uložené zdroje ∪ ČT).
     * Prázdné = přepínač vypnutý, nebo není co dokoukat → řada se vůbec nevykreslí.
     */
    val nextUp: List<HomeRowItem> = emptyList(),
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
    private val collectionResolver: FilmotekaCollectionResolver,
    private val nextUpLoader: FilmotekaNextUpLoader,
    // Poslední známá báze z disku → sekce ukáže obsah hned po otevření (user 2026-07-29).
    private val diskCache: FilmotekaDiskCache,
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

    /** ATRIUM — sdružené kolekce (prázdné při vypnutém přepínači „Karty kolekcí"). */
    @Volatile private var collectionGroups: List<FilmotekaCollectionGroup> = emptyList()

    /** VESTIBUL — „Další díly" (prázdné při vypnutém přepínači). */
    @Volatile private var nextUpItems: List<HomeRowItem> = emptyList()

    /** GENRE-FILTER — živý výběr žánrů (viz [FilmotekaUiState.genreFilter]). Drží se napříč přeskupením os. */
    @Volatile private var genreFilter: Set<String> = emptySet()

    /** COUNTRY-FILTER — živý výběr zemí/regionů (viz [FilmotekaUiState.countryFilter]). Drží se napříč osami. */
    @Volatile private var countryFilter: Set<CinematographyRegion> = emptySet()

    private var loadJob: Job? = null

    init {
        // Per-profil: přepni nastavení Filmotéky na profil, pak přenačti obsah (jeden collector = pořadí).
        var lastProfileId: Long? = null
        profileRepository.activeProfile
            .onEach { p ->
                settings.switchProfile(p?.id)
                // Báze PŘEDCHOZÍHO profilu musí pryč, jinak by se do doby doběhnutí sběru míchal obsah
                // dvou profilů (user 2026-07-29). Disková cache je per profil, takže se hned nahradí tou správnou.
                if (p?.id != lastProfileId) {
                    baseItems = emptyList()
                    favoriteItems = emptyList()
                    // 🔴 2026-08-26 — „Další díly" se tu dřív NEČISTILY, takže po přepnutí profilu
                    // v řadě dál visel obsah PŘEDCHOZÍHO profilu, dokud nedoběhl nový sběr (user viděl
                    // dětské pořady na profilu Dospělý). Báze se čistila od 2026-07-29, řada nad ní se
                    // na to zapomnělo — týž požadavek, jiné pole.
                    nextUpItems = emptyList()
                    lastProfileId = p?.id
                }
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
        // FOYER (SHW-107) — přepnutí „Jen s dohledaným zdrojem" → přenačti bázi (filtr běží při sběru).
        settings.onlyWithSource
            .drop(1)
            .onEach { filmotekaBase.invalidateRecent(); reload() }
            .launchIn(viewModelScope)

        // FOYER (SHW-107) — přepnutí „Karty kolekcí" v Nastavení → přenačti (kolekce se dotahují zvlášť).
        settings.showCollections
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)

        // VESTIBUL — přepnutí „Další díly" v Nastavení se musí projevit hned.
        settings.showNextUp
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)

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
            // Nejdřív poslední známý obsah z disku — ať je co ukázat, než doběhne sběr (Jellyfin knihovna
            // + uložené zdroje + watchlist + TMDB enrich trvá vteřiny). Jen když ještě nic nemáme.
            //
            // 🔴 Ukládá se SLOUČENÝ seznam včetně dopočteného `addedAtMs`, ne surová báze: datum „přidáno"
            // vzniká u části titulů až v [FilmotekaBaseLoader.mergeWithFavorites] (z Oblíbených a z data
            // prvního uložení zdroje). Kdyby se cachovala jen báze, po startu — než dorazí Oblíbené —
            // by těmhle titulům datum chybělo a spadly by na konec: „řazení ve Filmotéce je rozházené
            // ve smyslu nedávno přidaných" (user 2026-07-29).
            if (baseItems.isEmpty()) {
                val profileId = profileRepository.activeProfile.value?.id
                // 🔴 2026-08-26 (user: „nejdřív se zobrazí obsah Filmotéky a později Další díly, tím
                // pádem musíš odrolovat na začátek") — řadu ber z disku ZÁROVEŇ s bází, ať je
                // v PRVNÍM vykreslení. Dřív se kreslila jen báze a řada nad ní doskočila o vteřiny
                // později (síť: Jellyfin nextUp + Trakt na každý seriál + ČT feedy), čímž odsunula
                // už čtený obsah dolů. Jen když je zapnutá — jinak by se mihla i vypnutá.
                var fromDisk = false
                if (settings.showNextUp.value) {
                    diskCache.readNextUp(profileId)?.let { nextUpItems = it; fromDisk = true }
                }
                diskCache.read(profileId)?.let { cached -> baseItems = cached; fromDisk = true }
                // Překresli, když z disku přišlo COKOLI z toho dvojího. Dřív viselo `rebuild` uvnitř
                // větve báze, takže když byla na disku jen řada (ale ne báze), zůstala ležet v poli
                // a vykreslila se až s dopočtem ze sítě — přesně ten skok, který tohle má odstranit.
                if (fromDisk) rebuild(settings.defaultAxis.value)
            }
            // VESTIBUL: „Další díly" jedou SOUBĚŽNĚ s bází — jsou to jiné zdroje, čekání se nemá sčítat.
            val nextUpJob = async { loadNextUp() }
            baseItems = filmotekaBase.loadBase()
            nextUpItems = nextUpJob.await()
            // Čerstvá řada na disk pro příští první vykreslení. Zapisuje se i PRÁZDNÁ (dokoukáno =
            // řada má zmizet), ale jen když je přepínač zapnutý — vypnutý vrací prázdno vždy a
            // přepsal by tím poslední platný stav.
            // 🔴 A JEN Z DOROVNANÉHO ÚLOŽIŠTĚ. Hned po přepnutí profilu je lokál prázdný (čeká na
            // server), takže řada vyjde neúplná — a zapsat ji na disk znamená, že ji tam najde i
            // příští START appky. Přesně to user nahlásil po 1.2.84: *„chybí Legion a Yellowstone"*
            // + *„restart app je zpět nedostane"*. Bez tohohle guardu se z dočasné mezery stane
            // trvalá (můj regres z 1.2.84 — do té doby restart pomáhal).
            if (settings.showNextUp.value && workingSources.isReadyForActiveProfile()) {
                diskCache.writeNextUp(profileRepository.activeProfile.value?.id, nextUpItems)
            }
            // ATRIUM (SHW-118): sdružení AŽ po bázi — členy kolekce bereme jen z toho, co prošlo
            // dedupem i věkovým gate, takže se do kolekce nemůže propašovat skrytý titul.
            collectionGroups = resolveCollections()
            rebuild(settings.defaultAxis.value)
            // Na disk až hotový obsah (báze ∪ Oblíbené, s dopočtenými daty) a jen z ÚPLNÉHO sběru —
            // neúplný (JF zapnutý, ale knihovna mlčela) by se zafixoval.
            if (filmotekaBase.lastLoadComplete()) {
                diskCache.write(
                    profileRepository.activeProfile.value?.id,
                    filmotekaBase.mergeWithFavorites(baseItems, favoriteItems),
                )
            }
        }
    }

    /**
     * Obohaď + gatuj Oblíbené (jen filmy) **a lokální „Chci vidět"** (filmy i seriály) a přeskup.
     * Vypnutý zdroj → prázdný bucket (řeší loader). Obojí sedí v téže tabulce, takže dorazí jedním tokem.
     */
    private suspend fun refreshFavorites(list: List<FavoriteItem>) {
        favoriteItems = filmotekaBase.loadFavorites(list) + filmotekaBase.loadWants(list)
        // ATRIUM: Oblíbené můžou přinést další díl kolekce → přepočti sdružení, ať karta sedí (TMDB
        // odpovědi jsou cachované, takže druhý průchod nic nedotahuje).
        collectionGroups = resolveCollections()
        rebuild(_state.value.axis)
        // Oblíbené dorazí často AŽ po sběru báze a nesou data „přidáno" → přepiš jimi diskovou cache,
        // jinak by si příští start pamatoval seznam bez nich (a řadil je na konec).
        if (baseItems.isNotEmpty() && filmotekaBase.lastLoadComplete()) {
            diskCache.write(
                profileRepository.activeProfile.value?.id,
                filmotekaBase.mergeWithFavorites(baseItems, favoriteItems),
            )
        }
    }

    // ── Grupování (osa) ───────────────────────────────────────────────────────────

    /**
     * MIRROR (user 2026-07-20) — grupování/filtr delegováno na SDÍLENÝ [FilmotekaGrouping.build] (tentýž grouper
     * volá i „Pro tebe" → obě sekce filtrují/grupují 1:1, žádný drift). Zde jen posbírej bázi (base>favorites),
     * předej živé filtry + nastavení a výsledek promítni do stavu.
     */
    /** „Další díly" dle přepínače; selhání není fatální — řada se prostě nevykreslí. */
    private suspend fun loadNextUp(): List<HomeRowItem> {
        if (!settings.showNextUp.value) return emptyList()
        return runCatching { nextUpLoader.load() }
            .getOrElse { Timber.w(it, "[Filmoteka] další díly selhaly"); emptyList() }
    }

    /**
     * Sdružené kolekce nad aktuální bází. Vypnutý přepínač „Karty kolekcí" = prázdno (díly zůstanou
     * jednotlivě). Selhání není fatální — Filmotéka se pak jen zobrazí nesdružená, ne prázdná.
     */
    private suspend fun resolveCollections(): List<FilmotekaCollectionGroup> {
        if (!settings.showCollections.value) return emptyList()
        val all = filmotekaBase.mergeWithFavorites(baseItems, favoriteItems)
        return runCatching {
            collectionResolver.resolve(all, filmotekaBase.lastJellyfinCollections)
        }.getOrElse { Timber.w(it, "[Filmoteka] sdružení kolekcí selhalo"); emptyList() }
    }

    private fun rebuild(axis: FilmotekaAxis) {
        val staleNow = filmotekaBase.traktStale.value
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
            collectionGroups = collectionGroups,
        )
        _state.value = FilmotekaUiState(
            axis = axis, rails = result.rails, loading = false,
            allSort = settings.allSort.value, total = result.total,
            genreFilter = genreFilter, availableGenres = result.availableGenres,
            countryFilter = countryFilter, availableCountries = result.availableCountries,
            collectionGroups = collectionGroups,
            nextUp = nextUpItems,
            traktStale = staleNow,
        )
    }

}
