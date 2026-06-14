package com.github.jankoran90.showlyfin.feature.discover

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * Sekce „Na RD" — stažené filmy na RealDebrid účtu, namatchnuté na TMDB (poster/název/rok).
 * Backend `GET /api/stremio/rd/library` (Plan QUASAR Fáze D). Klik na kartu → bohatý Detail.
 */
@HiltViewModel
class RdLibraryViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RdLibraryUiState())
    val uiState = _uiState.asStateFlow()

    // VANTAGE/SWEEP: per-sekce mřížka/seznam (parita s ostatními sekcemi), klíč SECTION_RD.
    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { m -> if (m[ViewModeStore.SECTION_RD] == ViewModeStore.LIST) ViewMode.LIST else ViewMode.GRID }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    fun toggleViewMode() {
        val next = if (viewMode.value == ViewMode.GRID) ViewModeStore.LIST else ViewModeStore.GRID
        viewModeStore.set(ViewModeStore.SECTION_RD, next)
    }

    fun onSearchQueryChange(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setSort(sort: RdSort) = _uiState.update { it.copy(sortBy = sort) }

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    init { load() }

    fun load() {
        if (uploaderBaseUrl.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Uploader není nastaven v Nastavení.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { uploaderDs.getRdLibrary(uploaderBaseUrl, uploaderCookie) }
                .onSuccess { resp ->
                    val items = resp.items.mapNotNull { rd ->
                        val tmdb = rd.tmdbId?.toLong() ?: return@mapNotNull null
                        MediaItem(
                            traktId = 0L,
                            tmdbId = tmdb,
                            imdbId = rd.imdbId,
                            title = rd.title,
                            year = rd.year,
                            overview = rd.overview,
                            rating = null,
                            genres = null,
                            type = MediaType.MOVIE,
                            posterPath = rd.posterPath,
                        )
                    }
                    _uiState.update {
                        it.copy(isLoading = false, items = items, unmatchedCount = resp.unmatched.size)
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "[RdLibrary] load failed")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení knihovny RD selhalo.") }
                }
        }
    }
}
