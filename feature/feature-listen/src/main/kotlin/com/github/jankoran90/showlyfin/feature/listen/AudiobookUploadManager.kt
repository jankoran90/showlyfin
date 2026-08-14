package com.github.jankoran90.showlyfin.feature.listen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.github.jankoran90.showlyfin.data.uploader.AudiobookUploadRepository
import com.github.jankoran90.showlyfin.data.uploader.api.CountingRequestBody
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse
import com.github.jankoran90.showlyfin.feature.listen.service.UploadAudiobookService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import timber.log.Timber
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DROPSHIP F2d — upload audioknihy mimo viewModelScope, aby přežil přepnutí appky na pozadí
 * (Android sestřeluje proces v pozadí → „Unable to resolve host"). Vlastní [scope] + [state]
 * (StateFlow) sdílený s [UploadAudiobookService], která drží proces přiživu (foreground) a
 * ukazuje průběh v notifikaci. VM (UI) jen deleguje a zrcadlí [state].
 */
@Singleton
class AudiobookUploadManager @Inject constructor(
    private val uploaderRepo: AudiobookUploadRepository,
    @ApplicationContext private val context: Context,
) {

    /** Stav uploadu sdílený UI (VM) i notifikací (service). */
    data class UploadState(
        val isUploading: Boolean = false,
        val progress: Float = 0f,
        val result: AudiobookUploadResponse? = null,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UploadState())
    val state = _state.asStateFlow()
    private val running = AtomicBoolean(false)

    /**
     * Spustí upload (pokud žádný neběží): nahodí foreground službu a v [scope] streamuje
     * [uris] na uploader backend. Průběh 0..1 do [state]; výsledek/chyba do [state].
     */
    fun upload(uris: List<Uri>, libraryId: String, title: String?, author: String?, autoMatch: Boolean, coverUri: Uri? = null) {
        if (uris.isEmpty()) return
        if (!running.compareAndSet(false, true)) return // jeden upload v jednu chvíli
        _state.value = UploadState(isUploading = true)
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, UploadAudiobookService::class.java))
        }.onFailure { Timber.w(it, "[DROPSHIP] upload service start selhal (upload běží dál)") }
        scope.launch {
            runCatching {
                val sizes = uris.map { sizeOfUri(it) }
                val totalSize = sizes.filter { it > 0 }.sum().takeIf { it > 0 } ?: -1L
                // Průběh se sčítá napříč parts (jako dřív ve VM): delta kumulativního bytesWritten
                // daného partu → celkový AtomicLong. Parts se zapisují sekvenčně (jedno HTTP tělo).
                val cumulative = AtomicLong(0L)
                val perPart = LongArray(uris.size)
                val parts = uris.mapIndexed { i, uri ->
                    buildPart(uri, sizes[i]) { bytesWritten ->
                        val delta = bytesWritten - perPart[i]
                        if (delta > 0) {
                            perPart[i] = bytesWritten
                            val total = cumulative.addAndGet(delta)
                            if (totalSize > 0) {
                                _state.value = _state.value.copy(
                                    progress = (total.toFloat() / totalSize).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                }
                val coverPart = coverUri?.let { buildCoverPart(it) }
                uploaderRepo.upload(parts, libraryId, title, author, autoMatch, coverPart)
            }.onSuccess { res ->
                _state.value = UploadState(isUploading = false, progress = 1f, result = res)
            }.onFailure { e ->
                Timber.w(e, "[DROPSHIP] upload selhal")
                _state.value = UploadState(isUploading = false, error = e.message ?: "Nahrávání selhalo")
            }
            running.set(false)
        }
    }

    /** Vymaže hotový výsledek/chybu (při návratu na obrazovku / novém pokusu). */
    fun reset() {
        if (!running.get()) _state.value = UploadState()
    }

    /** MultipartBody.Part s [CountingRequestBody] nad [StreamRequestBody]; [onProgress] = kumulativní bytes partu. */
    private fun buildPart(
        uri: Uri,
        declaredSize: Long,
        onProgress: (bytesWritten: Long) -> Unit,
    ): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = asciiSafeName(queryDisplayName(uri) ?: defaultName(mime))
        val base = StreamRequestBody(resolver, uri, mime.toMediaTypeOrNull(), declaredSize)
        val counting = CountingRequestBody(base) { written, _ -> onProgress(written) }
        return MultipartBody.Part.createFormData("files", name, counting)
    }

    /** Cover obrázek z SAF — pole „cover", bez progress (malý soubor). */
    private fun buildCoverPart(uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = asciiSafeName(queryDisplayName(uri) ?: "cover.jpg")
        val body = StreamRequestBody(resolver, uri, mime.toMediaTypeOrNull(), sizeOfUri(uri))
        return MultipartBody.Part.createFormData("cover", name, body)
    }

    /**
     * Název souboru jde do hlavičky Content-Disposition — HTTP hlavičky smí obsahovat jen ASCII
     * (RFC 7230). OkHttp pošle české znaky raw UTF-8 a nginx request odmítne (400 → „Unexpected
     * end of stream"). Transliterace diakritiky + náhrada zbylých ne-ASCII znaků podtržítkem.
     * Server název používá jen jako fallback pro detekci (ta diakritiku stejně ignoruje).
     */
    private fun asciiSafeName(name: String): String {
        val norm = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val cleaned = norm.replace(Regex("[^A-Za-z0-9._() -]"), "_")
        return cleaned.ifBlank { "audiobook" }
    }

    private fun sizeOfUri(uri: Uri): Long = queryLong(uri, OpenableColumns.SIZE)

    private fun queryDisplayName(uri: Uri): String? =
        queryString(uri, OpenableColumns.DISPLAY_NAME)

    private fun queryString(uri: Uri, column: String): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull()

    private fun queryLong(uri: Uri, column: String): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun defaultName(mime: String): String = when {
        mime.contains("zip") -> "audiobook.zip"
        mime.contains("rar") -> "audiobook.rar"
        mime.contains("tar") -> "audiobook.tar"
        mime.contains("7z") -> "audiobook.7z"
        mime.contains("m4b") -> "audiobook.m4b"
        mime.contains("m4a") -> "audiobook.m4a"
        mime.contains("flac") -> "audiobook.flac"
        else -> "audiobook.mp3"
    }
}

/**
 * RequestBody, co streamuje z ContentResolver [InputStream] (8 KB buffer). [contentLength] =
 * deklarovaná velikost z `SIZE` sloupce (nebo -1, když ContentResolver neví). Progress hlásí
 * [CountingRequestBody] nad tímto tělem přes ForwardingSink (spolehlivé, jede po síťovém bufferu).
 */
private class StreamRequestBody(
    private val resolver: android.content.ContentResolver,
    private val uri: Uri,
    private val mediaType: okhttp3.MediaType?,
    private val length: Long,
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength(): Long = length
    override fun writeTo(sink: okio.BufferedSink) {
        resolver.openInputStream(uri)?.use { input: InputStream ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                sink.write(buf, 0, read)
            }
        } ?: error("Nepodařilo se otevřít soubor pro čtení.")
    }
}
