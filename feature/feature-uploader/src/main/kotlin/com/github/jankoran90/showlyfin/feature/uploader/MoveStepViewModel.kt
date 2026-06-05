package com.github.jankoran90.showlyfin.feature.uploader

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.feature.remux.SmartDetectViewModel
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class MoveStepUiState(
    val isLoading: Boolean = false,
    val libraries: List<String> = emptyList(),
    val isMoved: Boolean = false,
    val movedLibrary: String = "",
    val error: String? = null,
)

@HiltViewModel
class MoveStepViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoveStepUiState())
    val uiState: StateFlow<MoveStepUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_COOKIE, "") ?: ""

    fun loadLibraries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { uploaderDs.getLibraries(baseUrl, cookie) }
                .onSuccess { libs -> _uiState.update { it.copy(isLoading = false, libraries = libs) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun move(sid: String, library: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { uploaderDs.tmmMove(baseUrl, cookie, sid, library) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isMoved = true, movedLibrary = library) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
