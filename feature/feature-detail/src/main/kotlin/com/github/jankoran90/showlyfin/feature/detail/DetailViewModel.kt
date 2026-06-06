package com.github.jankoran90.showlyfin.feature.detail

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.csfd.CsfdScraper
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val tmdbApi: TmdbRemoteDataSource,
    private val csfdScraper: CsfdScraper,
    private val csfdRepository: CsfdRepository,
    private val jellyfinLibraryService: JellyfinLibraryService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(item: MediaItem) {
        val current = _uiState.value.item
        if (current != null) {
            val sameTrakt = current.traktId != 0L && current.traktId == item.traktId
            val sameTmdb = current.tmdbId != null && item.tmdbId != null && current.tmdbId == item.tmdbId
            if (sameTrakt || sameTmdb) return
        }
        _uiState.update {
            it.copy(
                item = item,
                isLoading = true,
                isCsfdLoading = item.type == MediaType.MOVIE,
                movieDetails = null,
                showDetails = null,
                tmdbCzOverview = null,
                tmdbCzTitle = null,
                csfdId = null,
                csfdRating = null,
                csfdPlot = null,
                csfdReviews = emptyList(),
                collection = null,
                isOwnedInLibrary = false,
                ownedJellyfinId = null,
                error = null,
            )
        }
        viewModelScope.launch { loadJellyfinOwned(item) }
        viewModelScope.launch {
            try {
                val tmdbId = item.tmdbId
                var resolvedCzTitle: String? = item.titleCz?.takeIf { it.isNotBlank() }
                if (tmdbId != null) {
                    if (item.type == MediaType.MOVIE) {
                        coroutineScope {
                            val detailsDeferred = async { tmdbApi.fetchMovieDetails(tmdbId) }
                            val translationDeferred = async { tmdbApi.fetchMovieTranslation(tmdbId, "cs") }
                            val details = detailsDeferred.await()
                            val translation = translationDeferred.await()
                            val tmdbCzTitle = translation?.title?.takeIf { it.isNotBlank() }
                            if (tmdbCzTitle != null) resolvedCzTitle = tmdbCzTitle
                            _uiState.update {
                                it.copy(
                                    movieDetails = details,
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = tmdbCzTitle,
                                    item = item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path),
                                    isLoading = false,
                                )
                            }
                            details?.belongs_to_collection?.id?.takeIf { it > 0 }?.let { collectionId ->
                                launch {
                                    val collection = tmdbApi.fetchCollection(collectionId)
                                    _uiState.update { it.copy(collection = collection) }
                                }
                            }
                        }
                    } else {
                        coroutineScope {
                            val detailsDeferred = async { tmdbApi.fetchShowDetails(tmdbId) }
                            val translationDeferred = async { tmdbApi.fetchShowTranslation(tmdbId, "cs") }
                            val details = detailsDeferred.await()
                            val translation = translationDeferred.await()
                            val tmdbCzTitle = translation?.name?.takeIf { it.isNotBlank() }
                            if (tmdbCzTitle != null) resolvedCzTitle = tmdbCzTitle
                            _uiState.update {
                                it.copy(
                                    showDetails = details,
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = tmdbCzTitle,
                                    item = item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path),
                                    isLoading = false,
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                if (item.type == MediaType.MOVIE) {
                    loadCsfd(item, resolvedCzTitle)
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, isCsfdLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadJellyfinOwned(item: MediaItem) {
        val userIdString = prefs.getString("jellyfin_user_id", "")?.takeIf { it.isNotBlank() } ?: return
        val userUuid = runCatching { UUID.fromString(userIdString) }.getOrNull() ?: return
        val owned = runCatching { jellyfinLibraryService.getOwnedIds(userUuid) }.getOrNull() ?: return
        val matchedJellyfinId = item.imdbId?.let { owned.imdbToJellyfin[it] }
            ?: item.tmdbId?.let { owned.tmdbToJellyfin[it] }
        _uiState.update {
            it.copy(
                ownedImdbToJellyfin = owned.imdbToJellyfin,
                ownedTmdbToJellyfin = owned.tmdbToJellyfin,
                isOwnedInLibrary = matchedJellyfinId != null,
                ownedJellyfinId = matchedJellyfinId,
            )
        }
    }

    private suspend fun loadCsfd(item: MediaItem, czTitle: String?) {
        val titles = buildList {
            czTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.title.takeIf { it.isNotBlank() }?.let { if (!contains(it)) add(it) }
        }
        val year = item.year ?: 0
        val imdbId = item.imdbId.orEmpty()
        val tmdbId = item.tmdbId
        try {
            var csfdId: Long? = null
            for (title in titles) {
                csfdId = csfdRepository.getCsfdId(imdbId, tmdbId, title, year)
                if (csfdId != null) break
            }
            if (csfdId == null) {
                csfdId = csfdRepository.getCsfdId(imdbId, tmdbId, "", year)
            }
            if (csfdId == null) {
                _uiState.update { it.copy(isCsfdLoading = false) }
                return
            }
            _uiState.update { it.copy(csfdId = csfdId) }
            coroutineScope {
                val plotDeferred = async { csfdRepository.getCzechPlot(csfdId) }
                val ratingDeferred = async { csfdScraper.scrapeRating(csfdId) }
                val reviewsDeferred = async { csfdScraper.scrapeReviews(csfdId).take(3) }
                _uiState.update {
                    it.copy(
                        csfdPlot = plotDeferred.await(),
                        csfdRating = ratingDeferred.await(),
                        csfdReviews = reviewsDeferred.await(),
                        isCsfdLoading = false,
                    )
                }
            }
        } catch (e: Throwable) {
            _uiState.update { it.copy(isCsfdLoading = false) }
        }
    }
}
