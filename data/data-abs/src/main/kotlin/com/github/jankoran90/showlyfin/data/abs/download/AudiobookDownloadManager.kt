package com.github.jankoran90.showlyfin.data.abs.download

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.content.edit
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsPlayback
import com.github.jankoran90.showlyfin.data.abs.model.AbsTrack
import com.github.jankoran90.showlyfin.data.abs.model.AudiobookDownload
import com.github.jankoran90.showlyfin.data.abs.model.DownloadState
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.data.abs.model.LocalAudiobookTrack
import com.github.jankoran90.showlyfin.data.abs.model.displayCover
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * Offline stažení CELÉ audioknihy (Plan CADENCE Fáze D). Audiokniha má v ABS víc audio souborů
 * (stop) — stáhne je všechny do `filesDir/audiobooks/<itemId>/`, drží per-kniha stav ([states]),
 * perzistuje index (Gson) a po restartu ho obnoví (ověří existenci všech souborů). Při přehrávání
 * stažené knihy přehrávač použije lokální soubory → funguje offline vč. skoku na kapitolu
 * (viz [offlineAudiobookPlayback]).
 *
 * URL stop bereme z ABS play session ([AbsRepository.startPlayback]) — plné URL s tokenem, stejný
 * zdroj, jaký jinak streamuje přehrávač. Sourozenec [EpisodeDownloadManager] (per-epizoda).
 */
@Singleton
class AudiobookDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repo: AbsRepository,
    private val absPrefs: AbsPreferences,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
    private val gson: Gson,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val gate by lazy { Semaphore(absPrefs.maxConcurrentDownloads.coerceIn(1, 5)) }

    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(Duration.ofMinutes(10))
            .writeTimeout(Duration.ofMinutes(10))
            .build()
    }

    private val dir: File by lazy { File(context.filesDir, "audiobooks").apply { mkdirs() } }

    /** Index stažených audioknih (itemId → záznam). */
    private val index: MutableMap<String, AudiobookDownload> = ConcurrentHashMap(loadIndex())

    /** Probíhající stahování (itemId → Job) pro zrušení. */
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(
        index.mapValues { DownloadState(DownloadStatus.DOWNLOADED) },
    )
    /** Per-kniha stav stažení (UI řádek v detailu). */
    val states = _states.asStateFlow()

    private val _downloads = MutableStateFlow(sortedDownloads())
    /** Seznam všech stažených audioknih (offline police v Poslechu, Plan CASTAWAY CA-2). */
    val downloads = _downloads.asStateFlow()

    fun stateFor(itemId: String): DownloadState = _states.value[itemId] ?: DownloadState()

    fun isDownloaded(itemId: String): Boolean = offlineAudiobookPlayback(itemId) != null

    /** Lokální záznam stažené knihy (pro offline detail), nebo null. */
    fun downloadRecord(itemId: String): AudiobookDownload? = index[itemId]

    /** Spustí stažení celé audioknihy (idempotentně — už stažená/stahující se přeskočí). */
    fun download(itemId: String, title: String, author: String?, coverUrl: String?) {
        if (isDownloaded(itemId)) return
        if (jobs[itemId]?.isActive == true) return
        setState(itemId, DownloadState(DownloadStatus.DOWNLOADING, 0f))
        jobs[itemId] = scope.launch {
            runCatching { doDownload(itemId, title, author, coverUrl) }
                .onSuccess { dl ->
                    index[itemId] = dl
                    persistIndex()
                    setState(itemId, DownloadState(DownloadStatus.DOWNLOADED))
                    refreshDownloads()
                }
                .onFailure { e ->
                    Timber.w(e, "[ABS] stažení audioknihy '%s' selhalo", title)
                    itemDir(itemId).deleteRecursively()
                    setState(itemId, DownloadState(DownloadStatus.FAILED))
                }
            jobs.remove(itemId)
        }
    }

    private suspend fun doDownload(
        itemId: String,
        title: String,
        author: String?,
        coverUrl: String?,
    ): AudiobookDownload = withContext(Dispatchers.IO) {
        if (absPrefs.downloadWifiOnly && !isOnWifi()) {
            error("Stahování je povolené jen přes Wi-Fi (viz Nastavení → Poslech).")
        }
        gate.withPermit { downloadLocked(itemId, title, author, coverUrl) }
    }

    private suspend fun downloadLocked(
        itemId: String,
        title: String,
        author: String?,
        coverUrl: String?,
    ): AudiobookDownload = withContext(Dispatchers.IO) {
        val pb = repo.startPlayback(itemId)
        require(pb.tracks.isNotEmpty()) { "Audiokniha nemá audio stopu." }
        val target = itemDir(itemId).apply { mkdirs() }
        // Progres vážíme délkou stop, aby ukazatel rostl plynule přes celou knihu.
        val totalDur = pb.tracks.sumOf { it.durationSec }.coerceAtLeast(1.0)
        var doneDur = 0.0
        val local = pb.tracks.map { t ->
            val out = File(target, "track_${t.index}.audio")
            val tmp = File(target, "track_${t.index}.part")
            downloadFile(t.url, tmp, out) { frac ->
                val overall = ((doneDur + frac * t.durationSec) / totalDur).coerceIn(0.0, 1.0)
                setState(itemId, DownloadState(DownloadStatus.DOWNLOADING, overall.toFloat()))
            }
            doneDur += t.durationSec
            LocalAudiobookTrack(t.index, out.absolutePath, t.startOffsetSec, t.durationSec)
        }
        // Plan CASTAWAY CA-4 — obal stáhnout lokálně, ať je vidět i offline (best-effort).
        val localCover = coverUrl?.let {
            runCatching { downloadCover(it, File(target, "cover.img")) }.getOrNull()
        }
        AudiobookDownload(
            itemId = itemId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            durationSec = pb.durationSec,
            chapters = pb.chapters,
            tracks = local,
            sizeBytes = local.sumOf { File(it.filePath).length() },
            localCoverPath = localCover,
        )
    }

    /** Stáhne obal do [out], vrátí absolutní cestu (Plan CASTAWAY CA-4). */
    private fun downloadCover(url: String, out: File): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("Prázdná odpověď serveru.")
            out.outputStream().use { os -> body.byteStream().use { it.copyTo(os) } }
        }
        return out.absolutePath
    }

    /** Stáhne jeden soubor do [tmp], po dokončení přejmenuje na [out]. [onProgress] = 0..1 pro stopu. */
    private fun downloadFile(url: String, tmp: File, out: File, onProgress: (Float) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
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
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        read = ins.read(buf)
                    }
                }
            }
        }
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
    }

    fun cancel(itemId: String) {
        jobs.remove(itemId)?.cancel()
        itemDir(itemId).deleteRecursively()
        setState(itemId, DownloadState(DownloadStatus.NONE))
    }

    fun delete(itemId: String) {
        jobs.remove(itemId)?.cancel()
        index.remove(itemId)
        itemDir(itemId).deleteRecursively()
        persistIndex()
        setState(itemId, DownloadState(DownloadStatus.NONE))
        refreshDownloads()
    }

    fun deleteAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        index.clear()
        dir.listFiles()?.forEach { it.deleteRecursively() }
        persistIndex()
        _states.value = emptyMap()
        refreshDownloads()
    }

    /**
     * Čistě lokální play session pro offline přehrání audioknihy. Vrátí null, pokud kniha není
     * stažená nebo některý soubor chybí. `sessionId == ""` → service nesynchronizuje pozici (offline).
     */
    fun offlineAudiobookPlayback(itemId: String): AbsPlayback? {
        val dl = index[itemId] ?: return null
        val tracks = dl.tracks.map { lt ->
            val f = File(lt.filePath).takeIf(File::exists) ?: return null
            AbsTrack(
                index = lt.index,
                url = Uri.fromFile(f).toString(),
                startOffsetSec = lt.startOffsetSec,
                durationSec = lt.durationSec,
            )
        }
        if (tracks.isEmpty()) return null
        return AbsPlayback(
            sessionId = "",
            title = dl.title,
            author = dl.author,
            coverUrl = dl.displayCover(),
            tracks = tracks,
            startPositionSec = 0.0,
            durationSec = dl.durationSec,
            chapters = dl.chapters,
        )
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun setState(id: String, s: DownloadState) {
        _states.update { it + (id to s) }
    }

    private fun refreshDownloads() {
        _downloads.value = sortedDownloads()
    }

    private fun sortedDownloads(): List<AudiobookDownload> =
        index.values.sortedWith(compareBy({ it.author ?: "" }, { it.title }))

    private fun itemDir(itemId: String) = File(dir, itemId.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    private fun loadIndex(): Map<String, AudiobookDownload> {
        val json = prefs.getString(KEY_INDEX, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, AudiobookDownload>>() {}.type
            gson.fromJson<Map<String, AudiobookDownload>>(json, type)
                .filterValues { dl -> dl.tracks.isNotEmpty() && dl.tracks.all { File(it.filePath).exists() } }
        }.getOrElse { emptyMap() }
    }

    private fun persistIndex() {
        prefs.edit { putString(KEY_INDEX, gson.toJson(index)) }
    }

    companion object {
        private const val KEY_INDEX = "abs_audiobook_downloads"
    }
}
