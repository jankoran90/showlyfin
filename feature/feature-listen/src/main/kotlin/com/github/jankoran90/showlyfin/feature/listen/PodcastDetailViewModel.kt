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

    // ── Auto-download NA ABS SERVER (ABS-nativní, per-podcast) ──
    private val _serverAutoDownloadOn = MutableStateFlow(false)
    val serverAutoDownloadOn = _serverAutoDownloadOn.asStateFlow()
    private val _serverAutoDownloadBusy = MutableStateFlow(false)
    val serverAutoDownloadBusy = _serverAutoDownloadBusy.asStateFlow()

    fun toggleServerAutoDownload() {
        val id = currentItemId ?: return
        val newVal = !_serverAutoDownloadOn.value
        viewModelScope.launch {
            _serverAutoDownloadBusy.value = true
            repo.setServerAutoDownload(id, newVal)
                .onSuccess { _serverAutoDownloadOn.value = newVal }
                .onFailure { Timber.w(it, "[Listen] server auto-download PATCH selhal") }
            _serverAutoDownloadBusy.value = false
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
        viewModelScope.launch { _serverAutoDownloadOn.value = repo.getServerAutoDownload(itemId) }
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
        val cover = raw?.podcast?.coverUrl
        connection.enqueue(QueuedEpisode(episode.itemId, episode.id, episode.title, cover), atFront)
    }

    fun removeFromQueue(episodeId: String) = connection.removeFromQueue(episodeId)
    fun clearQueue() = connection.clearQueue()

    // ──────────────────────────── Offline stažení ────────────────────────────

    /** Stáhne epizodu pro offline poslech. */
    fun downloadEpisode(episode: PodcastEpisode) {
        val p = raw?.podcast
        downloadManager.download(episode, p?.title, p?.coverUrl)
    }

    fun cancelDownload(episodeId: String) = downloadManager.cancel(episodeId)
    fun deleteDownload(episodeId: String) = downloadManager.delete(episodeId)
}
