package com.github.jankoran90.showlyfin.feature.remux

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class RemuxProgressUiState(
    val status: String = "",
    val pct: Double = 0.0,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RemuxProgressViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemuxProgressUiState())
    val uiState: StateFlow<RemuxProgressUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_COOKIE, "") ?: ""

    fun startPolling(jobId: String) {
        viewModelScope.launch {
            while (true) {
                runCatching { uploaderDs.getRemuxStatus(baseUrl, cookie, jobId) }
                    .onSuccess { job ->
                        when (job.status) {
                            "done" -> { _uiState.update { it.copy(status = job.status, pct = 100.0, isDone = true) }; return@launch }
                            "error" -> { _uiState.update { it.copy(status = job.status, pct = job.pct, error = job.error ?: "Chyba") }; return@launch }
                            else -> _uiState.update { it.copy(status = job.status, pct = job.pct) }
                        }
                    }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) }; return@launch }
                delay(2_000)
            }
        }
    }
}
