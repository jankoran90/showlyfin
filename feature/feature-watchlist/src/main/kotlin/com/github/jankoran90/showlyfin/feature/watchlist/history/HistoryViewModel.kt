package com.github.jankoran90.showlyfin.feature.watchlist.history

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.matchesQuery
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.feature.watchlist.mapper.toMovieMediaItem
import com.github.jankoran90.showlyfin.feature.watchlist.mapper.toShowMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/** Plan STRATA B5 — pohledy podsekce Historie (vzor yeshowly): naposledy zhlédnuté vs. celý seznam. */
enum class HistoryView(val label: String) { RECENT("Naposledy"), ALL("Vše") }

data class HistoryUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val view: HistoryView = HistoryView.RECENT,
    val items: List<MediaItem> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    val imdbToJellyfin: Map<String, String> = emptyMap(),
    val tmdbToJellyfin: Map<Long, String> = emptyMap(),
    val ownedImdbIds: Set<String> = emptySet(),
)

/**
 * Plan STRATA B5 — Historie zhlédnutého z Traktu (`sync/watched`). Filmy + seriály sloučené;
 * pohled „Naposledy" řadí dle [com.github.jankoran90.showlyfin.data.trakt.model.SyncItem.lastWatchedMillis],
 * „Vše" abecedně. Respektuje věkový zámek + žánrové filtry aktivního profilu (WARDEN/VAULT). Klik vede
 * na Jellyfin kartu, pokud položku vlastníme, jinak na Trakt/TMDB detail (parita s Chci vidět/Objevit).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val tmdbApi: TmdbRemoteDataSource,
    private val tokenProvider: TokenProvider,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val jellyfinLibraryService: JellyfinLibraryService,
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Položka + čas posledního zhlédnutí (epoch ms) pro řazení pohledu „Naposledy". */
    private var raw: List<Pair<MediaItem, Long>> = emptyList()
    private var lockedRating: AgeRating? = null

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        val loggedIn = tokenProvider.getToken() != null
        _uiState.update { it.copy(isLoggedIn = loggedIn) }
        if (loggedIn) load()
        loadJellyfinOwned()
        parentalControlsRepository.profile
            .onEach { profile ->
                lockedRating = if (profile.isLocked) profile.effectiveAgeRating else null
                reapply()
            }
            .launchIn(viewModelScope)
        profileRepository.activeConfig
            .onEach { reapply() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        if (_uiState.value.isLoggedIn) load()
    }

    fun selectView(view: HistoryView) {
        if (view == _uiState.value.view) return
        _uiState.update { it.copy(view = view) }
        reapply()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        reapply()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (movies, shows) = coroutineScope {
                    val m = async { authorizedTraktApi.fetchSyncWatchedMovies() }
                    val s = async { authorizedTraktApi.fetchSyncWatchedShows() }
                    m.await() to s.await()
                }
                val base = (
                    movies.map { it.toMovieMediaItem() to it.lastWatchedMillis() } +
                        shows.map { it.toShowMediaItem() to it.lastWatchedMillis() }
                    ).filter { it.first.traktId != 0L }

                val enriched = coroutineScope {
                    base.map { (item, ts) ->
                        async {
                            val tmdbId = item.tmdbId ?: return@async item to ts
                            if (item.type == MediaType.MOVIE) {
                                val details = async { runCatching { tmdbApi.fetchMovieDetails(tmdbId) }.getOrNull() }
                                val translation = async { runCatching { tmdbApi.fetchMovieTranslation(tmdbId, "cs") }.getOrNull() }
                                val d = details.await()
                                val t = translation.await()
                                item.copy(
                                    posterPath = d?.poster_path,
                                    backdropPath = d?.backdrop_path,
                                    titleCz = t?.title?.takeIf { s -> s.isNotBlank() },
                                    overviewCz = t?.overview?.takeIf { s -> s.isNotBlank() },
                                ) to ts
                            } else {
                                val details = async { runCatching { tmdbApi.fetchShowDetails(tmdbId) }.getOrNull() }
                                val translation = async { runCatching { tmdbApi.fetchShowTranslation(tmdbId, "cs") }.getOrNull() }
                                val d = details.await()
                                val t = translation.await()
                                item.copy(
                                    posterPath = d?.poster_path,
                                    backdropPath = d?.backdrop_path,
                                    titleCz = t?.name?.takeIf { s -> s.isNotBlank() },
                                    overviewCz = t?.overview?.takeIf { s -> s.isNotBlank() },
                                ) to ts
                            }
                        }
                    }.awaitAll()
                }
                raw = enriched
                reapply()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Throwable) {
                Timber.w(e, "[History] načtení historie selhalo")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba načítání") }
            }
        }
    }

    private fun loadJellyfinOwned() {
        viewModelScope.launch {
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""
            if (userId.isBlank()) return@launch
            runCatching {
                val owned = jellyfinLibraryService.getOwnedIds(UUID.fromString(userId))
                _uiState.update {
                    it.copy(
                        imdbToJellyfin = owned.imdbToJellyfin,
                        tmdbToJellyfin = owned.tmdbToJellyfin,
                        ownedImdbIds = owned.imdbIds,
                    )
                }
            }.onFailure { Timber.w(it, "[History] OwnedIds failed") }
        }
    }

    private fun reapply() {
        val state = _uiState.value
        var list = applyLock(raw)
        val cfg = profileRepository.activeConfig.value
        if (cfg.allowedGenres.isNotEmpty() || cfg.blockedGenres.isNotEmpty()) {
            list = list.filter { cfg.isGenreAllowed(it.first.genres) }
        }
        if (state.searchQuery.isNotBlank()) {
            list = list.filter { it.first.matchesQuery(state.searchQuery) }
        }
        val sorted = when (state.view) {
            HistoryView.RECENT -> list.sortedByDescending { it.second }
            HistoryView.ALL -> list.sortedBy { it.first.title.lowercase() }
        }
        _uiState.update { it.copy(items = sorted.map { p -> p.first }) }
    }

    private fun applyLock(items: List<Pair<MediaItem, Long>>): List<Pair<MediaItem, Long>> {
        val rating = lockedRating ?: return items
        return items.filter { (item, _) ->
            val genres = item.genres.orEmpty().map { it.lowercase() }
            when (rating) {
                AgeRating.UNRESTRICTED -> true
                AgeRating.CHILDREN -> genres.any { it in CHILDREN_ALLOWED } && genres.none { it in ADULT }
                AgeRating.FAMILY -> genres.none { it in FAMILY_BLOCKED } && genres.none { it in ADULT }
                AgeRating.TEEN -> genres.none { it in ADULT }
                AgeRating.ADULT -> true
            }
        }
    }

    companion object {
        private val CHILDREN_ALLOWED = setOf("family", "animation", "rodinné", "rodinný", "animovaný", "animovaný film", "kids", "children", "dětský")
        private val FAMILY_BLOCKED = setOf("horror", "horor", "thriller", "war", "válečný", "erotic", "erotika")
        private val ADULT = setOf("horror", "horor", "erotic", "erotika", "adult")
    }
}
