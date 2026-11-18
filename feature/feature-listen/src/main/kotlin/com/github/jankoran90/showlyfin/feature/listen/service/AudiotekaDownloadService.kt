package com.github.jankoran90.showlyfin.feature.listen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AudiotekaImporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stahování koupené knihy z Poličky Audioteky přežije přepnutí appky (user 2026-11-18: „možná jak jsem
 * překlikl na Velín v průběhu" → „Software caused connection abort"). Sama nic nestahuje — drží proces
 * na popředí, dokud [AudiotekaImporter] stahuje, a po stažení nechá notifikaci „vyber knihovnu".
 */
@AndroidEntryPoint
class AudiotekaDownloadService : Service() {

    @Inject lateinit var importer: AudiotekaImporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val first = build("Stahuji z Audioteky", "připravuji…", null, ongoing = true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, first)
        }
        scope.launch {
            importer.state.collectLatest { s ->
                when (s) {
                    is AudiotekaImporter.State.Downloading -> notifyNow(
                        build("Stahuji z Audioteky", s.name + " · ${s.bytes / 1_000_000} MB", s.progress, ongoing = true),
                    )
                    is AudiotekaImporter.State.Ready -> finish(
                        build("Staženo: ${s.name}", "Otevři Slovo a vyber knihovnu pro dovoz", null, ongoing = false),
                    )
                    is AudiotekaImporter.State.Failed -> finish(
                        build("Stažení z Audioteky selhalo", s.message, null, ongoing = false),
                    )
                    AudiotekaImporter.State.Idle -> finish(null)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    /** Konec služby; [last] zůstane v liště jako obyčejná (odklepnutelná) notifikace. */
    private fun finish(last: Notification?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (last != null) notifyNow(last, DONE_ID)
        stopSelf()
    }

    private fun notifyNow(n: Notification, id: Int = NOTIF_ID) {
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun build(title: String, text: String, progress: Float?, ongoing: Boolean): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(ongoing)
            .setContentIntent(open)
        if (ongoing) {
            val pct = ((progress ?: 0f) * 100).toInt()
            b.setProgress(100, pct, progress == null)
        }
        return b.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Stahování z Audioteky", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Průběh stahování koupené knihy z Poličky Audioteky" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "audioteka_download"
        private const val NOTIF_ID = 4721
        private const val DONE_ID = 4722
    }
}
