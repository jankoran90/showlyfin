package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * TWINE (SHW-74 / plán F7): SLOUČENÝ pohled na pořad, jehož audio (RSS) a video (YouTube) verze byly
 * propojeny ([PodcastLinkStore]). Načte epizody všech členských zdrojů přes sjednocené
 * [PodcastSourcesRepository.loadEpisodes], spáruje audio↔video ([PodcastPairing]) a vystaví řádky se
 * správným datem (audio/RSS). Přehrávání jde stejnou cestou jako Timeline/RSS obrazovky (sdílený
 * [AudiobookPlayerConnection] + resume klíče `rss:`/`yt:`), video přes proxy URL jako YouTube kanál.
 */
@HiltViewModel
class MergedPodcastViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val connection: AudiobookPlayerConnection,
    private val offline: OfflineDownloadManager,
    private val linkStore: PodcastLinkStore,
    private val naTv: NaTvService,
    resumeStore: DirectResumeStore,
    @javax.inject.Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Stav stahování epizod (badge / akce). Klíč = [PodcastPairing.MergedEpisode.key]. */
    val offlineStates = offline.states

    /** Stav přehrávače → zvýraznění hrané epizody + ikona hraje/pauza. */
    val playerState = connection.state

    /** Uložené pozice direct epizod (mediaId = resume klíč) → progres + „Pokračovat" u nehrané. */
    val resumeMarks = resumeStore.marks

    /** WEFT (SHW-75/W1): jednorázová hláška po pokusu o cast na TV (Toast v obrazovce). Parita s YT/RSS. */
    private val _castMessage = MutableStateFlow<String?>(null)
    val castMessage = _castMessage.asStateFlow()

    fun consumeCastMessage() { _castMessage.value = null }

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val title: String = "",
        val image: String? = null,
        val episodes: List<PodcastPairing.MergedEpisode> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var loadedFor: String? = null

    /** WEFT (SHW-75/W2-FIX): členské zdroje skupiny → odvození REF pro `itemId` fronty (cover skok). */
    private var members: List<PodcastSource> = emptyList()

    /** Načti sloučený pohled propojené skupiny [groupId]. */
    fun load(groupId: String) {
        if (loadedFor == groupId && _state.value.episodes.isNotEmpty()) return
        loadedFor = groupId
        val group = linkStore.links.value.firstOrNull { it.id == groupId }
        if (group == null) {
            _state.update { it.copy(isLoading = false, error = "Propojení už neexistuje.") }
            return
        }
        members = group.members.mapNotNull { mk -> repo.sources.value.firstOrNull { linkStore.key(it) == mk } }
        val title = group.title ?: members.firstOrNull()?.title.orEmpty()
        val image = group.thumbnail ?: members.firstNotNullOfOrNull { it.thumbnail }
        _state.update { it.copy(isLoading = true, error = null, title = title, image = image) }
        viewModelScope.launch {
            val episodesBySource = runCatching {
                withContext(Dispatchers.IO) {
                    members.map { src -> async { src to repo.loadEpisodes(src, limit = EPISODES_PER_SOURCE) } }.awaitAll()
                }
            }.getOrElse {
                Timber.w(it, "[TWINE] načtení epizod propojeného pořadu selhalo")
                _state.update { s -> s.copy(isLoading = false, error = "Nepodařilo se načíst epizody.") }
                return@launch
            }
            val audio = episodesBySource.flatMap { it.second }.filter { it.resumeKey?.startsWith("rss:") == true }
            val video = episodesBySource.flatMap { it.second }.filter { it.resumeKey?.startsWith("yt:") == true }
            val merged = PodcastPairing.pairEpisodes(audio, video)
            _state.update { it.copy(isLoading = false, episodes = merged, image = image ?: merged.firstNotNullOfOrNull { e -> e.imageUrl }) }
        }
    }

    fun unlink() {
        loadedFor?.let { linkStore.unlink(it) }
    }

    // ───────────────────────── Přehrávání / fronta / offline ─────────────────────────

    private fun toQueued(ep: SourceEpisode): QueuedEpisode {
        val key = ep.resumeKey ?: ep.id
        val podcast = ep.subtitle ?: _state.value.title
        val localUrl = offline.localVideo(key)?.let { Uri.fromFile(it).toString() }
        // WEFT (SHW-75/W2-FIX): itemId = REF členského zdroje téhož typu jako epizoda (`rss:`→rss member,
        // `yt:`→youtube member). `currentSourceTarget()` z něj odvodí cover-skok → `groupFor(type, ref)`
        // najde TUTO sloučenou skupinu → cover otevře sloučenou kartu + zvýrazní epizodu (ne NaVýbornou).
        val epType = if (key.startsWith("yt:")) "youtube" else "rss"
        val sourceRef = members.firstOrNull { it.type == epType }?.ref
            ?: members.firstOrNull()?.ref
        return QueuedEpisode(
            itemId = sourceRef ?: _state.value.title.ifBlank { "merged" },
            episodeId = key,
            title = ep.title,
            coverUrl = ep.imageUrl ?: _state.value.image,
            description = ep.description,
            podcastTitle = podcast,
            direct = DirectAudio(url = localUrl ?: ep.streamUrl, durationSec = ep.durationSec, author = podcast),
        )
    }

    /** Přehraj AUDIO verzi (RSS enclosure preferováno; u jen-video epizody = YT audio proxy). */
    fun playAudio(item: PodcastPairing.MergedEpisode) {
        val a = item.audio ?: return
        connection.playDirectEpisode(toQueued(a))
    }

    /** „Pokračovat" u načtené pozastavené epizody → navázat bez reloadu. */
    fun resumeCurrent() = connection.play()

    /** WEFT (SHW-75/W1): fronta s volbou pozice (další = hned po aktuální / na konec) — parita s RSS/YT. */
    fun enqueue(item: PodcastPairing.MergedEpisode, atFront: Boolean = false) {
        val a = item.audio ?: return
        connection.enqueue(toQueued(a), atFront = atFront)
    }

    /** VIDEO URL pro přehrání (YT proxy `kind=video`) — jen u epizody s video verzí. */
    fun videoUrl(item: PodcastPairing.MergedEpisode): String? {
        val v = item.video ?: return null
        val id = v.resumeKey?.removePrefix("yt:") ?: v.id
        return repo.youtubeVideoUrl(id)
    }

    /**
     * WEFT (SHW-75/W1): pošle VIDEO verzi (YouTube) propojené epizody na běžící TV session (FERRY cast),
     * stejnou cestou jako YouTube kanál. Jen u epizod s video verzí. Parita „Přehrát na TV (video)".
     */
    fun castVideoToTv(item: PodcastPairing.MergedEpisode) {
        val url = videoUrl(item) ?: return
        viewModelScope.launch {
            val jfUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val jfToken = prefs.getString("jellyfin_token", "") ?: ""
            val reportUrl = if (baseUrl.isNotBlank() && cookie.isNotBlank()) {
                "${baseUrl.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(cookie, "UTF-8")}"
            } else null
            val result = naTv.castFerry(jfUrl, jfToken, url, item.title, emptyList(), reportUrl)
            Timber.i("[WEFT] cast sloučené video → TV: %s result=%s", item.title, result)
            _castMessage.value = when (result) {
                CastResult.SENT -> "Spuštěno na TV: ${item.title}"
                CastResult.NO_SESSION -> "Na TV nikdo nehraje — otevři Showlyfin/Jellyfin na televizi a zkus znovu."
                CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení → Připojení a účty)."
                CastResult.FAILED -> "Nepodařilo se spustit na TV."
            }
        }
    }

    fun download(item: PodcastPairing.MergedEpisode) {
        val a = item.audio ?: return
        if (a.streamUrl.isBlank()) return
        offline.enqueue(
            OfflineRequest(
                key = item.key,
                title = item.title.ifBlank { _state.value.title },
                subtitle = _state.value.title,
                type = OfflineRequest.TYPE_PODCAST,
                sourceLabel = if (a.resumeKey?.startsWith("yt:") == true) "YouTube" else "Podcast",
                videoUrl = a.streamUrl,
                posterUrl = item.imageUrl ?: _state.value.image,
                durationSec = a.durationSec,
            ),
        )
    }

    fun deleteOffline(item: PodcastPairing.MergedEpisode) = offline.delete(item.key)

    companion object {
        private const val EPISODES_PER_SOURCE = 30
    }
}
