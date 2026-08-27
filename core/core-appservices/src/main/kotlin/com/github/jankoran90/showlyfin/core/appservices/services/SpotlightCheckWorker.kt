package com.github.jankoran90.showlyfin.core.appservices.services

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
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.data.uploader.SpotlightRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "showlyfin_spotlight"
private const val CHANNEL_NAME = "Novinky od tvůrců"
private const val NOTIFICATION_ID = 4713
private const val WORK_NAME = "showlyfin_spotlight_check"
private const val PREFS = "trakt_prefs"
private const val KEY_LAST_BATCH = "spotlight_last_notified_batch"

/** Nastavení: upozorňovat na novinky od sledovaných tvůrců (default zapnuto). */
const val KEY_SPOTLIGHT_NOTIFY = "spotlight_notify_enabled"

/**
 * SPOTLIGHT (FLM-02) — týdenní kontrola nových titulů od sledovaných tvůrců (bez FCM).
 *
 * Zadání usera (2026-08-27): „1 - nova sekce novinky, systemove upozorneni · 2 - 1x tydne v patek ·
 * 3 - [dětský profil si tvůrce ukládat může, upozornění dostane jen dospělý]".
 *
 * Mechanika je 1:1 s [CuratorCheckWorker]: zeptá se `GET /spotlight/status` na ID týdenní dávky a
 * když je jiné než naposledy notifikované (per-device), pošle lokální upozornění s prokliknutím do
 * sekce „Novinky" ([ListenNavSignal.EXTRA_OPEN_NOVINKY]). Dávka se na serveru otáčí V PÁTEK
 * (`CURATOR_ROTATE_DAY`) — sdílíme tentýž takt, ať v appce neexistují dva různé „týdny".
 *
 * Perioda workeru je 1 den ZÁMĚRNĚ, ne 7: WorkManager periodické okno neumí zacílit na den v týdnu
 * a při vypnutém/nespuštěném telefonu by se týdenní běh mohl minout. Rozhoduje `batchId` ze serveru,
 * takže častější dotaz nic nezopakuje — upozornění přijde stejně jednou týdně, jen spolehlivěji.
 */
class SpotlightCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun spotlightRepository(): SpotlightRepository
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SPOTLIGHT_NOTIFY, true)) return Result.success()

        val repo = EntryPointAccessors.fromApplication(ctx, Deps::class.java).spotlightRepository()
        // „Zatím jen dospělý profil" — dětský profil si tvůrce ukládat MŮŽE (hvězdička funguje),
        // jen mu nechodí upozornění. Kontroluje se při KAŽDÉM běhu, ne jen při enqueue.
        if (repo.isChildProfile()) return Result.success()

        val status = repo.status() ?: return Result.retry()
        val batchId = status.batchId ?: return Result.success()
        val last = prefs.getString(KEY_LAST_BATCH, null)
        when {
            // Baseline při instalaci: první běh jen zapamatuj, ať uživatel nedostane upozornění
            // na dávku, kterou už dávno „propásl". Stejný postup jako u kurátora.
            last == null -> prefs.edit().putString(KEY_LAST_BATCH, batchId).apply()
            last != batchId -> {
                if (status.count > 0) notifyNewTitles(status.count)
                prefs.edit().putString(KEY_LAST_BATCH, batchId).apply()
            }
        }
        return Result.success()
    }

    private fun notifyNewTitles(count: Int) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Nové filmy a seriály od tvůrců, které sleduješ"
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(ctx, AppServices.config.launcherActivityClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(ListenNavSignal.EXTRA_OPEN_NOVINKY, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(AppServices.config.notificationIconRes)
            .setContentTitle("Novinky od tvůrců")
            .setContentText(novinkyText(count))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
            .onFailure { Timber.w(it, "notify spotlight failed") }
    }

    companion object {
        /** Česky správné počítání: 1 titul / 2–4 tituly / 5+ titulů. */
        internal fun novinkyText(count: Int): String = when {
            count == 1 -> "Sledovaný tvůrce má nový titul — klepni pro zobrazení"
            count in 2..4 -> "Sledovaní tvůrci mají $count nové tituly — klepni pro zobrazení"
            else -> "Sledovaní tvůrci mají $count nových titulů — klepni pro zobrazení"
        }

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<SpotlightCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
