package com.github.jankoran90.showlyfin.data.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Plan NOMAD (SHW-60) — foreground služba, která drží proces živý během stahování velkých
 * video souborů (přežije přepnutí appky na pozadí) a zrcadlí postup z [OfflineDownloadManager.states]
 * do notifikace. Samotné stahování dělá manager; služba jen drží foreground + notifikuje a po
 * dotažení všech položek se sama ukončí.
 */
@AndroidEntryPoint
class OfflineDownloadService : Service() {

    @Inject lateinit var manager: OfflineDownloadManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground(buildNotification(active = 0, key = null, progress = 0))
        scope.launch {
            manager.states.collectLatest { states ->
                val active = states.entries.filter {
                    it.value.status == OfflineStatus.DOWNLOADING || it.value.status == OfflineStatus.QUEUED
                }
                if (active.isEmpty()) {
                    ServiceCompat.stopForeground(this@OfflineDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val downloading = active.firstOrNull { it.value.status == OfflineStatus.DOWNLOADING }
                        ?: active.first()
                    val progress = (downloading.value.progress * 100).roundToInt()
                    val nm = getSystemService<NotificationManager>()
                    nm?.notify(NOTIF_ID, buildNotification(active.size, downloading.key, progress))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun buildNotification(active: Int, key: String?, progress: Int): Notification {
        val title = when {
            active <= 1 && key != null -> manager.titleFor(key)
            active <= 1 -> "Příprava stahování…"
            else -> "Stahuji $active položky"
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stahování do telefonu")
            .setContentText(if (active <= 1) "$title · $progress %" else title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, active <= 1 && progress == 0)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Offline stahování", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Průběh stahování filmů a epizod do telefonu"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "offline_downloads"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, OfflineDownloadService::class.java))
        }
    }
}
