package com.github.jankoran90.showlyfin.feature.discover

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.TraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.RdMatchItem
import com.github.jankoran90.showlyfin.feature.discover.mapper.toMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val traktApi: TraktRemoteDataSource,
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val tmdbApi: TmdbRemoteDataSource,
    private val jellyfinLibraryService: JellyfinLibraryService,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val tokenProvider: TokenProvider,
    private val uploaderDs: UploaderRemoteDataSource,
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var searchJob: Job? = null
    private var rdMatchJob: Job? = null

    init {
        _uiState.update { it.copy(isTraktLoggedIn = tokenProvider.getToken() != null) }
        load(DiscoverTab.MOVIES, DiscoverFilter.TRENDING)
        loadFilterContext()
        parentalControlsRepository.profile
            .onEach { profile ->
                _uiState.update {
                    it.copy(parentalLockedAgeRating = if (profile.isLocked) profile.effectiveAgeRating else null)
                }
                reapplyFilters()
            }
            .launchIn(viewModelScope)
        // Plan PROFILES 1E: žánrový allow/block z aktivního profilu → re-aplikuj při změně profilu.
        profileRepository.activeConfig
            .onEach { reapplyFilters() }
            .launchIn(viewModelScope)
    }

    fun setSessionAgeOverride(rating: AgeRating?) {
        _uiState.update { it.copy(sessionAgeOverride = rating) }
        reapplyFilters()
    }

    fun selectTab(tab: DiscoverTab) {
        val filter = _uiState.value.activeFilter
        _uiState.update { it.copy(activeTab = tab, items = emptyList(), rawItems = emptyList()) }
        load(tab, filter)
    }

    fun selectFilter(filter: DiscoverFilter) {
        val tab = _uiState.value.activeTab
        _uiState.update { it.copy(activeFilter = filter, items = emptyList(), rawItems = emptyList()) }
        load(tab, filter)
    }

    fun refresh() = load(_uiState.value.activeTab, _uiState.value.activeFilter)

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), rawSearchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch(query)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), rawSearchResults = emptyList(), isSearching = false) }
    }

    fun openFilterSheet() = _uiState.update { it.copy(isFilterSheetOpen = true) }
    fun closeFilterSheet() = _uiState.update { it.copy(isFilterSheetOpen = false) }

    fun updateFilters(filters: DiscoverFilters) {
        _uiState.update { it.copy(filters = filters) }
        reapplyFilters()
    }

    fun resetFilters() {
        _uiState.update { it.copy(filters = DiscoverFilters()) }
        reapplyFilters()
    }

    /** Toggle filtru „jen co je na RD" (Fáze F++). Při zapnutí dopočítá card-level match přes uploader. */
    fun toggleRdOnly() {
        val newValue = !_uiState.value.rdOnly
        _uiState.update { it.copy(rdOnly = newValue) }
        if (newValue) computeRdMatch() else reapplyFilters()
    }

    private fun computeRdMatch() {
        if (uploaderBaseUrl.isBlank()) {
            _uiState.update { it.copy(rdOnly = false, error = "Uploader není nastaven — filtr Na RD nelze použít.") }
            return
        }
        rdMatchJob?.cancel()
        rdMatchJob = viewModelScope.launch {
            _uiState.update { it.copy(rdMatchLoading = true) }
            // distinct tituly napříč discover + search (title+year, card-level)
            val source = (_uiState.value.rawItems + _uiState.value.rawSearchResults)
                .distinctBy { it.traktId }
            val matched = runCatching {
                val indices = uploaderDs.rdMatch(
                    uploaderBaseUrl, uploaderCookie,
                    source.map { RdMatchItem(it.title, it.year) },
                )
                indices.mapNotNull { source.getOrNull(it)?.traktId }.toSet()
            }.getOrElse {
                Timber.w(it, "[Discover] rdMatch failed")
                emptySet()
            }
            Timber.i("[Discover] rdMatch: ${matched.size}/${source.size} na RD")
            _uiState.update {
                val items = applyFilters(it.rawItems, it.filters, it.copy(rdMatchedTraktIds = matched))
                val search = applyFilters(it.rawSearchResults, it.filters, it.copy(rdMatchedTraktIds = matched))
                it.copy(rdMatchedTraktIds = matched, rdMatchLoading = false, items = items, searchResults = search)
            }
        }
    }

    private fun reapplyFilters() {
        val state = _uiState.value
        val filteredItems = applyFilters(state.rawItems, state.filters, state)
        val filteredSearch = applyFilters(state.rawSearchResults, state.filters, state)
        _uiState.update { it.copy(items = filteredItems, searchResults = filteredSearch) }
    }

    private fun applyFilters(items: List<MediaItem>, filters: DiscoverFilters, state: DiscoverUiState): List<MediaItem> {
        var result = items
        if (filters.selectedGenres.isNotEmpty()) {
            result = result.filter { item ->
                item.genres.orEmpty().any { genre -> filters.selectedGenres.contains(genre) }
            }
        }
        if (filters.yearMin > 1950 || filters.yearMax < 2030) {
            result = result.filter { item ->
                val year = item.year ?: return@filter true
                year in filters.yearMin..filters.yearMax
            }
        }
        if (filters.minRating > 0f) {
            result = result.filter { item ->
                (item.rating ?: 0f) >= filters.minRating
            }
        }
        if (filters.hideInJellyfin && state.ownedImdbIds.isNotEmpty()) {
            result = result.filter { item ->
                item.imdbId == null || !state.ownedImdbIds.contains(item.imdbId)
            }
        }
        if (filters.hideInWatchlist && state.watchlistTraktIds.isNotEmpty()) {
            result = result.filter { item -> !state.watchlistTraktIds.contains(item.traktId) }
        }
        if (filters.hideWatched && state.watchedTraktIds.isNotEmpty()) {
            result = result.filter { item -> !state.watchedTraktIds.contains(item.traktId) }
        }
        if (state.rdOnly) {
            result = result.filter { item -> state.rdMatchedTraktIds.contains(item.traktId) }
        }
        // Plan PROFILES 1E: žánrový blacklist/allow-list z aktivního profilu.
        val profileConfig = profileRepository.activeConfig.value
        if (profileConfig.allowedGenres.isNotEmpty() || profileConfig.blockedGenres.isNotEmpty()) {
            result = result.filter { profileConfig.isGenreAllowed(it.genres) }
        }
        val effectiveAgeRating = state.sessionAgeOverride ?: state.parentalLockedAgeRating
        effectiveAgeRating?.let { rating ->
            result = result.filter { item -> isAllowedForRating(item, rating) }
        }
        result = when (filters.sortBy) {
            DiscoverSort.DEFAULT -> result
            DiscoverSort.RATING_DESC -> result.sortedByDescending { it.rating ?: 0f }
            DiscoverSort.YEAR_DESC -> result.sortedByDescending { it.year ?: 0 }
            DiscoverSort.YEAR_ASC -> result.sortedBy { it.year ?: Int.MAX_VALUE }
            DiscoverSort.ALPHABETICAL -> result.sortedBy { it.title }
        }
        return result
    }

    private fun isAllowedForRating(item: MediaItem, lockedRating: AgeRating): Boolean {
        val genres = item.genres.orEmpty().map { it.lowercase() }
        return when (lockedRating) {
            AgeRating.UNRESTRICTED -> true
            AgeRating.CHILDREN -> genres.any { it in CHILDREN_ALLOWED_GENRES } &&
                genres.none { it in ADULT_GENRES }
            AgeRating.FAMILY -> genres.none { it in FAMILY_BLOCKED_GENRES } &&
                genres.none { it in ADULT_GENRES }
            AgeRating.TEEN -> genres.none { it in ADULT_GENRES }
            AgeRating.ADULT -> true
        }
    }

    companion object {
        private val CHILDREN_ALLOWED_GENRES = setOf(
            "family", "animation", "rodinné", "rodinný", "animovaný", "animovaný film",
            "kids", "children", "dětský",
        )
        private val FAMILY_BLOCKED_GENRES = setOf(
            "horror", "horor", "thriller", "war", "válečný", "erotic", "erotika",
        )
        private val ADULT_GENRES = setOf(
            "horror", "horor", "erotic", "erotika", "adult",
        )
    }

    private fun loadFilterContext() {
        viewModelScope.launch {
            if (tokenProvider.getToken() != null) {
                runCatching {
                    val watchlistMovies = authorizedTraktApi.fetchSyncMoviesWatchlist()
                    val watchlistShows = authorizedTraktApi.fetchSyncShowsWatchlist()
                    val ids = (watchlistMovies + watchlistShows).mapNotNull { it.getTraktId() }.toSet()
                    _uiState.update { it.copy(watchlistTraktIds = ids) }
                }
                runCatching {
                    val watchedMovies = authorizedTraktApi.fetchSyncWatchedMovies()
                    val watchedShows = authorizedTraktApi.fetchSyncWatchedShows()
                    val combined = watchedMovies + watchedShows
                    val traktIds = combined.mapNotNull { it.getTraktId() }.toSet()
                    val imdbIds = combined.mapNotNull { it.getImdbId()?.takeIf { s -> s.isNotBlank() } }.toSet()
                    val tmdbIds = combined.mapNotNull { it.getTmdbId() }.toSet()
                    _uiState.update {
                        it.copy(
                            watchedTraktIds = traktIds,
                            watchedImdbIds = it.watchedImdbIds + imdbIds,
                            watchedTmdbIds = it.watchedTmdbIds + tmdbIds,
                        )
                    }
                    Timber.i("[Discover] Trakt watched: trakt=${traktIds.size} imdb=${imdbIds.size} tmdb=${tmdbIds.size}")
                }
            }
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""
            Timber.d("[Discover] loadFilterContext userId=$userId")
            if (userId.isNotBlank()) {
                runCatching {
                    val owned = jellyfinLibraryService.getOwnedIds(UUID.fromString(userId))
                    Timber.i("[Discover] OwnedIds loaded: imdb=${owned.imdbIds.size} tmdb=${owned.tmdbIds.size} imdbMap=${owned.imdbToJellyfin.size} tmdbMap=${owned.tmdbToJellyfin.size} watchedJf=${owned.watchedJellyfinIds.size}")
                    _uiState.update {
                        it.copy(
                            ownedImdbIds = owned.imdbIds,
                            imdbToJellyfin = owned.imdbToJellyfin,
                            tmdbToJellyfin = owned.tmdbToJellyfin,
                            watchedImdbIds = it.watchedImdbIds + owned.watchedImdbIds,
                            watchedTmdbIds = it.watchedTmdbIds + owned.watchedTmdbIds,
                        )
                    }
                }.onFailure { Timber.w(it, "[Discover] OwnedIds failed") }
            } else {
                Timber.w("[Discover] userId BLANK in prefs — Jellyfin nepřihlášen?")
            }
        }
    }

    private suspend fun runSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, error = null) }
        try {
            val combined = coroutineScope {
                val traktDeferred = async {
                    runCatching { traktApi.fetchSearch(query, withMovies = true).mapNotNull { it.toMediaItem() } }
                        .getOrDefault(emptyList())
                }
                val tmdbMoviesDeferred = async {
                    runCatching {
                        val tmdbResults = tmdbApi.searchMovies(query, "cs-CZ").take(10)
                        tmdbResults.map { tmdb ->
                            async {
                                val traktResults = runCatching {
                                    traktApi.fetchSearchId("tmdb", tmdb.id.toString())
                                }.getOrDefault(emptyList())
                                traktResults.firstOrNull { it.movie?.ids?.tmdb == tmdb.id }?.toMediaItem()
                            }
                        }.awaitAll().filterNotNull()
                    }.getOrDefault(emptyList())
                }
                val tmdbShowsDeferred = async {
                    runCatching {
                        val tmdbResults = tmdbApi.searchShows(query, "cs-CZ").take(10)
                        tmdbResults.map { tmdb ->
                            async {
                                val traktResults = runCatching {
                                    traktApi.fetchSearchId("tmdb", tmdb.id.toString())
                                }.getOrDefault(emptyList())
                                traktResults.firstOrNull { it.show?.ids?.tmdb == tmdb.id }?.toMediaItem()
                            }
                        }.awaitAll().filterNotNull()
                    }.getOrDefault(emptyList())
                }
                (traktDeferred.await() + tmdbMoviesDeferred.await() + tmdbShowsDeferred.await())
                    .distinctBy { "${it.type}_${it.traktId}" }
            }
            val mediaItems = combined
            val enriched = coroutineScope {
                mediaItems.map { item ->
                    async {
                        val tmdbId = item.tmdbId ?: return@async item
                        if (item.type == MediaType.MOVIE) {
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
            val state = _uiState.value
            val filtered = applyFilters(enriched, state.filters, state)
            _uiState.update {
                it.copy(
                    rawSearchResults = enriched,
                    searchResults = filtered,
                    availableGenres = mergeGenres(it.availableGenres, enriched),
                    isSearching = false,
                )
            }
            if (_uiState.value.rdOnly) computeRdMatch()
        } catch (e: Throwable) {
            _uiState.update { it.copy(isSearching = false, error = e.message ?: "Chyba vyhledávání") }
        }
    }

    private fun load(tab: DiscoverTab, filter: DiscoverFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (filter == DiscoverFilter.RECOMMENDED && tokenProvider.getToken() == null) {
                    _uiState.update {
                        it.copy(
                            rawItems = emptyList(),
                            items = emptyList(),
                            isLoading = false,
                            error = "needs_trakt_login",
                        )
                    }
                    return@launch
                }
                val rawItems = when (tab to filter) {
                    DiscoverTab.MOVIES to DiscoverFilter.TRENDING ->
                        traktApi.fetchTrendingMovies("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.MOVIES to DiscoverFilter.POPULAR ->
                        traktApi.fetchPopularMovies("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.MOVIES to DiscoverFilter.ANTICIPATED ->
                        traktApi.fetchAnticipatedMovies("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.MOVIES to DiscoverFilter.RECOMMENDED ->
                        authorizedTraktApi.fetchRecommendedMovies(40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.TRENDING ->
                        traktApi.fetchTrendingShows("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.POPULAR ->
                        traktApi.fetchPopularShows("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.ANTICIPATED ->
                        traktApi.fetchAnticipatedShows("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.RECOMMENDED ->
                        authorizedTraktApi.fetchRecommendedShows(40).map { it.toMediaItem() }
                    else -> emptyList()
                }
                val enriched = coroutineScope {
                    rawItems.map { item ->
                        async {
                            val tmdbId = item.tmdbId ?: return@async item
                            if (tab == DiscoverTab.MOVIES) {
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
                val state = _uiState.value
                val filtered = applyFilters(enriched, state.filters, state)
                _uiState.update {
                    it.copy(
                        rawItems = enriched,
                        items = filtered,
                        availableGenres = mergeGenres(it.availableGenres, enriched),
                        isLoading = false,
                    )
                }
                if (_uiState.value.rdOnly) computeRdMatch()
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba načítání") }
            }
        }
    }

    private fun mergeGenres(existing: List<String>, items: List<MediaItem>): List<String> {
        val merged = (existing + items.flatMap { it.genres.orEmpty() }).toSortedSet()
        return merged.toList()
    }
}
