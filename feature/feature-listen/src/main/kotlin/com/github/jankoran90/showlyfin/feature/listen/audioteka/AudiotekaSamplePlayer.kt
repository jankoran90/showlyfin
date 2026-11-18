package com.github.jankoran90.showlyfin.feature.listen.audioteka

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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Dvouminutová ukázka knihy z Audioteky přímo na kartě (user 2026-11-18: „chci ukázku přehrávat u nás").
 * Vlastní krátkodobý ExoPlayer jen pro tuhle obrazovku; audio focus pozastaví hlavní přehrávač Slova,
 * po odchodu z karty se uvolní.
 */
@Composable
internal fun AudiotekaSamplePlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var leftSec by remember { mutableStateOf<Long?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { p ->
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true,
        )
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) { p.pause(); p.seekTo(0); progress = 0f }
            }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        player = p
    }

    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p ->
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                if (dur > 0) {
                    progress = (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
                    leftSec = (dur - p.currentPosition).coerceAtLeast(0) / 1000
                }
            }
            delay(250)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(
            onClick = {
                failed = false
                val p = ensurePlayer()
                if (p.playerError != null) p.prepare()
                if (p.isPlaying) p.pause() else p.play()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    buffering && !playing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    playing -> Icon(Icons.Rounded.Pause, contentDescription = null)
                    else -> Icon(Icons.Rounded.Headphones, contentDescription = null)
                }
                val label = when {
                    failed -> "  Ukázku se nepodařilo přehrát — zkusit znovu"
                    playing -> "  Pozastavit ukázku" + (leftSec?.let { " · zbývá ${it / 60}:%02d".format(it % 60) } ?: "")
                    progress > 0f -> "  Pokračovat v ukázce"
                    else -> "  Přehrát ukázku"
                }
                Text(label)
            }
        }
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
