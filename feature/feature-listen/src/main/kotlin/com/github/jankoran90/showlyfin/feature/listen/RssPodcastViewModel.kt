package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.EpisodeVideo
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
import javax.inject.Named

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
    private val naTv: NaTvService,
    resumeStore: DirectResumeStore,
    videoResumeStore: VideoResumeStore,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Stav offline stahování epizod (badge / akce v menu). Klíč = [episodeKey]. */
    val offlineStates = offline.states

    /** L2b: stav přehrávače (aktuální epizoda + živá pozice) → zvýraznění řádku + ikona hraje/pauza. */
    val playerState = connection.state

    /** L2b: uložené pozice direct (audio) epizod (mediaId=[episodeKey]) → progres + „Pokračovat" u nehrané. */
    val resumeMarks = resumeStore.marks

    /** REWIND (SHW-68): uložené pozice VIDEA (sdílený klíč = [episodeKey]) → progres + „Pokračovat" u video epizody. */
    val videoResumeMarks = videoResumeStore.marks

    /** EXODUS E2: jednorázová hláška po pokusu o cast videa na TV (Toast v obrazovce). */
    private val _castMessage = MutableStateFlow<String?>(null)
    val castMessage = _castMessage.asStateFlow()

    fun consumeCastMessage() { _castMessage.value = null }

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

    /**
     * EXODUS (SHW-67) E2: pošle VIDEO verzi epizody (JF knihovní položka) na běžící yellyfin session
     * na TV/boxu (FERRY cast), jako film z Detailu. Jen u epizod, co mají [RssEpisode.jfItemId].
     */
    fun castVideoToTv(ep: RssEpisode) {
        val itemId = ep.jfItemId ?: return
        viewModelScope.launch {
            val jfUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val jfToken = prefs.getString("jellyfin_token", "") ?: ""
            val base = prefs.getString("uploader_base_url", "") ?: ""
            val cookie = prefs.getString("uploader_session_cookie", "") ?: ""
            val streamUrl = "${jfUrl.trimEnd('/')}/Videos/$itemId/stream?static=true&api_key=$jfToken"
            val reportUrl = if (base.isNotBlank() && cookie.isNotBlank()) {
                "${base.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(cookie, "UTF-8")}"
            } else null
            val result = naTv.castFerry(jfUrl, jfToken, streamUrl, ep.title, emptyList(), reportUrl)
            Timber.i("[EXODUS] cast NaVýbornou video → TV: %s result=%s", ep.title, result)
            _castMessage.value = when (result) {
                CastResult.SENT -> "Spuštěno na TV: ${ep.title}"
                CastResult.NO_SESSION -> "Na TV nikdo nehraje — otevři Showlyfin/Jellyfin na televizi a zkus znovu."
                CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení → Připojení a účty)."
                CastResult.FAILED -> "Nepodařilo se spustit na TV."
            }
        }
    }

    // ───────────────────────── AGORA (F5): VIDEO verze epizody přes YouTube ─────────────────────────

    /** F5: probíhá hledání video verze (spinner v menu / blokace dvojího ťuku). */
    private val _videoSearching = MutableStateFlow(false)
    val videoSearching = _videoSearching.asStateFlow()

    /** Sestaví dotaz pro YouTube: „<název podcastu> <název epizody>". */
    private fun videoQuery(ep: RssEpisode, fallbackTitle: String): String {
        val podcast = _state.value.title ?: fallbackTitle
        return listOf(podcast, ep.title).filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    /**
     * F5: vybere nejlepšího kandidáta video verze. Když epizoda zná délku, preferuj kandidáta s délkou
     * do ~20 % od epizody (video verze bývá ~stejně dlouhá); jinak preferuj uploadera textově podobného
     * názvu podcastu/autora; fallback = první (backend řadí dle relevance).
     */
    private fun pickBestVideo(candidates: List<EpisodeVideo>, ep: RssEpisode, fallbackTitle: String): EpisodeVideo? {
        if (candidates.isEmpty()) return null
        val epDur = parseDurationSec(ep.duration)
        if (epDur > 0) {
            val tol = epDur * 0.20
            candidates.filter { it.duration > 0 && kotlin.math.abs(it.duration - epDur) <= tol }
                .minByOrNull { kotlin.math.abs(it.duration - epDur) }
                ?.let { return it }
        }
        val podcast = (_state.value.title ?: fallbackTitle).lowercase()
        candidates.firstOrNull { c ->
            val up = c.uploader.lowercase()
            up.isNotBlank() && (podcast.contains(up) || up.contains(podcast) ||
                podcast.split(" ").any { it.length >= 4 && up.contains(it) })
        }?.let { return it }
        return candidates.first()
    }

    /**
     * F5: dohledej video verzi epizody na YouTube a PŘEHRÁJ ji (proxy `/api/yt/stream/{id}?kind=video`).
     * Přehrání řeší obrazovka přes [onResolved] (navigace na video přehrávač s externí URL) — stejnou
     * cestou jako video YouTube kanálu. Když nic nenajde → Toast „Video verze nenalezena".
     */
    fun findAndPlayVideo(ep: RssEpisode, fallbackTitle: String, onResolved: (url: String, title: String, poster: String?) -> Unit) {
        if (_videoSearching.value) return
        viewModelScope.launch {
            _videoSearching.value = true
            val best = pickBestVideo(repo.findEpisodeVideo(videoQuery(ep, fallbackTitle)), ep, fallbackTitle)
            _videoSearching.value = false
            if (best == null) { _castMessage.value = "Video verze nenalezena."; return@launch }
            Timber.i("[AGORA] video verze epizody '%s' → yt=%s (%s)", ep.title, best.id, best.title)
            onResolved(repo.youtubeVideoUrl(best.id), ep.title.ifBlank { fallbackTitle }, ep.image ?: _state.value.image)
        }
    }

    /**
     * F5: dohledej video verzi epizody na YouTube a pošli ji na TV (FERRY cast), stejně jako YouTube kanál.
     * URL = proxy `/api/yt/stream/{id}?kind=video`. Výsledek → jednorázová [castMessage] (Toast).
     */
    fun findAndCastVideo(ep: RssEpisode, fallbackTitle: String) {
        if (_videoSearching.value) return
        viewModelScope.launch {
            _videoSearching.value = true
            val best = pickBestVideo(repo.findEpisodeVideo(videoQuery(ep, fallbackTitle)), ep, fallbackTitle)
            _videoSearching.value = false
            if (best == null) { _castMessage.value = "Video verze nenalezena."; return@launch }
            val jfUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val jfToken = prefs.getString("jellyfin_token", "") ?: ""
            val base = prefs.getString("uploader_base_url", "") ?: ""
            val cookie = prefs.getString("uploader_session_cookie", "") ?: ""
            val reportUrl = if (base.isNotBlank() && cookie.isNotBlank()) {
                "${base.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(cookie, "UTF-8")}"
            } else null
            val result = naTv.castFerry(jfUrl, jfToken, repo.youtubeVideoUrl(best.id), ep.title, emptyList(), reportUrl)
            Timber.i("[AGORA] cast YouTube video verze → TV: %s result=%s", ep.title, result)
            _castMessage.value = when (result) {
                CastResult.SENT -> "Spuštěno na TV: ${ep.title}"
                CastResult.NO_SESSION -> "Na TV nikdo nehraje — otevři Showlyfin/Jellyfin na televizi a zkus znovu."
                CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení → Připojení a účty)."
                CastResult.FAILED -> "Nepodařilo se spustit na TV."
            }
        }
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
