package com.github.jankoran90.showlyfin.feature.listen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.abs.model.AudiobookDetail
import com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * DROPSHIP F2 — nahrání audioknihy z telefonu do ABS knihovny přes uploader backend.
 *
 * F2d: samotný upload běží v [AudiobookUploadManager] (vlastní scope + foreground služba),
 * aby přežil přepnutí appky na pozadí — VM jen deleguje, zrcadlí stav a drží UI věci
 * (knihovny, výběr, detekce názvu/autora z názvu souboru).
 */
@HiltViewModel
class UploadAudiobookViewModel @Inject constructor(
    private val absRepo: AbsRepository,
    private val uploadManager: AudiobookUploadManager,
) : ViewModel() {

    data class UiState(
        val libraries: List<AbsLibrary> = emptyList(),
        val selectedLibraryId: String? = null,
        val isUploading: Boolean = false,
        val progress: Float = 0f,
        val result: AudiobookUploadResponse? = null,
        val error: String? = null,
        val notConfigured: Boolean = false,
        // 2026-10-09 (user: „nikde nevidím výsledek a metadata pro kontrolu") — živý stav nahrané
        // knihy z ABS pro výsledkovou kartu: obálka, autor, vypravěč, rok, délka, kapitoly, popis.
        val uploadedDetail: AudiobookDetail? = null,
        val uploadedCoverUrl: String? = null,
        val enrichPending: Boolean = false,
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
        // Zrcadlení upload stavu z manageru (žije mimo viewModelScope).
        viewModelScope.launch {
            uploadManager.state.collect { m ->
                _state.update {
                    it.copy(
                        isUploading = m.isUploading,
                        progress = m.progress,
                        result = m.result,
                        error = m.error,
                    )
                }
                val id = m.result?.itemId
                if (id != null && id != loadedItemId) {
                    loadedItemId = id
                    watchUploaded(id, m.result?.enrich?.pending == true)
                }
            }
        }
    }

    private var loadedItemId: String? = null
    private var watchJob: Job? = null

    /**
     * Načte nahranou knihu z ABS. Když enrich běží na pozadí, přečte ji znovu po pár sekundách,
     * dokud nepřibude popis (= enrich doběhl) — karta se tak sama doplní o obálku a metadata.
     */
    private fun watchUploaded(itemId: String, pending: Boolean) {
        watchJob?.cancel()
        _state.update { it.copy(enrichPending = pending) }
        watchJob = viewModelScope.launch {
            val delays = if (pending) listOf(0L, 4_000L, 6_000L, 10_000L, 15_000L, 25_000L) else listOf(0L)
            for ((i, d) in delays.withIndex()) {
                delay(d)
                val detail = runCatching { absRepo.getAudiobookDetail(itemId) }
                    .onFailure { Timber.w(it, "[DROPSHIP] detail nahrané knihy") }
                    .getOrNull() ?: continue
                val done = !pending || detail.description != null || i == delays.lastIndex
                _state.update {
                    it.copy(
                        uploadedDetail = detail,
                        // parametr v= obejde cache obrázků — obálka se po enrichi mění pod stejnou adresou.
                        uploadedCoverUrl = "${absRepo.coverUrl(itemId)}&v=$i",
                        enrichPending = !done,
                    )
                }
                if (done) break
            }
        }
    }

    /** Znovu načte nahranou knihu (návrat z úpravy). */
    fun refreshUploaded() {
        val id = loadedItemId ?: return
        watchUploaded(id, pending = false)
    }

    fun selectLibrary(id: String) = _state.update { it.copy(selectedLibraryId = id) }

    /** Obnoví zrcadlo po (re)otevření obrazovky, když upload mezitím doběhl na pozadí. */
    fun syncFromManager() {
        val m = uploadManager.state.value
        _state.update {
            it.copy(isUploading = m.isUploading, progress = m.progress, result = m.result, error = m.error)
        }
    }

    /**
     * Nahraje vybrané soubory. [uris] = audio soubory NEBO archiv (ZIP/RAR/TAR/7Z) z SAF pickeru.
     * [title]/[author] předvyplněné z detekce, uživatelem editovatelné. [autoMatch] = Audible enrich.
     * Upload běží v manageru + foreground službě (přežije přepnutí appky).
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
        uploadManager.upload(uris, libraryId, title, author, autoMatch, coverUri)
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    /** Reset výsledku při návratu z obrazovky. */
    fun reset() {
        uploadManager.reset()
        watchJob?.cancel()
        loadedItemId = null
        _state.update {
            it.copy(
                result = null, error = null, progress = 0f, isUploading = false,
                uploadedDetail = null, uploadedCoverUrl = null, enrichPending = false,
            )
        }
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
