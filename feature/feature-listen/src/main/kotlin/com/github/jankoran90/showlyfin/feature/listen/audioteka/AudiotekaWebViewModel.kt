package com.github.jankoran90.showlyfin.feature.listen.audioteka

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Stránka Audioteky uvnitř Slova: dovoz stažené knihy ([AudiotekaImporter]) + knihovny k výběru. */
@HiltViewModel
class AudiotekaWebViewModel @Inject constructor(
    val importer: AudiotekaImporter,
    private val absRepo: AbsRepository,
    uploadManager: AudiobookUploadManager,
) : ViewModel() {

    val upload: StateFlow<AudiobookUploadManager.UploadState> = uploadManager.state

    private val _libraries = MutableStateFlow<List<AbsLibrary>>(emptyList())
    val libraries: StateFlow<List<AbsLibrary>> = _libraries.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { absRepo.getAudiobookLibraries() }
                .onSuccess { _libraries.value = it }
                .onFailure { Timber.w(it, "[AUDIOTEKA] knihovny pro dovoz se nenačetly") }
        }
    }
}
