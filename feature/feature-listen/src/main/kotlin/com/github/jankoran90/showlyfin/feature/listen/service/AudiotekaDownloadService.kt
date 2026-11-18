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
import androidx.core.app.ServiceCompat
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AudiotekaImporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stahování koupené knihy z Poličky Audioteky přežije přepnutí appky (user 2026-11-18: „možná jak jsem
 * překlikl na Velín v průběhu" → „Software caused connection abort"). Sama nic nestahuje — drží proces
 * na popředí po celý dovoz ([AudiotekaImporter]: stažení → nahrání do knihovny) a ukazuje jednu notifikaci
 * s fází, procenty a odhadem zbývajícího času (user 19:52: „vidět v notifikaci průběh s ETA … a úspěch").
 */
@AndroidEntryPoint
class AudiotekaDownloadService : Service() {

    @Inject lateinit var importer: AudiotekaImporter
    @Inject lateinit var uploadManager: AudiobookUploadManager

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
            combine(importer.state, uploadManager.state) { s, up -> s to up }.collectLatest { (s, up) ->
                when (s) {
                    is AudiotekaImporter.State.Downloading -> notifyNow(
                        build(
                            "Stahuji z Audioteky (1/2)",
                            line(s.name.substringBeforeLast('.'), s.progress, s.startedAt) + " · ${s.bytes / 1_000_000} MB",
                            s.progress, ongoing = true,
                        ),
                    )
                    is AudiotekaImporter.State.Importing -> notifyNow(
                        build(
                            "Nahrávám do knihovny ${s.target.libraryName} (2/2)",
                            line(s.ready.titleHint ?: s.ready.name.substringBeforeLast('.'), up.progress.takeIf { up.uploadId == s.uploadId && it > 0f }, s.startedAt),
                            up.progress.takeIf { up.uploadId == s.uploadId && it > 0f }, ongoing = true,
                        ),
                    )
                    is AudiotekaImporter.State.Done -> finish(
                        build("Hotovo: kniha je v knihovně ${s.libraryName}", s.name, null, ongoing = false, done = true),
                    )
                    is AudiotekaImporter.State.Ready -> finish(
                        build("Staženo: ${s.name}", s.note ?: "Otevři Slovo a vyber knihovnu pro dovoz", null, ongoing = false),
                    )
                    is AudiotekaImporter.State.Failed -> finish(
                        build("Dovoz z Audioteky selhal", s.message, null, ongoing = false),
                    )
                    AudiotekaImporter.State.Idle -> finish(null)
                }
            }
        }
    }

    /** „Název · 45 % · zbývá ~3 min" (odhad z dosavadního tempa, od 3 % průběhu). */
    private fun line(name: String, progress: Float?, startedAt: Long): String {
        if (progress == null || progress <= 0f) return name
        val pct = (progress * 100).toInt()
        val elapsed = System.currentTimeMillis() - startedAt
        if (progress < 0.03f || elapsed < 3_000) return "$name · $pct %"
        val leftS = (elapsed * (1 - progress) / progress / 1000).toLong()
        val eta = when {
            leftS < 60 -> "zbývá < 1 min"
            leftS < 3600 -> "zbývá ~${(leftS + 59) / 60} min"
            else -> "zbývá ~${leftS / 3600} h ${(leftS % 3600) / 60} min"
        }
        return "$name · $pct % · $eta"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    /** Konec služby; [last] zůstane v liště jako obyčejná (odklepnutelná) notifikace. */
    private fun finish(last: Notification?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) // API 23 nemá stopForeground(int)
        if (last != null) notifyNow(last, DONE_ID)
        stopSelf()
    }

    private fun notifyNow(n: Notification, id: Int = NOTIF_ID) {
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun build(title: String, text: String, progress: Float?, ongoing: Boolean, done: Boolean = false): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(ongoing)
            .apply { if (done) setPriority(NotificationCompat.PRIORITY_DEFAULT) }
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
