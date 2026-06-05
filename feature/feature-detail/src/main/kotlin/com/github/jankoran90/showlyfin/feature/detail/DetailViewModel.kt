package com.github.jankoran90.showlyfin.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val tmdbApi: TmdbRemoteDataSource,
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
                        val details = tmdbApi.fetchMovieDetails(tmdbId)
                        _uiState.update {
                            it.copy(
                                movieDetails = details,
                                item = item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path),
                                isLoading = false,
                            )
                        }
                    } else {
                        val details = tmdbApi.fetchShowDetails(tmdbId)
                        _uiState.update {
                            it.copy(
                                showDetails = details,
                                item = item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path),
                                isLoading = false,
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
