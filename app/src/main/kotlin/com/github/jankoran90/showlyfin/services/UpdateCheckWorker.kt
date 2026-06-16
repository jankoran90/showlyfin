package com.github.jankoran90.showlyfin.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.jankoran90.showlyfin.MainActivity
import com.github.jankoran90.showlyfin.R
import com.github.jankoran90.showlyfin.ShowlyfinApp
import com.github.jankoran90.showlyfin.core.domain.InstallGuard
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "showlyfin_updates"
private const val CHANNEL_NAME = "Aktualizace"
private const val NOTIFICATION_ID = 4711
private const val WORK_NAME = "showlyfin_update_check"

const val EXTRA_OPEN_UPDATE_DIALOG = "open_update_dialog"

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val checker = UpdateChecker()
        val manifest = checker.fetchManifest(ctx)
        UpdatePreferences.storeCheckAt(ctx)
        if (manifest == null) return Result.retry()
        if (!checker.isUpdateAvailable(manifest)) {
            UpdatePreferences.clearAvailable(ctx)
            return Result.success()
        }
        UpdatePreferences.storeAvailable(ctx, manifest)

        // EVERGREEN (SHW-64) — auto-update vypnut → jen notifikuj (ruční instalace z Nastavení / dialogu).
        if (!UpdatePreferences.isAutoUpdateEnabled(ctx)) {
            notifyUpdate(manifest.versionName)
            return Result.success()
        }

        // Stáhni novou verzi sám (na pozadí). Když selže, notifikuj a zkus příště.
        val apk = checker.downloadApk(ctx, manifest) { }
        if (apk == null) {
            notifyUpdate(manifest.versionName)
            return Result.retry()
        }

        // Tichá instalace jen když se nic neutne: appka NENÍ v popředí a nic nehraje (i se zhaslou
        // obrazovkou — MediaSession na pozadí). Jinak nech na ruční instalaci a zkus příští cyklus.
        val canSilent = UpdatePreferences.isSilentInstallEnabled(ctx) &&
            !ShowlyfinApp.isInForeground &&
            !InstallGuard.playbackActive
        if (canSilent && ApkInstaller.install(ctx, apk)) {
            // Instalace běží asynchronně; výsledek (vč. fallbacku na dialog) řeší InstallResultReceiver.
            return Result.success()
        }
        notifyUpdate(manifest.versionName)
        return Result.success()
    }

    private fun notifyUpdate(tag: String) {
        val ctx = applicationContext
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
                channel.description = "Notifikace o nových verzích Showlyfin"
                notificationManager.createNotificationChannel(channel)
            }
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Showlyfin $tag k dispozici")
            .setContentText("Klepněte pro instalaci")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Timber.w(it, "notify update failed") }
    }

    companion object {
        fun enqueue(context: Context) {
            // EVERGREEN — „jen na Wi-Fi" = UNMETERED, jinak libovolné připojení. UPDATE (ne KEEP), aby se
            // změna toggle „jen Wi-Fi" propsala do constraintu (MainActivity volá enqueue při startu;
            // setWifiOnly re-enqueue hned). 6 h = svižnější doručení než dřívějších 12 h.
            val networkType =
                if (UpdatePreferences.isWifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
