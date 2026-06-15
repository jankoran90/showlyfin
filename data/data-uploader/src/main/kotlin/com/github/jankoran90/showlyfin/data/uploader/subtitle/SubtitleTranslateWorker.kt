package com.github.jankoran90.showlyfin.data.uploader.subtitle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Plan LINGUA Fáze 3 — AI překlad titulků EN→CS běžící NA POZADÍ (přežije odchod z přehrávače i
 * zamčení telefonu). Polluje backend job, výsledek zapíše do [SubtitleTranslationStore] (live update
 * + persist `ai_sub_<key>` pro auto-nasazení po návratu) a po dokončení pošle notifikaci.
 *
 * Plain [CoroutineWorker] (jako `UpdateCheckWorker`) — závislosti přes Hilt [EntryPointAccessors],
 * notifikace přes generický launch intent (worker žije v `data-uploader`, nevidí MainActivity).
 */
class SubtitleTranslateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun uploaderDataSource(): UploaderRemoteDataSource
        fun translationStore(): SubtitleTranslationStore
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val deps = EntryPointAccessors.fromApplication(ctx, Deps::class.java)
        val ds = deps.uploaderDataSource()
        val store = deps.translationStore()

        val base = inputData.getString(K_BASE).orEmpty()
        val cookie = inputData.getString(K_COOKIE).orEmpty()
        val imdb = inputData.getString(K_IMDB).orEmpty()
        val title = inputData.getString(K_TITLE).orEmpty()
        val season = inputData.getInt(K_SEASON, -1).takeIf { it >= 0 }
        val episode = inputData.getInt(K_EPISODE, -1).takeIf { it >= 0 }
        if (base.isBlank() || imdb.isBlank()) return Result.failure()

        val key = store.keyOf(imdb, season, episode)
        store.setRunning(key)

        val started = runCatching {
            ds.startSubtitleTranslate(base, cookie, imdb, season, episode)
        }.getOrElse { e ->
            Timber.w(e, "[Lingua] start překladu na pozadí selhal imdb=$imdb")
            store.setError(key, e.message ?: "Překlad se nepodařilo spustit")
            notify(ctx, title, ok = false)
            return Result.success()
        }

        val jobId = started.jobId.ifBlank { started.subId }
        var status = started.status
        var subId = started.subId
        var error = started.error
        var waitedMs = 0L
        while (status == "running" && jobId.isNotBlank() && waitedMs < MAX_WAIT_MS) {
            delay(POLL_MS)
            waitedMs += POLL_MS
            val s = runCatching { ds.getSubtitleTranslateStatus(base, cookie, jobId) }.getOrNull() ?: continue
            status = s.status; subId = s.subId; error = s.error
        }

        return if (status == "done" && subId.isNotBlank()) {
            store.markDone(key, subId)
            notify(ctx, title, ok = true)
            Timber.i("[Lingua] překlad na pozadí hotový imdb=$imdb → $subId")
            Result.success()
        } else {
            val msg = error
                ?: if (status == "running") "Překlad trvá déle než obvykle — zkus to znovu" else "Překlad se nezdařil"
            store.setError(key, msg)
            notify(ctx, title, ok = false)
            Timber.w("[Lingua] překlad na pozadí neúspěšný imdb=$imdb status=$status err=$error")
            Result.success()
        }
    }

    private fun notify(ctx: Context, title: String, ok: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Titulky", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Dokončení AI překladu titulků do češtiny"
                },
            )
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                ctx, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val filmName = title.ifBlank { "film" }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(if (ok) "Titulky přeložené" else "Překlad titulků se nezdařil")
            .setContentText(
                if (ok) "$filmName — čeština je připravená" else "$filmName — zkus to prosím znovu",
            )
            .setAutoCancel(true)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
        runCatching { nm.notify(NOTIF_BASE_ID + (title.hashCode() and 0xffff), notification) }
            .onFailure { Timber.w(it, "[Lingua] notifikace selhala") }
    }

    companion object {
        private const val CHANNEL_ID = "showlyfin_subtitles"
        private const val NOTIF_BASE_ID = 4720
        private const val POLL_MS = 3000L
        private const val MAX_WAIT_MS = 600_000L  // 10 min strop (pak je výsledek v cache, stačí znovu)

        private const val K_BASE = "base"
        private const val K_COOKIE = "cookie"
        private const val K_IMDB = "imdb"
        private const val K_TITLE = "title"
        private const val K_SEASON = "season"
        private const val K_EPISODE = "episode"

        /** Zařadí překlad na pozadí. Unikátní per film (KEEP) → dvojí ťuknutí nespustí dva běhy. */
        fun enqueue(
            context: Context,
            base: String,
            cookie: String,
            imdb: String,
            title: String,
            season: Int?,
            episode: Int?,
        ) {
            val data = workDataOf(
                K_BASE to base,
                K_COOKIE to cookie,
                K_IMDB to imdb,
                K_TITLE to title,
                K_SEASON to (season ?: -1),
                K_EPISODE to (episode ?: -1),
            )
            val request = OneTimeWorkRequestBuilder<SubtitleTranslateWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(data)
                .build()
            val workName = "lingua_translate_" +
                if (season != null && episode != null) "${imdb}_s${season}e$episode" else imdb
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        }
    }
}
