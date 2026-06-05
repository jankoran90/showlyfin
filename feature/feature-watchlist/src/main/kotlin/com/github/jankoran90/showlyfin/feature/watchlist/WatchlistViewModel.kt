package com.github.jankoran90.showlyfin.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.feature.watchlist.mapper.toMovieMediaItem
import com.github.jankoran90.showlyfin.feature.watchlist.mapper.toShowMediaItem
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
class WatchlistViewModel @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val tmdbApi: TmdbRemoteDataSource,
    private val tokenProvider: TokenProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        val loggedIn = tokenProvider.getToken() != null
        _uiState.update { it.copy(isLoggedIn = loggedIn) }
        if (loggedIn) load(WatchlistTab.MOVIES)
    }

    fun selectTab(tab: WatchlistTab) {
        _uiState.update { it.copy(activeTab = tab, items = emptyList()) }
        if (_uiState.value.isLoggedIn) load(tab)
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
