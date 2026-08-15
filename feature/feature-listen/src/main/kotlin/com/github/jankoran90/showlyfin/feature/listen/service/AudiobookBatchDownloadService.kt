package com.github.jankoran90.showlyfin.feature.listen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * User (2026-08-15) „stahuje se, ale i na pozadí?" — foreground služba pro dávkové stahování
 * ("Stáhnout vše"), vzor [UploadAudiobookService]. Drží proces přiživu, dokud
 * [AudiobookDownloadManager.batchProgress] neskončí (null = dávka hotová/žádná neběží).
 * Jednotlivá stahování mimo dávku (long-press → Stáhnout) foreground službu NEpotřebují — jsou
 * krátká a typicky doběhnou, i než uživatel appku přepne pryč; dávka může trvat výrazně déle.
 */
@AndroidEntryPoint
class AudiobookBatchDownloadService : Service() {

    @Inject lateinit var manager: AudiobookDownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        scope.launch {
            manager.batchProgress.collectLatest { p ->
                if (p == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                notify(p.completed, p.total)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun startInForeground() {
        val notification = buildNotification(0, manager.batchProgress.value?.total ?: 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notify(completed: Int, total: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(completed, total))
    }

    private fun buildNotification(completed: Int, total: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Stahuji audioknihy")
            .setContentText("$completed / $total")
            .setProgress(total.coerceAtLeast(1), completed, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stahování audioknih",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Průběh dávkového stahování audioknih (Stáhnout vše)" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "audiobook_batch_download"
        private const val NOTIF_ID = 4712

        fun intent(context: Context) = Intent(context, AudiobookBatchDownloadService::class.java)
    }
}
