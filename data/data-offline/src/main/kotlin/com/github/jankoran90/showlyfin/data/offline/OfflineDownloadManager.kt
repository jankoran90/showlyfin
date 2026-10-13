package com.github.jankoran90.showlyfin.data.offline

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import java.io.FileOutputStream
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plan NOMAD (SHW-60) — jádro offline stahování FILMŮ/EPIZOD do telefonu („na chatu bez wifi").
 *
 * Vzor = [com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager]: index v
 * SharedPreferences (Gson), OkHttp streaming s progressem do `.part` → rename, flows [states]
 * a [downloads]. Rozdíly proti audioknihám:
 *  - velká video soubora → běh drží [OfflineDownloadService] (foreground), proto se po [enqueue]
 *    rovnou nastartuje; manager dělá samotné stahování v [scope] a service jen drží proces +
 *    notifikaci dle [states].
 *  - **range/resume**: při restartu procesu se nedostažený `.part` dotáhne přes `Range: bytes=`.
 *  - vlastní prefs soubor `offline_prefs` (přežije Trakt logout — viz subtitle_prefs).
 *
 * Rozhodnutí usera 2026-06-16: BEZ stropu velikosti (i 4K REMUX), interní úložiště, žádný
 * auto-úklid — jen [isLowOnSpace] varování (řeší UI), mazání ručně.
 */
@Singleton
class OfflineDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("offline_prefs", Context.MODE_PRIVATE)

    // Jen 1 video naráz — velká soubora paralelně by zahltila telefon i síť.
    private val gate = Semaphore(1)

    // Bez callTimeout — base klient má 60 s, což velké stažení zabije.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofMinutes(30))
            .writeTimeout(Duration.ofMinutes(30))
            .build()
    }

    private val videoDir: File by lazy { File(context.filesDir, "offline_video").apply { mkdirs() } }
    private val subDir: File by lazy { File(context.filesDir, "offline_subs").apply { mkdirs() } }
    private val posterDir: File by lazy { File(context.filesDir, "offline_posters").apply { mkdirs() } }

    private val index: MutableMap<String, OfflineDownload> = ConcurrentHashMap(loadIndex())
    private val requests = ConcurrentHashMap<String, OfflineRequest>()
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, OfflineState>>(
        index.mapValues { OfflineState(OfflineStatus.DOWNLOADED, 1f, it.value.sizeBytes, it.value.sizeBytes) },
    )
    /** Per-položka stav (UI badge v Detailu / progress v sekci Stažené). */
    val states = _states.asStateFlow()

    private val _downloads = MutableStateFlow(sortedDownloads())
    /** Seznam dokončených stažení (obrazovka „Stažené"). */
    val downloads = _downloads.asStateFlow()

    /**
     * Slovo 2026-10-13 (sekce Stahování) — požadavky, co ještě nejsou stažené: stahuje se, ve frontě
     * i selhané (dokud je uživatel neodebere / nezkusí znovu). Pořadí = pořadí zařazení (= pořadí
     * stahování, [gate] je férový FIFO semafor).
     */
    private val _pending = MutableStateFlow<List<OfflineRequest>>(emptyList())
    val pending = _pending.asStateFlow()

    private fun addPending(req: OfflineRequest) =
        _pending.update { list -> list.filterNot { it.key == req.key } + req }

    private fun removePending(key: String) = _pending.update { list -> list.filterNot { it.key == key } }

    /** Znovu zkusí selhané stažení (sekce Stahování). */
    fun retry(key: String) {
        val req = _pending.value.firstOrNull { it.key == key } ?: return
        enqueue(req)
    }

    fun stateFor(key: String): OfflineState = _states.value[key] ?: OfflineState()
    fun get(key: String): OfflineDownload? = index[key]
    fun localVideo(key: String): File? = index[key]?.let { File(it.videoPath).takeIf(File::exists) }
    fun isDownloaded(key: String): Boolean = localVideo(key) != null

    /** Titulek pro notifikaci (z čekajícího requestu nebo z indexu). */
    fun titleFor(key: String): String = requests[key]?.title ?: index[key]?.title ?: "Položka"

    /** Počet aktivních (QUEUED/DOWNLOADING) — řídí životnost foreground služby. */
    fun activeCount(): Int =
        _states.value.values.count { it.status == OfflineStatus.DOWNLOADING || it.status == OfflineStatus.QUEUED }

    /** Spustí stažení (idempotentně — už stažené / stahující se přeskočí). */
    fun enqueue(req: OfflineRequest) {
        val key = req.key
        if (isDownloaded(key)) return
        if (jobs[key]?.isActive == true) return
        requests[key] = req
        addPending(req)
        setState(key, OfflineState(OfflineStatus.QUEUED))
        OfflineDownloadService.start(context)
        jobs[key] = scope.launch {
            runCatching { gate.withPermit { doDownload(req) } }
                .onSuccess { dl ->
                    index[key] = dl
                    persistIndex()
                    setState(key, OfflineState(OfflineStatus.DOWNLOADED, 1f, dl.sizeBytes, dl.sizeBytes))
                    removePending(key)
                    refreshDownloads()
                }
                .onFailure { e ->
                    if (e is CancellationException) {
                        setState(key, OfflineState(OfflineStatus.NONE))
                        removePending(key)
                    } else {
                        Timber.w(e, "[NOMAD] stažení '%s' selhalo", req.title)
                        setState(key, OfflineState(OfflineStatus.FAILED, error = e.message))
                    }
                }
            jobs.remove(key)
            requests.remove(key)
        }
    }

    private suspend fun doDownload(req: OfflineRequest): OfflineDownload = withContext(Dispatchers.IO) {
        val tmp = partFile(req.key)
        val out = videoFile(req.key)
        val existing = if (tmp.exists()) tmp.length() else 0L

        val builder = Request.Builder().url(req.videoUrl)
        req.headers.forEach { (k, v) -> builder.header(k, v) }
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        // Slovo 2026-10-13: 202 = server soubor teprve skládá (ČT díl) → čekej Retry-After a zkus znovu.
        val prepDeadline = System.currentTimeMillis() + PREPARE_TIMEOUT_MS
        var call = client.newCall(builder.build()).execute()
        while (call.code == 202) {
            val wait = (call.header("Retry-After")?.toLongOrNull() ?: 4L).coerceIn(1L, 30L)
            call.close()
            if (System.currentTimeMillis() > prepDeadline) error("Server soubor nepřipravil včas.")
            setState(req.key, OfflineState(OfflineStatus.DOWNLOADING, 0f, existing, 0L, preparing = true))
            delay(wait * 1000)
            call = client.newCall(builder.build()).execute()
        }
        call.use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val partial = resp.code == 206
            val append = partial && existing > 0
            val body = resp.body ?: error("Prázdná odpověď serveru.")
            val remaining = body.contentLength()
            val grandTotal = if (append && remaining > 0) existing + remaining else remaining
            if (!append) tmp.delete()
            setState(req.key, OfflineState(OfflineStatus.DOWNLOADING, 0f, if (append) existing else 0L, grandTotal))
            FileOutputStream(tmp, append).use { os ->
                body.byteStream().use { ins ->
                    val buf = ByteArray(128 * 1024)
                    var done = if (append) existing else 0L
                    // Rychlost = klouzavý průměr po ~1 s oknech (jednotlivé read() jsou příliš trhané).
                    var windowStart = System.currentTimeMillis()
                    var windowBytes = 0L
                    var speed = 0L
                    var read = ins.read(buf)
                    while (read != -1) {
                        os.write(buf, 0, read)
                        done += read
                        windowBytes += read
                        val now = System.currentTimeMillis()
                        if (now - windowStart >= 1000) {
                            val inst = windowBytes * 1000 / (now - windowStart)
                            speed = if (speed == 0L) inst else (speed * 7 + inst * 3) / 10
                            windowStart = now
                            windowBytes = 0L
                        }
                        if (grandTotal > 0) {
                            val eta = if (speed > 0) (grandTotal - done).coerceAtLeast(0L) / speed else null
                            setState(
                                req.key,
                                OfflineState(
                                    OfflineStatus.DOWNLOADING, (done.toFloat() / grandTotal).coerceIn(0f, 1f), done, grandTotal,
                                    bytesPerSec = speed, etaSec = eta,
                                ),
                            )
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

        val subPath = req.subtitleUrl
            ?.let { url -> runCatching { downloadAux(subFile(req.key), url, req.headers) }.getOrNull() }
        val posterPath = req.posterUrl
            ?.let { url -> runCatching { downloadAux(posterFile(req.key), url, emptyMap()) }.getOrNull() }

        OfflineDownload(
            key = req.key,
            title = req.title,
            subtitle = req.subtitle,
            type = req.type,
            sourceLabel = req.sourceLabel,
            videoPath = out.absolutePath,
            subtitlePath = subPath,
            posterUrl = req.posterUrl,
            posterPath = posterPath,
            imdb = req.imdb,
            tmdb = req.tmdb,
            season = req.season,
            episode = req.episode,
            sizeBytes = out.length(),
            durationSec = req.durationSec,
            description = req.description,
            publishedAt = req.publishedAt,
            sourceKey = req.sourceKey,
            seriesTitle = req.seriesTitle,
        )
    }

    private fun downloadAux(out: File, url: String, headers: Map<String, String>): String {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("Prázdná odpověď.")
            out.outputStream().use { os -> body.byteStream().use { it.copyTo(os) } }
        }
        return out.absolutePath
    }

    /** Zruší probíhající stahování + uklidí `.part`. */
    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
        requests.remove(key)
        removePending(key)
        partFile(key).delete()
        setState(key, OfflineState(OfflineStatus.NONE))
    }

    /** Smaže stažený soubor + titulky + poster + záznam. */
    fun delete(key: String) {
        jobs.remove(key)?.cancel()
        removePending(key)
        index.remove(key)?.let { dl ->
            File(dl.videoPath).delete()
            dl.subtitlePath?.let { File(it).delete() }
            dl.posterPath?.let { File(it).delete() }
        }
        partFile(key).delete()
        persistIndex()
        setState(key, OfflineState(OfflineStatus.NONE))
        refreshDownloads()
    }

    /** Smaže všechna stažení. */
    fun deleteAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        requests.clear()
        _pending.value = emptyList()
        index.clear()
        listOf(videoDir, subDir, posterDir).forEach { d -> d.listFiles()?.forEach { it.delete() } }
        persistIndex()
        _states.value = emptyMap()
        refreshDownloads()
    }

    /**
     * LEVER (SHW-61) L3: smaže jen stažení daných [types] (FILMY/EPIZODY vs. PODCASTY sdílí jeden
     * manager → „Smazat vše" v jedné sekci nesmí smazat druhou). Ostatní typy nechá být.
     */
    fun deleteAll(types: Set<String>) {
        index.values.filter { it.type in types }.map { it.key }.forEach { delete(it) }
    }

    /** Typ položky (z indexu nebo z čekajícího requestu) — pro typové filtrování sekcí. */
    fun typeOf(key: String): String? = index[key]?.type ?: requests[key]?.type

    /** Uloží pozici přehrávání stažené položky (offline resume). */
    fun updateResume(key: String, positionMs: Long) {
        val dl = index[key] ?: return
        index[key] = dl.copy(lastPlayedAt = System.currentTimeMillis(), resumePositionMs = positionMs)
        persistIndex()
        refreshDownloads()
    }

    /**
     * RESONANCE (SHW-81) D: doplní popis + datum vydání + klíč zdroje u UŽ stažené epizody, pokud je
     * zatím nemá. Volá se online (spárováno dle klíče s čerstvým feedem) → staré stažené (bez těchto
     * polí, vč. `sourceKey` u epizod stažených před 1.45.144) se dovyplní samy → dětský profil je pak
     * offline filtruje. Nepřepisuje existující hodnoty ani neupravuje jiné položky. `null` args ignoruje.
     */
    fun backfillMeta(key: String, description: String?, publishedAt: Long?, sourceKey: String? = null, seriesTitle: String? = null) {
        val dl = index[key] ?: return
        val newDesc = dl.description ?: description?.takeIf { it.isNotBlank() }
        val newPub = dl.publishedAt ?: publishedAt?.takeIf { it > 0L }
        val newSrc = dl.sourceKey ?: sourceKey?.takeIf { it.isNotBlank() }
        val newSeries = dl.seriesTitle ?: seriesTitle?.takeIf { it.isNotBlank() }
        if (newDesc == dl.description && newPub == dl.publishedAt && newSrc == dl.sourceKey && newSeries == dl.seriesTitle) return
        index[key] = dl.copy(description = newDesc, publishedAt = newPub, sourceKey = newSrc, seriesTitle = newSeries)
        persistIndex()
        refreshDownloads()
    }

    // ── Úložiště (varování, ne strop — rozhodnutí usera 2026-06-16) ─────────────
    /** Volné místo v interním úložišti (bajty). */
    fun freeBytes(): Long = runCatching {
        val st = StatFs(context.filesDir.absolutePath)
        st.availableBlocksLong * st.blockSizeLong
    }.getOrDefault(Long.MAX_VALUE)

    /** Součet velikostí stažených položek. */
    fun usedBytes(): Long = index.values.sumOf { it.sizeBytes }

    /** Součet velikostí stažených položek daných [types] (per-sekce „Zabráno"). */
    fun usedBytes(types: Set<String>): Long = index.values.filter { it.type in types }.sumOf { it.sizeBytes }

    /** Málo místa = pod prahem (default 2 GB) → UI varuje. */
    fun isLowOnSpace(thresholdBytes: Long = 2L * 1024 * 1024 * 1024): Boolean = freeBytes() < thresholdBytes

    private fun setState(id: String, s: OfflineState) = _states.update { it + (id to s) }

    private fun refreshDownloads() {
        _downloads.value = sortedDownloads()
    }

    private fun sortedDownloads(): List<OfflineDownload> =
        index.values.sortedByDescending { it.addedAt }

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun videoFile(key: String) = File(videoDir, "${safe(key)}.video")
    private fun partFile(key: String) = File(videoDir, "${safe(key)}.part")
    private fun subFile(key: String) = File(subDir, "${safe(key)}.srt")
    private fun posterFile(key: String) = File(posterDir, "${safe(key)}.jpg")

    private fun loadIndex(): Map<String, OfflineDownload> {
        val json = prefs.getString(KEY_INDEX, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, OfflineDownload>>() {}.type
            gson.fromJson<Map<String, OfflineDownload>>(json, type)
                .filterValues { File(it.videoPath).exists() }
        }.getOrElse { emptyMap() }
    }

    private fun persistIndex() {
        prefs.edit { putString(KEY_INDEX, gson.toJson(index)) }
    }

    companion object {
        private const val KEY_INDEX = "offline_downloads"
        /** Jak dlouho čekat, než server soubor připraví (ČT díl ~1,5 min, při frontě na serveru víc). */
        private const val PREPARE_TIMEOUT_MS = 20 * 60 * 1000L
    }
}
