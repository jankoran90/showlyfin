package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
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
 *
 * LEVER (SHW-61) L3: AUDIO epizodu lze stáhnout DO TELEFONU (offline) přes sdílený
 * [OfflineDownloadManager] (`TYPE_PODCAST`) z proxy `/api/yt/stream/{id}?kind=audio`.
 */
@HiltViewModel
class YoutubeChannelViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    private val connection: AudiobookPlayerConnection,
    private val offline: OfflineDownloadManager,
    private val naTv: NaTvService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Stav offline stahování epizod (badge / akce v menu). Klíč = [episodeKey]. */
    val offlineStates = offline.states

    /** L4: jednorázová hláška po pokusu o cast na TV (Toast v obrazovce, pak [consumeCastMessage]). */
    private val _castMessage = MutableStateFlow<String?>(null)
    val castMessage = _castMessage.asStateFlow()

    fun consumeCastMessage() { _castMessage.value = null }

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

    /** Stabilní klíč epizody pro frontu i offline index. */
    fun episodeKey(ep: YtEpisode): String = "yt:${ep.id}"

    /**
     * Mapování YouTube epizody na položku fronty (LEVER): audio přes náš proxy, bez ABS session.
     * L3: stažená epizoda hraje z lokálního `file://` souboru (offline + šetří mobilní data).
     */
    private fun toQueued(ep: YtEpisode): QueuedEpisode {
        val channel = _state.value.channelTitle
        val key = episodeKey(ep)
        val localUrl = offline.localVideo(key)?.let { Uri.fromFile(it).toString() }
        return QueuedEpisode(
            itemId = loadedFor ?: "yt",
            episodeId = key,
            title = ep.title,
            coverUrl = ep.thumbnail,
            description = ep.description,
            podcastTitle = channel,
            direct = DirectAudio(
                url = localUrl ?: uploaderDs.ytStreamUrl(baseUrl, cookie, ep.id, "audio"),
                durationSec = ep.duration ?: 0.0,
                author = channel,
            ),
        )
    }

    /** Spustí AUDIO režim v našem poslechovém přehrávači (mini-player, pozadí, zámek) + do fronty. */
    fun playAudio(ep: YtEpisode) = connection.playDirectEpisode(toQueued(ep))

    /** Přidá YouTube epizodu do fronty (atFront = hned po aktuální, jinak na konec). */
    fun enqueue(ep: YtEpisode, atFront: Boolean) = connection.enqueue(toQueued(ep), atFront)

    /** L3: stáhni AUDIO epizodu do telefonu (offline) přes proxy `/api/yt/stream?kind=audio`. */
    fun download(ep: YtEpisode) {
        offline.enqueue(
            OfflineRequest(
                key = episodeKey(ep),
                title = ep.title,
                subtitle = _state.value.channelTitle,
                type = OfflineRequest.TYPE_PODCAST,
                sourceLabel = "YouTube",
                videoUrl = uploaderDs.ytStreamUrl(baseUrl, cookie, ep.id, "audio"),
                posterUrl = ep.thumbnail,
                durationSec = ep.duration ?: 0.0,
            ),
        )
    }

    /** L3: smaž staženou epizodu z telefonu. */
    fun deleteOffline(ep: YtEpisode) = offline.delete(episodeKey(ep))

    /**
     * L4 (LEVER): pošle VIDEO verzi epizody na běžící yellyfin session na TV/boxu (FERRY cast),
     * stejně jako film z Detailu. Bez titulků (YouTube video), bez „telefon = ovladač" (polish).
     * Výsledek → jednorázová [castMessage] (Toast v obrazovce).
     */
    fun castVideoToTv(ep: YtEpisode) {
        viewModelScope.launch {
            val jfUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val jfToken = prefs.getString("jellyfin_token", "") ?: ""
            val reportUrl = if (baseUrl.isNotBlank() && cookie.isNotBlank()) {
                "${baseUrl.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(cookie, "UTF-8")}"
            } else null
            val result = naTv.castFerry(jfUrl, jfToken, videoUrl(ep), ep.title, emptyList(), reportUrl)
            Timber.i("[LEVER] cast YouTube video → TV: %s result=%s", ep.title, result)
            _castMessage.value = when (result) {
                CastResult.SENT -> "Spuštěno na TV: ${ep.title}"
                CastResult.NO_SESSION -> "Na TV nikdo nehraje — otevři Showlyfin/Jellyfin na televizi a zkus znovu."
                CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení → Připojení a účty)."
                CastResult.FAILED -> "Nepodařilo se spustit na TV."
            }
        }
    }
}
