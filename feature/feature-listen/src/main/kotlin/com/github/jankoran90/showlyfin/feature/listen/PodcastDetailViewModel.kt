package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState = _uiState.asStateFlow()

    /** Fronta epizod (sdílená s přehrávačem) — pro indikátor a správu v detailu. */
    val queue = connection.queue

    /** Nefiltrovaný detail (drží všechny epizody; filtr se aplikuje na zobrazení). */
    private var raw: PodcastDetail? = null

    fun load(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getPodcastDetail(itemId) }
                .onSuccess { d ->
                    raw = d
                    _uiState.update {
                        it.copy(isLoading = false, detail = applyFilter(d), hideFinished = repo.hideFinishedEpisodes)
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "[Listen] podcast detail selhal")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení detailu podcastu selhalo.") }
                }
        }
    }

    private fun applyFilter(d: PodcastDetail): PodcastDetail =
        if (repo.hideFinishedEpisodes) d.copy(episodes = d.episodes.filterNot { it.isFinished }) else d

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
}
