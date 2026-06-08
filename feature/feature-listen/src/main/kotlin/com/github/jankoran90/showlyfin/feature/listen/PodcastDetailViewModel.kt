package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import com.github.jankoran90.showlyfin.data.abs.model.PodcastDetail
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val connection: AudiobookPlayerConnection,
    private val downloadManager: EpisodeDownloadManager,
    private val absPrefs: AbsPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState = _uiState.asStateFlow()

    /** Fronta epizod (sdílená s přehrávačem) — pro indikátor a správu v detailu. */
    val queue = connection.queue

    /** Stav přehrávače — pro zvýraznění právě hrané epizody v seznamu. */
    val playerState = connection.state

    /** Akce trailing tlačítka u epizody (z nastavení): 0=fronta konec, 1=fronta další, 2=stáhnout. */
    val episodeQuickAction: Int get() = absPrefs.episodeQuickAction

    /** Jednotné zobrazení epizody (řádky názvu/popisu, poutač hosta, měřítko písma) z nastavení. */
    val episodeDisplay: com.github.jankoran90.showlyfin.feature.listen.ui.EpisodeDisplaySettings
        get() = com.github.jankoran90.showlyfin.feature.listen.ui.EpisodeDisplaySettings(
            titleLines = absPrefs.episodeTitleLines,
            descriptionLines = absPrefs.episodeDescriptionLines,
            highlightGuest = absPrefs.highlightGuest,
            fontScale = absPrefs.episodeFontScale,
        )

    // ── Auto-download DO ZAŘÍZENÍ (offline na telefon) — náš EpisodeDownloadManager ──
    /** Device auto-download je omezený na vybrané podcasty (scope==1) → ukázat per-podcast přepínač. */
    val deviceAutoDownloadSelective: Boolean get() = absPrefs.autoDownloadScope == 1 && absPrefs.autoDownloadNewest > 0

    private val _deviceAutoDownloadOn = MutableStateFlow(false)
    val deviceAutoDownloadOn = _deviceAutoDownloadOn.asStateFlow()

    fun toggleDeviceAutoDownload() {
        val id = currentItemId ?: return
        val newVal = !_deviceAutoDownloadOn.value
        absPrefs.setAutoDownloadPodcast(id, newVal)
        _deviceAutoDownloadOn.value = newVal
    }

    // ── „Prohledat epizody" — dostupné RSS epizody k stažení na ABS server ──
    private val _findState = MutableStateFlow(FindEpisodesState())
    val findState = _findState.asStateFlow()

    /** Otevře sheet a načte dostupné (nestažené) epizody z RSS feedu. */
    fun openFindEpisodes() {
        val id = currentItemId ?: return
        _findState.value = FindEpisodesState(visible = true, loading = true)
        viewModelScope.launch {
            runCatching { repo.getNewServerEpisodes(id) }
                .onSuccess { eps -> _findState.update { it.copy(loading = false, episodes = eps) } }
                .onFailure { e ->
                    Timber.w(e, "[Listen] checkfornew selhal")
                    _findState.update { it.copy(loading = false, error = "Načtení epizod z feedu selhalo.") }
                }
        }
    }

    fun closeFindEpisodes() { _findState.value = FindEpisodesState() }

    fun toggleFindSelection(id: String) = _findState.update {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }

    fun selectAllFind() = _findState.update { it.copy(selectedIds = it.episodes.map { e -> e.id }.toSet()) }
    fun clearFindSelection() = _findState.update { it.copy(selectedIds = emptySet()) }
    fun consumeFindResult() = _findState.update { it.copy(resultMessage = null) }

    /** Stáhne vybrané feed epizody na ABS server (POST download). */
    fun downloadSelectedToServer() {
        val id = currentItemId ?: return
        val s = _findState.value
        val selected = s.episodes.filter { it.id in s.selectedIds }
        if (selected.isEmpty()) return
        _findState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            repo.downloadEpisodesToServer(id, selected)
                .onSuccess {
                    _findState.value = FindEpisodesState(resultMessage = "Zařazeno ke stažení na server: ${selected.size} epizod.")
                }
                .onFailure { e ->
                    Timber.w(e, "[Listen] download na server selhal")
                    _findState.update { it.copy(submitting = false, error = "Stažení na server selhalo.") }
                }
        }
    }

    private var currentItemId: String? = null

    /** Stav stažení per epizoda (badge u řádků). */
    val downloadStates = downloadManager.states

    /** Nefiltrovaný detail (drží všechny epizody; filtr se aplikuje na zobrazení). */
    private var raw: PodcastDetail? = null

    fun load(itemId: String) {
        currentItemId = itemId
        _deviceAutoDownloadOn.value = absPrefs.isAutoDownloadPodcast(itemId)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getPodcastDetail(itemId) }
                .onSuccess { d ->
                    raw = d
                    _uiState.update {
                        it.copy(isLoading = false, detail = applyFilter(d), hideFinished = repo.hideFinishedEpisodes)
                    }
                    // Auto-stáhnout N nejnovějších nepřehraných epizod (z nastavení; no-op když vyp).
                    downloadManager.autoDownloadNewestEpisodes(d.episodes, d.podcast.title, d.podcast.coverUrl)
                }
                .onFailure { e ->
                    Timber.w(e, "[Listen] podcast detail selhal")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení detailu podcastu selhalo.") }
                }
        }
    }

    private fun applyFilter(d: PodcastDetail): PodcastDetail {
        // repo vrací epizody newest-first; pro „nejstarší první" obrátíme.
        var eps = if (absPrefs.episodeSortNewestFirst) d.episodes else d.episodes.reversed()
        if (repo.hideFinishedEpisodes) eps = eps.filterNot { it.isFinished }
        val limit = absPrefs.episodeListLimit
        if (limit > 0 && eps.size > limit) eps = eps.take(limit)
        return d.copy(episodes = eps)
    }

    /** Long-press akce: označit epizodu dokončenou/nedokončenou (server + optimisticky lokálně). */
    fun setEpisodeFinished(episode: PodcastEpisode, finished: Boolean) {
        viewModelScope.launch {
            repo.setEpisodeFinished(episode.itemId, episode.id, finished)
            val cur = raw ?: return@launch
            val updated = cur.copy(
                episodes = cur.episodes.map {
                    if (it.id == episode.id) it.copy(
                        isFinished = finished,
                        progress = if (finished) 1.0 else 0.0,
                        currentTimeSec = if (finished) it.durationSec else 0.0,
                    ) else it
                },
            )
            raw = updated
            _uiState.update { it.copy(detail = applyFilter(updated)) }
        }
    }

    /** Long-press akce: přidat epizodu do fronty (atFront = hned po aktuální). */
    fun enqueue(episode: PodcastEpisode, atFront: Boolean) {
        val p = raw?.podcast
        connection.enqueue(
            QueuedEpisode(episode.itemId, episode.id, episode.title, p?.coverUrl, episode.guest, episode.description, p?.title),
            atFront,
        )
    }

    /** Spustí přehrávání položky z fronty (z detailové fronty). */
    fun playQueued(q: QueuedEpisode) = connection.playQueued(q)

    fun removeFromQueue(episodeId: String) = connection.removeFromQueue(episodeId)
    fun clearQueue() = connection.clearQueue()
    fun clearAll() = connection.clearAll()

    // ──────────────────────────── Offline stažení ────────────────────────────

    /** Stáhne epizodu pro offline poslech. */
    fun downloadEpisode(episode: PodcastEpisode) {
        val p = raw?.podcast
        downloadManager.download(episode, p?.title, p?.coverUrl)
    }

    fun cancelDownload(episodeId: String) = downloadManager.cancel(episodeId)
    fun deleteDownload(episodeId: String) = downloadManager.delete(episodeId)
}
