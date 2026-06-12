package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.DownloadState
import com.github.jankoran90.showlyfin.data.abs.model.toAudiobookDetail
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookDetailViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val audiobookDownloads: AudiobookDownloadManager,
    connection: AudiobookPlayerConnection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookDetailUiState())
    val uiState = _uiState.asStateFlow()

    /** Stav přehrávače — pro zvýraznění právě hrané kapitoly v seznamu. */
    val playerState = connection.state

    private val _itemId = MutableStateFlow<String?>(null)
    private var loadedId: String? = null

    /** Stav stažení CELÉ audioknihy (řádek v hlavičce detailu). */
    val downloadState: kotlinx.coroutines.flow.StateFlow<DownloadState> =
        combine(_itemId, audiobookDownloads.states) { id, states -> states[id] ?: DownloadState() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState())

    /** Stáhnout celou aktuální audioknihu do zařízení (offline). */
    fun downloadAudiobook() {
        val d = _uiState.value.detail?.book ?: return
        audiobookDownloads.download(d.id, d.title, d.author, d.coverUrl)
    }

    fun cancelDownload() {
        _uiState.value.detail?.book?.id?.let { audiobookDownloads.cancel(it) }
    }

    fun deleteDownload() {
        _uiState.value.detail?.book?.id?.let { audiobookDownloads.delete(it) }
    }

    fun load(itemId: String) {
        _itemId.value = itemId
        if (loadedId == itemId && _uiState.value.detail != null) return
        loadedId = itemId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getAudiobookDetail(itemId) }
                .onSuccess { d -> _uiState.update { it.copy(isLoading = false, detail = d) } }
                .onFailure { e ->
                    // Plan CASTAWAY — offline/selhání serveru: postav detail ze stažené knihy, ať jde
                    // otevřít a spustit přehrávač i bez sítě.
                    val offline = audiobookDownloads.downloadRecord(itemId)?.toAudiobookDetail()
                    if (offline != null) {
                        _uiState.update { it.copy(isLoading = false, detail = offline, error = null) }
                    } else {
                        Timber.w(e, "[Listen] detail selhal")
                        _uiState.update { it.copy(isLoading = false, error = "Načtení detailu selhalo.") }
                    }
                }
        }
    }
}
