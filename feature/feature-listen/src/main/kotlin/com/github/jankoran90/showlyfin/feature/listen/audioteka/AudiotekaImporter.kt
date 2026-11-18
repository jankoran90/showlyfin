package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.content.Context
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
        /** Staženo, čeká na volbu knihovny. */
        data class Ready(val file: File, val name: String, val titleHint: String?, val authorHint: String?) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val dir: File get() = AudiotekaPrefs.importDir(context).apply { mkdirs() }

    /** Stažení ohlášené WebView. [titleHint]/[authorHint] = kniha, ze které jsi šel nakupovat (může být null). */
    fun onDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        titleHint: String?,
        authorHint: String?,
    ) {
        if (_state.value is State.Downloading) return
        if (!url.startsWith("http")) {
            _state.value = State.Failed("Tohle stažení Slovo neumí převzít (${url.substringBefore(':')}). Stáhni knihu v prohlížeči a nahraj ji přes Nahrát audioknihu.")
            return
        }
        val name = safeName(URLUtil.guessFileName(url, contentDisposition, mimeType))
        job = scope.launch {
            val target = File(dir, name)
            runCatching {
                _state.value = State.Downloading(name, null, 0)
                val req = Request.Builder().url(url).apply {
                    CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) }
                    userAgent?.let { header("User-Agent", it) }
                }.build()
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("Audioteka odmítla stažení (HTTP ${res.code}). Zkus se v Poličce přihlásit znovu.")
                    val body = res.body ?: error("Prázdná odpověď")
                    val total = body.contentLength()
                    var done = 0L
                    var lastEmit = 0L
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (done - lastEmit > 1_000_000) {
                                    lastEmit = done
                                    _state.value = State.Downloading(name, if (total > 0) done.toFloat() / total else null, done)
                                }
                            }
                        }
                    }
                }
                if (target.length() == 0L) error("Stažený soubor je prázdný")
            }.onSuccess {
                _state.value = State.Ready(target, name, titleHint, authorHint)
            }.onFailure { e ->
                target.delete()
                if (e is CancellationException) {
                    _state.value = State.Idle
                } else {
                    Timber.w(e, "[AUDIOTEKA] stažení z Poličky selhalo")
                    _state.value = State.Failed(e.message ?: "Stažení selhalo")
                }
            }
        }
    }

    /** Pošle stažený soubor do zvolené knihovny (server archiv rozbalí, metadata dohledá). */
    fun import(libraryId: String, title: String?, author: String?) {
        val ready = _state.value as? State.Ready ?: return
        uploadManager.reset()
        uploadManager.upload(
            uris = listOf(Uri.fromFile(ready.file)),
            libraryId = libraryId,
            title = title?.takeIf { it.isNotBlank() },
            author = author?.takeIf { it.isNotBlank() },
            autoMatch = true,
        )
        _state.value = State.Idle
        val file = ready.file
        // Archiv smazat až po úspěšném nahrání (a jen když to Nastavení chce); při chybě zůstane pro další pokus.
        scope.launch {
            val done = uploadManager.state.first { !it.isUploading && (it.result != null || it.error != null) }
            if (done.result != null && AudiotekaPrefs.deleteAfterImport(context)) file.delete()
        }
    }

    fun cancel() {
        job?.cancel()
        (_state.value as? State.Ready)?.file?.delete()
        _state.value = State.Idle
    }

    fun dismissError() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }

    private fun safeName(n: String): String =
        n.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "audioteka.zip" }
}
