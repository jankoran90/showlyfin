package com.github.jankoran90.showlyfin.feature.uploader

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem
import com.github.jankoran90.showlyfin.feature.remux.SmartDetectViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class LibraryBrowserUiState(
    val isLoading: Boolean = false,
    val libraries: List<String> = emptyList(),
    val selectedLibrary: String? = null,
    val items: List<LibraryItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LibraryBrowserViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryBrowserUiState())
    val uiState: StateFlow<LibraryBrowserUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_COOKIE, "") ?: ""

    fun loadLibraries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { uploaderDs.getLibraries(baseUrl, cookie) }
                .onSuccess { libs -> _uiState.update { it.copy(isLoading = false, libraries = libs) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun selectLibrary(library: String) {
        if (library.isBlank()) { _uiState.update { it.copy(selectedLibrary = null, items = emptyList(), error = null) }; return }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedLibrary = library, items = emptyList(), error = null) }
            runCatching { uploaderDs.scanLibrary(baseUrl, cookie, library) }
                .onSuccess { items -> _uiState.update { it.copy(isLoading = false, items = items) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
