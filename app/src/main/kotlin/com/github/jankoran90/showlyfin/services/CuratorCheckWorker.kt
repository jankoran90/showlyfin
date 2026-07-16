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
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "showlyfin_curator"
private const val CHANNEL_NAME = "Doporučení Pro tebe"
private const val NOTIFICATION_ID = 4712
private const val WORK_NAME = "showlyfin_curator_check"
private const val PREFS = "trakt_prefs"
private const val KEY_LAST_BATCH = "curator_last_notified_batch"

/**
 * BESPOKE (SHW-95) F4 — periodická kontrola týdenní obměny doporučení „Pro tebe" (bez FCM). Zeptá se
 * `GET /curator/status` na ID aktuální dávky; když je jiné než naposledy notifikované (per-device),
 * pošle lokální upozornění s prokliknutím do sekce „Pro tebe" ([ListenNavSignal.EXTRA_OPEN_FORYOU]).
 * Vzor: [UpdateCheckWorker] (jen bez stahování/instalace). Doporučení se v sekci HROMADÍ (F2) — obměna
 * jen přidá čerstvou dávku navrch, nic nemaže.
 */
class CuratorCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val base = prefs.getString("uploader_base_url", "")?.trim()?.trimEnd('/').orEmpty()
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        if (base.isBlank()) return Result.success()   // nepřihlášeno → nic

        val batchId = fetchBatchId(base, cookie) ?: return Result.retry()
        val last = prefs.getString(KEY_LAST_BATCH, null)
        when {
            last == null -> prefs.edit().putString(KEY_LAST_BATCH, batchId).apply()  // baseline, neruš při instalaci
            last != batchId -> {
                notifyNewBatch()
                prefs.edit().putString(KEY_LAST_BATCH, batchId).apply()
            }
        }
        return Result.success()
    }

    private suspend fun fetchBatchId(base: String, cookie: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$base/curator/status")
                .apply { if (cookie.isNotBlank()) header("Cookie", "session=$cookie") }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                JSONObject(body).optString("batchId").takeIf { it.isNotBlank() }
            }
        }.onFailure { Timber.w(it, "[BESPOKE] curator status selhal") }.getOrNull()
    }

    private fun notifyNewBatch() {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Upozornění na čerstvá kurátorská doporučení"
            nm.createNotificationChannel(channel)
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(ListenNavSignal.EXTRA_OPEN_FORYOU, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nová doporučení Pro tebe")
            .setContentText("Kurátor pro tebe vybral čerstvé filmy — klepni pro zobrazení")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
            .onFailure { Timber.w(it, "notify curator failed") }
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<CuratorCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
