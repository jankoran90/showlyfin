package com.github.jankoran90.showlyfin.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.csfd.CsfdScraper
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val tmdbApi: TmdbRemoteDataSource,
    private val csfdScraper: CsfdScraper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun load(item: MediaItem) {
        if (_uiState.value.item?.traktId == item.traktId) return
        _uiState.update { it.copy(item = item, isLoading = true) }
        viewModelScope.launch {
            try {
                val tmdbId = item.tmdbId
                if (tmdbId != null) {
                    if (item.type == MediaType.MOVIE) {
                        coroutineScope {
                            val detailsDeferred = async { tmdbApi.fetchMovieDetails(tmdbId) }
                            val translationDeferred = async { tmdbApi.fetchMovieTranslation(tmdbId, "cs") }
                            val details = detailsDeferred.await()
                            val translation = translationDeferred.await()
                            _uiState.update {
                                it.copy(
                                    movieDetails = details,
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = translation?.title?.takeIf { t -> t.isNotBlank() },
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
                            _uiState.update {
                                it.copy(
                                    showDetails = details,
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = translation?.name?.takeIf { t -> t.isNotBlank() },
                                    item = item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path),
                                    isLoading = false,
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
        loadCsfd(item)
    }

    private fun loadCsfd(item: MediaItem) {
        if (item.type != MediaType.MOVIE) return
        val title = item.title.ifBlank { return }
        val year = item.year ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCsfdLoading = true) }
            try {
                val csfdId = csfdScraper.searchByTitle(title, year) ?: run {
                    _uiState.update { it.copy(isCsfdLoading = false) }
                    return@launch
                }
                _uiState.update { it.copy(csfdId = csfdId) }
                coroutineScope {
                    val plotDeferred = async { csfdScraper.scrapePlot(csfdId) }
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
}
