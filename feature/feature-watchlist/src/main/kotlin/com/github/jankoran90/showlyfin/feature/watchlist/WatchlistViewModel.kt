package com.github.jankoran90.showlyfin.feature.watchlist

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.matchesQuery
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.RdMatchItem
import org.jellyfin.sdk.model.UUID
import javax.inject.Named
import com.github.jankoran90.showlyfin.feature.watchlist.mapper.toMovieMediaItem
import com.github.jankoran90.showlyfin.feature.watchlist.mapper.toShowMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val tmdbApi: TmdbRemoteDataSource,
    private val tokenProvider: TokenProvider,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val jellyfinLibraryService: JellyfinLibraryService,
    private val uploaderDs: UploaderRemoteDataSource,
    private val profileRepository: ProfileRepository,
    private val csfdRepository: CsfdRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    // VANTAGE (SHW-48): per-sekce volba zobrazení — Chci vidět výchozí SEZNAM (řádky s popisem).
    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { m -> ViewMode.fromKey(m[ViewModeStore.SECTION_WATCHLIST] ?: ViewMode.LIST.storeKey) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.LIST)

    fun setViewMode(mode: ViewMode) = viewModeStore.set(ViewModeStore.SECTION_WATCHLIST, mode.storeKey)

    private val _rawItems = MutableStateFlow<List<MediaItem>>(emptyList())
    private var lockedRating: AgeRating? = null
    private var rdMatchJob: kotlinx.coroutines.Job? = null

    /** VISTA V3 — čas přidání na watchlist (Trakt `listed_at`) podle traktId → řazení „Naposledy přidané". */
    private var listedAtMap: Map<Long, Long> = emptyMap()

    /** VISTA V3 — líně načtené ČSFD hodnocení (0–100 %) podle traktId pro řádky „Chci vidět". */
    private val _csfdRatings = MutableStateFlow<Map<Long, Int?>>(emptyMap())
    val csfdRatings: StateFlow<Map<Long, Int?>> = _csfdRatings.asStateFlow()
    private val csfdInFlight = mutableSetOf<Long>()

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        val loggedIn = tokenProvider.getToken() != null
        val savedSort = runCatching {
            WatchlistSort.valueOf(prefs.getString("watchlist_sort", null) ?: "")
        }.getOrDefault(WatchlistSort.DEFAULT)
        _uiState.update { it.copy(isLoggedIn = loggedIn, sort = savedSort) }
        if (loggedIn) load(WatchlistTab.MOVIES)
        loadJellyfinOwned()
        parentalControlsRepository.profile
            .onEach { profile ->
                lockedRating = if (profile.isLocked) profile.effectiveAgeRating else null
                reapply()
            }
            .launchIn(viewModelScope)
        // Plan PROFILES 1E: žánrový allow/block z aktivního profilu → re-aplikuj při změně profilu.
        profileRepository.activeConfig
            .onEach { reapply() }
            .launchIn(viewModelScope)
    }

    private fun loadJellyfinOwned() {
        viewModelScope.launch {
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""
            Timber.d("[Watchlist] loadJellyfinOwned userId=$userId")
            if (userId.isBlank()) {
                Timber.w("[Watchlist] userId BLANK — Jellyfin nepřihlášen?")
                return@launch
            }
            runCatching {
                val owned = jellyfinLibraryService.getOwnedIds(UUID.fromString(userId))
                Timber.i("[Watchlist] OwnedIds loaded: imdb=${owned.imdbIds.size} tmdb=${owned.tmdbIds.size} watchedJf=${owned.watchedJellyfinIds.size}")
                _uiState.update {
                    it.copy(
                        ownedImdbIds = owned.imdbIds,
                        imdbToJellyfin = owned.imdbToJellyfin,
                        tmdbToJellyfin = owned.tmdbToJellyfin,
                        watchedImdbIds = it.watchedImdbIds + owned.watchedImdbIds,
                        watchedTmdbIds = it.watchedTmdbIds + owned.watchedTmdbIds,
                    )
                }
            }.onFailure { Timber.w(it, "[Watchlist] OwnedIds failed") }
        }
    }

    fun selectTab(tab: WatchlistTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                items = emptyList(),
                progressMap = emptyMap(),
                genreFilter = null,
                availableGenres = emptyList(),
            )
        }
        _rawItems.value = emptyList()
        if (_uiState.value.isLoggedIn) load(tab)
    }

    fun selectSort(sort: WatchlistSort) {
        prefs.edit().putString("watchlist_sort", sort.name).apply()
        _uiState.update { it.copy(sort = sort) }
        reapply()
    }

    fun selectGenre(genre: String?) {
        _uiState.update { it.copy(genreFilter = genre) }
        reapply()
    }

    /** Lokální hledání ve watchlistu podle českého i originálního názvu (diakritika/velikost-insensitive). */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        reapply()
    }

    /** Toggle filtru „jen co je na RD" (Fáze F++). Při zapnutí dopočítá card-level match přes uploader. */
    fun toggleRdOnly() {
        val newValue = !_uiState.value.rdOnly
        _uiState.update { it.copy(rdOnly = newValue) }
        if (newValue) computeRdMatch() else reapply()
    }

    private fun computeRdMatch() {
        if (uploaderBaseUrl.isBlank()) {
            _uiState.update { it.copy(rdOnly = false, error = "Uploader není nastaven — filtr Na RD nelze použít.") }
            return
        }
        rdMatchJob?.cancel()
        rdMatchJob = viewModelScope.launch {
            _uiState.update { it.copy(rdMatchLoading = true) }
            val source = _rawItems.value.distinctBy { it.traktId }
            val matched = runCatching {
                val indices = uploaderDs.rdMatch(
                    uploaderBaseUrl, uploaderCookie,
                    source.map { RdMatchItem(it.title, it.year) },
                )
                indices.mapNotNull { source.getOrNull(it)?.traktId }.toSet()
            }.getOrElse {
                Timber.w(it, "[Watchlist] rdMatch failed")
                emptySet()
            }
            Timber.i("[Watchlist] rdMatch: ${matched.size}/${source.size} na RD")
            _uiState.update {
                it.copy(rdMatchedTraktIds = matched, rdMatchLoading = false)
            }
            reapply()
        }
    }

    fun refresh() {
        if (_uiState.value.isLoggedIn) load(_uiState.value.activeTab)
    }

    /**
     * VISTA V3 — líně načti ČSFD hodnocení pro JEDEN řádek (volá řádek při zobrazení). Drahé
     * (Wikidata + ČSFD scrape/PoW) → jen viditelné řádky, výsledek (i null) se nezahazuje, aby se
     * neopakoval scrape. Cache uvnitř `CsfdRepository` (prefs TTL) drží i mezi sezeními.
     */
    fun loadCsfdRating(item: MediaItem) {
        val key = item.traktId
        if (_csfdRatings.value.containsKey(key) || key in csfdInFlight) return
        csfdInFlight += key
        viewModelScope.launch {
            val rating = runCatching {
                csfdRepository.getRating(item.imdbId ?: "", item.tmdbId, item.title, item.year ?: 0)
            }.getOrNull()
            _csfdRatings.update { it + (key to rating) }
            csfdInFlight -= key
        }
    }

    private fun load(tab: WatchlistTab) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val syncItems = if (tab == WatchlistTab.MOVIES) {
                    authorizedTraktApi.fetchSyncMoviesWatchlist()
                } else {
                    authorizedTraktApi.fetchSyncShowsWatchlist()
                }
                // VISTA V3: zachyť čas přidání (listed_at) pro řazení „Naposledy přidané".
                listedAtMap = syncItems.mapNotNull { si ->
                    si.getTraktId()?.let { it to si.lastListedMillis() }
                }.toMap()
                val rawItems = syncItems.map {
                    if (tab == WatchlistTab.MOVIES) it.toMovieMediaItem() else it.toShowMediaItem()
                }
                val enriched = coroutineScope {
                    rawItems.map { item ->
                        async {
                            val tmdbId = item.tmdbId ?: return@async item
                            if (tab == WatchlistTab.MOVIES) {
                                val detailsDeferred = async { runCatching { tmdbApi.fetchMovieDetails(tmdbId) }.getOrNull() }
                                val translationDeferred = async { runCatching { tmdbApi.fetchMovieTranslation(tmdbId, "cs") }.getOrNull() }
                                val details = detailsDeferred.await()
                                val translation = translationDeferred.await()
                                item.copy(
                                    posterPath = details?.poster_path,
                                    backdropPath = details?.backdrop_path,
                                    titleCz = translation?.title?.takeIf { it.isNotBlank() },
                                    overviewCz = translation?.overview?.takeIf { it.isNotBlank() },
                                )
                            } else {
                                val detailsDeferred = async { runCatching { tmdbApi.fetchShowDetails(tmdbId) }.getOrNull() }
                                val translationDeferred = async { runCatching { tmdbApi.fetchShowTranslation(tmdbId, "cs") }.getOrNull() }
                                val details = detailsDeferred.await()
                                val translation = translationDeferred.await()
                                item.copy(
                                    posterPath = details?.poster_path,
                                    backdropPath = details?.backdrop_path,
                                    titleCz = translation?.name?.takeIf { it.isNotBlank() },
                                    overviewCz = translation?.overview?.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                    }.awaitAll()
                }
                _rawItems.value = enriched
                val genres = enriched
                    .flatMap { it.genres.orEmpty() }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                _uiState.update { state ->
                    state.copy(
                        items = applyAll(enriched, state.sort, state.genreFilter),
                        availableGenres = genres,
                        isLoading = false,
                    )
                }

                if (_uiState.value.rdOnly) computeRdMatch()

                if (tab == WatchlistTab.SHOWS) {
                    loadProgress()
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba načítání") }
            }
        }
    }

    private suspend fun loadProgress() {
        runCatching {
            val watchedShows = authorizedTraktApi.fetchSyncWatchedShows(extended = "full")
            val map = mutableMapOf<Long, WatchProgress>()
            for (entry in watchedShows) {
                val show = entry.show ?: continue
                val traktId = show.ids?.trakt ?: continue
                val totalEpisodes = show.aired_episodes ?: continue
                val watchedEpisodes = entry.seasons?.sumOf { season ->
                    season.episodes?.size ?: 0
                } ?: 0
                if (totalEpisodes > 0) {
                    map[traktId] = WatchProgress(watchedEpisodes, totalEpisodes)
                }
            }
            val watchedMovies = runCatching { authorizedTraktApi.fetchSyncWatchedMovies() }.getOrDefault(emptyList())
            val combined = watchedShows + watchedMovies
            val traktIds = combined.mapNotNull { it.getTraktId() }.toSet()
            val imdbIds = combined.mapNotNull { it.getImdbId()?.takeIf { s -> s.isNotBlank() } }.toSet()
            val tmdbIds = combined.mapNotNull { it.getTmdbId() }.toSet()
            Timber.i("[Watchlist] Trakt watched: trakt=${traktIds.size} imdb=${imdbIds.size} tmdb=${tmdbIds.size}")
            _uiState.update {
                it.copy(
                    progressMap = map,
                    watchedTraktIds = traktIds,
                    watchedImdbIds = it.watchedImdbIds + imdbIds,
                    watchedTmdbIds = it.watchedTmdbIds + tmdbIds,
                )
            }
        }
    }

    private fun reapply() {
        val state = _uiState.value
        _uiState.update { it.copy(items = applyAll(_rawItems.value, state.sort, state.genreFilter)) }
    }

    private fun applyAll(items: List<MediaItem>, sort: WatchlistSort, genre: String?): List<MediaItem> {
        var result = applyLock(items)
        // Plan PROFILES 1E: žánrový blacklist/allow-list z aktivního profilu.
        val profileConfig = profileRepository.activeConfig.value
        if (profileConfig.allowedGenres.isNotEmpty() || profileConfig.blockedGenres.isNotEmpty()) {
            result = result.filter { profileConfig.isGenreAllowed(it.genres) }
        }
        if (!genre.isNullOrBlank()) {
            result = result.filter { it.genres.orEmpty().any { g -> g.equals(genre, ignoreCase = true) } }
        }
        val state = _uiState.value
        if (state.rdOnly) {
            result = result.filter { state.rdMatchedTraktIds.contains(it.traktId) }
        }
        val query = state.searchQuery
        if (query.isNotBlank()) {
            result = result.filter { it.matchesQuery(query) }
        }
        result = when (sort) {
            // VISTA V3: „Naposledy přidané" = sestupně podle Trakt listed_at (dřív bez řazení).
            WatchlistSort.DEFAULT -> result.sortedByDescending { listedAtMap[it.traktId] ?: 0L }
            WatchlistSort.TITLE -> result.sortedBy { it.title.lowercase() }
            WatchlistSort.YEAR_DESC -> result.sortedByDescending { it.year ?: Int.MIN_VALUE }
            WatchlistSort.YEAR_ASC -> result.sortedBy { it.year ?: Int.MAX_VALUE }
            WatchlistSort.RATING_DESC -> result.sortedByDescending { it.rating ?: -1f }
        }
        return result
    }

    private fun applyLock(items: List<MediaItem>): List<MediaItem> {
        val rating = lockedRating ?: return items
        return items.filter { item ->
            val genres = item.genres.orEmpty().map { it.lowercase() }
            when (rating) {
                AgeRating.UNRESTRICTED -> true
                AgeRating.CHILDREN -> genres.any { it in CHILDREN_ALLOWED } && genres.none { it in ADULT }
                AgeRating.FAMILY -> genres.none { it in FAMILY_BLOCKED } && genres.none { it in ADULT }
                AgeRating.TEEN -> genres.none { it in ADULT }
                AgeRating.ADULT -> true
            }
        }
    }

    companion object {
        private val CHILDREN_ALLOWED = setOf("family", "animation", "rodinné", "rodinný", "animovaný", "animovaný film", "kids", "children", "dětský")
        private val FAMILY_BLOCKED = setOf("horror", "horor", "thriller", "war", "válečný", "erotic", "erotika")
        private val ADULT = setOf("horror", "horor", "erotic", "erotika", "adult")
    }
}
