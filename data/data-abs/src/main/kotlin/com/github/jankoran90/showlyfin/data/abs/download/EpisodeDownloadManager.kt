package com.github.jankoran90.showlyfin.data.abs.download

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.AbsTrack
import com.github.jankoran90.showlyfin.data.abs.model.DownloadState
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.data.abs.model.EpisodeDownload
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Offline stažení podcast epizod (Plan MARCONI Fáze E). Stáhne audio soubor epizody do
 * `filesDir/podcast_episodes`, drží per-epizoda stav ([states]), perzistuje index stažení do
 * SharedPreferences (Gson) a po restartu ho obnoví (ověří existenci souborů). Při přehrávání
 * stažené epizody se použije lokální soubor → funguje i offline (viz [offlinePlayback]).
 *
 * URL audio souboru získáváme z ABS play session ([AbsRepository.startEpisodePlayback]), která
 * vrací plné URL s tokenem (stejný zdroj, jaký jinak streamuje přehrávač).
 */
@Singleton
class EpisodeDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repo: AbsRepository,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
    private val gson: Gson,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Vlastní klient bez callTimeout — base klient má callTimeout 60 s, což by velké stažení zabilo.
    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(Duration.ofMinutes(10))
            .writeTimeout(Duration.ofMinutes(10))
            .build()
    }

    private val dir: File by lazy { File(context.filesDir, "podcast_episodes").apply { mkdirs() } }

    /** Index stažených epizod (episodeId → záznam). ConcurrentHashMap kvůli IO/UI přístupu. */
    private val index: MutableMap<String, EpisodeDownload> = ConcurrentHashMap(loadIndex())

    /** Probíhající stahování (episodeId → Job) pro zrušení. */
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(
        index.mapValues { DownloadState(DownloadStatus.DOWNLOADED) },
    )
    /** Per-epizoda stav stažení (UI badge). */
    val states = _states.asStateFlow()

    private val _downloads = MutableStateFlow(sortedDownloads())
    /** Seznam všech stažených epizod (obrazovka „Stažené"). */
    val downloads = _downloads.asStateFlow()

    fun stateFor(episodeId: String): DownloadState = _states.value[episodeId] ?: DownloadState()

    /** Lokální soubor stažené epizody, pokud existuje. */
    fun localFile(episodeId: String): File? =
        index[episodeId]?.let { File(it.filePath).takeIf(File::exists) }

    /** Spustí stažení epizody (idempotentně — už stažená/stahující se přeskočí). */
    fun download(episode: PodcastEpisode, podcastTitle: String?, coverUrl: String?) {
        val id = episode.id
        if (localFile(id) != null) return
        if (jobs[id]?.isActive == true) return
        setState(id, DownloadState(DownloadStatus.DOWNLOADING, 0f))
        jobs[id] = scope.launch {
            runCatching { doDownload(episode, podcastTitle, coverUrl) }
                .onSuccess { dl ->
                    index[id] = dl
                    persistIndex()
                    setState(id, DownloadState(DownloadStatus.DOWNLOADED))
                    refreshDownloads()
                }
                .onFailure { e ->
                    Timber.w(e, "[ABS] stažení epizody '%s' selhalo", episode.title)
                    tempFile(id).delete()
                    setState(id, DownloadState(DownloadStatus.FAILED))
                }
            jobs.remove(id)
        }
    }

    private suspend fun doDownload(
        episode: PodcastEpisode,
        podcastTitle: String?,
        coverUrl: String?,
    ): EpisodeDownload = withContext(Dispatchers.IO) {
        val pb = repo.startEpisodePlayback(episode.itemId, episode.id)
        val track = pb.tracks.firstOrNull() ?: error("Epizoda nemá audio stopu.")
        val tmp = tempFile(episode.id)
        val out = outFile(episode.id)
        client.newCall(Request.Builder().url(track.url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("Prázdná odpověď serveru.")
            val total = body.contentLength()
            tmp.outputStream().use { os ->
                body.byteStream().use { ins ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var read = ins.read(buf)
                    while (read != -1) {
                        os.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            setState(episode.id, DownloadState(DownloadStatus.DOWNLOADING, (done.toFloat() / total).coerceIn(0f, 1f)))
                        }
                        read = ins.read(buf)
                    }
                }
            }
        }
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        EpisodeDownload(
            episodeId = episode.id,
            itemId = episode.itemId,
            title = episode.title,
            podcastTitle = podcastTitle,
            coverUrl = coverUrl,
            filePath = out.absolutePath,
            durationSec = episode.durationSec.takeIf { it > 0 } ?: pb.durationSec,
            sizeBytes = out.length(),
        )
    }

    /** Zruší probíhající stahování + uklidí dočasný soubor. */
    fun cancel(episodeId: String) {
        jobs.remove(episodeId)?.cancel()
        tempFile(episodeId).delete()
        setState(episodeId, DownloadState(DownloadStatus.NONE))
    }

    /** Smaže stažený soubor + záznam v indexu. */
    fun delete(episodeId: String) {
        jobs.remove(episodeId)?.cancel()
        index.remove(episodeId)?.let { File(it.filePath).delete() }
        tempFile(episodeId).delete()
        outFile(episodeId).delete()
        persistIndex()
        setState(episodeId, DownloadState(DownloadStatus.NONE))
        refreshDownloads()
    }

    /** Smaže všechna stažení. */
    fun deleteAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        index.values.forEach { File(it.filePath).delete() }
        index.clear()
        dir.listFiles()?.forEach { it.delete() }
        persistIndex()
        _states.value = emptyMap()
        refreshDownloads()
    }

    /**
     * Čistě lokální play session pro offline přehrání (bez serveru). sessionId == "" → přehrávací
     * service nesynchronizuje pozici (offline). Vrátí null, pokud epizoda není stažená.
     */
    fun offlinePlayback(episodeId: String): AbsPlayback? {
        val dl = index[episodeId] ?: return null
        val file = File(dl.filePath).takeIf(File::exists) ?: return null
        return AbsPlayback(
            sessionId = "",
            title = dl.title,
            author = dl.podcastTitle,
            coverUrl = dl.coverUrl,
            tracks = listOf(AbsTrack(index = 0, url = Uri.fromFile(file).toString(), startOffsetSec = 0.0, durationSec = dl.durationSec)),
            startPositionSec = 0.0,
            durationSec = dl.durationSec,
            chapters = emptyList(),
        )
    }

    private fun setState(id: String, s: DownloadState) {
        _states.update { it + (id to s) }
    }

    private fun refreshDownloads() {
        _downloads.value = sortedDownloads()
    }

    private fun sortedDownloads(): List<EpisodeDownload> =
        index.values.sortedWith(compareBy({ it.podcastTitle ?: "" }, { it.title }))

    private fun outFile(id: String) = File(dir, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.audio")
    private fun tempFile(id: String) = File(dir, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.part")

    private fun loadIndex(): Map<String, EpisodeDownload> {
        val json = prefs.getString(KEY_INDEX, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, EpisodeDownload>>() {}.type
            gson.fromJson<Map<String, EpisodeDownload>>(json, type)
                .filterValues { File(it.filePath).exists() }
        }.getOrElse { emptyMap() }
    }

    private fun persistIndex() {
        prefs.edit { putString(KEY_INDEX, gson.toJson(index)) }
    }

    companion object {
        private const val KEY_INDEX = "abs_episode_downloads"
    }
}
