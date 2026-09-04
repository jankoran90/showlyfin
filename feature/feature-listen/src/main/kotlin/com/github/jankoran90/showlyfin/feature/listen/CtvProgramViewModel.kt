package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.CastTargetPrefs
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.CtvEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.enqueue
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * KAVKA (SHW-76): ČT iVysílání pořad jako podcast — seznam dílů (přes backend api/ctv), přehrání
 * VIDEO (callback → externí přehrávač, DASH) nebo AUDIO (náš poslechový přehrávač přes [playAudio],
 * audio-only DASH varianta). Symetrické k [YoutubeChannelViewModel]; ČT díl nese zvuk i obraz v jednom,
 * takže žádné párování dvou zdrojů (na rozdíl od TWINE).
 *
 * Streaming-only: ČT díl je DASH (manifest + segmenty), ne jeden soubor → offline stahování se zatím
 * nenabízí (na rozdíl od YouTube m4a). Resume pozice (DirectResumeStore) se sdílí jako u YouTube/RSS.
 */
@HiltViewModel
class CtvProgramViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    private val connection: AudiobookPlayerConnection,
    private val naTv: NaTvService,
    private val resumeStore: DirectResumeStore,
    // BUG (2026-09-04): parita s YoutubeChannelViewModel/RssPodcastScreen — video (PlaybackViewModel.
    // saveExternalPosition, REWIND SHW-68 store) se dřív do „poslechového" resume vůbec nepromítlo.
    private val videoResumeStore: com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore,
    // EPHEMERON (2026-09-04): epizody manuálně připojené ke kartě přes scoped hledání (i mimo okno feedu).
    private val attachedStore: com.github.jankoran90.showlyfin.feature.listen.player.AttachedEpisodeStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Stav přehrávače (aktuální epizoda + živá pozice) → zvýraznění řádku + ikona hraje/pauza. */
    val playerState = connection.state

    /** Uložené pozice direct epizod (mediaId=[episodeKey]) → progres + „Pokračovat" u nehrané. */
    val resumeMarks = resumeStore.marks

    /** BUG (2026-09-04): video watch pozice (stejný klíč `ctv:<id>`, jiný store bez `isFinished`). */
    val videoResumeMarks = videoResumeStore.marks

    /** Jednorázová hláška po pokusu o cast na TV (Toast v obrazovce, pak [consumeCastMessage]). */
    private val _castMessage = MutableStateFlow<String?>(null)
    val castMessage = _castMessage.asStateFlow()

    fun consumeCastMessage() { _castMessage.value = null }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val showTitle: String? = null,
        val episodes: List<CtvEpisode> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    // EPHEMERON (2026-09-04): REAKTIVNÍ merge s [AttachedEpisodeStore] — viz shodná oprava a
    // zdůvodnění v [YoutubeChannelViewModel.state] (`hiltViewModel()` bez klíče přežívá návrat na
    // obrazovku, jednorázový merge v `load()` se bez toho nikdy neprojevil po připojení epizody jinde).
    private val _rawEpisodes = MutableStateFlow<List<CtvEpisode>>(emptyList())
    val state: StateFlow<UiState> = combine(_state, _rawEpisodes, attachedStore.bySource) { base, raw, attachedMap ->
        val show = loadedFor
        val knownIds = raw.mapTo(mutableSetOf()) { it.id }
        val attached = if (show != null) {
            attachedMap["ctv:$show"].orEmpty()
                .filter { it.id !in knownIds }
                .map { ep ->
                    CtvEpisode(
                        id = ep.id, title = ep.title, description = ep.description,
                        duration = ep.durationSec.takeIf { it > 0.0 }, date = ep.date, image = ep.imageUrl,
                    )
                }
        } else emptyList()
        base.copy(episodes = attached + raw)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var loadedFor: String? = null

    fun load(show: String) {
        if (loadedFor == show && _rawEpisodes.value.isNotEmpty()) return
        loadedFor = show
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.getCtvFeed(baseUrl, cookie, show, limit = 100) }
                .onSuccess { feed ->
                    _rawEpisodes.value = feed.episodes
                    _state.update { it.copy(isLoading = false, showTitle = feed.title) }
                    // Pre-warm: resolvni nejnovější pár dílů → start přehrávání pak ~okamžitý.
                    feed.episodes.take(2).forEach { ep ->
                        viewModelScope.launch { runCatching { uploaderDs.warmCtv(baseUrl, cookie, ep.id) } }
                    }
                }
                .onFailure {
                    Timber.w(it, "[KAVKA] načtení ČT feedu selhalo")
                    loadedFor = null
                    _state.update { s -> s.copy(isLoading = false, error = "Nepodařilo se načíst díly pořadu.") }
                }
        }
    }

    /** URL pro video přehrávač i cast na TV — plný DASH manifest (ExoPlayer ABR, default nejvyšší). */
    fun videoUrl(ep: CtvEpisode): String = uploaderDs.ctvVideoUrl(baseUrl, cookie, ep.id)

    /** Stabilní klíč epizody pro frontu i resume (shoda s [PodcastSourcesRepository] `ctv:<idec>`). */
    fun episodeKey(ep: CtvEpisode): String = "ctv:${ep.id}"

    /** User (2026-08-15 16:49) — „Reset poslechu" u rozposlouchané epizody (long-press menu). */
    // BUG (2026-09-04): smaž i video pozici (jiný store, jinak zůstane epizoda vypadat rozkoukaná).
    fun resetPosition(ep: CtvEpisode) {
        val key = episodeKey(ep)
        resumeStore.clear(key)
        videoResumeStore.clear(key)
    }

    /** User (2026-08-16, „chci volbu, která označí jako poslechnuto") — ruční „Označit jako poslechnuté". */
    fun markFinished(ep: CtvEpisode) {
        val key = episodeKey(ep)
        resumeStore.markFinished(key)
        videoResumeStore.clear(key)
    }

    /** Mapování ČT dílu na položku fronty: audio přes audio-only DASH manifest (poslech). */
    private fun toQueued(ep: CtvEpisode): QueuedEpisode {
        val show = _state.value.showTitle
        return QueuedEpisode(
            itemId = loadedFor ?: "ctv",
            episodeId = episodeKey(ep),
            title = ep.title,
            coverUrl = ep.image,
            description = ep.description,
            podcastTitle = show,
            direct = DirectAudio(
                url = uploaderDs.ctvAudioUrl(baseUrl, cookie, ep.id),
                durationSec = ep.duration ?: 0.0,
                author = show,
            ),
        )
    }

    /** Spustí AUDIO režim v poslechovém přehrávači (mini-player, pozadí, zámek) + do fronty. */
    fun playAudio(ep: CtvEpisode) = connection.playDirectEpisode(toQueued(ep))

    /** „Pokračovat" u právě načtené (pozastavené) epizody → jen navázat (bez reloadu). */
    fun resumeCurrent() = connection.play()

    /** Přidá ČT díl do fronty (atFront = hned po aktuální, jinak na konec). */
    fun enqueue(ep: CtvEpisode, atFront: Boolean) = connection.enqueue(toQueued(ep), atFront)

    /**
     * Pošle VIDEO verzi dílu na běžící yellyfin session na TV/boxu (FERRY cast), stejně jako YouTube/film.
     * Manifest je samonosný (`?key=`) a segmenty tečou přes náš server (CDN IP-lock obejit) → box přehraje.
     */
    fun castVideoToTv(ep: CtvEpisode) {
        viewModelScope.launch {
            val jfUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val jfToken = prefs.getString("jellyfin_token", "") ?: ""
            val reportUrl = if (baseUrl.isNotBlank() && cookie.isNotBlank()) {
                "${baseUrl.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(cookie, "UTF-8")}"
            } else null
            val result = naTv.castFerry(jfUrl, jfToken, videoUrl(ep), ep.title, emptyList(), reportUrl, preferredDeviceId = CastTargetPrefs.defaultDeviceId(prefs))
            Timber.i("[KAVKA] cast ČT video → TV: %s result=%s", ep.title, result)
            _castMessage.value = when (result) {
                CastResult.SENT -> "Spuštěno na TV: ${ep.title}"
                CastResult.NO_SESSION -> "Na TV nikdo nehraje — otevři Showlyfin/Jellyfin na televizi a zkus znovu."
                CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení → Připojení a účty)."
                CastResult.FAILED -> "Nepodařilo se spustit na TV."
            }
        }
    }
}
