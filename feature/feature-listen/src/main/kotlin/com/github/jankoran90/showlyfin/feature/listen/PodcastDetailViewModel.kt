package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repo: AbsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var loadedId: String? = null

    fun load(itemId: String) {
        if (loadedId == itemId && _uiState.value.detail != null) return
        loadedId = itemId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getPodcastDetail(itemId) }
                .onSuccess { d -> _uiState.update { it.copy(isLoading = false, detail = d) } }
                .onFailure { e ->
                    Timber.w(e, "[Listen] podcast detail selhal")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení detailu podcastu selhalo.") }
                }
        }
    }
}
