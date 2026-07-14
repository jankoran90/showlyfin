package com.github.jankoran90.showlyfin.feature.playback.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.feature.playback.service.MoviePlayerService
import com.google.common.util.concurrent.MoreExecutors
import com.github.jankoran90.showlyfin.feature.playback.PlaybackViewModel
import com.github.jankoran90.showlyfin.feature.playback.SubtitleEdge
import com.github.jankoran90.showlyfin.feature.playback.SubtitleStyle
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Aktuální jas okna jako 0..1; pokud okno jas neřídí (−1), spadne na systémový jas. */
private fun currentBrightness(activity: Activity?, context: Context): Float {
    val cur = activity?.window?.attributes?.screenBrightness ?: -1f
    if (cur in 0f..1f) return cur
    return try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

internal fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// Paleta barev titulků — preferované jantarové/žluté odstíny + bílá.
private val SUBTITLE_EDGES = listOf(
    SubtitleEdge.OUTLINE to "Obrys",
    SubtitleEdge.SHADOW to "Stín",
    SubtitleEdge.BOX to "Podklad",
    SubtitleEdge.NONE to "Bez",
)

private val SUBTITLE_COLORS = listOf(
    0xFFFFBF00.toInt(), // amber
    0xFFFFC107.toInt(), // gold
    0xFFFFD54F.toInt(), // light amber
    0xFFFFEB3B.toInt(), // yellow
    0xFFFFF176.toInt(), // light yellow
    0xFFFFFFFF.toInt(), // white
)

// Titulky NErenderuje ExoPlayer — kreslíme je vlastním overlayem (viz SubtitleOverlay),
// aby šel posun/přepnutí stopy aplikovat okamžitě bez re-prepare videa (žádný rebuffer).
private fun buildMediaItem(url: String, title: String, posterUrl: String?): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        // MARQUEE: název + plakát do systémové notifikace / na zámek (MediaController je předá službě;
        // remote plakát natáhne média notifikace sama přes svůj bitmap loader).
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title.ifBlank { "Přehrávání" })
                .setArtworkUri(posterUrl?.let(Uri::parse))
                .build(),
        )
        // CLARITY (SHW-75): HLS podcast proxy (/api/yt/hls/…) nemá příponu .m3u8 → ExoPlayer by typ
        // neuhodl a hrál to jako progresivní. Vynutíme HLS MediaSource (itag 95/96 = 720p/1080p+audio).
        // KAVKA (SHW-76): ČT video proxy (/api/ctv/manifest/…mpd) = DASH (o2tv CDN, až 1080p) → vynutíme DASH.
        .apply {
            when {
                url.contains("/api/yt/hls/") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                url.contains("/api/ctv/manifest/") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }
        .build()

@OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(
    itemId: String = "",
    positionMs: Long = 0L,
    externalUrl: String? = null,
    externalTitle: String = "",
    subtitleQuery: SubtitleQuery? = null,
    // MARQUEE: plakát do notifikace u externích streamů (Stremio/RD); Jellyfin si ho odvodí ViewModel.
    externalPosterUrl: String? = null,
    // NOMAD (SHW-60): offline přehrání staženého souboru z telefonu (file://) + lokální .srt + resume klíč.
    localVideoPath: String? = null,
    localSubtitlePath: String? = null,
    localPosterPath: String? = null,
    offlineKey: String = "",
    // REWIND (SHW-68): klíč lokálního resume pro JF-item VIDEO (NaVýbornou video → sdílený s RSS epizodou).
    resumeKey: String? = null,
    onBack: () -> Unit,
    // CASCADE Fáze 4: externí stream (Stremio/RD) selhal v ExoPlayeru → zkus dalšího kandidáta
    // místo zobrazení chyby. Volá se jen u externalUrl (Jellyfin přehrávání kandidáty nemá).
    onPlaybackFailed: ((String) -> Unit)? = null,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId, externalUrl, localVideoPath) {
        if (localVideoPath != null) viewModel.loadLocal(localVideoPath, localSubtitlePath, externalTitle, offlineKey, localPosterPath)
        else if (externalUrl != null) viewModel.loadExternal(externalUrl, externalTitle, subtitleQuery, externalPosterUrl)
        else viewModel.load(itemId, positionMs, resumeKey, externalTitle)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    // MARQUEE (SHW-57): přehrávač drží MoviePlayerService (MediaSessionService) → ovládání z
    // notifikace/zámku/sluchátek (stejně jako audioknihy). Napojíme se přes MediaController, který
    // se připojuje asynchronně — než je hotový, je `controller == null` a efekty čekají.
    // Stavbu ExoPlayeru (NextLib FFmpeg dekodér, dataSource s UA, loadControl, vypnutý text renderer)
    // dělá služba v onCreate; tady jen řídíme přes Player rozhraní controlleru.
    var controller by remember { mutableStateOf<MediaController?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var resumeDecided by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    // CASCADE Fáze 4: hlásíme selhání externího streamu jen jednou (onPlayerError může přijít víckrát).
    var failureReported by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var currentSubtitle by remember { mutableStateOf<String?>(null) }
    // Krátký popis zdroje do lišty: rozlišení · video kodek · audio kodek · kanály.
    var sourceMeta by remember { mutableStateOf("") }
    // TEMPO (SHW-49): výběr zvukové stopy v lokálním přehrávači. Telefon (ExoPlayer bez FFmpeg dekodéru)
    // neumí DTS/DTS-HD/TrueHD → `isTrackSupported` říká autoritativně, co reálně hraje. Když je vybraná
    // stopa nepodporovaná a existuje podporovaná (např. AC3 vedle DTS-HD), přepneme; když žádná, hlásíme.
    var showAudioMenu by remember { mutableStateOf(false) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var audioNotice by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    // TV: panel titulků je touch-only, na TV ho zpřístupníme D-padem (otevření Up/Menu + fokus do panelu).
    val isTv = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val menuFocusRequester = remember { FocusRequester() }
    // TENFOOT F2c: fokus přistane na ⏯ v TV transport liště; interactionTick resetuje auto-hide při navigaci.
    val barFocusRequester = remember { FocusRequester() }
    var interactionTick by remember { mutableStateOf(0) }

    // Seekbar (ruční přesouvání) + gesta jas/hlasitost
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var gestureIndicator by remember { mutableStateOf<String?>(null) }
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    // Čteme aktuální hodnoty uvnitř gesture detektoru bez restartu pointerInput.
    val controlsVisibleNow = rememberUpdatedState(controlsVisible)
    val menuOpenNow = rememberUpdatedState(showSubtitleMenu)

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val token = SessionToken(context, ComponentName(context, MoviePlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                timber.log.Timber.e(error, "[Playback] ExoPlayer error code=${error.errorCodeName} cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message}")
                // CASCADE Fáze 4: u externího streamu zkus dalšího kandidáta (auto-advance) místo chyby.
                if (externalUrl != null && onPlaybackFailed != null && !failureReported) {
                    failureReported = true
                    onPlaybackFailed(error.errorCodeName)
                } else {
                    playerError = error.errorCodeName
                }
            }
        }
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { c ->
                c.addListener(listener)
                isPlaying = c.isPlaying
                controller = c
            }
        }, MoreExecutors.directExecutor())
        onDispose {
            // PICKUP: ulož finální pozici externího streamu. Přehrávač NEuvolňujeme — drží ho služba,
            // takže navigace jinam v appce hraje dál na pozadí (controller jen odpojíme). Skutečné
            // ukončení (Zpět) řeší exitPlayback() → ACTION_STOP službě.
            controller?.let { c ->
                if (externalUrl != null || localVideoPath != null) viewModel.saveExternalPosition(c.currentPosition, c.duration)
                else viewModel.saveVideoPosition(c.currentPosition, c.duration) // REWIND: JF-item video lokálně
                c.removeListener(listener)
            }
            view.keepScreenOn = false
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    // Immersive fullscreen: skryj status bar (nahoře) i navigační/gesture lištu (dole) po celou dobu
    // přehrávání, swipem od kraje se dočasně ukážou. Při odchodu obnov.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Media prepare — jen video. Titulky kreslíme sami (overlay), takže se MediaItem
    // už nikdy nepřestavuje kvůli titulkům (žádný rebuffer při posunu / přepnutí stopy).
    LaunchedEffect(state.streamUrl, controller) {
        val url = state.streamUrl ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        timber.log.Timber.i("[Playback] setMediaItem external=${externalUrl != null} url=${url.take(90)}")
        c.setMediaItem(buildMediaItem(url, state.title, state.posterUrl))
        c.prepare()
        if (state.resumePositionMs <= 0L) {
            resumeDecided = true
            c.playWhenReady = true
        }
    }

    // position/duration poll
    LaunchedEffect(resumeDecided, controller) {
        if (!resumeDecided) return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        while (true) {
            position = c.currentPosition
            duration = c.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    // Aktivní titulek — vlastní render. Offset se aplikuje live (t = pozice − offset).
    LaunchedEffect(resumeDecided, controller) {
        if (!resumeDecided) return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        while (true) {
            val s = viewModel.state.value
            currentSubtitle = if (s.selectedSubtitleIndex >= 0 && s.subtitleCues.isNotEmpty()) {
                val t = c.currentPosition - s.subtitleStyle.offsetMs
                s.subtitleCues.firstOrNull { t in it.startMs..it.endMs }?.text
            } else {
                null
            }
            delay(100)
        }
    }
    // PICKUP/REWIND: průběžně ukládej pozici pro pozdější „Pokračovat" — externí/offline streamy lokálně
    // přes saveExternalPosition; JF-item VIDEO přes saveVideoPosition (showlyfin nereportuje JF progress
    // na server → resume videa děláme lokálně; no-op u filmu bez resumeKey). Save i v onDispose.
    LaunchedEffect(resumeDecided, externalUrl, localVideoPath, controller) {
        if (!resumeDecided) return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        while (true) {
            delay(5000)
            if (externalUrl != null || localVideoPath != null) viewModel.saveExternalPosition(c.currentPosition, c.duration)
            else viewModel.saveVideoPosition(c.currentPosition, c.duration)
        }
    }
    // auto-hide controls (ne když je otevřený panel titulků/zvuku). TENFOOT F2c: timeout konfigurovatelný
    // (state.controlsHideSec, 0 = nikdy neskrývat); interactionTick resetuje odpočet při navigaci v liště.
    LaunchedEffect(controlsVisible, isPlaying, showSubtitleMenu, showAudioMenu, interactionTick) {
        val hideMs = state.controlsHideSec * 1000L
        if (controlsVisible && isPlaying && !showSubtitleMenu && !showAudioMenu && hideMs > 0L) {
            delay(hideMs)
            controlsVisible = false
        }
    }
    // skryj gesture indikátor (jas/hlasitost) krátce po poslední změně
    LaunchedEffect(gestureIndicator) {
        if (gestureIndicator != null) { delay(800); gestureIndicator = null }
    }
    LaunchedEffect(resumeDecided) {
        if (resumeDecided && !isTv) runCatching { focusRequester.requestFocus() }
    }
    // TV: směruj D-pad fokus dle stavu — panel (titulky/zvuk) > transport lišta (⏯) > capture layer.
    // TENFOOT F2c: když je lišta viditelná, fokus patří jí (ne capture vrstvě); po skrytí se vrátí zpět.
    LaunchedEffect(showSubtitleMenu, showAudioMenu, controlsVisible, resumeDecided) {
        if (!isTv || !resumeDecided) return@LaunchedEffect
        delay(50)
        runCatching {
            when {
                showSubtitleMenu || showAudioMenu -> menuFocusRequester.requestFocus()
                controlsVisible -> barFocusRequester.requestFocus()
                else -> focusRequester.requestFocus()
            }
        }
    }

    // TEMPO: vyber audio stopu (override) — telefon ji pak hraje, pokud ji umí dekódovat.
    val applyAudio: (AudioTrackOption) -> Unit = { opt ->
        controller?.let { c ->
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex))
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }
    }
    // TEMPO: sleduj stopy přehrávače → seznam audio stop (+ co telefon umí) + auto-fallback na podporovanou.
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val opts = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { g ->
                        (0 until g.length).map { i ->
                            AudioTrackOption(
                                group = g.mediaTrackGroup,
                                trackIndex = i,
                                label = audioTrackLabel(g.getTrackFormat(i), i),
                                supported = g.isTrackSupported(i),
                                selected = g.isTrackSelected(i),
                            )
                        }
                    }
                audioTracks = opts
                val selected = opts.firstOrNull { it.selected }
                if (opts.isNotEmpty() && (selected == null || !selected.supported)) {
                    val better = opts.firstOrNull { it.supported }
                    if (better != null && !better.selected) {
                        applyAudio(better)
                        audioNotice = "Přepnuto na zvuk: ${better.label} (telefon neumí původní stopu)"
                    } else if (better == null) {
                        audioNotice = "Telefon neumí přehrát zvuk tohoto zdroje (DTS-HD/TrueHD) — zkus přehrát na TV."
                    }
                }
                // Krátké info o zdroji do lišty (vybrané video + audio).
                val vf = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                    .firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
                    ?: tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                        .firstOrNull()?.let { if (it.length > 0) it.getTrackFormat(0) else null }
                val af = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    .firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } }
                sourceMeta = buildSourceMeta(vf, af)
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }
    // TEMPO: hlášku o zvuku po chvíli skryj.
    LaunchedEffect(audioNotice) {
        if (audioNotice != null) { delay(6000); audioNotice = null }
    }

    // MARQUEE: skutečné opuštění přehrávače → ukonči přehrávání a zastav službu (notifikace zmizí).
    // (Navigace jinam BEZ Zpět = controller se uvolní v onDispose, ale služba hraje dál na pozadí.)
    val exitPlayback: () -> Unit = {
        runCatching {
            context.startService(
                Intent(context, MoviePlayerService::class.java).setAction(MoviePlayerService.ACTION_STOP),
            )
        }
        onBack()
    }

    BackHandler {
        when {
            showAudioMenu -> showAudioMenu = false
            showSubtitleMenu -> showSubtitleMenu = false
            else -> exitPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Telefon gesta:
            //  • skryté ovládání → první dotek JEN zobrazí prvky (akce/gesta až když jsou viditelné)
            //  • tap: levá ⅓ = −5 s, pravá ⅓ = +5 s, střed = play/pause
            //  • vertikální swipe: levá ½ = jas (přebíjí systémový), pravá ½ = hlasitost
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val startX = down.position.x
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val slop = viewConfiguration.touchSlop
                    val visibleAtStart = controlsVisibleNow.value
                    val menuAtStart = menuOpenNow.value
                    var moved = false
                    var vertical = false
                    var startBrightness = 0f
                    var startVolume = 0
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!moved) {
                                when {
                                    menuAtStart -> showSubtitleMenu = false
                                    !visibleAtStart -> controlsVisible = true   // první dotek jen odhalí
                                    startX < w / 3f -> { controller?.seekBack(); controlsVisible = true }
                                    startX > w / 3f * 2f -> { controller?.seekForward(); controlsVisible = true }
                                    else -> { controller?.let { if (it.isPlaying) it.pause() else it.play() }; controlsVisible = true }
                                }
                            }
                            break
                        }
                        // při skrytém ovládání / otevřeném panelu žádná gesta (jen tap na release výše)
                        if (menuAtStart || !visibleAtStart) continue
                        val dx = change.position.x - startX
                        val dy = change.position.y - down.position.y
                        if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                            moved = true
                            vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx)
                            if (vertical) {
                                if (startX < w / 2f) startBrightness = currentBrightness(activity, context)
                                else startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            }
                        }
                        if (moved && vertical) {
                            change.consume()
                            val frac = (down.position.y - change.position.y) / h   // nahoru = +
                            if (startX < w / 2f) {
                                val nb = (startBrightness + frac).coerceIn(0.01f, 1f)
                                activity?.let {
                                    val lp = it.window.attributes
                                    lp.screenBrightness = nb
                                    it.window.attributes = lp
                                }
                                gestureIndicator = "☀ ${(nb * 100).toInt()} %"
                            } else {
                                val nv = (startVolume + frac * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                                gestureIndicator = "🔊 ${nv * 100 / maxVolume} %"
                            }
                        }
                    }
                }
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
                Button(onClick = exitPlayback) { Text("Zpět") }
            }
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        }
                    },
                    // controller se připojí asynchronně → přiřaď ho do PlayerView, až je hotový.
                    update = { it.player = controller },
                    modifier = Modifier.fillMaxSize(),
                )

                // Vlastní titulkový overlay (nad videem, pod ovládáním) — render řízen poll smyčkou výše.
                currentSubtitle?.let { sub ->
                    SubtitleOverlay(text = sub, style = state.subtitleStyle)
                }

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
                        Button(onClick = exitPlayback) { Text("Zpět") }
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
                                controller?.seekTo(state.resumePositionMs)
                                controller?.playWhenReady = true
                                resumeDecided = true
                            }) { Text("Pokračovat (${fmtTime(state.resumePositionMs)})") }
                            Button(onClick = {
                                controller?.seekTo(0L)
                                controller?.playWhenReady = true
                                resumeDecided = true
                            }) { Text("Od začátku") }
                        }
                    }
                } else {
                    // D-pad capture layer (TV i telefon s D-padem). Když je otevřený panel titulků,
                    // klávesy NEpožíráme → fokus i navigaci přebírá panel (menuFocusRequester).
                    // Capture layer = rychlé přímé ovládání, když lišta NENÍ vidět (a není otevřený panel).
                    // TENFOOT F2c: na TV se fokus při viditelné liště přesouvá do ní → tady capture vypneme,
                    // ať tlačítka/osu ovládá D-pad. Seek respektuje konfigurovatelný krok (state.seekStepSec).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!showSubtitleMenu && !showAudioMenu && !(isTv && controlsVisible)) Modifier
                                    .focusRequester(focusRequester)
                                    .focusable()
                                    .onKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                        val stepMs = state.seekStepSec * 1000L
                                        when (ev.key) {
                                            Key.DirectionLeft -> {
                                                controller?.let { it.seekTo((it.currentPosition - stepMs).coerceAtLeast(0L)) }
                                                controlsVisible = true; true
                                            }
                                            Key.DirectionRight -> {
                                                controller?.let {
                                                    val d = it.duration
                                                    val t = it.currentPosition + stepMs
                                                    it.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                                                }
                                                controlsVisible = true; true
                                            }
                                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                                                controlsVisible = true; true
                                            }
                                            // Up / Menu = otevři panel titulků (na TV jediná cesta k němu)
                                            Key.DirectionUp, Key.Menu -> { showSubtitleMenu = true; controlsVisible = true; true }
                                            Key.DirectionDown -> { controlsVisible = true; true }
                                            else -> false
                                        }
                                    }
                                else Modifier,
                            ),
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
                        if (isTv) {
                            // TENFOOT F2c: 10-foot transport lišta (fokusovatelná, D-pad scrubbing).
                            TvTransportBar(
                                isPlaying = isPlaying,
                                title = state.title,
                                sourceMeta = sourceMeta,
                                positionMs = position,
                                durationMs = duration,
                                seekStepMs = state.seekStepSec * 1000L,
                                hasAudioChoice = audioTracks.size > 1 || audioTracks.any { !it.supported },
                                audioProblem = audioTracks.none { it.selected && it.supported },
                                subtitleActive = state.selectedSubtitleIndex >= 0,
                                subtitleColor = Color(state.subtitleStyle.colorArgb),
                                playPauseFocusRequester = barFocusRequester,
                                onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                                onSeekTo = { ms -> controller?.seekTo(ms); position = ms },
                                onAudio = { showAudioMenu = true; controlsVisible = true },
                                onSubtitles = { showSubtitleMenu = true; controlsVisible = true },
                                onInteraction = { interactionTick++; controlsVisible = true },
                                onDismiss = { controlsVisible = false },
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        } else {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            Slider(
                                value = if (scrubbing) scrubValue
                                else position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(0f)),
                                onValueChange = { scrubbing = true; scrubValue = it; controlsVisible = true },
                                onValueChangeFinished = {
                                    controller?.seekTo(scrubValue.toLong())
                                    position = scrubValue.toLong()
                                    scrubbing = false
                                },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${if (isPlaying) "▶" else "⏸"}  ${fmtTime(if (scrubbing) scrubValue.toLong() else position)} / ${fmtTime(duration)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // Zvuková stopa + CC ikona titulků
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // TEMPO: výběr audio stopy — ukaž, jen když je z čeho vybírat / něco telefon neumí.
                                    if (audioTracks.size > 1 || audioTracks.any { !it.supported }) {
                                        val audioProblem = audioTracks.none { it.selected && it.supported }
                                        Text(
                                            text = "🔊",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .border(
                                                    1.dp,
                                                    if (audioProblem) Color(0xFFFFB300) else Color.White.copy(alpha = 0.5f),
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .clickable { showAudioMenu = true; controlsVisible = true }
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
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
                    }

                    if (showSubtitleMenu) {
                        SubtitleSettingsPanel(
                            state = state,
                            onSelect = { viewModel.selectSubtitle(it) },
                            onFontScale = { viewModel.setFontScale(it) },
                            onColor = { viewModel.setColor(it) },
                            onPosition = { viewModel.setBottomPadding(it) },
                            onNudge = { viewModel.nudgeOffset(it) },
                            onEdge = { viewModel.setEdge(it) },
                            onEdgeStrength = { viewModel.setEdgeStrength(it) },
                            onTranslateAi = { viewModel.translateSubtitlesAi() },
                            onClose = { showSubtitleMenu = false },
                            firstItemFocusRequester = if (isTv) menuFocusRequester else null,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }

                    // TEMPO: panel výběru zvukové stopy.
                    if (showAudioMenu) {
                        AudioTrackPanel(
                            tracks = audioTracks,
                            onSelect = { applyAudio(it); showAudioMenu = false },
                            onClose = { showAudioMenu = false },
                            firstItemFocusRequester = if (isTv) menuFocusRequester else null,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }

                    // TEMPO: krátká hláška o automatickém přepnutí / nepřehratelném zvuku.
                    audioNotice?.let { txt ->
                        Text(
                            text = txt,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp)
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }

                    // Overlay indikátor jasu / hlasitosti během vertikálního swipe
                    gestureIndicator?.let { txt ->
                        Text(
                            text = txt,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Vlastní render aktuálního titulku — dole na střed. Font DĚDÍ z UI (patkový, když má user serif zapnutý),
 *  okraj i jeho síla jsou konfigurovatelné (obrys tloušťka / stín rozostření / podklad krytí). */
@Composable
private fun SubtitleOverlay(text: String, style: SubtitleStyle, modifier: Modifier = Modifier) {
    val screenH = LocalConfiguration.current.screenHeightDp
    val fontSize = (22 * style.fontScale).sp
    val lineH = fontSize * 1.25f
    val fill = Color(style.colorArgb)
    val k = style.edgeStrength
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = (style.bottomPaddingFraction * screenH).dp),
    ) {
        val bottom = Modifier.align(Alignment.BottomCenter)
        when (style.edge) {
            // Podklad: text v polopropustném černém rámečku, který ho obepíná (Netflix „box" styl). Síla = krytí.
            SubtitleEdge.BOX -> Box(
                modifier = bottom.fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = (0.55f * k).coerceIn(0.2f, 0.95f)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = fill, fontSize = fontSize, lineHeight = lineH, textAlign = TextAlign.Center,
                )
            }
            // Stín: měkký vržený stín pod textem. Síla = rozostření + posun.
            SubtitleEdge.SHADOW -> Text(
                text = text,
                modifier = bottom.fillMaxWidth().padding(horizontal = 16.dp),
                color = fill, fontSize = fontSize, lineHeight = lineH, textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2f * k), blurRadius = 6f * k)),
            )
            // Bez: čistá výplň bez okraje.
            SubtitleEdge.NONE -> Text(
                text = text,
                modifier = bottom.fillMaxWidth().padding(horizontal = 16.dp),
                color = fill, fontSize = fontSize, lineHeight = lineH, textAlign = TextAlign.Center,
            )
            // Obrys (default): černý obrys pod barevnou výplní. Síla = tloušťka.
            SubtitleEdge.OUTLINE -> {
                val textMod = bottom.fillMaxWidth().padding(horizontal = 16.dp)
                Text(
                    text = text, modifier = textMod, color = Color.Black,
                    fontSize = fontSize, lineHeight = lineH, textAlign = TextAlign.Center,
                    style = TextStyle(drawStyle = Stroke(width = 5f * k, join = StrokeJoin.Round)),
                )
                Text(
                    text = text, modifier = textMod, color = fill,
                    fontSize = fontSize, lineHeight = lineH, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SubtitleSettingsPanel(
    state: com.github.jankoran90.showlyfin.feature.playback.PlaybackUiState,
    onSelect: (Int) -> Unit,
    onFontScale: (Float) -> Unit,
    onColor: (Int) -> Unit,
    onPosition: (Float) -> Unit,
    onNudge: (Long) -> Unit,
    onEdge: (SubtitleEdge) -> Unit,
    onEdgeStrength: (Float) -> Unit,
    onTranslateAi: () -> Unit,
    onClose: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Titulky", color = Color.White, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose, modifier = Modifier.tvFocusBorder(RoundedCornerShape(8.dp))) { Text("Hotovo") }
        }

        // Stopa
        Text("Stopa (CZ)", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        SubtitleRow(
            label = "Vypnuto",
            selected = state.selectedSubtitleIndex < 0,
            onClick = { onSelect(-1) },
            focusRequester = firstItemFocusRequester,
        )
        state.subtitleCandidates.forEachIndexed { i, c ->
            val rel = c.release.ifBlank { c.title }
            // Zdroj titulku z prefixu id (CAPTION/LINGUA): titulky.com → počet stažení; OS/AI → název zdroje.
            val src = when {
                // SUBWEAVE B: oficiální OS API má prefix `osf_` (addon = `os_`) — obojí je OpenSubtitles.
                c.id.startsWith("os_") || c.id.startsWith("osf_") -> "OpenSubtitles"
                c.id.startsWith("ai_") -> "AI překlad"
                else -> ""
            }
            val meta = buildString {
                if (c.imdbMatch) append("✓ ")
                if (c.fps > 0) append("${c.fps} fps · ")
                if (src.isNotBlank()) append(src) else append("${c.downloads}×")
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

        // LINGUA/SUBWEAVE — AI překlad EN→CS. Nabízí se VŽDY (i když jsou titulky nalezené), aby si
        // uživatel mohl vynutit vlastní kvalitní překlad, když jsou stažené špatné (např. strojový OpenSubtitles).
        if (state.canTranslateAi || state.aiTranslating || state.aiTranslateError != null) {
            if (state.aiTranslating) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFFFBF00))
                    Spacer(Modifier.width(10.dp))
                    Text("Překládám titulky… (chvíli to potrvá)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusBorder(RoundedCornerShape(6.dp))
                        .clickable(onClick = onTranslateAi)
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🌐", color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Přeložit do češtiny (AI)", color = Color(0xFFFFBF00), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            state.aiTranslateError ?: if (state.subtitleCandidates.any { !it.id.startsWith("ai_") })
                                "Nejsou dobré? Přeložím anglické naší AI"
                            else "Žádné české titulky — přeložím anglické přes AI",
                            color = if (state.aiTranslateError != null) Color(0xFFEF5350) else Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Velikost (jemný krok 5 %)
        StepperRow("Velikost", "${(state.subtitleStyle.fontScale * 100).roundToInt()} %",
            onMinus = { onFontScale(state.subtitleStyle.fontScale - 0.05f) },
            onPlus = { onFontScale(state.subtitleStyle.fontScale + 0.05f) })

        // Pozice (výška odspodu, jemný krok 2 %)
        StepperRow("Pozice", "${(state.subtitleStyle.bottomPaddingFraction * 100).roundToInt()} %",
            onMinus = { onPosition(state.subtitleStyle.bottomPaddingFraction - 0.02f) },
            onPlus = { onPosition(state.subtitleStyle.bottomPaddingFraction + 0.02f) })

        // Posun (synchronizace, krok 0,25 s) — per film; nový film startuje na 0.
        StepperRow("Posun", "%+.2f s".format(state.subtitleStyle.offsetMs / 1000.0),
            onMinus = { onNudge(-250L) },
            onPlus = { onNudge(250L) })

        // Okraj (vzhled pozadí titulku)
        Spacer(Modifier.height(8.dp))
        Text("Okraj", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUBTITLE_EDGES.forEach { (edge, label) ->
                val sel = state.subtitleStyle.edge == edge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusBorder(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { onEdge(edge) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // Síla okraje (obrys tloušťka / stín rozostření / podklad krytí) — nedává smysl u „Bez".
        if (state.subtitleStyle.edge != SubtitleEdge.NONE) {
            StepperRow("Síla", "${(state.subtitleStyle.edgeStrength * 100).roundToInt()} %",
                onMinus = { onEdgeStrength(state.subtitleStyle.edgeStrength - 0.2f) },
                onPlus = { onEdgeStrength(state.subtitleStyle.edgeStrength + 0.2f) })
        }

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
                        .tvFocusBorder(CircleShape)
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
private fun SubtitleRow(
    label: String,
    sub: String = "",
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusBorder(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "●" else "○", color = if (selected) Color(0xFFFFBF00) else Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
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
            TextButton(onClick = onMinus, modifier = Modifier.tvFocusBorder(CircleShape)) { Text("−", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = onPlus, modifier = Modifier.tvFocusBorder(CircleShape)) { Text("+", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * TV fokus-highlight: bílý prstenec kolem prvku, když je zafokusovaný D-padem.
 * Musí být v řetězci modifierů PŘED `clickable`/button, aby `onFocusChanged` viděl jejich fokus.
 * Na telefonu (dotyk) se nikdy neukáže — focus stav nastává jen při D-pad/klávesnicové navigaci.
 */
@Composable
private fun Modifier.tvFocusBorder(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape,
        )
}

/** TEMPO (SHW-49): jedna zvuková stopa pro výběr v lokálním přehrávači (supported = telefon ji umí dekódovat). */
private data class AudioTrackOption(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val supported: Boolean,
    val selected: Boolean,
)

/** TEMPO: panel výběru zvukové stopy. Nepodporované stopy (DTS-HD/TrueHD na telefonu) jsou označené. */
@Composable
private fun AudioTrackPanel(
    tracks: List<AudioTrackOption>,
    onSelect: (AudioTrackOption) -> Unit,
    onClose: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Zvuk", color = Color.White, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose, modifier = Modifier.tvFocusBorder(RoundedCornerShape(8.dp))) { Text("Hotovo") }
        }
        if (tracks.isNotEmpty() && tracks.none { it.supported }) {
            Text(
                "⚠ Telefon neumí žádnou zvukovou stopu tohoto zdroje (DTS-HD/TrueHD). Přehraj ho na TV.",
                color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        tracks.forEachIndexed { i, t ->
            SubtitleRow(
                label = t.label,
                sub = if (!t.supported) "telefon neumí přehrát" else "",
                selected = t.selected,
                onClick = { onSelect(t) },
                focusRequester = if (i == 0) firstItemFocusRequester else null,
            )
        }
    }
}

/** TEMPO: čitelný popis zvukové stopy (jazyk · kodek · kanály). */
private fun audioTrackLabel(f: Format, i: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && !it.equals("und", true) }?.uppercase()
    val parts = listOfNotNull(lang, audioCodecName(f), f.channelCount.takeIf { it > 0 }?.let { channelLabel(it) })
    return parts.joinToString(" · ").ifBlank { f.label ?: "Stopa ${i + 1}" }
}

/** TEMPO: lidský název audio kodeku z MIME (pro odlišení DTS-HD/TrueHD od AC3/AAC). */
private fun audioCodecName(f: Format): String? = when (f.sampleMimeType) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
    else -> f.sampleMimeType?.substringAfter('/')?.uppercase()
}

private fun channelLabel(c: Int): String = when (c) {
    1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 7 -> "6.1"; 8 -> "7.1"; else -> "${c}ch"
}

/** Krátký popis zdroje do lišty: rozlišení · video kodek · audio kodek · kanály. */
private fun buildSourceMeta(video: Format?, audio: Format?): String {
    val parts = listOfNotNull(
        video?.let { resolutionLabel(it.width, it.height) },
        video?.let { videoCodecName(it) },
        audio?.let { audioCodecName(it) },
        audio?.channelCount?.takeIf { it > 0 }?.let { channelLabel(it) },
    )
    return parts.joinToString(" · ")
}

private fun resolutionLabel(w: Int, h: Int): String? {
    val p = maxOf(w, h).takeIf { it > 0 } ?: return null // vertikální/horizontální jistota
    val lines = minOf(w, h)
    return when {
        p >= 3000 || lines >= 1600 -> "4K"
        lines >= 1000 -> "1080p"
        lines >= 700 -> "720p"
        lines >= 500 -> "576p"
        lines > 0 -> "${lines}p"
        else -> null
    }
}

private fun videoCodecName(f: Format): String? = when (f.sampleMimeType) {
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
    else -> f.sampleMimeType?.substringAfter('/')?.uppercase()
}
