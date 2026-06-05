package com.github.jankoran90.showlyfin.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.TraktRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.mapper.toMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val traktApi: TraktRemoteDataSource,
    private val tmdbApi: TmdbRemoteDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        load(DiscoverTab.MOVIES, DiscoverFilter.TRENDING)
    }

    fun selectTab(tab: DiscoverTab) {
        val filter = _uiState.value.activeFilter
        _uiState.update { it.copy(activeTab = tab, items = emptyList()) }
        load(tab, filter)
    }

    fun selectFilter(filter: DiscoverFilter) {
        val tab = _uiState.value.activeTab
        _uiState.update { it.copy(activeFilter = filter, items = emptyList()) }
        load(tab, filter)
    }

    fun refresh() = load(_uiState.value.activeTab, _uiState.value.activeFilter)

    private fun load(tab: DiscoverTab, filter: DiscoverFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val rawItems = when (tab to filter) {
                    DiscoverTab.MOVIES to DiscoverFilter.TRENDING ->
                        traktApi.fetchTrendingMovies("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.MOVIES to DiscoverFilter.POPULAR ->
                        traktApi.fetchPopularMovies("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.MOVIES to DiscoverFilter.ANTICIPATED ->
                        traktApi.fetchAnticipatedMovies("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.TRENDING ->
                        traktApi.fetchTrendingShows("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.POPULAR ->
                        traktApi.fetchPopularShows("", "", 40).map { it.toMediaItem() }
                    DiscoverTab.SHOWS to DiscoverFilter.ANTICIPATED ->
                        traktApi.fetchAnticipatedShows("", "", 40).map { it.toMediaItem() }
                    else -> emptyList()
                }
                val enriched = coroutineScope {
                    rawItems.map { item ->
                        async {
                            val tmdbId = item.tmdbId ?: return@async item
                            if (tab == DiscoverTab.MOVIES) {
                                val details = tmdbApi.fetchMovieDetails(tmdbId)
                                item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path)
                            } else {
                                val details = tmdbApi.fetchShowDetails(tmdbId)
                                item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path)
                            }
                        }
                    }.awaitAll()
                }
                _uiState.update { it.copy(items = enriched, isLoading = false) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba načítání") }
            }
        }
    }
}
