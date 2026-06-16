package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.RssEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * PRESET (SHW-65): RSS podcast jako zdroj Poslechu — seznam epizod z feedu (přímé audio enclosure URL),
 * přehrání přes náš poslechový přehrávač ([playDirect] = mini-player, pozadí, zámek, rychlost, sleep).
 * Žádné stahování na server (na rozdíl od ABS) → sedí do filozofie nezávislosti.
 */
@HiltViewModel
class RssPodcastViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val connection: AudiobookPlayerConnection,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val title: String? = null,
        val image: String? = null,
        val episodes: List<RssEpisode> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var loadedFor: String? = null

    fun load(feedUrl: String) {
        if (loadedFor == feedUrl && _state.value.episodes.isNotEmpty()) return
        loadedFor = feedUrl
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.loadRss(feedUrl) }
                .onSuccess { feed ->
                    _state.update {
                        it.copy(isLoading = false, title = feed.title, image = feed.image, episodes = feed.episodes)
                    }
                }
                .onFailure {
                    Timber.w(it, "[PRESET] načtení RSS feedu selhalo")
                    loadedFor = null
                    _state.update { s -> s.copy(isLoading = false, error = "Nepodařilo se načíst epizody podcastu.") }
                }
        }
    }

    /** Spustí epizodu v našem poslechovém přehrávači (přímá enclosure URL, bez stahování). */
    fun playAudio(ep: RssEpisode, fallbackTitle: String) {
        connection.playDirect(
            url = ep.audioUrl,
            title = ep.title.ifBlank { fallbackTitle },
            author = _state.value.title ?: fallbackTitle,
            coverUrl = ep.image ?: _state.value.image,
            durationSec = parseDurationSec(ep.duration),
            mediaId = "rss:${ep.id}",
        )
    }
}

/** itunes:duration → sekundy. Podporuje "HH:MM:SS", "MM:SS" i čisté sekundy. */
private fun parseDurationSec(d: String?): Double {
    val t = d?.trim().orEmpty()
    if (t.isEmpty()) return 0.0
    if (":" in t) {
        val p = t.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (p.size) {
            3 -> (p[0] * 3600 + p[1] * 60 + p[2]).toDouble()
            2 -> (p[0] * 60 + p[1]).toDouble()
            else -> 0.0
        }
    }
    return t.toDoubleOrNull() ?: 0.0
}
