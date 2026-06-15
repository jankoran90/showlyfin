package com.github.jankoran90.showlyfin.feature.playback.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

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
@OptIn(UnstableApi::class)
class MoviePlayerService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // TEMPO Fáze C: FFmpeg SW dekodér (NextLib) pro DTS/DTS-HD core/TrueHD, které telefon nemá
            // v HW → jinak u REMUXů ticho. EXTENSION_RENDERER_MODE_ON = HW dekodér má přednost, ffmpeg
            // jen vyplní díry. JEN telefon (showlyfin), NIKDY box (yellyfin drží passthrough do AVR).
            .setRenderersFactory(
                NextRenderersFactory(applicationContext)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true),
            )
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
            .build()
            .apply {
                // Titulky kreslíme VLASTNÍM overlayem (Compose, z externích .srt). Vestavěné text stopy
                // (často cizojazyčné) nesmí přehrávač renderovat sám → vypnout celý text renderer.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Zpět v přehrávači → ukonči přehrávání a zastav službu (notifikace zmizí). stopSelf() reálně
        // dojede až po odpojení controlleru (UI ho uvolní v onDispose) = čisté pořadí.
        if (intent?.action == ACTION_STOP) {
            session?.player?.run { stop(); clearMediaItems() }
            stopSelf()
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
    }
}
