package com.github.jankoran90.showlyfin.feature.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.github.jankoran90.showlyfin.core.domain.InstallGuard
import com.github.jankoran90.showlyfin.core.domain.audio.AudioBoost
import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import dagger.hilt.android.AndroidEntryPoint
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import javax.inject.Inject
import javax.inject.Named

/**
 * MARQUEE (SHW-57): hostí ExoPlayer pro **filmový** přehrávač jako [MediaSessionService], takže ho
 * systém vidí → ovládání z notifikace, zámku a sluchátek (play/pauza/seek), stejně jako audioknihy
 * ([com.github.jankoran90.showlyfin.feature.listen.service.AudiobookPlayerService]). Na rozdíl od
 * audia tu NENÍ Android Auto browse strom — video se v Auto nevykreslí, proto stačí prostý
 * MediaSessionService (jen session, žádný [androidx.media3.session.MediaLibraryService]).
 *
 * Přehrávač staví tahle služba (dřív se stavěl inline v PlaybackScreen); Compose se na něj napojuje
 * přes [androidx.media3.session.MediaController] a posílá mu MediaItem (URL + název do metadat).
 *
 * **Chování na pozadí (volba uživatele 2026-06-15):** zámek / přepnutí jinam v appce → zvuk hraje
 * dál + ovládání v notifikaci/na zámku (controller se odpojí, služba zůstane foreground a hraje).
 * Tlačítko Zpět v přehrávači = konec → UI pošle [ACTION_STOP] → stop + stopSelf.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MoviePlayerService : MediaSessionService() {

    @Inject @Named("traktPreferences") lateinit var prefs: SharedPreferences

    private var session: MediaSession? = null

    /** Plan EVEN — DRC/normalizér FILMU. Default VYP; jen telefon (na TV hraje box = passthrough). */
    private var audioBoost: AudioBoost? = null

    /** Stabilní audio session id (pin pro DRC) — sdílené i po rebuildu přehrávače na SW dekodér. */
    private var audioSessionId: Int = 0

    /** True = přehrávač aktuálně jede na SOFTWAROVÉM (FFmpeg) video dekodéru (pref nebo auto-fallback). */
    private var swVideoActive = false

    override fun onCreate() {
        super.onCreate()
        // Plan EVEN — pinneme stabilní audio session id pro připojení filmového DRC (jen telefon).
        audioSessionId = C.generateAudioSessionIdV21(this)
        // FISSION (SHW-98): SW dekodér obrazu — buď natvrdo z prefs (uživatel vynutil), nebo default HW
        // s auto-fallbackem na SW při decode chybě (ACTION_FORCE_SW). Exynos/Tensor padá na některých HEVC.
        val forceSw = prefs.getBoolean(PlayerPrefs.FORCE_SW_DECODER_KEY, PlayerPrefs.DEFAULT_FORCE_SW_DECODER)
        swVideoActive = forceSw
        val player = buildPlayer(forceSwVideo = forceSw)
        val drcLevel = prefs.getInt(AudioBoost.MOVIE_DRC_KEY, 0)
        if (drcLevel > 0) {
            audioBoost = AudioBoost(audioSessionId).also { it.apply(drcLevel, AudioBoost.Profile.MOVIE) }
        }
        session = MediaSession.Builder(this, player)
            // Media3 vyžaduje v rámci JEDNOHO procesu UNIKÁTNÍ session ID. Audioknihová
            // AudiobookPlayerService drží MediaLibrarySession s výchozím prázdným ID ("") → bez
            // vlastního ID by se obě session srazily ("Session ID must be unique. ID=") a služba by
            // spadla už v onCreate → pád při přehrávání čehokoliv (RD/Jellyfin/NaVýbornou).
            .setId("showlyfin_movie")
            .apply { contentActivityPendingIntent()?.let { setSessionActivity(it) } }
            .build()
    }

    /**
     * Postaví ExoPlayer. [forceSwVideo] = preferovat NextLib FFmpeg video renderer (spolehlivé HEVC při
     * selhání HW dekodéru). Volá se v [onCreate] i při auto-fallbacku (ACTION_FORCE_SW) — proto reuse
     * [audioSessionId] (DRC drží), vlastní listener [InstallGuard], vypnutý text renderer (titulky kreslíme sami).
     */
    private fun buildPlayer(forceSwVideo: Boolean): ExoPlayer {
        // F2d (A/V lip-sync): na TV boxu s AVR (eARC, 5.1 passthrough) NEsmí do audio cesty NextLib FFmpeg SW
        // dekodér — mění latenci passthrough → video napřed. Na TV proto čistý DefaultRenderersFactory (bitstream
        // passthrough do AVR) + audio offload (jako yellyfin, který sync problém nemá). Telefon BEZE ZMĚNY
        // (FFmpeg nutný pro DTS/TrueHD, které telefon nemá v HW). Konfigurovatelné (PlayerPrefs, default zap na TV).
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val boxAudio = isTv &&
            prefs.getBoolean(PlayerPrefs.TV_AUDIO_PASSTHROUGH_KEY, PlayerPrefs.DEFAULT_TV_AUDIO_PASSTHROUGH)
        // forceSwVideo → FFmpeg jako PREFEROVANÝ video renderer (i na TV; user si vynutil SW, lip-sync ustoupí).
        val renderersFactory: RenderersFactory = if (boxAudio && !forceSwVideo) {
            DefaultRenderersFactory(applicationContext).setEnableDecoderFallback(true)
        } else {
            NextRenderersFactory(applicationContext)
                .setExtensionRendererMode(
                    if (forceSwVideo) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
                )
                .setEnableDecoderFallback(true)
        }
        val builder = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory()))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(60_000, 300_000, 5_000, 10_000)
                    .build(),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // Sluchátka: vytažení jacku / odpojení BT → pauza (ne hraní do reproduktoru naplno).
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
        if (boxAudio) {
            // Audio offload (parita s yellyfinem) — kratší DSP cesta při passthrough → stabilnější A/V sync.
            builder.setTrackSelector(
                DefaultTrackSelector(applicationContext).apply {
                    setParameters(
                        buildUponParameters().setAudioOffloadPreferences(
                            AudioOffloadPreferences.Builder()
                                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                                .build(),
                        ),
                    )
                },
            )
        }
        val player = builder
            .build()
            .apply {
                // Titulky kreslíme VLASTNÍM overlayem (Compose, z externích .srt). Vestavěné text stopy
                // (často cizojazyčné) nesmí přehrávač renderovat sám → vypnout celý text renderer.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
        // Plan EVEN — filmové DRC (audioBoost) se připíná v onCreate na session id TOHOTO přehrávače
        // (jen telefon; na TV běží box → passthrough). audioSessionId je pole = drží i po SW rebuildu.
        player.setAudioSessionId(audioSessionId)
        // EVERGREEN — tichá auto-instalace na pozadí nesmí utnout běžící film (i se zhasnutou obrazovkou).
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                InstallGuard.playbackActive = isPlaying
            }
        })
        return player
    }

    /**
     * FISSION auto-fallback: HW dekodér selhal (`ERROR_CODE_DECODING_FAILED`) → přestav přehrávač na
     * SOFTWAROVÝ (FFmpeg) video renderer a nasaď TENTÝŽ zdroj od stejné pozice. Idempotentní (jen jednou —
     * `swVideoActive`), aby se necyklilo, když nedekóduje ani SW. Controller zůstává navázaný (swap přes
     * [MediaSession.setPlayer]) → UI ani pozice se neztratí.
     */
    private fun switchToSoftwareDecoder() {
        val s = session ?: return
        val old = s.player
        if (swVideoActive || old.currentMediaItem == null) return
        swVideoActive = true
        val item = old.currentMediaItem!!
        val pos = old.currentPosition.coerceAtLeast(0L)
        val newPlayer = buildPlayer(forceSwVideo = true)
        newPlayer.setMediaItem(item, pos)
        newPlayer.prepare()
        newPlayer.playWhenReady = true
        s.setPlayer(newPlayer)
        (old as? ExoPlayer)?.release()
        android.util.Log.i("MoviePlayer", "[FISSION] auto-fallback na SW dekodér, resume @${pos}ms")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Zpět v přehrávači → ukonči přehrávání a zastav službu (notifikace zmizí). stopSelf() reálně
        // dojede až po odpojení controlleru (UI ho uvolní v onDispose) = čisté pořadí.
        if (intent?.action == ACTION_STOP) {
            session?.player?.run { stop(); clearMediaItems() }
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_FORCE_SW) {
            switchToSoftwareDecoder()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Klepnutí na notifikaci → otevři appku (návrat do přehrávače řeší navigace appky). */
    private fun contentActivityPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** DefaultDataSource (file:// i http(s)) nad HTTP factory s UA jako v původním inline přehrávači. */
    private fun dataSourceFactory(): DefaultDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36",
            )
        return DefaultDataSource.Factory(this, upstream)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        InstallGuard.playbackActive = false
        audioBoost?.release()
        audioBoost = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /** Intent akce: UI (Zpět) žádá ukončení přehrávání + zastavení služby. */
        const val ACTION_STOP = "com.github.jankoran90.showlyfin.MOVIE_STOP"

        /** Intent akce: UI hlásí decode chybu → přestav přehrávač na SW dekodér a nasaď stejný zdroj. */
        const val ACTION_FORCE_SW = "com.github.jankoran90.showlyfin.MOVIE_FORCE_SW"
    }
}
