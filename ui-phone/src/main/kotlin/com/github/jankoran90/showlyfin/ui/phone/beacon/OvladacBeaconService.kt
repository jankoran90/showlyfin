package com.github.jankoran90.showlyfin.ui.phone.beacon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * BEACON — foreground [MediaSessionService], který vystaví běžící Jellyfin **TV** přehrávání jako
 * mediální ovládání na **lockscreenu / mediapanelu telefonu**. Sám pollne `/Sessions` (~2 s,
 * nezávisle na UI) a plní [RemoteTvPlayer]; transport/seek/hlasitost se přes [RemoteTvPlayer.Commander]
 * routují do [NaTvService]. Když na TV nic neběží, služba notifikaci sundá a `stopSelf()`.
 *
 * Spouští ji `OvladacViewModel`, jakmile při pollu najde běžící TV session (`startForegroundService`);
 * od té chvíle žije nezávisle na appce (přežije odchod na plochu) až do konce přehrávání.
 *
 * Vzor: [com.github.jankoran90.showlyfin.feature.listen.service.AudiobookPlayerService] (media3
 * session + custom akce), ale bez ExoPlayeru — přehrávání je vzdálené na TV.
 */
@AndroidEntryPoint
class OvladacBeaconService : MediaSessionService(), RemoteTvPlayer.Commander {

    @Inject lateinit var naTv: NaTvService
    @Inject @Named("traktPreferences") lateinit var prefs: SharedPreferences

    private val scope = CoroutineScope(Dispatchers.Main)
    private var session: MediaSession? = null
    private var player: RemoteTvPlayer? = null
    private var pollJob: Job? = null

    /** Poslední vyřešená TV session (pro subtitle toggle + adjust hlasitosti + cílový sessionId). */
    private var lastSession: JellyfinSessionSummary? = null
    /** Počet po sobě jdoucích pollů bez běžícího přehrávání → po [MAX_EMPTY_POLLS] se služba ukončí. */
    private var emptyPolls = 0

    private val cmdToggleSubs = SessionCommand(ACTION_TOGGLE_SUBTITLES, Bundle.EMPTY)

    private val customLayout: ImmutableList<CommandButton> by lazy {
        ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setSessionCommand(cmdToggleSubs)
                .setDisplayName("Titulky")
                .setIconResId(com.github.jankoran90.showlyfin.ui.phone.R.drawable.ic_beacon_subtitles)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // media3 použije STEJNÉ id+kanál jako naše placeholder notifikace → svou MediaStyle notifikaci
        // jen nahradí (bez druhé/blikající notifikace).
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIF_ID)
                .setChannelId(CHANNEL_ID)
                .build(),
        )
        val p = RemoteTvPlayer(mainLooper, this)
        // Placeholder „připojuji" stav (hraje+má obsah); první poll do ~2 s ho přepíše.
        p.updateFromSession(PLACEHOLDER_SESSION, null)
        player = p
        session = MediaSession.Builder(this, p)
            .setCallback(callback)
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(applicationContext)))
            .apply { contentActivityPendingIntent()?.let { setSessionActivity(it) } }
            .build()
        startPolling()
    }

    /**
     * KLÍČOVÉ: `startForegroundService` má 5s kontrakt na `startForeground`. Nespoléhej, že media3
     * stihne posadit notifikaci sám (zvlášť než se povolí POST_NOTIFICATIONS) — jinak systém appku
     * shodí (`ForegroundServiceDidNotStartInTimeException`). Proto foreground splníme DETERMINISTICKY
     * hned tady vlastní notifikací; media3 ji pak (stejné id+kanál) převezme.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { startForegroundCompat() }
            .onFailure { Timber.w(it, "[Beacon] startForeground selhal") }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildPlaceholderNotification(), type)
    }

    private fun buildPlaceholderNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setContentTitle("Ovladač TV")
            .setContentText("Připojuji…")
            .setOngoing(true)
            .apply { contentActivityPendingIntent()?.let { setContentIntent(it) } }
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ovladač TV", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(cmdToggleSubs)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(customLayout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_SUBTITLES) toggleSubtitles()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    // ---- Poll loop ----

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                poll()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun poll() {
        val c = creds()
        if (c == null) { finishNoPlayback(); return }
        val sessions = naTv.getSessions(c.url, c.token)
        val watch = naTv.pickWatchSession(sessions)?.takeIf { it.nowPlayingTitle != null }
        lastSession = watch
        if (watch == null) {
            emptyPolls++
            player?.updateFromSession(null, null)
            if (emptyPolls >= MAX_EMPTY_POLLS) {
                Timber.i("[Beacon] žádné TV přehrávání %d× → stopSelf", emptyPolls)
                stopSelf()
            }
            return
        }
        emptyPolls = 0
        val cover = watch.itemId?.let { naTv.imageUrl(c.url, c.token, it, watch.imageTag) }
        player?.updateFromSession(watch, cover)
    }

    private fun finishNoPlayback() {
        player?.updateFromSession(null, null)
        stopSelf()
    }

    // ---- RemoteTvPlayer.Commander — příkazy z lockscreenu → NaTvService ----

    override fun playPause() = onTv { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "PlayPause") }
    override fun stop() = onTv { c, id -> naTv.sendPlaystateCommand(c.url, c.token, id, "Stop") }
    override fun seekTo(positionMs: Long) =
        onTv { c, id -> naTv.sendSeek(c.url, c.token, id, positionMs * TICKS_PER_MS) }
    override fun setVolume(volume: Int) = onTv { c, id -> naTv.setVolume(c.url, c.token, id, volume) }
    override fun setMuted(muted: Boolean) = onTv { c, id ->
        // NaTvService umí jen toggle → přepni jen když se cílový stav liší od posledního známého.
        if (lastSession?.isMuted != muted) naTv.toggleMute(c.url, c.token, id) else true
    }

    override fun adjustVolume(delta: Int) = onTv { c, id ->
        val base = lastSession?.volumeLevel ?: 0
        naTv.setVolume(c.url, c.token, id, (base + delta).coerceIn(0, 100))
    }

    private fun toggleSubtitles() = onTv { c, id ->
        val s = lastSession ?: return@onTv false
        val targetIndex = if (s.currentSubtitleIndex >= 0) -1 else s.subtitleTracks.firstOrNull()?.index
        if (targetIndex == null) false else naTv.setSubtitleIndex(c.url, c.token, id, targetIndex)
    }

    private fun onTv(block: suspend (Creds, String) -> Boolean) {
        val c = creds() ?: return
        val id = lastSession?.sessionId ?: return
        scope.launch {
            block(c, id)
            delay(COMMAND_SETTLE_MS)
            poll()
        }
    }

    // ---- Lifecycle / helpers ----

    /** Tap na notifikaci otevře appku (launcher). */
    private fun contentActivityPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Zavření appky z přehledu úloh přehrávání na TV neukončuje, ale lockscreen ovladač už nemá smysl.
        stopSelf()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        session?.run { player.release(); release() }
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private data class Creds(val url: String, val token: String)

    private fun creds(): Creds? {
        val url = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        return if (url.isBlank() || token.isBlank()) null else Creds(url, token)
    }

    companion object {
        const val ACTION_TOGGLE_SUBTITLES = "com.github.jankoran90.showlyfin.beacon.TOGGLE_SUBTITLES"
        private const val CHANNEL_ID = "beacon_tv_remote"
        private const val NOTIF_ID = 0xBEAC
        private const val POLL_MS = 2_000L
        private const val COMMAND_SETTLE_MS = 300L
        private const val MAX_EMPTY_POLLS = 2
        private const val TICKS_PER_MS = 10_000L

        /** Krátký „připojuji" stav, aby media3 stihlo startForeground; první poll ho přepíše. */
        private val PLACEHOLDER_SESSION = JellyfinSessionSummary(
            sessionId = "beacon-init",
            deviceName = "TV",
            client = null,
            isActive = true,
            nowPlayingTitle = "Přehrávání na TV",
            nowPlayingSubtitle = "Připojuji…",
            isPlaying = true,
        )

        /** Idempotentní spuštění služby — volá `OvladacViewModel` při detekci běžícího TV přehrávání. */
        fun start(context: android.content.Context) {
            val intent = Intent(context, OvladacBeaconService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
