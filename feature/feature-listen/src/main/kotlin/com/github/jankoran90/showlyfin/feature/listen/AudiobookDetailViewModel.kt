package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookDetailViewModel @Inject constructor(
    private val repo: AbsRepository,
    connection: AudiobookPlayerConnection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookDetailUiState())
    val uiState = _uiState.asStateFlow()

    /** Stav přehrávače — pro zvýraznění právě hrané kapitoly v seznamu. */
    val playerState = connection.state

    private var loadedId: String? = null

    fun load(itemId: String) {
        if (loadedId == itemId && _uiState.value.detail != null) return
        loadedId = itemId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getAudiobookDetail(itemId) }
                .onSuccess { d -> _uiState.update { it.copy(isLoading = false, detail = d) } }
                .onFailure { e ->
                    Timber.w(e, "[Listen] detail selhal")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení detailu selhalo.") }
                }
        }
    }
}
