package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.github.jankoran90.showlyfin.feature.listen.service.AudiotekaDownloadService
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AudiotekaRepository
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dovoz koupené knihy z Audioteky do naší knihovny (user 2026-11-18: „po koupi rovnou integrovat nahrávku do appky").
 * Stránka Audioteky běží uvnitř Slova ([AudiotekaWebScreen]); když v Poličce ťukneš na Stáhnout, WebView ohlásí
 * stahování → tady se soubor stáhne s přihlášením z WebView (cookies) do úložiště appky a po volbě knihovny
 * ho převezme stávající nahrávání po kouscích ([AudiobookUploadManager]) → server rozbalí archiv do ABS.
 * Nic nehádá o API Audioteky: používá jen jejich vlastní tlačítko stažení.
 */
@Singleton
class AudiotekaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadManager: AudiobookUploadManager,
    private val absRepo: AbsRepository,
    private val atRepo: AudiotekaRepository,
) {
    sealed interface State {
        data object Idle : State
        data class Downloading(
            val name: String,
            val progress: Float?,
            val bytes: Long,
            val startedAt: Long = System.currentTimeMillis(),
        ) : State
        /**
         * Staženo, čeká na volbu knihovny. [note] = proč předchozí pokus nevyšel (jiné nahrávání, chyba);
         * [suggestion] = knihovna navržená podle kategorie Audioteky (dialog ji předvybere).
         */
        data class Ready(
            val file: File,
            val name: String,
            val titleHint: String?,
            val authorHint: String?,
            val note: String? = null,
            val suggestion: ImportTarget? = null,
        ) : State
        /** Nahrává se do knihovny ([uploadId] = nahrávání v [AudiobookUploadManager]). */
        data class Importing(val ready: Ready, val target: ImportTarget, val uploadId: Long, val startedAt: Long) : State
        data class Done(val name: String, val libraryName: String) : State
        data class Failed(val message: String) : State
    }

    /** Cílová knihovna dovozu; [reason] = kategorie Audioteky, podle které ji Slovo navrhlo (null = vybral user). */
    data class ImportTarget(val libraryId: String, val libraryName: String, val reason: String?)

    /** Co víme o kupované knize (karta / stránka knihy ve WebView) a kam ji dovézt. */
    data class Hints(
        val title: String? = null,
        val author: String? = null,
        val bookId: String? = null,
        val slug: String? = null,
        /** Knihovna zvolená / navržená už u nákupu (user 2026-11-18 19:53: „návrh … už u platby"). */
        val target: ImportTarget? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    /** Číslo aktuálního stahování: zrušená úloha, která doběhne později, už stav ani soubory nového nepřepíše. */
    private val generation = AtomicInteger(0)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Stažení ohlášené WebView. [hints] = kniha, ze které jsi šel nakupovat (může být prázdné). */
    fun onDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        hints: Hints,
    ) {
        val cur = _state.value
        if (cur is State.Downloading || cur is State.Ready || cur is State.Importing) return
        val titleHint = hints.title
        val authorHint = hints.author
        if (!url.startsWith("http")) {
            _state.value = State.Failed("Tohle stažení Slovo neumí převzít (${url.substringBefore(':')}). Stáhni knihu v prohlížeči a nahraj ji přes Nahrát audioknihu.")
            return
        }
        val name = safeName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        // Název a autora knihy, ze které jsi šel nakupovat, předvyplnit jen když stažený soubor zjevně patří k ní
        // (v Poličce můžeš stáhnout úplně jinou knihu); jinak prázdné → server dohledá podle souboru.
        val matches = (titleHint != null && fileMatchesTitle(name, titleHint)) ||
            (hints.slug != null && slugMatches(name, hints.slug))
        val gen = generation.incrementAndGet()
        // Každé stahování ve vlastní podsložce: zrušené a nové se stejným jménem souboru si nesmažou data.
        val target = File(AudiotekaPrefs.importDir(context), "d$gen-${System.currentTimeMillis()}").apply { mkdirs() }.let { File(it, name) }
        val startedAt = System.currentTimeMillis()
        _state.value = State.Downloading(name, null, 0, startedAt)
        // Služba na popředí: přepnutí do jiné appky jinak stahování utne („Software caused connection abort").
        runCatching { ContextCompat.startForegroundService(context, Intent(context, AudiotekaDownloadService::class.java)) }
            .onFailure { Timber.w(it, "[AUDIOTEKA] služba stahování nenaběhla (stahuje se dál)") }
        job = scope.launch {
            runCatching {
                var done = 0L
                var total = -1L
                var attempt = 0
                // Výpadek sítě uprostřed → navázat od staženého místa (Range), jinak znovu od začátku.
                while (true) {
                    try {
                        val req = Request.Builder().url(url).apply {
                            CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) }
                            userAgent?.let { header("User-Agent", it) }
                            if (done > 0) header("Range", "bytes=$done-")
                        }.build()
                        http.newCall(req).execute().use { res ->
                            if (!res.isSuccessful) error("Audioteka odmítla stažení (HTTP ${res.code}). Zkus se v Poličce přihlásit znovu.")
                            val resumed = done > 0 && res.code == 206
                            if (!resumed) done = 0
                            val body = res.body ?: error("Prázdná odpověď")
                            if (!resumed) total = body.contentLength()
                            var lastEmit = done
                            body.byteStream().use { input ->
                                java.io.FileOutputStream(target, resumed).use { out ->
                                    val buf = ByteArray(256 * 1024)
                                    while (true) {
                                        ensureActive()
                                        val n = input.read(buf)
                                        if (n < 0) break
                                        out.write(buf, 0, n)
                                        done += n
                                        if (done - lastEmit > 1_000_000 && generation.get() == gen) {
                                            lastEmit = done
                                            _state.value = State.Downloading(name, if (total > 0) done.toFloat() / total else null, done, startedAt)
                                        }
                                    }
                                }
                            }
                        }
                        if (total > 0 && done < total) throw java.io.IOException("Spojení skončilo předčasně ($done z $total B)")
                        break
                    } catch (e: java.io.IOException) {
                        attempt++
                        if (attempt > MAX_RETRIES || generation.get() != gen) throw e
                        Timber.w(e, "[AUDIOTEKA] stažení přerušeno, pokus $attempt/$MAX_RETRIES od $done B")
                        delay(2000L * attempt)
                    }
                }
                if (target.length() == 0L) error("Stažený soubor je prázdný")
            }.onSuccess {
                if (generation.get() != gen) {
                    target.parentFile?.deleteRecursively()
                    return@onSuccess
                }
                // Knihovna: zvolená u nákupu (jen když soubor patří ke kupované knize), jinak podle kategorie.
                val dest = hints.target?.takeIf { matches }
                    ?: suggest(name.substringBeforeLast('.'), hints.bookId?.takeIf { matches })
                if (generation.get() != gen) {
                    target.parentFile?.deleteRecursively()
                    return@onSuccess
                }
                val ready = State.Ready(target, name, titleHint.takeIf { matches }, authorHint.takeIf { matches }, suggestion = dest)
                if (dest != null && AudiotekaPrefs.autoImport(context)) {
                    // User 2026-11-18 19:52: „chci koupit a už nic nedělat" → rovnou dovézt. Bez mezistavu Ready:
                    // služba by na něm skončila (Downloading → Importing ji drží na popředí bez přerušení).
                    startImport(ready, dest, ready.titleHint, ready.authorHint)
                } else {
                    _state.value = ready
                }
            }.onFailure { e ->
                target.parentFile?.deleteRecursively()
                if (generation.get() != gen || e is CancellationException) return@onFailure
                Timber.w(e, "[AUDIOTEKA] stažení z Poličky selhalo")
                _state.value = State.Failed(e.message ?: "Stažení selhalo")
            }
        }
    }

    /** Pošle stažený soubor do zvolené knihovny (server archiv rozbalí, metadata dohledá). Volá dialog. */
    fun import(target: ImportTarget, title: String?, author: String?) {
        val ready = _state.value as? State.Ready ?: return
        // Dialog: appka je na popředí → služba smí znovu naběhnout a drží notifikaci nahrávání.
        runCatching { ContextCompat.startForegroundService(context, Intent(context, AudiotekaDownloadService::class.java)) }
            .onFailure { Timber.w(it, "[AUDIOTEKA] služba dovozu nenaběhla (nahrává se dál)") }
        startImport(ready, target, title, author)
    }

    private fun startImport(ready: State.Ready, target: ImportTarget, title: String?, author: String?) {
        uploadManager.reset()
        val uploadId = uploadManager.upload(
            uris = listOf(Uri.fromFile(ready.file)),
            libraryId = target.libraryId,
            title = title?.takeIf { it.isNotBlank() },
            author = author?.takeIf { it.isNotBlank() },
            autoMatch = true,
            startService = false, // notifikaci celého řetězce drží AudiotekaDownloadService
        )
        if (uploadId == 0L) {
            // Běží jiné nahrávání (např. z obrazovky Nahrát audioknihu) → tenhle soubor zůstává k dovozu.
            _state.value = ready.copy(note = "Právě běží jiné nahrávání. Počkej, až skončí, a zkus to znovu.")
            return
        }
        _state.value = State.Importing(ready, target, uploadId, System.currentTimeMillis())
        val gen = generation.get()
        scope.launch {
            // Výsledek jen tohoto nahrávání (podle id); když ho mezitím přepsalo jiné, nehádat a soubor nechat.
            val done = uploadManager.state.first {
                it.uploadId != uploadId || (!it.isUploading && (it.result != null || it.error != null))
            }
            val mine = (_state.value as? State.Importing)?.uploadId == uploadId && generation.get() == gen
            if (done.uploadId != uploadId) {
                // Výsledek přepsalo jiné nahrávání (revize P2): soubor nechat k dovozu / smazání, ne ztratit.
                if (mine) _state.value = ready.copy(
                    note = "Výsledek nahrávání se nepodařilo zjistit (souběh s jiným nahráváním). Zkontroluj knihovnu, než knihu dovezeš znovu.",
                    suggestion = target,
                )
                return@launch
            }
            if (done.result != null) {
                if (AudiotekaPrefs.deleteAfterImport(context)) ready.file.parentFile?.deleteRecursively()
                if (mine) _state.value = State.Done(ready.titleHint ?: ready.name.substringBeforeLast('.'), target.libraryName)
            } else if (mine) {
                // Chyba nahrání: soubor zůstal, nabídnout znovu bez nového stahování.
                _state.value = ready.copy(note = "Nahrání do knihovny selhalo (${done.error}). Zkus to znovu.", suggestion = target)
            }
        }
    }

    /** Knihovna podle kategorie Audioteky (Pohádky → dětská). null = kniha nenalezena / server neodpověděl. */
    private suspend fun suggest(stem: String, bookId: String?): ImportTarget? = withTimeoutOrNull(15_000) {
        val hint = atRepo.libraryHint(stem, bookId) ?: return@withTimeoutOrNull null
        val libs = runCatching { absRepo.getAudiobookLibraries() }.getOrNull().orEmpty()
        pickLibrary(libs.map { it.id to it.name }, hint.kids)?.let { (id, n) -> ImportTarget(id, n, hint.category) }
    }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        (_state.value as? State.Ready)?.file?.parentFile?.deleteRecursively()
        _state.value = State.Idle
    }

    /** Zavře hlášku chyby / hotovo. */
    fun dismissError() {
        if (_state.value is State.Failed || _state.value is State.Done) _state.value = State.Idle
    }

    private companion object {
        const val MAX_RETRIES = 5
    }

    /** Archiv z Poličky se jmenuje podle slugu knihy (např. „cesky-skritek-poznava-svet.zip"). */
    private fun slugMatches(fileName: String, slug: String): Boolean {
        val f = norm(fileName.substringBeforeLast('.'))
        val w = norm(slug)
        return w.isNotEmpty() && (f == w || f.startsWith("$w "))
    }

    private fun safeName(n: String): String =
        n.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "audioteka.zip" }

    /** Patří stažený soubor ke knize? Aspoň dvě (u krátkého názvu jedno) významná slova názvu ve jméně souboru. */
    private fun fileMatchesTitle(fileName: String, title: String): Boolean {
        val f = norm(fileName)
        val words = norm(title).split(' ').filter { it.length >= 3 }
        if (words.isEmpty()) return false
        val need = if (words.size >= 2) 2 else 1
        return words.count { f.contains(it) } >= need
    }

    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

/** Dětská knihovna podle názvu („děti", „Dětské", „Pohádky", „Kids") — ne „Detektivky". */
fun isKidsLibraryName(name: String): Boolean =
    java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
        .split(Regex("[^a-z0-9]+")).any { it.startsWith("deti") || it.startsWith("detsk") || it.startsWith("pohad") || it == "kids" }

/** Návrh knihovny: dětská kniha → první dětská knihovna, jinak první nedětská. [libs] = (id, název). */
fun pickLibrary(libs: List<Pair<String, String>>, kids: Boolean): Pair<String, String>? =
    libs.firstOrNull { isKidsLibraryName(it.second) == kids }
