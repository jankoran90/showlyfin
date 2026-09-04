package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.normalizeForSearch
import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import com.github.jankoran90.showlyfin.feature.listen.player.enqueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * TRAWL (Slovo, 2026-09-02, user „fulltext vč. celé historie, napříč zdroji"): fulltext hledání
 * epizod přes VŠECHNY sledované zdroje (YouTube+RSS+NaVýbornou) najednou, hledá i v popisu ("hosté"
 * = text v popisu), diakritika/case-insensitive ([com.github.jankoran90.showlyfin.core.domain.SearchUtil]).
 * Datová vrstva [PodcastSourcesRepository.searchEpisodes] dělá práci (YouTube = živé server-side
 * hledání v CELÉ historii kanálu, RSS/NaVýbornou = velký lokální fetch + klientský filtr) — tahle
 * VM jen debounce dotazu + řazení výsledků.
 */
@HiltViewModel
class PodcastSearchViewModel @Inject constructor(
    private val repo: PodcastSourcesRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val connection: AudiobookPlayerConnection,
    private val offline: OfflineDownloadManager,
    // BUG (2026-09-04, user „nevidím pokračovat"): hledání nemělo ŽÁDNÝ resume stav — ani progress
    // bar, ani „Pokračovat" — parita se zdrojovými obrazovkami (YoutubeChannelScreen aj.).
    private val resumeStore: DirectResumeStore,
    private val videoResumeStore: VideoResumeStore,
    // EPHEMERON (2026-09-04): trvalé připojení scoped-hledáním nalezené epizody ke kartě zdroje.
    private val attachedStore: com.github.jankoran90.showlyfin.feature.listen.player.AttachedEpisodeStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    enum class SortMode(val label: String) {
        RELEVANCE("Relevance"), DATE("Datum"), NAME("Název"), VIEWS("Zhlédnutí"),
    }

    data class UiState(
        val query: String = "",
        val loading: Boolean = false,
        val searched: Boolean = false,
        val results: List<SourceEpisode> = emptyList(),
        val sort: SortMode = SortMode.RELEVANCE,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /** Stav přehrávače → zvýraznění právě hrané epizody v řádku. */
    val playerState = connection.state

    /** Stav offline stahování epizod (badge / akce Stáhnout). Klíč = `resumeKey`. */
    val offlineStates = offline.states

    /** BUG (2026-09-04): progres poslechu ve výsledcích hledání — dřív úplně chyběl. */
    val resumeMarks = resumeStore.marks
    /** BUG (2026-09-04): progres rozkoukání videa — video má přednost (sdílený klíč), nemá isFinished. */
    val videoResumeMarks = videoResumeStore.marks

    private var searchJob: Job? = null
    private var lastResults: List<SourceEpisode> = emptyList()

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    // FOCUS (2026-09-03, user „hlavně bych měl hledat na jedním zdrojem, ne plošně"): non-null =
    // hledání scoped jen na tenhle jeden zdroj (lupa v YoutubeChannel/Rss/CtvProgram obrazovce),
    // null = původní chování (napříč VŠEMI sledovanými zdroji, FAB na Timeline/Sledované).
    private var scopeSource: PodcastSource? = null

    /** Nastav scope PŘED prvním hledáním (voláno z obrazovky přes LaunchedEffect). */
    fun setScope(source: PodcastSource?) { scopeSource = source }

    /** Volá se při KAŽDÉM stisku v poli — debounce ať se nehledá po každém písmenu. */
    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        if (text.isBlank()) {
            lastResults = emptyList()
            _state.update { it.copy(results = emptyList(), loading = false, searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            runSearch(text)
        }
    }

    /** Znovu spustí PRÁVĚ zadaný dotaz (např. po chybě / obnovení připojení). */
    fun retry() {
        val q = _state.value.query
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(q) }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(loading = true) }
        val results = runCatching {
            scopeSource?.let { repo.searchInSource(it, query) } ?: repo.searchEpisodes(query)
        }
            .onFailure { Timber.w(it, "[TRAWL] hledání selhalo") }
            .getOrDefault(emptyList())
        lastResults = results
        _state.update { it.copy(loading = false, searched = true, results = sorted(results, it.sort, query)) }
    }

    fun setSort(mode: SortMode) {
        _state.update { it.copy(sort = mode, results = sorted(lastResults, mode, it.query)) }
    }

    private fun sorted(list: List<SourceEpisode>, mode: SortMode, query: String): List<SourceEpisode> = when (mode) {
        SortMode.DATE -> list.sortedByDescending { parseEpisodeDateMs(it.date) ?: Long.MIN_VALUE }
        SortMode.NAME -> list.sortedBy { it.title.normalizeForSearch() }
        SortMode.VIEWS -> list.sortedByDescending { it.viewCount ?: -1L }
        SortMode.RELEVANCE -> list.sortedByDescending { relevanceScore(it, query) }
    }

    /** Titulek > začátek titulku > titulek obsahuje > jen v popisu (YouTube popis matchuje přes
     *  vlastní server-side hledání, appka to nedokáže odlišit od 0, proto malý bonus i za samotnou
     *  přítomnost ve výsledcích YouTube — řadí se za textové shody, ne před ně). */
    private fun relevanceScore(ep: SourceEpisode, query: String): Int {
        val q = query.normalizeForSearch()
        val title = ep.title.normalizeForSearch()
        return when {
            q.isBlank() -> 0
            title == q -> 100
            title.startsWith(q) -> 80
            title.contains(q) -> 60
            ep.description?.normalizeForSearch()?.contains(q) == true -> 30
            else -> 10 // YouTube server-side shoda mimo název/popis (např. přepis/metadata, co appka nevidí)
        }
    }

    private fun toQueued(ep: SourceEpisode): QueuedEpisode {
        val key = ep.resumeKey ?: ep.id
        val localUrl = offline.localVideo(key)?.let { android.net.Uri.fromFile(it).toString() }
        return QueuedEpisode(
            itemId = ep.sourceKey?.substringAfter(':') ?: ep.subtitle ?: "search",
            episodeId = key,
            title = ep.title,
            coverUrl = ep.imageUrl,
            description = ep.description,
            podcastTitle = ep.subtitle,
            direct = DirectAudio(url = localUrl ?: ep.streamUrl, durationSec = ep.durationSec, author = ep.subtitle),
        )
    }

    /** EPHEMERON (2026-09-04): scoped hledání (karta konkrétního zdroje) → epizoda se poznala v
     *  okamžiku hledání, žádné dohledávání není třeba, stačí si to zapamatovat. Cross-source hledání
     *  ([scopeSource] == null) nemá jednoznačný zdroj, netýká se ho to. */
    private fun attachIfScoped(ep: SourceEpisode) {
        val src = scopeSource ?: return
        attachedStore.attach("${src.type}:${src.ref}", ep.copy(sourceKey = "${src.type}:${src.ref}"))
    }

    /** Přehraj výsledek přes poslechový přehrávač (sdílený resume klíč s per-zdroj obrazovkami). */
    fun play(ep: SourceEpisode) {
        attachIfScoped(ep)
        connection.playDirectEpisode(toQueued(ep))
    }

    /** BUG (2026-09-04, user screenshot „Nezobrazí se video pri hledání"): hledání nabízelo jen
     *  audio „Poslech", video volba chyběla úplně — parita se zdrojovou obrazovkou (YoutubeChannel/
     *  CtvProgram mají tlačítko „Video" u každé epizody). RSS nemá video URL v [SourceEpisode]
     *  (jen výjimečně přes jfItemId, ten hledání nenese) → null, tlačítko se v UI nezobrazí. */
    fun videoUrl(ep: SourceEpisode): String? = when {
        ep.resumeKey?.startsWith("yt:") == true -> repo.youtubeVideoUrl(ep.id, PodcastVideoQuality.stream(prefs))
        ep.resumeKey?.startsWith("ctv:") == true -> uploaderDs.ctvVideoUrl(baseUrl, cookie, ep.id)
        else -> null
    }

    /** EPHEMERON — Screen volá při tapu na tlačítko „Video" (videoUrl() samo se čte i při každém
     *  vykreslení řádku, přípojení nesmí viset na tom, jen na SKUTEČNÉM stisku). */
    fun onVideoTap(ep: SourceEpisode) = attachIfScoped(ep)

    /** Přidej výsledek do fronty (další/na konec). */
    fun enqueue(ep: SourceEpisode, atFront: Boolean = false) {
        attachIfScoped(ep)
        connection.enqueue(toQueued(ep), atFront = atFront)
    }

    /** Stáhni výsledek do telefonu (offline poslech). */
    fun download(ep: SourceEpisode) {
        if (ep.streamUrl.isBlank()) return
        attachIfScoped(ep)
        offline.enqueue(
            OfflineRequest(
                key = ep.resumeKey ?: ep.id,
                title = ep.title.ifBlank { ep.subtitle.orEmpty() },
                subtitle = ep.subtitle,
                type = OfflineRequest.TYPE_PODCAST,
                sourceLabel = if (ep.resumeKey?.startsWith("yt:") == true) "YouTube" else "Podcast",
                videoUrl = ep.streamUrl,
                posterUrl = ep.imageUrl,
                durationSec = ep.durationSec,
                description = ep.description,
                publishedAt = parseEpisodeDateMs(ep.date),
                sourceKey = ep.sourceKey,
            ),
        )
    }

    fun deleteOffline(ep: SourceEpisode) = offline.delete(ep.resumeKey ?: ep.id)
}
