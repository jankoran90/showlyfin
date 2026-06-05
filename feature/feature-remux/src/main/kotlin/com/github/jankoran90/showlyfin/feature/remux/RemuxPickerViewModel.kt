package com.github.jankoran90.showlyfin.feature.remux

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.ProbeStream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class RemuxPickerUiState(
    val isLoading: Boolean = false,
    val streams: List<ProbeStream> = emptyList(),
    val totalDurMs: Long = 0L,
    val selectedIndices: Set<Int> = emptySet(),
    val recommendedIndices: Set<Int> = emptySet(),
    val error: String? = null,
    val startedJobId: String? = null,
)

@HiltViewModel
class RemuxPickerViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemuxPickerUiState())
    val uiState: StateFlow<RemuxPickerUiState> = _uiState.asStateFlow()

    private val baseUrl get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(SmartDetectViewModel.PREF_UPLOADER_COOKIE, "") ?: ""

    fun loadStreams(library: String, folder: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { uploaderDs.probeStreams(baseUrl, cookie, library, folder) }
                .onSuccess { response ->
                    val recommended = recommendKeepIndices(response.streams)
                    _uiState.update {
                        it.copy(isLoading = false, streams = response.streams, totalDurMs = response.totalDurMs, selectedIndices = recommended, recommendedIndices = recommended)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun toggleTrack(index: Int) {
        val current = _uiState.value.selectedIndices.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _uiState.update { it.copy(selectedIndices = current) }
    }

    fun startRemux(library: String, folder: String) {
        val state = _uiState.value
        if (state.selectedIndices.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { uploaderDs.startRemux(baseUrl, cookie, library, folder, state.selectedIndices.sorted(), state.totalDurMs) }
                .onSuccess { jobId -> _uiState.update { it.copy(isLoading = false, startedJobId = jobId) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    private fun recommendKeepIndices(streams: List<ProbeStream>): Set<Int> {
        val result = mutableSetOf<Int>()
        streams.filter { it.type == "video" }.forEach { result.add(it.index) }
        val audioStreams = streams.filter { it.type == "audio" }
        val czLangs = setOf("cze", "ces", "cs", "cz")
        val czAudio = audioStreams.filter { it.lang.lowercase() in czLangs }
        val bestAudio = if (czAudio.isNotEmpty()) czAudio.maxWithOrNull(compareBy({ it.channels }, { codecPriority(it.codec) }))
        else audioStreams.maxWithOrNull(compareBy({ it.channels }, { codecPriority(it.codec) }))
        bestAudio?.let { result.add(it.index) }
        streams.filter { it.type == "subtitle" && it.lang.lowercase() in czLangs }.forEach { result.add(it.index) }
        return result
    }

    private fun codecPriority(codec: String): Int = when (codec.lowercase()) {
        "eac3" -> 4; "ac3" -> 3; "dts" -> 2; "aac" -> 1; else -> 0
    }
}
