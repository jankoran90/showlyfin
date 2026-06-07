package com.github.jankoran90.showlyfin.feature.playback.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.github.jankoran90.showlyfin.feature.playback.PlaybackCache
import com.github.jankoran90.showlyfin.feature.playback.PlaybackViewModel
import kotlinx.coroutines.delay

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(
    itemId: String = "",
    positionMs: Long = 0L,
    externalUrl: String? = null,
    externalTitle: String = "",
    onBack: () -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId, externalUrl) {
        if (externalUrl != null) viewModel.loadExternal(externalUrl, externalTitle)
        else viewModel.load(itemId, positionMs)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    val exoPlayer = remember {
        // Disk-backed cache + generous buffer → smooth streaming over jittery links (RD/Jellyfin).
        val upstream = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            // Addon/debrid servery (AIOStreams apod.) bez browser UA často vrátí HTML/redirect
            // místo videa → ExoPlayer pak hlásí ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED.
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        // Disk CacheDataSource odstraněn — způsoboval ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        // u velkých souborů (3GB mkv). Velký in-memory buffer níže pokrývá jitter (RD/Jellyfin).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 300_000, 5_000, 10_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(upstream))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var resumeDecided by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                timber.log.Timber.e(error, "[Playback] ExoPlayer error code=${error.errorCodeName} cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")
                playerError = error.errorCodeName
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            view.keepScreenOn = false
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        timber.log.Timber.i("[Playback] setMediaItem external=${externalUrl != null} url=${url.take(90)}")
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        if (state.resumePositionMs <= 0L) {
            resumeDecided = true
            exoPlayer.playWhenReady = true
        }
    }

    // position/duration poll
    LaunchedEffect(resumeDecided) {
        if (!resumeDecided) return@LaunchedEffect
        while (true) {
            position = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    // auto-hide controls
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }
    // grab focus for D-pad once playing
    LaunchedEffect(resumeDecided) {
        if (resumeDecided) runCatching { focusRequester.requestFocus() }
    }

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error!!, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Zpět") }
            }
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                playerError?.let { err ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Tento stream nejde přehrát v aplikaci",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            err,
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (externalUrl != null) {
                            Button(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        },
                                    )
                                }
                            }) { Text("Otevřít v jiné aplikaci") }
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = onBack) { Text("Zpět") }
                    }
                }

                if (!resumeDecided && state.resumePositionMs > 0L) {
                    // Resume vs start-over chooser
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.title.isNotBlank()) {
                            Text(state.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = {
                                exoPlayer.seekTo(state.resumePositionMs)
                                exoPlayer.playWhenReady = true
                                resumeDecided = true
                            }) { Text("Pokračovat (${fmtTime(state.resumePositionMs)})") }
                            Button(onClick = {
                                exoPlayer.seekTo(0L)
                                exoPlayer.playWhenReady = true
                                resumeDecided = true
                            }) { Text("Od začátku") }
                        }
                    }
                } else {
                    // D-pad capture layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (ev.key) {
                                    Key.DirectionLeft -> {
                                        exoPlayer.seekBack(); controlsVisible = true; true
                                    }
                                    Key.DirectionRight -> {
                                        exoPlayer.seekForward(); controlsVisible = true; true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        controlsVisible = true; true
                                    }
                                    Key.DirectionUp, Key.DirectionDown -> {
                                        controlsVisible = true; true
                                    }
                                    else -> false
                                }
                            },
                    )

                    if (controlsVisible) {
                        // Title (hides with controls)
                        if (state.title.isNotBlank()) {
                            Text(
                                text = state.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        // Bottom bar: progress + time + state
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { if (duration > 0) (position.toFloat() / duration) else 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${if (isPlaying) "▶" else "⏸"}  ${fmtTime(position)} / ${fmtTime(duration)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "◀ ▶ převíjení · OK pauza",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
