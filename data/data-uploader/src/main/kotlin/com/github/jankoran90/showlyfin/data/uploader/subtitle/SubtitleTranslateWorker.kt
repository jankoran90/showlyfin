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
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleTranslateJob
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
        val title = inputData.getString(K_TITLE).orEmpty()
        val continueJobId = inputData.getString(K_CONTINUE_JOB_ID)
        if (base.isBlank()) return Result.failure()

        val key: String
        val started: SubtitleTranslateJob
        if (continueJobId != null) {
            // PROGRESSIVE pokračování (2026-09-16): user si explicitně vyžádal 2. půlku — klíč jde
            // rovnou v datech (žádné imdb/season/episode tady, ty zná jen VM co job odstartoval).
            key = inputData.getString(K_KEY).orEmpty()
            if (key.isBlank()) return Result.failure()
            started = runCatching { ds.continueSubtitleTranslate(base, cookie, continueJobId) }
                .getOrElse { e ->
                    Timber.w(e, "[Lingua] pokračování překladu selhalo job=$continueJobId")
                    store.setError(key, e.message ?: "Pokračování překladu se nepodařilo spustit")
                    notify(ctx, title, ok = false)
                    return Result.success()
                }
        } else {
            val imdb = inputData.getString(K_IMDB).orEmpty()
            val season = inputData.getInt(K_SEASON, -1).takeIf { it >= 0 }
            val episode = inputData.getInt(K_EPISODE, -1).takeIf { it >= 0 }
            val progressive = inputData.getBoolean(K_PROGRESSIVE, false)
            if (imdb.isBlank()) return Result.failure()
            key = store.keyOf(imdb, season, episode)
            store.setRunning(key)
            started = runCatching { ds.startSubtitleTranslate(base, cookie, imdb, season, episode, progressive) }
                .getOrElse { e ->
                    Timber.w(e, "[Lingua] start překladu na pozadí selhal imdb=$imdb")
                    store.setError(key, e.message ?: "Překlad se nepodařilo spustit")
                    notify(ctx, title, ok = false)
                    return Result.success()
                }
        }

        val jobId = started.jobId.ifBlank { started.subId }
        var status = started.status
        var subId = started.subId
        var error = started.error

        // PARTIAL (PROGRESSIVE) = klidový, ne chybový stav — první půlka je hotová a stažitelná,
        // druhá čeká na uživatelův pokyn ([enqueueContinue]). Jen zapiš a skonči, NEpolluj donekonečna
        // (na "partial" už appka sama nic dalšího nedostane, dokud nepřijde continue).
        if (status == "partial" && subId.isNotBlank()) {
            store.markPartial(key, subId)
            Timber.i("[Lingua] první půlka hotová (čeká na pokyn) → $subId")
            return Result.success()
        }

        if (status == "running") {
            store.updateRunningProgress(key, subId.ifBlank { null }, started.okCount, started.totalChunks)
        }

        var waitedMs = 0L
        while (status == "running" && jobId.isNotBlank() && waitedMs < MAX_WAIT_MS) {
            delay(POLL_MS)
            waitedMs += POLL_MS
            val s = runCatching { ds.getSubtitleTranslateStatus(base, cookie, jobId) }.getOrNull() ?: continue
            status = s.status; subId = s.subId; error = s.error
            if (status == "partial" && subId.isNotBlank()) {
                store.markPartial(key, subId)
                Timber.i("[Lingua] první půlka hotová (čeká na pokyn) → $subId")
                return Result.success()
            }
            // PRŮBĚŽNĚ (2026-09-16): server ukládá po každé dávce, i za "running" — jakmile má
            // subId (aspoň 1 dávka hotová), VM smí rozpracovaný obsah stahovat/přenačítat živě.
            if (status == "running") {
                store.updateRunningProgress(key, subId.ifBlank { null }, s.okCount, s.totalChunks)
            }
        }

        return if (status == "done" && subId.isNotBlank()) {
            store.markDone(key, subId)
            notify(ctx, title, ok = true)
            Timber.i("[Lingua] překlad na pozadí hotový → $subId")
            Result.success()
        } else {
            val msg = error
                ?: if (status == "running") "Překlad trvá déle než obvykle — zkus to znovu" else "Překlad se nezdařil"
            store.setError(key, msg)
            notify(ctx, title, ok = false)
            Timber.w("[Lingua] překlad na pozadí neúspěšný status=$status err=$error")
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
        private const val K_PROGRESSIVE = "progressive"
        private const val K_CONTINUE_JOB_ID = "continue_job_id"
        private const val K_KEY = "key"

        /** Zařadí překlad na pozadí. Unikátní per film (KEEP) → dvojí ťuknutí nespustí dva běhy.
         *  [progressive] (2026-09-16, jen LINGUA-YT) — server smí vrátit "partial" (1. půlka),
         *  viz [SubtitleTranslationStore.State.Partial]. */
        fun enqueue(
            context: Context,
            base: String,
            cookie: String,
            imdb: String,
            title: String,
            season: Int?,
            episode: Int?,
            progressive: Boolean = false,
        ) {
            val data = workDataOf(
                K_BASE to base,
                K_COOKIE to cookie,
                K_IMDB to imdb,
                K_TITLE to title,
                K_SEASON to (season ?: -1),
                K_EPISODE to (episode ?: -1),
                K_PROGRESSIVE to progressive,
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

        /** PROGRESSIVE pokračování — 2. půlka na explicitní pokyn usera (řídí spotřebu mozek kvóty).
         *  [jobId] = subId z [SubtitleTranslationStore.State.Partial], [key] stejný jako u [enqueue]. */
        fun enqueueContinue(
            context: Context,
            base: String,
            cookie: String,
            jobId: String,
            key: String,
            title: String,
        ) {
            val data = workDataOf(
                K_BASE to base,
                K_COOKIE to cookie,
                K_TITLE to title,
                K_CONTINUE_JOB_ID to jobId,
                K_KEY to key,
            )
            val request = OneTimeWorkRequestBuilder<SubtitleTranslateWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("lingua_translate_continue_$jobId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
