package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.Chapter
import com.github.jankoran90.showlyfin.feature.listen.service.AudiobookPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most mezi UI a [AudiobookPlayerService]. Drží jeden [MediaController] (sdílený fullscreen
 * playerem i mini-playerem), publikuje [PlayerState] (poll 500 ms + Player listener) a
 * forwarduje příkazy. Kapitoly aktuální knihy drží zvlášť pro skip ◀▶ a název kapitoly.
 * Pro podcasty drží frontu epizod (auto-advance + auto mark-finished na konci).
 */
@Singleton
class AudiobookPlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repo: AbsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private var pollJob: Job? = null
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters = _chapters.asStateFlow()

    /** Fronta podcastových epizod. */
    private val _queue = MutableStateFlow<List<QueuedEpisode>>(emptyList())
    val queue = _queue.asStateFlow()

    /** Aktuálně hraná podcast epizoda (null = audiokniha → bez fronty/auto-mark). */
    private var currentEpisode: QueuedEpisode? = null
    private var advancing = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            pushState()
            if (player.playbackState == Player.STATE_ENDED) onPlaybackEnded()
        }
    }

    private fun ensureController() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, AudiobookPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(playerListener)
            startPolling()
            pending?.let { it(c); pending = null }
            pushState()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending = block; ensureController() }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                pushState()
                delay(500)
            }
        }
    }

    private fun pushState() {
        val c = controller ?: return
        val pos = bookPosMs(c)
        val posSec = pos / 1000.0
        val currentChapter = _chapters.value.firstOrNull { posSec >= it.startSec && posSec < it.endSec }
        val bookDurationMs = (c.currentMediaItem?.mediaMetadata?.extras
            ?.getDouble(AudiobookPlayerService.KEY_DURATION_SEC) ?: 0.0).let { (it * 1000).toLong() }
        _state.update {
            it.copy(
                isActive = c.mediaItemCount > 0,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                title = c.mediaMetadata.title?.toString() ?: it.title,
                author = c.mediaMetadata.artist?.toString() ?: it.author,
                coverUrl = c.mediaMetadata.artworkUri?.toString() ?: it.coverUrl,
                positionMs = pos,
                durationMs = bookDurationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                speed = c.playbackParameters.speed,
                currentChapterTitle = currentChapter?.title,
                currentChapterIndex = currentChapter?.index,
            )
        }
    }

    fun playBook(pb: AbsPlayback, fromStart: Boolean, startOverrideSec: Double? = null, episode: QueuedEpisode? = null) {
        currentEpisode = episode
        // epizodu spuštěnou napřímo odstraň z fronty (ať nehraje dvakrát)
        if (episode != null) _queue.update { q -> q.filterNot { it.episodeId == episode.episodeId } }
        _chapters.value = pb.chapters
        _state.update {
            it.copy(
                isActive = true, title = pb.title, author = pb.author, coverUrl = pb.coverUrl,
                durationMs = (pb.durationSec * 1000).toLong(),
            )
        }
        withController { c ->
            val artwork = pb.coverUrl?.let(Uri::parse)
            val items = pb.tracks.map { t ->
                val extras = Bundle().apply {
                    putString(AudiobookPlayerService.KEY_SESSION_ID, pb.sessionId)
                    putDouble(AudiobookPlayerService.KEY_DURATION_SEC, pb.durationSec)
                    putDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC, t.startOffsetSec)
                }
                MediaItem.Builder()
                    .setUri(t.url)
                    .setMediaId("${pb.sessionId}_${t.index}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(pb.title)
                            .setArtist(pb.author)
                            .setArtworkUri(artwork)
                            .setExtras(extras)
                            .build(),
                    )
                    .build()
            }
            c.setMediaItems(items)
            c.prepare()
            val startBookMs = when {
                startOverrideSec != null -> (startOverrideSec * 1000).toLong()
                fromStart -> 0L
                else -> (pb.startPositionSec * 1000).toLong()
            }
            if (startBookMs > 0) seekBook(c, startBookMs)
            c.playWhenReady = true
        }
    }

    /** Pozice v čase CELÉ knihy = offset aktuálního souboru + pozice v něm. */
    private fun bookPosMs(c: MediaController): Long {
        val offSec = c.currentMediaItem?.mediaMetadata?.extras
            ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0
        return (offSec * 1000).toLong() + c.currentPosition.coerceAtLeast(0L)
    }

    /** Skok na pozici v čase celé knihy — najde správný soubor a posun v něm. */
    private fun seekBook(c: MediaController, bookMs: Long) {
        val target = bookMs.coerceAtLeast(0L)
        val count = c.mediaItemCount
        if (count <= 1) { c.seekTo(target); return }
        var idx = 0
        for (i in 0 until count) {
            val offMs = ((c.getMediaItemAt(i).mediaMetadata.extras
                ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
            if (offMs <= target + 1) idx = i else break
        }
        val idxOffMs = ((c.getMediaItemAt(idx).mediaMetadata.extras
            ?.getDouble(AudiobookPlayerService.KEY_TRACK_OFFSET_SEC) ?: 0.0) * 1000).toLong()
        c.seekTo(idx, (target - idxOffMs).coerceAtLeast(0L))
    }

    fun playPause() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    /** [ms] je v čase celé knihy. */
    fun seekTo(ms: Long) = withController { seekBook(it, ms) }

    fun seekBy(deltaMs: Long) = withController { c -> seekBook(c, bookPosMs(c) + deltaMs) }

    fun nextChapter() = withController { c ->
        val posSec = bookPosMs(c) / 1000.0
        val next = _chapters.value.firstOrNull { it.startSec > posSec + 1.0 }
        if (next != null) seekBook(c, (next.startSec * 1000).toLong())
    }

    fun prevChapter() = withController { c ->
        val posSec = bookPosMs(c) / 1000.0
        // do začátku aktuální kapitoly; pokud jsme < 3 s v ní, skoč na předchozí
        val current = _chapters.value.lastOrNull { it.startSec <= posSec }
        val target = if (current != null && posSec - current.startSec > 3.0) {
            current.startSec
        } else {
            _chapters.value.lastOrNull { it.startSec < (current?.startSec ?: posSec) - 0.1 }?.startSec ?: 0.0
        }
        seekBook(c, (target * 1000).toLong())
    }

    /** Skok na konkrétní kapitolu (čas celé knihy). Pro seznam kapitol v playeru. */
    fun seekToChapter(startSec: Double) = withController { seekBook(it, (startSec * 1000).toLong()) }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed.coerceIn(0.5f, 3f)) }

    /** Sleep timer v minutách; null = zrušit. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepMinutesLeft = null) }
            return
        }
        sleepJob = scope.launch {
            var left = minutes
            while (left > 0) {
                _state.update { it.copy(sleepMinutesLeft = left) }
                delay(60_000)
                left--
            }
            _state.update { it.copy(sleepMinutesLeft = null) }
            controller?.pause()
        }
    }

    // ──────────────────────────── Fronta epizod ────────────────────────────

    /** Přidá epizodu do fronty. [atFront] = hned po aktuální, jinak na konec. Bez duplicit. */
    fun enqueue(episode: QueuedEpisode, atFront: Boolean) {
        _queue.update { q ->
            val without = q.filterNot { it.episodeId == episode.episodeId }
            if (atFront) listOf(episode) + without else without + episode
        }
    }

    fun removeFromQueue(episodeId: String) {
        _queue.update { q -> q.filterNot { it.episodeId == episodeId } }
    }

    fun clearQueue() { _queue.value = emptyList() }

    /**
     * Konec přehrávání. U podcast epizody: označ ji jako dokončenou na serveru a přehraj další
     * z fronty (pokud je). U audioknihy ([currentEpisode] == null) neděláme nic.
     */
    private fun onPlaybackEnded() {
        if (advancing) return
        val ended = currentEpisode ?: return
        advancing = true
        currentEpisode = null
        scope.launch { repo.setEpisodeFinished(ended.itemId, ended.episodeId, true) }
        val next = _queue.value.firstOrNull()
        if (next == null) { advancing = false; return }
        _queue.update { it.drop(1) }
        scope.launch {
            runCatching { repo.startEpisodePlayback(next.itemId, next.episodeId) }
                .onSuccess { pb -> playBook(pb, fromStart = false, episode = next) }
                .onFailure { Timber.w(it, "[Listen] auto-advance fronty selhal") }
            advancing = false
        }
    }
}
