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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Most mezi UI a [AudiobookPlayerService]. Drží jeden [MediaController] (sdílený fullscreen
 * playerem i mini-playerem), publikuje [PlayerState] (poll 500 ms + Player listener) a
 * forwarduje příkazy. Kapitoly aktuální knihy drží zvlášť pro skip ◀▶ a název kapitoly.
 */
@Singleton
class AudiobookPlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
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

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()
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
        val pos = c.currentPosition.coerceAtLeast(0L)
        val chapterTitle = _chapters.value.firstOrNull {
            val p = pos / 1000.0
            p >= it.startSec && p < it.endSec
        }?.title
        _state.update {
            it.copy(
                isActive = c.mediaItemCount > 0,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                title = c.mediaMetadata.title?.toString() ?: it.title,
                author = c.mediaMetadata.artist?.toString() ?: it.author,
                coverUrl = c.mediaMetadata.artworkUri?.toString() ?: it.coverUrl,
                positionMs = pos,
                durationMs = c.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                speed = c.playbackParameters.speed,
                currentChapterTitle = chapterTitle,
            )
        }
    }

    fun playBook(pb: AbsPlayback, fromStart: Boolean) {
        _chapters.value = pb.chapters
        _state.update {
            it.copy(
                isActive = true, title = pb.title, author = pb.author, coverUrl = pb.coverUrl,
                durationMs = (pb.durationSec * 1000).toLong(),
            )
        }
        withController { c ->
            val extras = Bundle().apply {
                putString(AudiobookPlayerService.KEY_SESSION_ID, pb.sessionId)
                putDouble(AudiobookPlayerService.KEY_DURATION_SEC, pb.durationSec)
            }
            val item = MediaItem.Builder()
                .setUri(pb.streamUrl)
                .setMediaId(pb.sessionId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(pb.title)
                        .setArtist(pb.author)
                        .setArtworkUri(pb.coverUrl?.let(Uri::parse))
                        .setExtras(extras)
                        .build(),
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            val startMs = if (fromStart) 0L else (pb.startPositionSec * 1000).toLong()
            if (startMs > 0) c.seekTo(startMs)
            c.playWhenReady = true
        }
    }

    fun playPause() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms.coerceAtLeast(0L)) }

    fun seekBy(deltaMs: Long) = withController { c ->
        val target = (c.currentPosition + deltaMs).coerceIn(0L, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        c.seekTo(target)
    }

    fun nextChapter() = withController { c ->
        val posSec = c.currentPosition / 1000.0
        val next = _chapters.value.firstOrNull { it.startSec > posSec + 1.0 }
        if (next != null) c.seekTo((next.startSec * 1000).toLong())
    }

    fun prevChapter() = withController { c ->
        val posSec = c.currentPosition / 1000.0
        // do začátku aktuální kapitoly; pokud jsme < 3 s v ní, skoč na předchozí
        val current = _chapters.value.lastOrNull { it.startSec <= posSec }
        val target = if (current != null && posSec - current.startSec > 3.0) {
            current.startSec
        } else {
            _chapters.value.lastOrNull { it.startSec < (current?.startSec ?: posSec) - 0.1 }?.startSec ?: 0.0
        }
        c.seekTo((target * 1000).toLong())
    }

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
}
