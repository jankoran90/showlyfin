package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.YtEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * TUNER (SHW-62): YouTube kanál jako podcast — seznam epizod (streaming přes backend api/yt),
 * přehrání VIDEO (callback → externí přehrávač) nebo AUDIO (náš poslechový přehrávač přes [playDirect]).
 * Žádné stahování na server.
 */
@HiltViewModel
class YoutubeChannelViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    private val connection: AudiobookPlayerConnection,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val channelTitle: String? = null,
        val episodes: List<YtEpisode> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var loadedFor: String? = null

    fun load(channel: String) {
        if (loadedFor == channel && _state.value.episodes.isNotEmpty()) return
        loadedFor = channel
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.getYtFeed(baseUrl, cookie, channel, limit = 40) }
                .onSuccess { feed ->
                    _state.update {
                        it.copy(isLoading = false, channelTitle = feed.channel, episodes = feed.entries)
                    }
                    // Pre-warm: resolvni audio nejnovějších pár epizod → start přehrávání pak ~okamžitý.
                    feed.entries.take(3).forEach { ep ->
                        viewModelScope.launch { runCatching { uploaderDs.warmYt(baseUrl, cookie, ep.id, "audio") } }
                    }
                }
                .onFailure {
                    Timber.w(it, "[TUNER] načtení YouTube feedu selhalo")
                    loadedFor = null
                    _state.update { s -> s.copy(isLoading = false, error = "Nepodařilo se načíst epizody kanálu.") }
                }
        }
    }

    /** URL pro externí (video) přehrávač — byte-proxy přes backend. */
    fun videoUrl(ep: YtEpisode): String = uploaderDs.ytStreamUrl(baseUrl, cookie, ep.id, "video")

    /** Mapování YouTube epizody na položku fronty (LEVER): audio přes náš proxy, bez ABS session. */
    private fun toQueued(ep: YtEpisode): QueuedEpisode {
        val channel = _state.value.channelTitle
        return QueuedEpisode(
            itemId = loadedFor ?: "yt",
            episodeId = "yt:${ep.id}",
            title = ep.title,
            coverUrl = ep.thumbnail,
            description = ep.description,
            podcastTitle = channel,
            direct = DirectAudio(
                url = uploaderDs.ytStreamUrl(baseUrl, cookie, ep.id, "audio"),
                durationSec = ep.duration ?: 0.0,
                author = channel,
            ),
        )
    }

    /** Spustí AUDIO režim v našem poslechovém přehrávači (mini-player, pozadí, zámek) + do fronty. */
    fun playAudio(ep: YtEpisode) = connection.playDirectEpisode(toQueued(ep))

    /** Přidá YouTube epizodu do fronty (atFront = hned po aktuální, jinak na konec). */
    fun enqueue(ep: YtEpisode, atFront: Boolean) = connection.enqueue(toQueued(ep), atFront)
}
