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
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DROPSHIP F2d — foreground služba, která drží proces přiživu během uploadu audioknihy,
 * aby přežil přepnutí appky na pozadí (Android jinak proces zabije → „Unable to resolve host").
 * Sama nic nenahrává — upload běží v [AudiobookUploadManager]; služba jen zobrazuje průběh
 * v notifikaci (dataSync typ) a skončí, jakmile upload doběhne (result i error).
 */
@AndroidEntryPoint
class UploadAudiobookService : Service() {

    @Inject lateinit var manager: AudiobookUploadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground()
        // Notifikace zrcadlí stav uploadu; terminal stav (result/error) = hotovo → stopSelf.
        scope.launch {
            manager.state.collectLatest { s ->
                if (!s.isUploading && (s.result != null || s.error != null)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                if (s.isUploading) notify(s.progress)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun startInForeground() {
        val notification = buildNotification(0f)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notify(progress: Float) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(progress))
    }

    private fun buildNotification(progress: Float): Notification {
        val pct = (progress * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Nahrávám audioknihu")
            .setContentText(if (pct > 0) "$pct %" else "připravuji…")
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nahrávání audioknih",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Průběh nahrávání audioknihy na server" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "audiobook_upload"
        private const val NOTIF_ID = 4711

        /** Explicitní intent pro start z manageru (nevázáno na třídu z venku). */
        fun intent(context: Context) = Intent(context, UploadAudiobookService::class.java)
    }
}
