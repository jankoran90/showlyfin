package com.github.jankoran90.showlyfin.feature.listen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
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
    private val downloadManager: EpisodeDownloadManager,
    private val audiobookDownloads: AudiobookDownloadManager,
    private val absPrefs: com.github.jankoran90.showlyfin.data.abs.AbsPreferences,
) : ViewModel() {

    val state = connection.state
    val chapters = connection.chapters
    val queue = connection.queue

    /** Jednotné zobrazení epizody ve frontě (stejné jako v detailu; z nastavení). */
    val episodeDisplay: com.github.jankoran90.showlyfin.feature.listen.ui.EpisodeDisplaySettings
        get() = com.github.jankoran90.showlyfin.feature.listen.ui.EpisodeDisplaySettings(
            titleLines = absPrefs.episodeTitleLines,
            descriptionLines = absPrefs.episodeDescriptionLines,
            highlightGuest = absPrefs.highlightGuest,
            fontScale = absPrefs.episodeFontScale,
        )

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
                // Index stažených čteme JEDNOU do val (dřív se volalo 2× — guard + !! — a souběžná
                // změna indexu mezi voláními mohla hodit NPE → tiché selhání přehrávání v runCatching).
                val offlineBook = if (episodeId == null) audiobookDownloads.offlineAudiobookPlayback(itemId) else null
                val localEpisode = if (episodeId != null) downloadManager.localFile(episodeId) else null
                when {
                    // Stažená audiokniha: hraj z lokálních souborů (offline). Když jsme online, vezmi
                    // server session kvůli resume pozici + syncu, ale URL stop přepiš na lokální (dle
                    // indexu); když server selže (offline) → čistě lokální session.
                    offlineBook != null -> {
                        val server = runCatching { repo.startPlayback(itemId) }.getOrNull()
                        if (server != null) {
                            val localByIndex = offlineBook.tracks.associateBy { it.index }
                            server.copy(tracks = server.tracks.map { t -> localByIndex[t.index]?.let { t.copy(url = it.url) } ?: t })
                        } else {
                            offlineBook
                        }
                    }
                    episodeId == null -> repo.startPlayback(itemId)
                    // Stažená epizoda: hraj z lokálního souboru (funguje offline). Když jsme online,
                    // přesto otevřeme server session kvůli resume pozici + syncu, ale audio URL
                    // přepíšeme na lokální soubor; když server selže (offline) → čistě lokální session.
                    localEpisode != null -> {
                        val server = runCatching { repo.startEpisodePlayback(itemId, episodeId) }.getOrNull()
                        server?.copy(tracks = listOf(server.tracks.first().copy(url = Uri.fromFile(localEpisode).toString())))
                            ?: downloadManager.offlinePlayback(episodeId)
                            ?: error("Stažený soubor epizody chybí.")
                    }
                    else -> repo.startEpisodePlayback(itemId, episodeId)
                }
            }
                .onSuccess { pb ->
                    val ep = if (episodeId != null) {
                        QueuedEpisode(itemId, episodeId, pb.title, pb.coverUrl)
                    } else null
                    connection.playBook(pb, fromStart, startSec, ep, itemId)
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
    fun setSleepEndOfCurrent() = connection.setSleepEndOfCurrent()

    // ──────── Fronta (podcast epizody) ────────
    fun playQueued(episode: QueuedEpisode) = connection.playQueued(episode)
    fun playNextInQueue() = connection.playNextInQueue()
    fun playPrevInQueue() = connection.playPrevInQueue()
    fun removeFromQueue(episodeId: String) = connection.removeFromQueue(episodeId)
    fun clearQueue() = connection.clearQueue()
    fun clearAll() = connection.clearAll()
    fun moveQueueItem(from: Int, to: Int) = connection.moveQueueItem(from, to)
    fun moveQueuedToFront(episodeId: String) = connection.moveToFront(episodeId)
    /** Stáhnout položku fronty offline. ABS přes server; RSS/YT (`direct`) zatím no-op (L3 = NOMAD). */
    fun downloadQueued(ep: QueuedEpisode) {
        if (ep.direct != null) return
        downloadManager.downloadByIds(ep.itemId, ep.episodeId, ep.title, ep.coverUrl)
    }

    /** Akce swipe doprava ve frontě dle nastavení (0=stáhnout, 1=přehrát, 2=na začátek). */
    fun onQueueSwipeAction(ep: QueuedEpisode, action: Int) = when (action) {
        1 -> connection.playQueued(ep)
        2 -> connection.moveToFront(ep.episodeId)
        else -> downloadQueued(ep)
    }
}
