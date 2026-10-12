package com.github.jankoran90.showlyfin.feature.listen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.enqueue
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastEpisodeSeriesGrouping
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Poslech dětí — dlouhý stisk na dlaždici pořadu / série (user 2026-10-12: „nabídka stáhnout —
 * zařadí do fronty a stáhne všechny díly"). Díly v pořadí od nejstaršího (série se poslouchá
 * od 1. dílu), jen audio. Hotovo = hláška v [message].
 */
@HiltViewModel
class KidsShelfActionsViewModel @Inject constructor(
    private val sourcesRepo: PodcastSourcesRepository,
    private val offline: OfflineDownloadManager,
    private val connection: AudiobookPlayerConnection,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    /** [seriesSlug] != null = jen díly téhle série (dítěti schválená jen série). */
    fun downloadAndQueue(source: PodcastSource, seriesSlug: String?, title: String) {
        viewModelScope.launch {
            val episodes = sourcesRepo.loadEpisodes(source, limit = 500)
                .filter { ep ->
                    seriesSlug == null || PodcastEpisodeSeriesGrouping.detectSeriesTitle(ep.title)
                        ?.let(PodcastEpisodeSeriesGrouping::seriesSlug) == seriesSlug
                }
                .filter { it.streamUrl.isNotBlank() }
                .reversed() // feed = nejnovější první → fronta od 1. dílu
            if (episodes.isEmpty()) {
                _message.value = "Žádné díly ke stažení (zkontroluj připojení)."
                return@launch
            }
            episodes.forEach { ep ->
                val key = ep.resumeKey ?: "${source.type}:${ep.id}"
                offline.enqueue(
                    OfflineRequest(
                        key = key,
                        title = ep.title,
                        subtitle = title,
                        type = OfflineRequest.TYPE_PODCAST,
                        sourceLabel = "Podcast",
                        videoUrl = ep.streamUrl,
                        posterUrl = ep.imageUrl,
                        durationSec = ep.durationSec,
                        description = ep.description,
                        publishedAt = parseEpisodeDateMs(ep.date),
                        sourceKey = source.key,
                    ),
                )
                val local = offline.localVideo(key)?.let { Uri.fromFile(it).toString() }
                connection.enqueue(
                    QueuedEpisode(
                        itemId = source.ref,
                        episodeId = key,
                        title = ep.title,
                        coverUrl = ep.imageUrl,
                        description = ep.description,
                        podcastTitle = title,
                        direct = DirectAudio(url = local ?: ep.streamUrl, durationSec = ep.durationSec, author = title),
                    ),
                    atFront = false,
                )
            }
            _message.value = "Stahuji ${episodes.size} dílů a řadím je do fronty."
        }
    }
}
