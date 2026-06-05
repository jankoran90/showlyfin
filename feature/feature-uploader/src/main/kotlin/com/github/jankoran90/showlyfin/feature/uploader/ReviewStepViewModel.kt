package com.github.jankoran90.showlyfin.feature.uploader

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.TmmCandidate
import com.github.jankoran90.showlyfin.feature.remux.SmartDetectViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class ReviewStepUiState(
    val isLoading: Boolean = false,
    val candidates: List<TmmCandidate> = emptyList(),
    val isConfirmed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ReviewStepViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewStepUiState())
    val uiState: StateFlow<ReviewStepUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_COOKIE, "") ?: ""

    fun loadCandidates(sid: String, fid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val session = uploaderDs.getTmmSession(baseUrl, cookie, sid)
                session.files[fid]?.candidates ?: emptyList()
            }.onSuccess { candidates -> _uiState.update { it.copy(isLoading = false, candidates = candidates) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun search(sid: String, fid: String, query: String, year: Int?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { uploaderDs.tmmSearch(baseUrl, cookie, sid, fid, query, year) }
                .onSuccess { candidates -> _uiState.update { it.copy(isLoading = false, candidates = candidates) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun confirm(sid: String, fid: String, tmdbId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { uploaderDs.tmmConfirm(baseUrl, cookie, sid, fid, tmdbId) }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isConfirmed = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun confirmById(sid: String, fid: String, tmdbIdStr: String) {
        val id = tmdbIdStr.trim().toIntOrNull()
        if (id == null || id <= 0) { _uiState.update { it.copy(error = "Neplatné TMDB ID") }; return }
        confirm(sid, fid, id)
    }
}
