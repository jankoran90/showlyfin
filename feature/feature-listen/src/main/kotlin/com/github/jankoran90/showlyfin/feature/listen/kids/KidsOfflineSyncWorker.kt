package com.github.jankoran90.showlyfin.feature.listen.kids

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Rodičovská správa R3 (2026-11-19) — periodické samostahování obsahu Dětí jen na Wi-Fi (bez měření dat).
 * Totéž se pouští i při startu appky a návratu do popředí ([KidsOfflineSync.trigger]); tahle úloha
 * pokryje dobu, kdy telefon leží a appka je na pozadí. Nic nedělá mimo dětský profil / vypnuté nastavení.
 */
class KidsOfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun kidsOfflineSync(): KidsOfflineSync
    }

    override suspend fun doWork(): Result {
        if (!KidsAutoDownloadPrefs.isEnabled(applicationContext)) return Result.success()
        val sync = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).kidsOfflineSync()
        sync.run("úloha na pozadí", waitForDownloads = true)
        return Result.success()
    }

    companion object {
        private const val WORK_PERIODIC = "kids_offline_sync"
        private const val WORK_NOW = "kids_offline_sync_now"

        private fun wifiOnly() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()

        /** Periodicky (každé 3 h, jen na Wi-Fi). Idempotentní — volat při startu appky. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KidsOfflineSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(wifiOnly())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Jednorázově, až bude Wi-Fi (appka spuštěná bez Wi-Fi → dožene se po připojení). */
        fun runWhenOnWifi(context: Context) {
            val request = OneTimeWorkRequestBuilder<KidsOfflineSyncWorker>().setConstraints(wifiOnly()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
