package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
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

    /**
     * Spustí přehrávání knihy nebo podcast epizody. [episodeId] != null = podcast epizoda
     * (single track, bez kapitol). [startSec] != null = skok na začátek vybrané kapitoly.
     * Pokud už totéž hraje (Activity-scoped VM), kapitolu řešíme přímým seekem bez restartu.
     */
    fun open(itemId: String, fromStart: Boolean, startSec: Double? = null, episodeId: String? = null) {
        val key = if (episodeId != null) "$itemId/$episodeId" else itemId
        if (openedFor == key) {
            if (startSec != null) connection.seekTo((startSec * 1000).toLong())
            return
        }
        openedFor = key
        viewModelScope.launch {
            runCatching {
                if (episodeId != null) repo.startEpisodePlayback(itemId, episodeId)
                else repo.startPlayback(itemId)
            }
                .onSuccess { pb ->
                    val ep = if (episodeId != null) {
                        QueuedEpisode(itemId, episodeId, pb.title, pb.coverUrl)
                    } else null
                    connection.playBook(pb, fromStart, startSec, ep)
                }
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
    fun seekToChapter(startSec: Double) = connection.seekToChapter(startSec)
    fun setSpeed(speed: Float) = connection.setSpeed(speed)
    fun setSleepTimer(minutes: Int?) = connection.setSleepTimer(minutes)
}
