package com.github.jankoran90.showlyfin.feature.listen.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dedikovaný audio přehrávač pro poslechovou sekci (audioknihy). MediaSessionService dává
 * background audio + lock-screen/notifikaci. Periodicky synchronizuje pozici zpět na ABS
 * (sessionId + duration jsou v extras MediaItemu, který nastaví AudiobookPlayerConnection).
 */
@AndroidEntryPoint
class AudiobookPlayerService : MediaSessionService() {

    @Inject lateinit var repo: AbsRepository

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startSync() else { stopSync(); syncNow() }
            }
        })
        player = exo
        session = MediaSession.Builder(this, exo)
            .apply { contentActivityPendingIntent()?.let { setSessionActivity(it) } }
            .build()
    }

    /** PendingIntent na vlastní app (launcher) s extra → po klepnutí na notifikaci se otevře Poslech. */
    private fun contentActivityPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra(ListenNavSignal.EXTRA_OPEN_LISTEN, true)
        } ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        syncNow()
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        syncNow()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (true) {
                delay(SYNC_INTERVAL_MS)
                syncNow()
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /** Pošle aktuální pozici na ABS (drží „Pokračovat v poslechu"). */
    private fun syncNow() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val sessionId = extras.getString(KEY_SESSION_ID) ?: return
        val durationSec = extras.getDouble(KEY_DURATION_SEC)
        val posSec = (p.currentPosition / 1000.0).coerceAtLeast(0.0)
        scope.launch {
            repo.syncProgress(sessionId, posSec, SYNC_INTERVAL_MS / 1000.0, durationSec)
        }
    }

    companion object {
        const val KEY_SESSION_ID = "abs_session_id"
        const val KEY_DURATION_SEC = "abs_duration_sec"
        private const val SYNC_INTERVAL_MS = 15_000L
    }
}
