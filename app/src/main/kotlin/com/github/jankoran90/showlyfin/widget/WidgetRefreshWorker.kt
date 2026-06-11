package com.github.jankoran90.showlyfin.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * RELAY — periodicky obnovuje obsah obou widgetů (now-playing na TV / v audio přehrávači).
 * Min. interval AppWidget `updatePeriodMillis` je 30 min a baterii nešetří; WorkManager dovolí
 * 15 min s network constraintem. Akce na widgetu navíc překreslují okamžitě (`update`).
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // updateAll je no-op, když daný widget na ploše není.
        runCatching { ListenWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "relay_widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
