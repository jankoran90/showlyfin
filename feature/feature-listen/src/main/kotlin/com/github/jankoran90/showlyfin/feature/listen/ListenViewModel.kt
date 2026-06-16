package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.network.ConnectivityObserver
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.toAudiobook
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
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
    private val audiobookDownloads: AudiobookDownloadManager,
    private val connectivity: ConnectivityObserver,
    private val profileRepository: ProfileRepository,
    private val sourcesRepo: PodcastSourcesRepository,
    private val absPrefs: AbsPreferences,
) : ViewModel() {

    /** PRESET (SHW-65) — seřaď knihovny dle ručního pořadí ([order] = ID knihoven); neznámé na konec. */
    private fun List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>.ordered(order: List<String>):
        List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary> {
        if (order.isEmpty()) return this
        val idx = order.withIndex().associate { (i, id) -> id to i }
        return sortedBy { idx[it.id] ?: Int.MAX_VALUE }
    }

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

        // Plan CASTAWAY — offline police: drž množinu stažených knih (badge „staženo") a v offline
        // režimu jimi naplň seznam, aby šly otevřít i bez sítě.
        audiobookDownloads.downloads
            .onEach { dls ->
                _uiState.update { it.copy(downloadedBookIds = dls.map { d -> d.itemId }.toSet()) }
                if (!connectivity.isCurrentlyOnline()) refresh()
            }
            .launchIn(viewModelScope)

        // Reaguj na změnu konektivity: offline → degraduj na stažené; návrat online → načti znovu.
        // (StateFlow už emituje jen distinct hodnoty, proto bez distinctUntilChanged.)
        connectivity.isOnline
            .onEach { online ->
                _uiState.update { it.copy(isOffline = !online) }
                refresh()
                if (online && (_uiState.value.podcastsLoaded || _uiState.value.mode == ListenMode.PODCASTS)) {
                    loadPodcastLibraries()
                }
                // PRESET — vlastní zdroje (sdílené ze serveru) jsou nezávislé na ABS; načti při návratu online.
                if (online) loadSources()
            }
            .launchIn(viewModelScope)

        // PRESET (SHW-65) — reaktivně zrcadli sdílený seznam zdrojů do UI (přidání/odebrání kdekoli se
        // okamžitě projeví v sekci Podcasty). Nezávislé na ABS přihlášení.
        sourcesRepo.sources
            // PRESET FIX: vlastní zdroje řaď ABECEDNĚ dle názvu (ne v pořadí přidání — joe rogan
            // se jinak lepil nahoru jako poslední přidaný). Diakritika-insensitivně.
            .onEach { srcs ->
                val sorted = srcs.sortedBy { it.title.lowercase(java.util.Locale("cs")) }
                _uiState.update { it.copy(customSources = sorted) }
            }
            .launchIn(viewModelScope)
        loadSources()
        _uiState.update { it.copy(booksFirst = absPrefs.listenBooksFirst) }
    }

    /** PRESET — načti/obnov sdílený seznam vlastních zdrojů (YouTube/RSS) ze serveru. */
    fun loadSources() {
        viewModelScope.launch { sourcesRepo.refresh() }
    }

    /**
     * PRESET (SHW-65) — znovu načti pořadí Poslechu z Nastavení (po návratu z Nastavení) a přeřaď
     * už načtené knihovny. Volá ListenScreen při vstupu.
     */
    fun reloadOrderPrefs() {
        _uiState.update {
            it.copy(
                booksFirst = absPrefs.listenBooksFirst,
                libraries = it.libraries.ordered(absPrefs.audiobookLibraryOrder),
                podcastLibraries = it.podcastLibraries.ordered(absPrefs.podcastLibraryOrder),
            )
        }
    }

    /** PRESET — odeber vlastní zdroj ze sdíleného store (projeví se u celé rodiny). */
    fun removeSource(id: String) {
        viewModelScope.launch { sourcesRepo.remove(id) }
    }

    /** Stažené audioknihy jako UI police (Plan CASTAWAY CA-2). */
    private fun downloadedBooks(): List<Audiobook> =
        audiobookDownloads.downloads.value.map { it.toAudiobook() }

    /** Načte knihovny audioknih a knihy ve vybrané (či první) knihovně. */
    fun refresh() {
        val offlineBooks = downloadedBooks()
        // Plan CASTAWAY — bez přihlášení k ABS ukaž aspoň stažené knihy (offline police), ať jdou hrát.
        if (!repo.isConfigured) {
            _uiState.update {
                it.copy(
                    isConfigured = offlineBooks.isNotEmpty(),
                    isLoading = false,
                    isOffline = !connectivity.isCurrentlyOnline(),
                    libraries = emptyList(),
                    books = offlineBooks,
                    error = null,
                )
            }
            return
        }
        // Plan CASTAWAY — offline: nestreamuj seznam, rovnou ukaž stažené knihy.
        if (!connectivity.isCurrentlyOnline()) {
            _uiState.update {
                it.copy(
                    isConfigured = true, isLoading = false, isOffline = true,
                    libraries = emptyList(), books = offlineBooks, error = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConfigured = true, isLoading = true, isOffline = false, error = null) }
            runCatching { repo.getAudiobookLibraries().applyProfileWhitelist().ordered(absPrefs.audiobookLibraryOrder) }
                .onSuccess { libs ->
                    if (libs.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, libraries = emptyList(), books = downloadedBooks()) }
                        return@onSuccess
                    }
                    val selected = _uiState.value.selectedLibraryId?.takeIf { id -> libs.any { it.id == id } }
                        ?: libs.first().id
                    _uiState.update { it.copy(libraries = libs, selectedLibraryId = selected) }
                    loadBooks(selected)
                }
                .onFailure { e ->
                    // Síť/server selhaly i přes „online" stav → degraduj na stažené knihy místo prázdna.
                    Timber.w(e, "[Listen] knihovny selhaly")
                    val books = downloadedBooks()
                    _uiState.update {
                        it.copy(
                            isLoading = false, libraries = emptyList(), books = books, isOffline = true,
                            error = if (books.isEmpty()) "Načtení knihoven selhalo. Zkontroluj přihlášení k Audiobookshelf v Nastavení." else null,
                        )
                    }
                }
        }
    }

    /** Přepnutí Audioknihy ↔ Podcasty. Podcasty se načtou líně při prvním přepnutí. */
    fun setMode(mode: ListenMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update { it.copy(mode = mode, error = null) }
        if (mode == ListenMode.PODCASTS) {
            if (!_uiState.value.podcastsLoaded) loadPodcastLibraries()
            loadSources()   // PRESET — obnov vlastní zdroje při vstupu do Podcastů (mohly přibýt z jiného telefonu)
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
                val offline = downloadedBooks()
                _uiState.update {
                    it.copy(
                        isLoading = false, books = offline, isOffline = true,
                        error = if (offline.isEmpty()) "Načtení audioknih selhalo." else null,
                    )
                }
            }
    }

    // ──────────────────────────── Podcasty ────────────────────────────

    private fun loadPodcastLibraries() {
        if (!repo.isConfigured) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getPodcastLibraries().applyProfileWhitelist().ordered(absPrefs.podcastLibraryOrder) }
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
