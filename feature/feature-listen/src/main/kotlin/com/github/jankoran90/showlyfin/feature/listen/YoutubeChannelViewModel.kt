package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.CastTargetPrefs
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.YtEpisode
import com.github.jankoran90.showlyfin.data.uploader.youtubeVideoUrl
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.enqueue
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val resumeStore: DirectResumeStore,
    // BUG (2026-09-04, user „uvidím to video z hledání i na kartě... jako rozposlouchané?"): video
    // (PlaybackViewModel.saveExternalPosition, REWIND SHW-68 store) — parita s RssPodcastScreen,
    // co videoResumeMarks už dřív četlo pro svoje jfItemId video.
    private val videoResumeStore: VideoResumeStore,
    // EPHEMERON (2026-09-04): epizody manuálně připojené ke kartě přes scoped hledání (i mimo okno feedu).
    private val attachedStore: com.github.jankoran90.showlyfin.feature.listen.player.AttachedEpisodeStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Stav offline stahování epizod (badge / akce v menu). Klíč = [episodeKey]. */
    val offlineStates = offline.states

    /** L2b: stav přehrávače (aktuální epizoda + živá pozice) → zvýraznění řádku + ikona hraje/pauza. */
    val playerState = connection.state

    /** L2b: uložené pozice direct epizod (mediaId=[episodeKey]) → progres + „Pokračovat" u nehrané. */
    val resumeMarks = resumeStore.marks

    /** BUG (2026-09-04): video watch pozice (stejný klíč `yt:<id>`, jiný store — video nemá `isFinished`,
     *  viz [com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore] dokumentace). */
    val videoResumeMarks = videoResumeStore.marks

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
    // EPHEMERON (2026-09-04, user 20:51 „pořád nevidím připíchlou tuhle epizodu na kartě podcastu"):
    // `state.episodes` dřív psal JEN jednorázově `load()` úspěch — `hiltViewModel()` bez klíče žije
    // po celou dobu Activity (manuální backstack, ne NavBackStackEntry), takže návrat na tuhle
    // obrazovku PO připojení epizody (jiná obrazovka, scoped hledání) narazil na `loadedFor == channel
    // && episodes.isNotEmpty()` guard a NIKDY se znovu nesloučil s [attachedStore]. Teď REAKTIVNÍ —
    // `state` je `combine` čerstvého feedu s live [AttachedEpisodeStore.bySource], projeví se okamžitě
    // i beze změny obrazovky.
    private val _rawEpisodes = MutableStateFlow<List<YtEpisode>>(emptyList())
    val state: StateFlow<UiState> = combine(_state, _rawEpisodes, attachedStore.bySource) { base, raw, attachedMap ->
        val channel = loadedFor
        val knownIds = raw.mapTo(mutableSetOf()) { it.id }
        val attached = if (channel != null) {
            attachedMap["youtube:$channel"].orEmpty()
                .filter { it.id !in knownIds }
                .map { ep ->
                    YtEpisode(
                        id = ep.id, title = ep.title, thumbnail = ep.imageUrl,
                        duration = ep.durationSec.takeIf { it > 0.0 }, uploadDate = ep.date,
                        description = ep.description, viewCount = ep.viewCount, uploader = ep.subtitle,
                    )
                }
        } else emptyList()
        base.copy(episodes = attached + raw)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""
    // CLARITY: kvalita videa pro stream z Nastavení (360 progresiv / 720·max HLS).
    private val streamQuality get() = PodcastVideoQuality.stream(prefs)

    private var loadedFor: String? = null

    fun load(channel: String) {
        if (loadedFor == channel && _rawEpisodes.value.isNotEmpty()) return
        loadedFor = channel
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.getYtFeed(baseUrl, cookie, channel, limit = YoutubeFeedPrefs.limit(prefs)) }
                .onSuccess { feed ->
                    _rawEpisodes.value = feed.entries
                    _state.update { it.copy(isLoading = false, channelTitle = feed.channel) }
                    // Pre-warm: resolvni audio nejnovějších pár epizod → start přehrávání pak ~okamžitý.
                    feed.entries.take(3).forEach { ep ->
                        viewModelScope.launch { runCatching { uploaderDs.warmYt(baseUrl, cookie, ep.id, "audio") } }
                    }
                    // RESONANCE (SHW-81) D: dovyplň popis + datum u UŽ stažených epizod z čerstvého feedu
                    // (parita s RSS backfill) → offline detail je ukáže i u pořadů otevřených přes YouTube.
                    // Ne-stažené epizody backfillMeta ignoruje (levný no-op).
                    // PERF (2026-08-15, WATCHDOG incident): backfillMeta serializuje CELÝ stažený index
                    // na hlavním vlákně při každé změně → přesunuto na IO (viz RssPodcastViewModel).
                    withContext(Dispatchers.IO) {
                        feed.entries.forEach { ep ->
                            offline.backfillMeta(episodeKey(ep), ep.description, parseEpisodeDateMs(ep.uploadDate), loadedFor?.let { "youtube:$it" })
                        }
                    }
                }
                .onFailure {
                    Timber.w(it, "[TUNER] načtení YouTube feedu selhalo")
                    loadedFor = null
                    _state.update { s -> s.copy(isLoading = false, error = "Nepodařilo se načíst epizody kanálu.") }
                }
        }
    }

    /** URL pro video přehrávač i cast na TV. TRELLIS: přes sdílenou extension (ne holé ytVideoUrl) —
     *  jinak by 720p/„max" chyběl zvuk (starý kombinovaný HLS YouTube přestalo vydávat, appka teď
     *  spojuje video+audio proud sama, viz [UploaderRemoteDataSource.youtubeVideoUrl]). */
    fun videoUrl(ep: YtEpisode): String = uploaderDs.youtubeVideoUrl(baseUrl, cookie, ep.id, streamQuality)

    /** Stabilní klíč epizody pro frontu i offline index (audio). */
    fun episodeKey(ep: YtEpisode): String = "yt:${ep.id}"

    /** User (2026-08-15 16:49) — „Reset poslechu" u rozposlouchané epizody (long-press menu).
     *  BUG (2026-09-04): smaž i video pozici (jiný store) — jinak by "Reset poslechu" nechal video
     *  mark stát a epizoda by dál vypadala rozkoukaná (video má v UI přednost). */
    fun resetPosition(ep: YtEpisode) {
        val key = episodeKey(ep)
        resumeStore.clear(key)
        videoResumeStore.clear(key)
    }

    /** User (2026-08-16, „chci volbu, která označí jako poslechnuto") — ruční „Označit jako poslechnuté". */
    fun markFinished(ep: YtEpisode) {
        val key = episodeKey(ep)
        resumeStore.markFinished(key)
        // BUG (2026-09-04): video mark nemá isFinished a v UI má přednost — bez smazání by "Označit
        // jako poslechnuté" na rozkoukaném videu zůstalo tiše bez efektu.
        videoResumeStore.clear(key)
    }

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

    /** L2b: „Pokračovat" u PRÁVĚ NAČTENÉ (pozastavené) epizody → jen navázat přehrávání (bez reloadu). */
    fun resumeCurrent() = connection.play()

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
                description = ep.description,
                publishedAt = parseEpisodeDateMs(ep.uploadDate),
                // RESONANCE (SHW-81) D: klíč zdroje pro filtr skrytých pořadů offline (dětský profil).
                sourceKey = loadedFor?.let { "youtube:$it" },
            ),
        )
    }

    /** L3: smaž staženou AUDIO epizodu z telefonu. */
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
            val result = naTv.castFerry(jfUrl, jfToken, videoUrl(ep), ep.title, emptyList(), reportUrl, preferredDeviceId = CastTargetPrefs.defaultDeviceId(prefs))
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
