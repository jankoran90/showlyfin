package com.github.jankoran90.showlyfin.widget

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.jankoran90.showlyfin.feature.listen.service.AudiobookPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * RELAY — most mezi „Poslouchej" widgetem a media3 `AudiobookPlayerService`.
 * Pro každou akci/čtení stavu naváže krátkodobý `MediaController`, provede operaci a uvolní ho.
 * MediaController musí žít na hlavním vlákně → vše běží přes Dispatchers.Main.
 */
object ListenRemote {

    private const val SEEK_BACK_MS = 15_000L
    private const val SEEK_FWD_MS = 30_000L

    data class State(
        val connected: Boolean,
        val title: String?,
        val subtitle: String?,
        val isPlaying: Boolean,
    ) {
        companion object {
            val EMPTY = State(connected = false, title = null, subtitle = null, isPlaying = false)
        }
    }

    suspend fun load(context: Context): State = withController(context) { c ->
        val md = c.mediaMetadata
        val hasItem = c.currentMediaItem != null || md.title != null
        State(
            connected = hasItem,
            title = md.title?.toString(),
            subtitle = (md.artist ?: md.albumTitle ?: md.subtitle)?.toString(),
            isPlaying = c.isPlaying,
        )
    } ?: State.EMPTY

    suspend fun playPause(context: Context) {
        withController(context) { c -> if (c.isPlaying) c.pause() else c.play() }
    }

    suspend fun rewind(context: Context) {
        withController(context) { c -> c.seekTo((c.currentPosition - SEEK_BACK_MS).coerceAtLeast(0L)) }
    }

    suspend fun forward(context: Context) {
        withController(context) { c ->
            val duration = c.duration
            var target = c.currentPosition + SEEK_FWD_MS
            if (duration > 0L) target = target.coerceAtMost(duration)
            c.seekTo(target)
        }
    }

    private suspend fun <T> withController(context: Context, block: (MediaController) -> T): T? =
        withContext(Dispatchers.Main.immediate) {
            val controller = awaitController(context) ?: return@withContext null
            try {
                block(controller)
            } finally {
                controller.release()
            }
        }

    private suspend fun awaitController(context: Context): MediaController? =
        suspendCancellableCoroutine { cont ->
            val token = SessionToken(
                context,
                ComponentName(context, AudiobookPlayerService::class.java),
            )
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                { cont.resume(runCatching { future.get() }.getOrNull()) },
                ContextCompat.getMainExecutor(context),
            )
            cont.invokeOnCancellation {
                runCatching { if (future.isDone) future.get()?.release() else future.cancel(true) }
            }
        }
}
