package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Dvouminutová ukázka knihy z Audioteky přímo na kartě (user 2026-11-18: „chci ukázku přehrávat u nás").
 * Stav žije na úrovni obrazovky (ne v položce seznamu), takže odrolování ukázku neutne;
 * odchod appky do pozadí ji pozastaví, opuštění karty uvolní přehrávač. Audio focus pozastaví hlavní přehrávač.
 */
internal class AtSampleState(private val context: Context) {
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var leftSec by mutableStateOf<Long?>(null); private set
    private var player: ExoPlayer? = null
    private var url: String? = null

    fun toggle(newUrl: String) {
        failed = false
        val p = player ?: build().also { player = it }
        if (url != newUrl) {
            url = newUrl
            progress = 0f
            p.setMediaItem(MediaItem.fromUri(newUrl))
            p.prepare()
        } else if (p.playerError != null) {
            p.prepare()
        }
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() { player?.pause() }

    fun tick() {
        val p = player ?: return
        val dur = p.duration.takeIf { it > 0 } ?: return
        progress = (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
        leftSec = (dur - p.currentPosition).coerceAtLeast(0) / 1000
    }

    fun release() {
        player?.release()
        player = null
        url = null
        playing = false
    }

    private fun build(): ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true,
        )
        p.setHandleAudioBecomingNoisy(true)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) { p.pause(); p.seekTo(0); progress = 0f; leftSec = null }
            }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        })
    }
}

/** Stav ukázky pro celou obrazovku detailu: pauza při odchodu do pozadí, uvolnění při opuštění karty. */
@Composable
internal fun rememberAtSampleState(): AtSampleState {
    val context = LocalContext.current.applicationContext
    val state = remember { AtSampleState(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) state.pause() }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            state.release()
        }
    }
    LaunchedEffect(state.playing) {
        while (state.playing) { state.tick(); delay(250) }
    }
    return state
}

@Composable
internal fun AudiotekaSamplePlayer(url: String, state: AtSampleState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = { state.toggle(url) }, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    state.buffering && !state.playing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.playing -> Icon(Icons.Rounded.Pause, contentDescription = null)
                    else -> Icon(Icons.Rounded.Headphones, contentDescription = null)
                }
                val left = state.leftSec
                val label = when {
                    state.failed -> "  Ukázku se nepodařilo přehrát — zkusit znovu"
                    state.playing -> "  Pozastavit ukázku" + (left?.let { " · zbývá ${it / 60}:%02d".format(it % 60) } ?: "")
                    state.progress > 0f -> "  Pokračovat v ukázce"
                    else -> "  Přehrát ukázku"
                }
                Text(label)
            }
        }
        if (state.progress > 0f) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
