package com.github.jankoran90.showlyfin.feature.watchlist

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val tmdbApi: TmdbRemoteDataSource,
    private val tokenProvider: TokenProvider,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val jellyfinLibraryService: JellyfinLibraryService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _rawItems = MutableStateFlow<List<MediaItem>>(emptyList())
    private var lockedRating: AgeRating? = null

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        val loggedIn = tokenProvider.getToken() != null
        _uiState.update { it.copy(isLoggedIn = loggedIn) }
        if (loggedIn) load(WatchlistTab.MOVIES)
        loadJellyfinOwned()
        parentalControlsRepository.profile
            .onEach { profile ->
                lockedRating = if (profile.isLocked) profile.effectiveAgeRating else null
                reapply()
            }
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
                Timber.i("[Watchlist] OwnedIds loaded: imdb=${owned.imdbIds.size} tmdb=${owned.tmdbIds.size}")
                _uiState.update {
                    it.copy(
                        ownedImdbIds = owned.imdbIds,
                        imdbToJellyfin = owned.imdbToJellyfin,
                        tmdbToJellyfin = owned.tmdbToJellyfin,
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
        _uiState.update { it.copy(sort = sort) }
        reapply()
    }

    fun selectGenre(genre: String?) {
        _uiState.update { it.copy(genreFilter = genre) }
        reapply()
    }

    fun refresh() {
        if (_uiState.value.isLoggedIn) load(_uiState.value.activeTab)
    }

    private fun load(tab: WatchlistTab) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val rawItems = if (tab == WatchlistTab.MOVIES) {
                    authorizedTraktApi.fetchSyncMoviesWatchlist().map { it.toMovieMediaItem() }
                } else {
                    authorizedTraktApi.fetchSyncShowsWatchlist().map { it.toShowMediaItem() }
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
            val watched = authorizedTraktApi.fetchSyncWatchedShows(extended = "full")
            val map = mutableMapOf<Long, WatchProgress>()
            for (entry in watched) {
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
            _uiState.update { it.copy(progressMap = map) }
        }
    }

    private fun reapply() {
        val state = _uiState.value
        _uiState.update { it.copy(items = applyAll(_rawItems.value, state.sort, state.genreFilter)) }
    }

    private fun applyAll(items: List<MediaItem>, sort: WatchlistSort, genre: String?): List<MediaItem> {
        var result = applyLock(items)
        if (!genre.isNullOrBlank()) {
            result = result.filter { it.genres.orEmpty().any { g -> g.equals(genre, ignoreCase = true) } }
        }
        result = when (sort) {
            WatchlistSort.DEFAULT -> result
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
