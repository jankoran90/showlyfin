package com.github.jankoran90.showlyfin.feature.playback.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.feature.playback.PlaybackViewModel
import com.github.jankoran90.showlyfin.feature.playback.SubtitleStyle
import kotlinx.coroutines.delay

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// Paleta barev titulků — preferované jantarové/žluté odstíny + bílá.
private val SUBTITLE_COLORS = listOf(
    0xFFFFBF00.toInt(), // amber
    0xFFFFC107.toInt(), // gold
    0xFFFFD54F.toInt(), // light amber
    0xFFFFEB3B.toInt(), // yellow
    0xFFFFF176.toInt(), // light yellow
    0xFFFFFFFF.toInt(), // white
)

@OptIn(UnstableApi::class)
private fun buildMediaItem(url: String, subUri: String?): MediaItem {
    val b = MediaItem.Builder().setUri(url)
    if (!subUri.isNullOrBlank()) {
        val sub = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUri))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("cs")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        b.setSubtitleConfigurations(listOf(sub))
    }
    return b.build()
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(
    itemId: String = "",
    positionMs: Long = 0L,
    externalUrl: String? = null,
    externalTitle: String = "",
    subtitleQuery: SubtitleQuery? = null,
    onBack: () -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId, externalUrl) {
        if (externalUrl != null) viewModel.loadExternal(externalUrl, externalTitle, subtitleQuery)
        else viewModel.load(itemId, positionMs)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    val exoPlayer = remember {
        val upstream = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 300_000, 5_000, 10_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(upstream))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguage("cs")
                    .build()
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var resumeDecided by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var subtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }
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

    // Initial media prepare (subtitle included if už dostupný)
    LaunchedEffect(state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        timber.log.Timber.i("[Playback] setMediaItem external=${externalUrl != null} url=${url.take(90)}")
        exoPlayer.setMediaItem(buildMediaItem(url, state.subtitleFileUri))
        exoPlayer.prepare()
        if (state.resumePositionMs <= 0L) {
            resumeDecided = true
            exoPlayer.playWhenReady = true
        }
    }

    // Titulky se změnily (jiná stopa / offset) PO inicializaci → přestav MediaItem, zachovej pozici.
    LaunchedEffect(state.subtitleFileUri) {
        if (!resumeDecided) return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        val pos = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.playWhenReady
        exoPlayer.setMediaItem(buildMediaItem(url, state.subtitleFileUri))
        exoPlayer.prepare()
        exoPlayer.seekTo(pos)
        exoPlayer.playWhenReady = wasPlaying
    }

    // Aplikace stylu titulků na SubtitleView
    LaunchedEffect(state.subtitleStyle, subtitleViewRef) {
        subtitleViewRef?.applyStyle(state.subtitleStyle)
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
    // auto-hide controls (ne když je otevřený panel titulků)
    LaunchedEffect(controlsVisible, isPlaying, showSubtitleMenu) {
        if (controlsVisible && isPlaying && !showSubtitleMenu) {
            delay(4000)
            controlsVisible = false
        }
    }
    LaunchedEffect(resumeDecided) {
        if (resumeDecided) runCatching { focusRequester.requestFocus() }
    }

    BackHandler {
        if (showSubtitleMenu) showSubtitleMenu = false else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Telefon: tap kdekoli přepne ovládací lištu.
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (showSubtitleMenu) showSubtitleMenu = false
                    else controlsVisible = !controlsVisible
                })
            },
    ) {
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
                            subtitleViewRef = subtitleView
                            subtitleView?.applyStyle(state.subtitleStyle)
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
                        Text("Tento stream nejde přehrát v aplikaci", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(err, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
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
                    // D-pad capture layer (TV)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (ev.key) {
                                    Key.DirectionLeft -> { exoPlayer.seekBack(); controlsVisible = true; true }
                                    Key.DirectionRight -> { exoPlayer.seekForward(); controlsVisible = true; true }
                                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        controlsVisible = true; true
                                    }
                                    Key.DirectionUp, Key.DirectionDown -> { controlsVisible = true; true }
                                    else -> false
                                }
                            },
                    )

                    if (controlsVisible) {
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
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${if (isPlaying) "▶" else "⏸"}  ${fmtTime(position)} / ${fmtTime(duration)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // CC ikona titulků
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.subtitlesLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Text(
                                        text = "CC",
                                        color = if (state.selectedSubtitleIndex >= 0) Color(state.subtitleStyle.colorArgb) else Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .border(
                                                1.dp,
                                                if (state.selectedSubtitleIndex >= 0) Color(state.subtitleStyle.colorArgb) else Color.White.copy(alpha = 0.5f),
                                                RoundedCornerShape(4.dp),
                                            )
                                            .clickable { showSubtitleMenu = true; controlsVisible = true }
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (showSubtitleMenu) {
                        SubtitleSettingsPanel(
                            state = state,
                            onSelect = { viewModel.selectSubtitle(it) },
                            onFontScale = { viewModel.setFontScale(it) },
                            onColor = { viewModel.setColor(it) },
                            onPosition = { viewModel.setBottomPadding(it) },
                            onNudge = { viewModel.nudgeOffset(it) },
                            onClose = { showSubtitleMenu = false },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun SubtitleView.applyStyle(style: SubtitleStyle) {
    setStyle(
        CaptionStyleCompat(
            style.colorArgb,
            0x00000000,                       // pozadí průhledné
            0x00000000,                       // okno průhledné
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            0xFF000000.toInt(),               // černý obrys pro čitelnost
            null,
        ),
    )
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.fontScale)
    setBottomPaddingFraction(style.bottomPaddingFraction)
}

@Composable
private fun SubtitleSettingsPanel(
    state: com.github.jankoran90.showlyfin.feature.playback.PlaybackUiState,
    onSelect: (Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onColor: (Int) -> Unit,
    onPosition: (Float) -> Unit,
    onNudge: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(360.dp)
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Titulky", color = Color.White, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose) { Text("Hotovo") }
        }

        // Stopa
        Text("Stopa (CZ)", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        SubtitleRow(
            label = "Vypnuto",
            selected = state.selectedSubtitleIndex < 0,
            onClick = { onSelect(-1) },
        )
        state.subtitleCandidates.forEachIndexed { i, c ->
            val rel = c.release.ifBlank { c.title }
            val meta = buildString {
                if (c.imdbMatch) append("✓ ")
                if (c.fps > 0) append("${c.fps} fps · ")
                append("${c.downloads}×")
            }
            SubtitleRow(
                label = rel,
                sub = meta,
                selected = state.selectedSubtitleIndex == i,
                onClick = { onSelect(i) },
            )
        }
        if (state.subtitleRuntimeOk == "0" && state.selectedSubtitleIndex >= 0) {
            Text(
                "⚠ Délka titulků nesedí na film — zkus jinou stopu nebo posun.",
                color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        // Velikost
        StepperRow("Velikost", "${(state.subtitleStyle.fontScale * 100).toInt()} %",
            onMinus = { onFontScale(state.subtitleStyle.fontScale - 0.1f) },
            onPlus = { onFontScale(state.subtitleStyle.fontScale + 0.1f) })

        // Pozice (výška odspodu)
        StepperRow("Pozice", "${(state.subtitleStyle.bottomPaddingFraction * 100).toInt()} %",
            onMinus = { onPosition(state.subtitleStyle.bottomPaddingFraction - 0.04f) },
            onPlus = { onPosition(state.subtitleStyle.bottomPaddingFraction + 0.04f) })

        // Posun (synchronizace)
        StepperRow("Posun", "%+.1f s".format(state.subtitleStyle.offsetMs / 1000.0),
            onMinus = { onNudge(-500L) },
            onPlus = { onNudge(500L) })

        Spacer(Modifier.height(12.dp))
        // Barva
        Text("Barva", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SUBTITLE_COLORS.forEach { argb ->
                val sel = state.subtitleStyle.colorArgb == argb
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(argb), CircleShape)
                        .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { onColor(argb) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleRow(label: String, sub: String = "", selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "●" else "○", color = if (selected) Color(0xFFFFBF00) else Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) { Text("−", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = onPlus) { Text("+", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        }
    }
}
