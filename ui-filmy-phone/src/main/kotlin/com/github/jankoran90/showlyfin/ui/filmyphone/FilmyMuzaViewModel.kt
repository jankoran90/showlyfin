package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.PREF_ACTIVE_PROFILE_IS_CHILD
import com.github.jankoran90.showlyfin.core.db.repository.WantToSeeRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportItem
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportRequest
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.data.uploader.MuzaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * MUZA (SHW-123, user 2026-08-28 20:38) — „řeknu o jaky tema mi jde a ty projedes pruzkumem
 * vcetne divackych ohlasu a vystrelis mi karty filmu... kazde jine tema by melo vlastni historii
 * doporuceni s moznosti navazani."
 *
 * Hledání běží na serveru na pozadí (přes minutu — brainstorm + ověření + kurátor na několik
 * titulů) → appka POSTne dotaz, dostane `topicId` a POLLuje detail, dokud `status != "running"`.
 */
@HiltViewModel
class FilmyMuzaViewModel @Inject constructor(
    private val repo: MuzaRepository,
    private val wantToSee: WantToSeeRepository,
    private val authorizedTrakt: AuthorizedTraktRemoteDataSource,
    private val tokenProvider: TokenProvider,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val searching: Boolean = false,
        val history: List<MuzaRepository.TopicSummary> = emptyList(),
        val historyLoading: Boolean = true,
        val activeTopic: MuzaRepository.TopicDetail? = null,
        val error: String? = null,
        /** user 2026-08-28 21:24 („pokracuj s tlacitkem") — rychlé „+ Chci vidět" přímo z karty MUZA,
         * bez nutnosti otevřít detail. Klíč = "tmdbId_isShow", stejně jako klíč karty ve výsledcích. */
        val addedKeys: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init { loadHistory() }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun loadHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(historyLoading = true)
            val list = repo.topics()
            _state.value = _state.value.copy(history = list.orEmpty(), historyLoading = false)
        }
    }

    /** Nové téma z textového pole. */
    fun search() {
        val q = _state.value.query.trim()
        if (q.isBlank() || _state.value.searching) return
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, error = null)
            val topicId = repo.search(q)
            if (topicId == null) {
                _state.value = _state.value.copy(searching = false, error = "Nepodařilo se spustit hledání — zkus to znovu.")
                return@launch
            }
            _state.value = _state.value.copy(
                activeTopic = MuzaRepository.TopicDetail(topicId, q, "running", emptyList(), 0),
            )
            pollTopic(topicId)
        }
    }

    /** Otevři téma z historie (bez nového kola — jen zobraz, co už tam je / dopolluj běžící). */
    fun openTopic(summary: MuzaRepository.TopicSummary) {
        viewModelScope.launch {
            val detail = repo.topicDetail(summary.id) ?: return@launch
            _state.value = _state.value.copy(activeTopic = detail, searching = detail.status == "running")
            if (detail.status == "running") pollTopic(summary.id)
        }
    }

    /** Další kolo na STEJNÉ téma — nové návrhy se PŘIDAJÍ k dosavadním výsledkům. */
    fun continueActiveTopic() {
        val topic = _state.value.activeTopic ?: return
        if (_state.value.searching) return
        viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, error = null)
            val ok = repo.continueTopic(topic.id)
            if (!ok) {
                _state.value = _state.value.copy(searching = false, error = "Navázání se nepodařilo spustit.")
                return@launch
            }
            pollTopic(topic.id)
        }
    }

    fun closeActiveTopic() {
        pollJob?.cancel()
        _state.value = _state.value.copy(activeTopic = null, searching = false, query = "")
        loadHistory()
    }

    /**
     * @return MediaItem pro otevření karty detailu (odkud se dá „+ Chci vidět" / uložit zdroj apod.).
     * user 2026-08-28 21:28 („dej ten kuratorsky text i jako sdileci kartu... kdyz dam sdilet z
     * karty filmu") — MUZA text se předá přes [MuzaBlurbHandoff], ať ho detail použije jako
     * „Co na to diváci" i pro sdílení, místo aby si dopekl vlastní obecný SUMÁŘ text.
     */
    fun toMediaItem(r: MuzaRepository.TopicResult): MediaItem {
        r.blurb?.let { com.github.jankoran90.showlyfin.core.domain.MuzaBlurbHandoff.stash(r.tmdbId, r.isShow, it) }
        return MediaItem(
            traktId = 0L,
            tmdbId = r.tmdbId,
            imdbId = r.imdbId.takeIf { it.isNotBlank() },
            title = r.title,
            year = r.year.takeIf { it > 0 },
            overview = null,
            rating = null,
            genres = null,
            type = if (r.isShow) MediaType.SHOW else MediaType.MOVIE,
        )
    }

    /**
     * Rychlé „+ Chci vidět" přímo z karty výsledků, bez otevření detailu (user: „pokracuj s
     * tlacitkem"). Vždy zapíše do MÍSTNÍHO seznamu (stejný sdílený `WantToSeeRepository` jako
     * detail karty — SUBSTRATE, dědí per-profil sync+tombstone, viz dnešní oprava split-brainu).
     * Dospělý profil s Traktem navíc dostane best-effort přímý zápis do Trakt watchlistu (fire-
     * and-forget) — chybí-li token nebo volání selže, lokální „Chci vidět" v appce platí i tak
     * (přesně jako `keptLocally()` fallback v DetailViewModel), jen se to Traktu nedozví hned.
     */
    fun quickAddToWantToSee(r: MuzaRepository.TopicResult) {
        val key = "${r.tmdbId}_${r.isShow}"
        if (key in _state.value.addedKeys) return
        _state.value = _state.value.copy(addedKeys = _state.value.addedKeys + key)
        wantToSee.add(r.tmdbId, r.isShow, r.title, r.posterUrl, r.year.takeIf { it > 0 })
        val isChild = prefs.getBoolean(PREF_ACTIVE_PROFILE_IS_CHILD, false)
        if (isChild || tokenProvider.getToken() == null) return
        viewModelScope.launch {
            runCatching {
                val item = SyncExportItem.fromIds(0L, r.tmdbId, r.imdbId.takeIf { it.isNotBlank() })
                    ?: return@launch
                if (r.isShow) authorizedTrakt.postSyncWatchlist(SyncExportRequest(shows = listOf(item)))
                else authorizedTrakt.postSyncWatchlist(SyncExportRequest(movies = listOf(item)))
            }
        }
    }

    private fun pollTopic(topicId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // Celý běh je desítky sekund až pár minut (víc titulů = víc kurátorských volání na mozek) —
            // pollovat po 4 s je dost časté na pocit živosti, dost řídké na to appku/server nezatížit.
            while (isActive) {
                delay(4_000)
                val detail = repo.topicDetail(topicId) ?: continue
                _state.value = _state.value.copy(activeTopic = detail, searching = detail.status == "running")
                if (detail.status != "running") break
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
