package com.github.jankoran90.showlyfin.feature.listen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.github.jankoran90.showlyfin.data.uploader.AudiobookUploadRepository
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse
import com.github.jankoran90.showlyfin.feature.listen.service.UploadAudiobookService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DROPSHIP F2d — upload audioknihy mimo viewModelScope, aby přežil přepnutí appky na pozadí
 * (Android sestřeluje proces v pozadí → „Unable to resolve host"). Vlastní [scope] + [state]
 * (StateFlow) sdílený s [UploadAudiobookService], která drží proces přiživu (foreground) a
 * ukazuje průběh v notifikaci. VM (UI) jen deleguje a zrcadlí [state].
 *
 * SLOVO-UPLOAD-NET — jeden velký multipart request (stovky MB v kuse) na mobilní síti/CGNAT/MTU
 * cestě spolehlivě umíral (RST z telefonu) po ~45s-2min, dřív než tělo dorazilo (nginx info log
 * potvrdil: abort iniciuje telefon/cesta, ne server). Fix: soubor se posílá po malých kouscích
 * ([CHUNK_SIZE]), každý vlastní HTTP request s vlastním retry — nespolehlivá cesta unese pár
 * vteřin spojení, a ztracený kousek se zopakuje bez nutnosti nahrávat znovu celý soubor.
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
     * Spustí upload (pokud žádný neběží): nahodí foreground službu a v [scope] pošle [uris] na
     * uploader backend po kouscích (session → chunky → finish per soubor → finalize).
     * Průběh 0..1 do [state]; výsledek/chyba do [state].
     */
    fun upload(uris: List<Uri>, libraryId: String, title: String?, author: String?, autoMatch: Boolean, coverUri: Uri? = null) {
        if (uris.isEmpty()) return
        if (!running.compareAndSet(false, true)) return // jeden upload v jednu chvíli
        _state.value = UploadState(isUploading = true)
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, UploadAudiobookService::class.java))
        }.onFailure { Timber.w(it, "[DROPSHIP] upload service start selhal (upload běží dál)") }
        scope.launch {
            var sessionId: String? = null
            runCatching {
                val sid = uploaderRepo.startUploadSession(libraryId, title, author, autoMatch)
                sessionId = sid
                val sizes = uris.map { sizeOfUri(it) }
                val totalSize = if (sizes.any { it <= 0 }) -1L else sizes.sum()
                var confirmed = 0L
                uris.forEachIndexed { fileIndex, uri ->
                    val name = asciiSafeName(
                        queryDisplayName(uri) ?: defaultName(context.contentResolver.getType(uri) ?: "application/octet-stream"),
                    )
                    val size = sizes[fileIndex]
                    val totalChunks = if (size > 0) ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt() else -1
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buf = ByteArray(CHUNK_SIZE)
                        var chunkIndex = 0
                        while (true) {
                            val read = readFully(input, buf)
                            if (read <= 0) break
                            val bytes = if (read == buf.size) buf.copyOf() else buf.copyOf(read)
                            uploadChunkWithRetry(sid, fileIndex, chunkIndex, totalChunks, name, bytes)
                            confirmed += read
                            if (totalSize > 0) {
                                _state.value = _state.value.copy(progress = (confirmed.toFloat() / totalSize).coerceIn(0f, 1f))
                            }
                            chunkIndex++
                        }
                    } ?: error("Nepodařilo se otevřít soubor pro čtení.")
                    uploaderRepo.finishUploadFile(sid, fileIndex)
                }
                val coverPart = coverUri?.let { buildCoverPart(it) }
                uploaderRepo.finalizeUpload(sid, coverPart)
            }.onSuccess { res ->
                _state.value = UploadState(isUploading = false, progress = 1f, result = res)
            }.onFailure { e ->
                Timber.w(e, "[DROPSHIP] upload selhal")
                sessionId?.let { uploaderRepo.cancelUploadSession(it) }
                _state.value = UploadState(isUploading = false, error = e.message ?: "Nahrávání selhalo")
            }
            running.set(false)
        }
    }

    /** Vymaže hotový výsledek/chybu (při návratu na obrazovku / novém pokusu). */
    fun reset() {
        if (!running.get()) _state.value = UploadState()
    }

    /** Pošle jeden kousek s retry (exponenciální backoff) — přežije krátký výpadek/RST na cestě. */
    private suspend fun uploadChunkWithRetry(
        sessionId: String,
        fileIndex: Int,
        chunkIndex: Int,
        totalChunks: Int,
        filename: String,
        bytes: ByteArray,
    ) {
        var attempt = 0
        while (true) {
            try {
                uploaderRepo.uploadChunk(sessionId, fileIndex, chunkIndex, totalChunks, filename, bytes)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt > MAX_CHUNK_RETRIES) throw e
                Timber.w(e, "[DROPSHIP] chunk $fileIndex/$chunkIndex selhal, pokus $attempt/$MAX_CHUNK_RETRIES")
                delay(RETRY_DELAYS_MS.getOrElse(attempt - 1) { RETRY_DELAYS_MS.last() })
            }
        }
    }

    /** Naplní [buf] až do konce, nebo míň, když stream skončí (0 = konec). Víc read() volání, protože InputStream.read() nemusí vrátit celý požadovaný blok naráz. */
    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n == -1) break
            total += n
        }
        return total
    }

    /** Cover obrázek z SAF — pole „cover", malý soubor, čte se celý do paměti najednou. */
    private fun buildCoverPart(uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = asciiSafeName(queryDisplayName(uri) ?: "cover.jpg")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Nepodařilo se otevřít obrázek obálky.")
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
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

    companion object {
        /** 4 MB — dost malé, aby i nestabilní mobilní síť/CGNAT/MTU cesta zvládla jeden kousek (viz SLOVO-UPLOAD-NET). */
        private const val CHUNK_SIZE = 4 * 1024 * 1024
        private const val MAX_CHUNK_RETRIES = 5
        private val RETRY_DELAYS_MS = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
    }
}
