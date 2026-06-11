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
        val checker = UpdateChecker()
        val manifest = checker.fetchManifest(applicationContext)
        UpdatePreferences.storeCheckAt(applicationContext)
        if (manifest == null) return Result.retry()
        if (!checker.isUpdateAvailable(manifest)) {
            UpdatePreferences.clearAvailable(applicationContext)
            return Result.success()
        }
        UpdatePreferences.storeAvailable(applicationContext, manifest)
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
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
