package com.github.jankoran90.showlyfin.feature.uploader

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem
import com.github.jankoran90.showlyfin.data.uploader.model.TmdbDetail
import com.github.jankoran90.showlyfin.data.uploader.model.TmmCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class LibraryDetailUiState(
    val item: LibraryItem? = null,
    val tmdbDetail: TmdbDetail? = null,
    val isLoadingTmdb: Boolean = false,
    val isApplying: Boolean = false,
    val isLoadingSearch: Boolean = false,
    val searchResults: List<TmmCandidate> = emptyList(),
    val selectedPosterUrl: String? = null,
    val selectedBackdropUrl: String? = null,
    val selectedLogoUrl: String? = null,
    val message: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
    val collections: List<String> = emptyList(),
    val openCollectionPicker: Boolean = false,
)

@HiltViewModel
class LibraryDetailViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryDetailUiState())
    val uiState: StateFlow<LibraryDetailUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    fun init(item: LibraryItem) {
        _uiState.update {
            it.copy(item = item, selectedPosterUrl = item.artworkPosterUrl, selectedBackdropUrl = item.artworkBackdropUrl, selectedLogoUrl = item.artworkLogoUrl)
        }
        item.tmdbId?.let { loadTmdbDetail(it, item.mediaType) }
    }

    private fun loadTmdbDetail(tmdbId: Int, mediaType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTmdb = true) }
            runCatching { uploaderDs.getTmdbDetail(baseUrl, cookie, tmdbId, mediaType) }
                .onSuccess { detail ->
                    val current = _uiState.value
                    _uiState.update {
                        it.copy(
                            isLoadingTmdb = false, tmdbDetail = detail,
                            selectedPosterUrl = current.selectedPosterUrl ?: detail.posterUrl,
                            selectedBackdropUrl = current.selectedBackdropUrl ?: detail.backdropUrl,
                            selectedLogoUrl = current.selectedLogoUrl ?: detail.logoUrl,
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(isLoadingTmdb = false) } }
        }
    }

    fun selectArtwork(type: String, url: String) = when (type) {
        "poster" -> _uiState.update { it.copy(selectedPosterUrl = url) }
        "backdrop" -> _uiState.update { it.copy(selectedBackdropUrl = url) }
        "logo" -> _uiState.update { it.copy(selectedLogoUrl = url) }
        else -> {}
    }

    fun applyChanges(library: String) {
        val state = _uiState.value
        val item = state.item ?: return
        val tmdbId = item.tmdbId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, error = null) }
            runCatching {
                uploaderDs.storageboxConfirm(baseUrl, cookie, library, item.name, tmdbId, item.mediaType, state.selectedPosterUrl, state.selectedBackdropUrl, state.selectedLogoUrl, item.jfItemId)
            }.onSuccess { response ->
                _uiState.update { it.copy(isApplying = false, item = item.copy(hasNfo = response.hasNfo, hasPoster = response.hasPoster, hasFanart = response.hasFanart, hasLogo = response.hasLogo, complete = response.complete), message = "Uloženo") }
            }.onFailure { e -> _uiState.update { it.copy(isApplying = false, error = e.message) } }
        }
    }

    fun confirmMatch(library: String, tmdbId: Int) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, error = null) }
            runCatching { uploaderDs.storageboxConfirm(baseUrl, cookie, library, item.name, tmdbId, item.mediaType, null, null, null, item.jfItemId) }
                .onSuccess { response ->
                    val updatedItem = item.copy(title = response.title, hasNfo = response.hasNfo, hasPoster = response.hasPoster, hasFanart = response.hasFanart, hasLogo = response.hasLogo, complete = response.complete, tmdbId = tmdbId)
                    _uiState.update { it.copy(isApplying = false, item = updatedItem, searchResults = emptyList(), message = "TMDB aktualizováno") }
                    response.match?.let { match -> _uiState.update { it.copy(tmdbDetail = match, selectedPosterUrl = match.posterUrl, selectedBackdropUrl = match.backdropUrl, selectedLogoUrl = match.logoUrl) } }
                        ?: loadTmdbDetail(tmdbId, item.mediaType)
                }
                .onFailure { e -> _uiState.update { it.copy(isApplying = false, error = e.message) } }
        }
    }

    fun search(library: String, query: String) {
        val item = _uiState.value.item ?: return
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSearch = true, error = null) }
            runCatching { uploaderDs.storageboxSearch(baseUrl, cookie, library, item.name, query, item.mediaType) }
                .onSuccess { results -> _uiState.update { it.copy(isLoadingSearch = false, searchResults = results) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingSearch = false, error = e.message) } }
        }
    }

    fun toggleWatched(library: String) {
        val item = _uiState.value.item ?: return
        val newVal = !item.watched
        _uiState.update { it.copy(item = item.copy(watched = newVal)) }
        viewModelScope.launch {
            runCatching { uploaderDs.updateUserdata(baseUrl, cookie, library, item.name, newVal, null, null) }
                .onFailure { e -> _uiState.update { it.copy(item = item, error = e.message) } }
        }
    }

    fun deleteFolder(library: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, error = null) }
            runCatching { uploaderDs.deleteFolder(baseUrl, cookie, library, item.name) }
                .onSuccess { _uiState.update { it.copy(isApplying = false, deleted = true) } }
                .onFailure { e -> _uiState.update { it.copy(isApplying = false, error = e.message) } }
        }
    }

    fun loadAndPickCollection(library: String) {
        viewModelScope.launch {
            runCatching { uploaderDs.getCollections(baseUrl, cookie, library) }
                .onSuccess { cols -> _uiState.update { it.copy(collections = cols, openCollectionPicker = true) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun clearCollectionPicker() { _uiState.update { it.copy(openCollectionPicker = false) } }

    fun saveUserCollection(library: String, collection: String) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            runCatching { uploaderDs.setCollection(baseUrl, cookie, library, item.name, collection) }
                .onSuccess { _uiState.update { state -> state.copy(isApplying = false, item = state.item?.copy(userCollection = collection.ifBlank { null })) } }
                .onFailure { e -> _uiState.update { it.copy(isApplying = false, error = e.message) } }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null, error = null) } }
}
