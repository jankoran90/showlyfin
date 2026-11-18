package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.github.jankoran90.showlyfin.feature.listen.service.AudiotekaDownloadService
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
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
) {
    sealed interface State {
        data object Idle : State
        data class Downloading(val name: String, val progress: Float?, val bytes: Long) : State
        /** Staženo, čeká na volbu knihovny. [note] = proč předchozí pokus nevyšel (jiné nahrávání, chyba). */
        data class Ready(
            val file: File,
            val name: String,
            val titleHint: String?,
            val authorHint: String?,
            val note: String? = null,
        ) : State
        data class Failed(val message: String) : State
    }

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

    /** Stažení ohlášené WebView. [titleHint]/[authorHint] = kniha, ze které jsi šel nakupovat (může být null). */
    fun onDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        titleHint: String?,
        authorHint: String?,
    ) {
        if (_state.value is State.Downloading || _state.value is State.Ready) return
        if (!url.startsWith("http")) {
            _state.value = State.Failed("Tohle stažení Slovo neumí převzít (${url.substringBefore(':')}). Stáhni knihu v prohlížeči a nahraj ji přes Nahrát audioknihu.")
            return
        }
        val name = safeName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        // Název a autora knihy, ze které jsi šel nakupovat, předvyplnit jen když stažený soubor zjevně patří k ní
        // (v Poličce můžeš stáhnout úplně jinou knihu); jinak prázdné → server dohledá podle souboru.
        val matches = titleHint != null && fileMatchesTitle(name, titleHint)
        val gen = generation.incrementAndGet()
        // Každé stahování ve vlastní podsložce: zrušené a nové se stejným jménem souboru si nesmažou data.
        val target = File(AudiotekaPrefs.importDir(context), "d$gen-${System.currentTimeMillis()}").apply { mkdirs() }.let { File(it, name) }
        _state.value = State.Downloading(name, null, 0)
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
                                            _state.value = State.Downloading(name, if (total > 0) done.toFloat() / total else null, done)
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
                if (generation.get() == gen) {
                    _state.value = State.Ready(target, name, titleHint.takeIf { matches }, authorHint.takeIf { matches })
                } else {
                    target.parentFile?.deleteRecursively()
                }
            }.onFailure { e ->
                target.parentFile?.deleteRecursively()
                if (generation.get() != gen || e is CancellationException) return@onFailure
                Timber.w(e, "[AUDIOTEKA] stažení z Poličky selhalo")
                _state.value = State.Failed(e.message ?: "Stažení selhalo")
            }
        }
    }

    /** Pošle stažený soubor do zvolené knihovny (server archiv rozbalí, metadata dohledá). */
    fun import(libraryId: String, title: String?, author: String?) {
        val ready = _state.value as? State.Ready ?: return
        uploadManager.reset()
        val started = uploadManager.upload(
            uris = listOf(Uri.fromFile(ready.file)),
            libraryId = libraryId,
            title = title?.takeIf { it.isNotBlank() },
            author = author?.takeIf { it.isNotBlank() },
            autoMatch = true,
        )
        if (!started) {
            // Běží jiné nahrávání (např. z obrazovky Nahrát audioknihu) → tenhle soubor zůstává k dovozu.
            _state.value = ready.copy(note = "Právě běží jiné nahrávání. Počkej, až skončí, a zkus to znovu.")
            return
        }
        _state.value = State.Idle
        val gen = generation.get()
        scope.launch {
            // Stav managera teď patří právě tomuhle nahrávání (jiné se spustit nemohlo, dokud běží).
            val done = uploadManager.state.first { !it.isUploading && (it.result != null || it.error != null) }
            if (done.result != null) {
                if (AudiotekaPrefs.deleteAfterImport(context)) ready.file.parentFile?.deleteRecursively()
            } else if (_state.value == State.Idle && generation.get() == gen) {
                // Chyba nahrání: soubor zůstal, nabídnout znovu bez nového stahování.
                _state.value = ready.copy(note = "Nahrání do knihovny selhalo (${done.error}). Zkus to znovu.")
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        (_state.value as? State.Ready)?.file?.parentFile?.deleteRecursively()
        _state.value = State.Idle
    }

    fun dismissError() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }

    private companion object {
        const val MAX_RETRIES = 5
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
