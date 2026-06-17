package com.github.jankoran90.showlyfin.feature.listen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.RssEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
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
 *
 * LEVER (SHW-61) L3: epizodu lze stáhnout DO TELEFONU (offline „na chatu bez wifi") přes sdílený
 * [OfflineDownloadManager] (`TYPE_PODCAST`). Stažená epizoda se přehraje z lokálního `file://` souboru.
 */
@HiltViewModel
class RssPodcastViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val connection: AudiobookPlayerConnection,
    private val offline: OfflineDownloadManager,
    resumeStore: DirectResumeStore,
) : ViewModel() {

    /** Stav offline stahování epizod (badge / akce v menu). Klíč = [episodeKey]. */
    val offlineStates = offline.states

    /** L2b: stav přehrávače (aktuální epizoda + živá pozice) → zvýraznění řádku + ikona hraje/pauza. */
    val playerState = connection.state

    /** L2b: uložené pozice direct epizod (mediaId=[episodeKey]) → progres + „Pokračovat" u nehrané. */
    val resumeMarks = resumeStore.marks

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

    /** Stabilní klíč epizody pro frontu i offline index. */
    fun episodeKey(ep: RssEpisode): String = "rss:${ep.id}"

    /**
     * Mapování RSS epizody na položku fronty (LEVER): přímá enclosure URL, bez ABS session.
     * L3: když je epizoda STAŽENÁ, hraj z lokálního `file://` souboru (offline + šetří data).
     */
    private fun toQueued(ep: RssEpisode, fallbackTitle: String): QueuedEpisode {
        val podcast = _state.value.title ?: fallbackTitle
        val key = episodeKey(ep)
        val localUrl = offline.localVideo(key)?.let { Uri.fromFile(it).toString() }
        return QueuedEpisode(
            itemId = loadedFor ?: "rss",
            episodeId = key,
            title = ep.title.ifBlank { fallbackTitle },
            coverUrl = ep.image ?: _state.value.image,
            description = ep.description,
            podcastTitle = podcast,
            direct = DirectAudio(
                url = localUrl ?: ep.audioUrl,
                durationSec = parseDurationSec(ep.duration),
                author = podcast,
            ),
        )
    }

    /** Spustí epizodu v našem poslechovém přehrávači (přímá enclosure URL, bez stahování) + do fronty. */
    fun playAudio(ep: RssEpisode, fallbackTitle: String) =
        connection.playDirectEpisode(toQueued(ep, fallbackTitle))

    /** L2b: „Pokračovat" u PRÁVĚ NAČTENÉ (pozastavené) epizody → jen navázat přehrávání (bez reloadu). */
    fun resumeCurrent() = connection.play()

    /** Přidá RSS epizodu do fronty (atFront = hned po aktuální, jinak na konec). */
    fun enqueue(ep: RssEpisode, fallbackTitle: String, atFront: Boolean) =
        connection.enqueue(toQueued(ep, fallbackTitle), atFront)

    /** L3: stáhni epizodu do telefonu (offline). Idempotentní (už stažené/stahující se přeskočí). */
    fun download(ep: RssEpisode, fallbackTitle: String) {
        if (ep.audioUrl.isBlank()) return
        val podcast = _state.value.title ?: fallbackTitle
        offline.enqueue(
            OfflineRequest(
                key = episodeKey(ep),
                title = ep.title.ifBlank { fallbackTitle },
                subtitle = podcast,
                type = OfflineRequest.TYPE_PODCAST,
                sourceLabel = "Podcast",
                videoUrl = ep.audioUrl,
                posterUrl = ep.image ?: _state.value.image,
                durationSec = parseDurationSec(ep.duration),
            ),
        )
    }

    /** L3: smaž staženou epizodu z telefonu. */
    fun deleteOffline(ep: RssEpisode) = offline.delete(episodeKey(ep))
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
