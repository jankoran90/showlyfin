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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.data.uploader.ViewModeStore
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
    /** VISTA V1 — je k dispozici další blok pod aktuálním oknem (tlačítko „načíst dalších 20"). */
    val hasMore: Boolean = false,
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
    private val viewModeStore: ViewModeStore,
) : ViewModel() {

    // VANTAGE (SHW-48): per-sekce volba zobrazení (mřížka/seznam) — Historie výchozí mřížka.
    val viewMode: StateFlow<ViewMode> = viewModeStore.modes
        .map { m -> if (m[ViewModeStore.SECTION_HISTORY] == ViewModeStore.LIST) ViewMode.LIST else ViewMode.GRID }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.GRID)

    fun toggleViewMode() {
        val next = if (viewMode.value == ViewMode.GRID) ViewModeStore.LIST else ViewModeStore.GRID
        viewModeStore.set(ViewModeStore.SECTION_HISTORY, next)
    }

    /**
     * VISTA V1 — surová (NEobohacená) historie: položka + čas posledního zhlédnutí (epoch ms).
     * Titul/rok jsou z Traktu hned; TMDB enrichment (poster/CZ překlad) probíhá líně jen pro
     * viditelné okno (níž). Dřív se obohacovala CELÁ historie (1146+ × 2 TMDB volání) před zobrazením.
     */
    private var raw: List<Pair<MediaItem, Long>> = emptyList()
    /** Cache obohacených položek podle traktId (base garantuje traktId != 0). */
    private val enrichedCache = mutableMapOf<Long, MediaItem>()
    /** Kolik položek aktuálního filtrovaného+seřazeného seznamu je viditelných (okno). */
    private var visibleCount: Int = PAGE
    private var enrichJob: Job? = null
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
        resetWindow()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        resetWindow()
    }

    /** VISTA V1 — rozbalit další blok historie (PAGE položek) + obohatit jeho okno. */
    fun loadMore() {
        val total = filteredSorted().size
        if (visibleCount >= total) return
        visibleCount = (visibleCount + PAGE).coerceAtMost(total)
        reapply()
        enrichVisible()
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
                // VISTA V1: jen surová data (titul/rok z Traktu) — žádné TMDB obohacení tady.
                raw = (
                    movies.map { it.toMovieMediaItem() to it.lastWatchedMillis() } +
                        shows.map { it.toShowMediaItem() to it.lastWatchedMillis() }
                    ).filter { it.first.traktId != 0L }
                enrichedCache.clear()
                _uiState.update { it.copy(isLoading = false) }
                resetWindow()
            } catch (e: Throwable) {
                Timber.w(e, "[History] načtení historie selhalo")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba načítání") }
            }
        }
    }

    /**
     * VISTA V1 — nastaví počáteční okno podle pohledu: RECENT = vše z posledních [RECENT_DAYS] dní
     * (min. [PAGE]), ALL = první [PAGE]. Pak hned vykreslí (titulky) a líně obohatí okno.
     */
    private fun resetWindow() {
        val sorted = filteredSorted()
        val initial = when (_uiState.value.view) {
            HistoryView.RECENT -> {
                val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
                maxOf(sorted.count { it.second >= cutoff }, PAGE)
            }
            HistoryView.ALL -> PAGE
        }
        visibleCount = initial.coerceAtMost(sorted.size)
        reapply()
        enrichVisible()
    }

    /** Líně obohatí (poster/CZ překlad z TMDB) jen položky aktuálního okna, které ještě nejsou v cache. */
    private fun enrichVisible() {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val window = filteredSorted().take(visibleCount).map { it.first }
            val toEnrich = window.filter { it.traktId !in enrichedCache && it.tmdbId != null }
            if (toEnrich.isEmpty()) return@launch
            coroutineScope {
                toEnrich.map { item -> async { enrichedCache[item.traktId] = enrich(item) } }.awaitAll()
            }
            reapply()
        }
    }

    private suspend fun enrich(item: MediaItem): MediaItem = coroutineScope {
        val tmdbId = item.tmdbId ?: return@coroutineScope item
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
            )
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
            )
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

    /** Filtr (zámek + žánry profilu + hledání) a řazení dle pohledu — nad celým surovým seznamem. */
    private fun filteredSorted(): List<Pair<MediaItem, Long>> {
        val state = _uiState.value
        var list = applyLock(raw)
        val cfg = profileRepository.activeConfig.value
        if (cfg.allowedGenres.isNotEmpty() || cfg.blockedGenres.isNotEmpty()) {
            list = list.filter { cfg.isGenreAllowed(it.first.genres) }
        }
        if (state.searchQuery.isNotBlank()) {
            list = list.filter { it.first.matchesQuery(state.searchQuery) }
        }
        return when (state.view) {
            HistoryView.RECENT -> list.sortedByDescending { it.second }
            HistoryView.ALL -> list.sortedBy { it.first.title.lowercase() }
        }
    }

    /** Vykreslí jen aktuální okno (mapuje na obohacenou variantu z cache, jinak surovou položku). */
    private fun reapply() {
        val sorted = filteredSorted()
        val count = visibleCount.coerceAtMost(sorted.size)
        val window = sorted.take(count).map { (item, _) -> enrichedCache[item.traktId] ?: item }
        _uiState.update { it.copy(items = window, hasMore = sorted.size > count) }
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
        /** VISTA V1 — velikost bloku „načíst dalších N". */
        private const val PAGE = 20
        /** VISTA V1 — okno „Naposledy": vše z posledních 90 dní se ukáže hned. */
        private const val RECENT_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
        private val CHILDREN_ALLOWED = setOf("family", "animation", "rodinné", "rodinný", "animovaný", "animovaný film", "kids", "children", "dětský")
        private val FAMILY_BLOCKED = setOf("horror", "horor", "thriller", "war", "válečný", "erotic", "erotika")
        private val ADULT = setOf("horror", "horor", "erotic", "erotika", "adult")
    }
}
