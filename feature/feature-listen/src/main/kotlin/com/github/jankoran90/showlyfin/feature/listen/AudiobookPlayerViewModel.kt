package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Most fullscreen/mini playeru na sdílený [AudiobookPlayerConnection]. `open()` otevře ABS
 * play session (stream URL + uložená pozice + kapitoly) a předá ji connectionu.
 */
@HiltViewModel
class AudiobookPlayerViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val connection: AudiobookPlayerConnection,
) : ViewModel() {

    val state = connection.state
    val chapters = connection.chapters

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var openedFor: String? = null

    /** Spustí přehrávání knihy. Volá se jednou při otevření playeru. */
    fun open(itemId: String, fromStart: Boolean) {
        if (openedFor == itemId) return
        openedFor = itemId
        viewModelScope.launch {
            runCatching { repo.startPlayback(itemId) }
                .onSuccess { connection.playBook(it, fromStart) }
                .onFailure {
                    Timber.w(it, "[Listen] startPlayback selhal")
                    _error.value = "Přehrávání se nepodařilo spustit."
                    openedFor = null
                }
        }
    }

    fun playPause() = connection.playPause()
    fun seekTo(ms: Long) = connection.seekTo(ms)
    fun seekBy(deltaMs: Long) = connection.seekBy(deltaMs)
    fun nextChapter() = connection.nextChapter()
    fun prevChapter() = connection.prevChapter()
    fun setSpeed(speed: Float) = connection.setSpeed(speed)
    fun setSleepTimer(minutes: Int?) = connection.setSleepTimer(minutes)
}
