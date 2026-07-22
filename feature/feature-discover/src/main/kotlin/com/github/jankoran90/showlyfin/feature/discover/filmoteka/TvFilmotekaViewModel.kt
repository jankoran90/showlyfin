package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import com.github.jankoran90.showlyfin.data.uploader.TraktSyncSignal
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.isSavedPlayable
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Named

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
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val traktLoader: TraktRowLoader,
    private val enricher: MediaEnricher,
    private val favorites: FavoritesRepository,
    private val workingSources: WorkingSourceStore,
    private val parentalControls: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    private val settings: FilmotekaSettingsStore,
    private val traktSyncSignal: TraktSyncSignal,
    private val viewModeStore: ViewModeStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
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

    private fun ageCap(): Int? = parentalControls.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parentalControls.profile.value.hideUnratedForAge

    /**
     * CONVERGE bug (2026-07-16) — „Chci vidět" se ve Filmotéce nezobrazovalo, i když v sekci Trakt ano.
     * Root cause: sekce Trakt volá `loader.watchlist("all")` BEZ guardu (autorizace přes interceptor +
     * [TraktTokenProvider], který čte pref `TRAKT_ACCESS_TOKEN`), kdežto tady jsme fetch podmiňovali JEN
     * per-profil `activeConfig.credentials.trakt.accessToken`. Na TV bývá tenhle config-token PRÁZDNÝ
     * (login běží na telefonu / device-flow zapíše token rovnou do prefs, do backend configu se ale
     * nepropíše) → guard=false → watchlist se do báze nikdy nesloučil (ani po reloadu/restartu).
     * Fix: přijmi i pref-token = STEJNÝ zdroj pravdy, jaký reálně autorizuje API (a jaký vidí sekce Trakt).
     * Dětský profil: [ProfileConfigApplier] při přepnutí na profil BEZ Traktu pref `TRAKT_ACCESS_TOKEN`
     * odstraní → fallback je null → watchlist se nezahrne (+ [ContentAgeGate] jako druhá pojistka).
     */
    private fun traktAllowed(): Boolean =
        !profileRepository.activeConfig.value.credentials.trakt?.accessToken.isNullOrBlank() ||
            !prefs.getString(KEY_TRAKT_ACCESS_TOKEN, null).isNullOrBlank()

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
            val enabled = settings.sources.value
            val cap = ageCap()
            val base = gatherBase(enabled)
            val enriched = enricher.enrich(base, withCertification = cap != null)
            baseItems = ContentAgeGate.filter(cap, enriched, hideUnrated())
            rebuild(settings.defaultAxis.value)
        }
    }

    /** Obohaď + gatuj Oblíbené (jen filmy) a přeskup. Vypnutý zdroj → prázdný bucket. */
    private suspend fun refreshFavorites(list: List<FavoriteItem>) {
        if (FilmotekaSource.FAVORITES !in settings.sources.value) {
            favoriteItems = emptyList()
            rebuild(_state.value.axis)
            return
        }
        val cap = ageCap()
        val base = list.filter { it.kind == FavoriteKind.MOVIE && it.id > 0L }
            .map { fav -> stub(fav.id, null, fav.name, fav.year, isShow = false, addedAtMs = fav.addedAtMs.takeIf { it > 0L }) }
        val enriched = enricher.enrich(base, withCertification = cap != null)
        favoriteItems = ContentAgeGate.filter(cap, enriched, hideUnrated())
        rebuild(_state.value.axis)
    }

    // ── Sběr báze ───────────────────────────────────────────────────────────────

    private suspend fun gatherBase(enabled: Set<FilmotekaSource>): List<MediaItem> = coroutineScope {
        val jfD = async { if (FilmotekaSource.JELLYFIN in enabled) loadJellyfinLibrary() else emptyList() }
        val wsD = async { if (FilmotekaSource.WORKING in enabled) loadWorkingSources() else emptyList() }
        val tkD = async {
            if (FilmotekaSource.TRAKT_WATCHLIST in enabled && traktAllowed())
                runCatching { traktLoader.watchlist("all") }.getOrElse { emptyList() }
            else emptyList()
        }
        val jf = jfD.await(); val ws = wsD.await(); val tk = tkD.await()
        // Precedence PRO OBSAH (metadata): JELLYFIN > WORKING > TRAKT_WATCHLIST (putIfAbsent v tomto pořadí).
        val merged = LinkedHashMap<String, MediaItem>()
        for (list in listOf(jf, ws, tk)) {
            for (item in list) { val k = dedupKey(item) ?: continue; merged.putIfAbsent(k, item) }
        }
        // CATALOGUE — stabilní „přidáno": priorita JF datum > Trakt listed_at > uložený zdroj savedAtMs. Datum
        // ČLENSTVÍ (JF/Trakt) MÁ PŘEDNOST před datem uložení zdroje → dohledání zdroje NEPŘESKLÁDÁ řazení
        // „Nedávno přidané" (film si drží svou pozici, i když mu právě naskočil WorkingSource). Vkládá se na
        // vítěze dedupu jako jeho `addedAtMs` (cestuje s položkou přes enrich → řadíme podle pole, ne mapy).
        // CATALOGUE (fix 2026-07-21) — „přidáno" = datum ČLENSTVÍ (JF DateCreated / Trakt listed_at), NIKDY datum
        // přiděleného working-source zdroje (savedAtMs = kdy appka sehnala RD/sdílej zdroj, ne kdy sis film přidal).
        // ws vyřazen z recency: jinak auto-cache/dohledání zdroje přeskládalo „Nedávno přidané" (Anora tmdb 1064213:
        // watchlist 13.6, ale ws savedAtMs 18.7 ji chybně vynášel nahoru). Robustní i vůči výpadku Trakt fetche
        // v jednom reloadu — titul bez členského data → addedAtMs=null → KONEC „Nedávno přidané" (self-heal příštím
        // reloadem s načteným Traktem), NIKDY falešně nahoru. Working-only cached filmy (mimo watchlist/JF) → konec.
        val recency = HashMap<String, Long>()
        for (list in listOf(jf, tk)) {
            for (item in list) { val k = dedupKey(item) ?: continue; item.addedAtMs?.let { recency.putIfAbsent(k, it) } }
        }
        merged.values.map { item ->
            val k = dedupKey(item)
            val r = if (k != null) recency[k] else null
            // Membership datum (JF DateCreated / Trakt listed_at) = datum přidání na seznam → řídí pořadí.
            // Working-only film (r==null, není v JF/Trakt) → addedAtMs=null; finální datum (Oblíbené / měsíc
            // zpět) mu přidělí `rebuild`, kde jsou vidět i Oblíbené (user 2026-07-22).
            item.copy(addedAtMs = r)
        }
    }

    private suspend fun loadJellyfinLibrary(): List<MediaItem> = coroutineScope {
        val session = prepareJellyfin() ?: return@coroutineScope emptyList()
        // ORCHARD (user 07-19) — Filmotéka respektuje SVŮJ výběr JF knihoven (filmotekaJfLibraries; null = všechny,
        // prázdné = žádná). Dřív se táhly všechny JF knihovny bez ohledu na výběr → do Filmotéky prosakovalo vše.
        val filmoWhitelist = profileRepository.activeConfig.value.filmotekaJfLibraries
            ?.map { it.replace("-", "").lowercase() }?.toSet()
        val views = runCatching { apiClient.userViewsApi.getUserViews(session.userUuid).content.items }
            .getOrElse { Timber.w(it, "[Filmoteka] getUserViews selhalo"); emptyList() }
            .filter { it.isFilmotekaLibrary() }
            .let { list ->
                if (filmoWhitelist == null) list
                else list.filter { it.id.toString().replace("-", "").lowercase() in filmoWhitelist }
            }
        views.map { view ->
            async {
                runCatching {
                    apiClient.itemsApi.getItems(
                        userId = session.userUuid,
                        parentId = view.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.GENRES, ItemFields.DATE_CREATED),
                        limit = 400,
                    ).content.items
                }.getOrElse { Timber.w(it, "[Filmoteka] getItems '${view.name}' selhalo"); emptyList() }
            }
        }.awaitAll().flatten().mapNotNull { it.toFilmotekaMediaItem() }
    }

    private suspend fun loadWorkingSources(): List<MediaItem> {
        workingSources.refresh()
        return workingSources.getAll().mapNotNull { ws ->
            if (ws.tmdb <= 0L && ws.imdb.isBlank()) return@mapNotNull null
            if (!ws.isSavedPlayable()) return@mapNotNull null   // SENTINEL bod 3 B — Filmotéka jen reálně cached
            MediaItem(
                traktId = 0L,
                tmdbId = ws.tmdb.takeIf { it > 0L },
                imdbId = ws.imdb.takeIf { it.isNotBlank() },
                title = ws.title,
                year = null,
                overview = null,
                rating = null,
                genres = null,
                type = MediaType.MOVIE,
                // NEMĚNNÉ datum prvního uložení (ne bumpovaný savedAtMs) → working-only film v „Nedávno
                // přidané" neskáče při re-cache/re-rankingu. Starý záznam bez firstSavedAtMs → fallback savedAtMs.
                addedAtMs = ws.firstSavedAtMs.takeIf { it > 0L } ?: ws.savedAtMs.takeIf { it > 0L },
            )
        }
    }

    // ── Grupování (osa) ───────────────────────────────────────────────────────────

    /**
     * MIRROR (user 2026-07-20) — grupování/filtr delegováno na SDÍLENÝ [FilmotekaGrouping.build] (tentýž grouper
     * volá i „Pro tebe" → obě sekce filtrují/grupují 1:1, žádný drift). Zde jen posbírej bázi (base>favorites),
     * předej živé filtry + nastavení a výsledek promítni do stavu.
     */
    private fun rebuild(axis: FilmotekaAxis) {
        // Base (JF+Working+Trakt) má přednost před Oblíbenými (putIfAbsent v pořadí base → favorites).
        val combined = LinkedHashMap<String, MediaItem>()
        for (item in baseItems + favoriteItems) { val k = dedupKey(item) ?: continue; combined.putIfAbsent(k, item) }
        // Datum „přidáno" pro řazení „Nedávno přidané" (user 2026-07-22): začátek Filmotéky MUSÍ sedět s „Chci
        // vidět". Priorita: JF/Trakt datum (z gatherBase) → Oblíbené addedAtMs → jinak (jen stažený zdroj, mimo
        // všechny seznamy) MĚSÍC ZPĚT (spadne pod čerstvé z Chci vidět, ale zůstane ve Filmotéce).
        val favDates = HashMap<String, Long>()
        for (f in favoriteItems) { val k = dedupKey(f) ?: continue; f.addedAtMs?.let { favDates.putIfAbsent(k, it) } }
        val backdate = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val all = combined.values.map { item ->
            if (item.addedAtMs != null) item
            else item.copy(addedAtMs = (dedupKey(item)?.let { favDates[it] }) ?: backdate)
        }
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

    // ── Mapování ────────────────────────────────────────────────────────────────

    private fun BaseItemDto.toFilmotekaMediaItem(): MediaItem? {
        val tmdb = providerIds?.get("Tmdb")?.toLongOrNull()
        val imdb = providerIds?.get("Imdb")?.takeIf { it.isNotBlank() }
        if (tmdb == null && imdb == null) return null
        val isShow = type == BaseItemKind.SERIES
        return MediaItem(
            traktId = 0L,
            tmdbId = tmdb,
            imdbId = imdb,
            title = name ?: "",
            year = productionYear,
            overview = null,
            rating = null,
            genres = genres?.takeIf { it.isNotEmpty() },
            type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
            addedAtMs = dateCreated?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        )
    }

    private fun dedupKey(item: MediaItem): String? = when {
        item.tmdbId != null -> "tmdb:${item.tmdbId}"
        !item.imdbId.isNullOrBlank() -> "imdb:${item.imdbId}"
        else -> null
    }

    private fun stub(tmdbId: Long, imdbId: String?, title: String, year: Int?, isShow: Boolean, addedAtMs: Long? = null) = MediaItem(
        traktId = 0L,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        year = year,
        overview = null,
        rating = null,
        genres = null,
        type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
        addedAtMs = addedAtMs,
    )

    // ── Jellyfin session (vzor TvHomeViewModel) ────────────────────────────────────

    private data class JfSession(val serverUrl: String, val token: String, val userUuid: UUID)

    private fun prepareJellyfin(): JfSession? {
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        val userId = prefs.getString("jellyfin_user_id", "").orEmpty()
        if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) return null
        apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
        val userUuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        return JfSession(serverUrl, token, userUuid)
    }

    private companion object {
        /** Zrcadlí `TraktTokenProvider.KEY_ACCESS_TOKEN` (data-trakt) — reálný autorizační token API. */
        const val KEY_TRAKT_ACCESS_TOKEN = "TRAKT_ACCESS_TOKEN"
    }
}

/** Filmové/seriálové/smíšené knihovny (vzor LibraryRowsViewModel.isMediaLibrary); RealDebrid vynech. */
private fun BaseItemDto.isFilmotekaLibrary(): Boolean {
    val ct = collectionType?.name?.uppercase()
    val allowed = ct == null || ct == "MOVIES" || ct == "TVSHOWS" || ct == "MIXED"
    if (!allowed) return false
    val n = name?.lowercase() ?: return true
    return !n.contains("realdebrid") && !n.contains("real-debrid")
}
