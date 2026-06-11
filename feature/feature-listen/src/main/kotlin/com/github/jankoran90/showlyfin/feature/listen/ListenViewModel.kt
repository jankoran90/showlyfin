package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ListenViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val downloadManager: EpisodeDownloadManager,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /**
     * Profilový whitelist ABS knihoven (Plan PROFILES Fáze 4E). null = bez omezení (vidět vše).
     * Filtruje audioknihy i podcasty police podle aktivního profilu.
     */
    private fun List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>.applyProfileWhitelist():
        List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary> {
        val wl = profileRepository.activeConfig.value.absLibraryWhitelist
        Timber.i("[VAULT] ABS whitelist=$wl libs=${this.map { it.id to it.name }}")
        if (wl == null) return this
        return filter { it.id in wl }
    }

    /**
     * Per-profil skrytí jednotlivých podcastů (admin authoring ve Správě). Odfiltruje pořady, jejichž
     * id je ve [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.hiddenPodcastIds].
     */
    private fun List<com.github.jankoran90.showlyfin.data.abs.model.Podcast>.applyProfileHidden():
        List<com.github.jankoran90.showlyfin.data.abs.model.Podcast> {
        val hidden = profileRepository.activeConfig.value.hiddenPodcastIds
        if (hidden.isEmpty()) return this
        return filter { it.id !in hidden }
    }

    private val _uiState = MutableStateFlow(ListenUiState())
    val uiState = _uiState.asStateFlow()

    /** Všechny stažené epizody (správa offline stažení). */
    val downloads = downloadManager.downloads

    fun deleteDownload(episodeId: String) = downloadManager.delete(episodeId)
    fun deleteAllDownloads() = downloadManager.deleteAll()

    init {
        // Plan VAULT — refresh řízený configem aktivního profilu, ne jen vznikem VM. StateFlow emitne
        // hned (= původní init refresh) a pak při každé změně whitelistu/ABS creds (přepnutí profilu,
        // sync z backendu). Applier zapisuje prefs PŘED emisí configu (ProfileRepository), takže tady
        // už čteme správné přihlášení — řeší závod „fetch knihoven se starým tokenem → prázdné libs".
        profileRepository.activeConfig
            .map { Triple(it.absLibraryWhitelist, it.credentials.abs, it.hiddenPodcastIds) }
            .distinctUntilChanged()
            .onEach {
                refresh()
                if (_uiState.value.podcastsLoaded || _uiState.value.mode == ListenMode.PODCASTS) {
                    loadPodcastLibraries()
                }
            }
            .launchIn(viewModelScope)
    }

    /** Načte knihovny audioknih a knihy ve vybrané (či první) knihovně. */
    fun refresh() {
        if (!repo.isConfigured) {
            _uiState.update { it.copy(isConfigured = false, isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConfigured = true, isLoading = true, error = null) }
            runCatching { repo.getAudiobookLibraries().applyProfileWhitelist() }
                .onSuccess { libs ->
                    if (libs.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, libraries = emptyList(), books = emptyList()) }
                        return@onSuccess
                    }
                    val selected = _uiState.value.selectedLibraryId?.takeIf { id -> libs.any { it.id == id } }
                        ?: libs.first().id
                    _uiState.update { it.copy(libraries = libs, selectedLibraryId = selected) }
                    loadBooks(selected)
                }
                .onFailure { e ->
                    Timber.w(e, "[Listen] knihovny selhaly")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení knihoven selhalo. Zkontroluj přihlášení k Audiobookshelf v Nastavení.") }
                }
        }
    }

    /** Přepnutí Audioknihy ↔ Podcasty. Podcasty se načtou líně při prvním přepnutí. */
    fun setMode(mode: ListenMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update { it.copy(mode = mode, error = null) }
        if (mode == ListenMode.PODCASTS && !_uiState.value.podcastsLoaded) {
            loadPodcastLibraries()
        }
    }

    fun selectLibrary(libraryId: String) {
        if (libraryId == _uiState.value.selectedLibraryId) return
        _uiState.update { it.copy(selectedLibraryId = libraryId) }
        viewModelScope.launch { loadBooks(libraryId) }
    }

    private suspend fun loadBooks(libraryId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.getAudiobooks(libraryId) }
            .onSuccess { books -> _uiState.update { it.copy(isLoading = false, books = books) } }
            .onFailure { e ->
                Timber.w(e, "[Listen] knihy selhaly")
                _uiState.update { it.copy(isLoading = false, error = "Načtení audioknih selhalo.") }
            }
    }

    // ──────────────────────────── Podcasty ────────────────────────────

    private fun loadPodcastLibraries() {
        if (!repo.isConfigured) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getPodcastLibraries().applyProfileWhitelist() }
                .onSuccess { libs ->
                    if (libs.isEmpty()) {
                        _uiState.update {
                            it.copy(isLoading = false, podcastLibraries = emptyList(), podcasts = emptyList(), podcastsLoaded = true)
                        }
                        return@onSuccess
                    }
                    val selected = _uiState.value.selectedPodcastLibraryId?.takeIf { id -> libs.any { it.id == id } }
                        ?: libs.first().id
                    _uiState.update { it.copy(podcastLibraries = libs, selectedPodcastLibraryId = selected, podcastsLoaded = true) }
                    loadPodcasts(selected)
                }
                .onFailure { e ->
                    Timber.w(e, "[Listen] podcast knihovny selhaly")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení podcastů selhalo. Zkontroluj přihlášení k Audiobookshelf v Nastavení.") }
                }
        }
    }

    fun selectPodcastLibrary(libraryId: String) {
        if (libraryId == _uiState.value.selectedPodcastLibraryId) return
        _uiState.update { it.copy(selectedPodcastLibraryId = libraryId) }
        viewModelScope.launch { loadPodcasts(libraryId) }
    }

    private suspend fun loadPodcasts(libraryId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.getPodcasts(libraryId) }
            .onSuccess { ps -> _uiState.update { it.copy(isLoading = false, podcasts = ps.applyProfileHidden()) } }
            .onFailure { e ->
                Timber.w(e, "[Listen] podcasty selhaly")
                _uiState.update { it.copy(isLoading = false, error = "Načtení podcastů selhalo.") }
            }
    }
}
