package com.github.jankoran90.showlyfin.feature.listen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.uploader.AudiobookUploadRepository
import com.github.jankoran90.showlyfin.data.uploader.api.CountingRequestBody
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import timber.log.Timber
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * DROPSHIP F2 — nahrání audioknihy z telefonu do ABS knihovny přes uploader backend.
 *
 * Průběh: [Uri] → `ContentResolver.openInputStream` → [StreamRequestBody] zabalený do
 * [CountingRequestBody] → `MultipartBody.Part` → [AudiobookUploadRepository]. Průběh se sčítá přes
 * všechny soubory (AtomicLong) a emituje se `progress: Float` 0..1 do state.
 *
 * Detekce `title`/`author` z názvu souboru: split na " - " (vzor backendu `_detect_title_author`).
 */
@HiltViewModel
class UploadAudiobookViewModel @Inject constructor(
    private val absRepo: AbsRepository,
    private val uploaderRepo: AudiobookUploadRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val libraries: List<AbsLibrary> = emptyList(),
        val selectedLibraryId: String? = null,
        val isUploading: Boolean = false,
        val progress: Float = 0f,
        val result: AudiobookUploadResponse? = null,
        val error: String? = null,
        val notConfigured: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (!absRepo.isConfigured) {
                _state.update { it.copy(notConfigured = true) }
                return@launch
            }
            runCatching { absRepo.getAudiobookLibraries() }
                .onSuccess { libs ->
                    _state.update {
                        it.copy(libraries = libs, selectedLibraryId = libs.firstOrNull()?.id)
                    }
                }
                .onFailure { Timber.w(it, "[DROPSHIP] načtení ABS knihoven selhalo") }
        }
    }

    fun selectLibrary(id: String) = _state.update { it.copy(selectedLibraryId = id) }

    /**
     * Nahraje vybrané soubory. [uris] = audio soubory NEBO archiv (ZIP/RAR/TAR/7Z) z SAF pickeru.
     * [title]/[author] předvyplněné z detekce, uživatelem editovatelné. [autoMatch] = Audible enrich.
     */
    fun upload(uris: List<Uri>, title: String?, author: String?, autoMatch: Boolean, coverUri: Uri? = null) {
        val libraryId = _state.value.selectedLibraryId ?: run {
            _state.update { it.copy(error = "Vyber cílovou knihovnu.") }
            return
        }
        if (uris.isEmpty()) {
            _state.update { it.copy(error = "Nejsou vybrány žádné soubory.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, progress = 0f, error = null, result = null) }
            runCatching {
                val sizes = uris.map { sizeOfUri(it) }
                val totalSize = sizes.filter { it > 0 }.sum().takeIf { it > 0 } ?: -1L
                // Průběh se sčítá napříč parts: každý CountingRequestBody hlásí vlastní kumulativní
                // bytesWritten; počítáme delta oproti minulé reportované hodnotě partu a přičítáme do
                // celkového AtomicLong. Parts se zapisují sekvenčně (jedno HTTP tělo), bez souběhu.
                val cumulative = AtomicLong(0L)
                val perPart = LongArray(uris.size)
                val parts = uris.mapIndexed { i, uri ->
                    buildPart(uri, sizes[i]) { bytesWritten ->
                        val delta = bytesWritten - perPart[i]
                        if (delta > 0) {
                            perPart[i] = bytesWritten
                            val total = cumulative.addAndGet(delta)
                            if (totalSize > 0) {
                                _state.update {
                                    it.copy(progress = (total.toFloat() / totalSize).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                val coverPart = coverUri?.let { buildCoverPart(it) }
                uploaderRepo.upload(parts, libraryId, title, author, autoMatch, coverPart)
            }.onSuccess { res ->
                _state.update { it.copy(isUploading = false, progress = 1f, result = res) }
            }.onFailure { e ->
                Timber.w(e, "[DROPSHIP] upload selhal")
                _state.update { it.copy(isUploading = false, error = e.message ?: "Nahrávání selhalo") }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    /** Reset výsledku při návratu z obrazovky. */
    fun reset() = _state.update { it.copy(result = null, error = null, progress = 0f, isUploading = false) }

    /**
     * Vytvoří MultipartBody.Part se [CountingRequestBody] nad [StreamRequestBody]. `fileName` z
     * `DISPLAY_NAME`, fallback dle mime. [onProgress] dostává kumulativní `bytesWritten` daného partu.
     */
    private fun buildPart(
        uri: Uri,
        declaredSize: Long,
        onProgress: (bytesWritten: Long) -> Unit,
    ): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(uri) ?: defaultName(mime)
        val base = StreamRequestBody(resolver, uri, mime.toMediaTypeOrNull(), declaredSize)
        val counting = CountingRequestBody(base) { written, _ -> onProgress(written) }
        return MultipartBody.Part.createFormData("files", name, counting)
    }

    /** Cover obrázek z SAF — pole „cover", bez progress (malý soubor). */
    private fun buildCoverPart(uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = queryDisplayName(uri) ?: "cover.jpg"
        val body = StreamRequestBody(resolver, uri, mime.toMediaTypeOrNull(), sizeOfUri(uri))
        return MultipartBody.Part.createFormData("cover", name, body)
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

/**
 * Detekce `title`/`author` z názvu souboru — split na " - " (vzor backendu `_detect_title_author`).
 * `Autor - Název.ext` → (Autor, Název). Bez " - " → (celý název bez přípony, null).
 */
internal fun detectTitleAuthor(filename: String): Pair<String, String?> {
    val raw = filename.substringBeforeLast('.').trim()
    if (" - " !in raw) return prettyCase(cleanSep(raw)) to null
    val (left, right) = raw.split(" - ", limit = 2).map { prettyCase(cleanSep(it.trim())) }
    // Kratší strana (méně slov) bývá autor (knihovní konvence „Autor - Název").
    return if (left.split(' ').size <= right.split(' ').size) left to right else right to left
}

/** Pomlčky/podtržítka (mimo „ - ") → mezery, sbalené mezery. */
private fun cleanSep(s: String): String =
    s.replace("[-_]+".toRegex(), " ").replace(Regex(" +"), " ").trim()

/** „kral-valecnik" → „Kral Valecnik" (pomlčky už nahrazeny mezerami, title case). */
private fun prettyCase(s: String): String =
    s.lowercase().split(' ').filter { it.isNotEmpty() }.joinToString(" ") {
        it.replaceFirstChar { c -> c.uppercase() }
    }
