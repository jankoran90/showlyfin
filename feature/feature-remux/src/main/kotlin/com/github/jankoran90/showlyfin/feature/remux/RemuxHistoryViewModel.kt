package com.github.jankoran90.showlyfin.feature.remux

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.RemuxSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class RemuxHistoryUiState(
    val sessions: List<RemuxSession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RemuxHistoryViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemuxHistoryUiState())
    val uiState: StateFlow<RemuxHistoryUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_COOKIE, "") ?: ""

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { uploaderDs.getRemuxHistory(baseUrl, cookie) }
                .onSuccess { sessions -> _uiState.update { it.copy(sessions = sessions, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun deleteSession(rsid: String) {
        viewModelScope.launch {
            runCatching { uploaderDs.deleteRemuxSession(baseUrl, cookie, rsid) }
                .onSuccess { _uiState.update { state -> state.copy(sessions = state.sessions.filter { it.id != rsid }) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
