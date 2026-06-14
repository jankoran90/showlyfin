package com.github.jankoran90.showlyfin.feature.detail

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.csfd.CsfdScraper
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.FerrySubtitle
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import com.github.jankoran90.showlyfin.data.jellyfin.normalizeBoxSetName
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.PersonRole
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbCollection
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson
import com.github.jankoran90.showlyfin.data.tmdb.model.czLabel
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportItem
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportRequest
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.CsfdPlotResponse
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderCaptureRequest
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val tmdbApi: TmdbRemoteDataSource,
    private val csfdScraper: CsfdScraper,
    private val csfdRepository: CsfdRepository,
    private val jellyfinLibraryService: JellyfinLibraryService,
    private val authorizedTrakt: AuthorizedTraktRemoteDataSource,
    private val tokenProvider: TokenProvider,
    private val uploaderDs: UploaderRemoteDataSource,
    private val naTv: NaTvService,
    private val workingSourceStore: com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore,
    private val favoritesStore: com.github.jankoran90.showlyfin.data.uploader.FavoritesStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var rdPollJob: Job? = null

    // VISTA V4 (id-robustnost): rozdělaný `load()`. Při překliku karta→karta ho zrušíme,
    // aby pozdě doběhlé coroutiny předchozího filmu (`item = item.copy(...)`) nepřepsaly
    // stav nově otevřeného → jinak detail „visí na původním filmu".
    private var loadJob: Job? = null

    /** Týž titul? Stub z pásu nese jen `tmdbId` (trakt 0, imdb null), proto OR přes všechna id. */
    private fun MediaItem.isSameAs(other: MediaItem): Boolean {
        if (traktId != 0L && traktId == other.traktId) return true
        if (tmdbId != null && tmdbId == other.tmdbId) return true
        if (!imdbId.isNullOrBlank() && imdbId == other.imdbId) return true
        return false
    }

    // CASCADE Fáze 4: poslední přehrávaný stream z pickeru → po chybě přehrávání víme,
    // odkud v seznamu `streams` pokračovat dalším kandidátem (v UŽIVATELOVĚ pořadí, bez přeřazování).
    private var lastPlayedStream: UploaderStream? = null

    // Plan WINNOW (SHW-41, item 2): RD hashe, které appka zkoušela pro TENTO film v této relaci.
    // Při „zapamatovat zdroj" smažeme z RD účtu jen tyto (kromě zapamatovaného) → bezpečný úklid,
    // nikdy nesáhneme na nesouvisející torrenty. Reset při načtení jiného filmu (`load`).
    private val attemptedRdHashes = LinkedHashSet<String>()

    fun load(item: MediaItem) = load(item, force = false)

    /** VISTA V4: znovunačtení po síťové chybě (obejde dedup guard). */
    fun retry() {
        val item = _uiState.value.item ?: return
        load(item, force = true)
    }

    private fun load(item: MediaItem, force: Boolean) {
        val current = _uiState.value.item
        if (!force && current != null) {
            val sameTrakt = current.traktId != 0L && current.traktId == item.traktId
            val sameTmdb = current.tmdbId != null && item.tmdbId != null && current.tmdbId == item.tmdbId
            if (sameTrakt || sameTmdb) return
        }
        // VISTA V4: zruš rozdělaný load předchozího filmu → jeho pozdě doběhlé coroutiny
        // nepřepíšou stav nově otevřeného (konec race „visí na původním").
        loadJob?.cancel()
        lastPlayedStream = null
        attemptedRdHashes.clear()
        _uiState.update {
            it.copy(
                item = item,
                isLoading = true,
                isCsfdLoading = item.type == MediaType.MOVIE,
                movieDetails = null,
                showDetails = null,
                tmdbCzOverview = null,
                tmdbCzTitle = null,
                csfdId = null,
                csfdRating = null,
                csfdPlot = null,
                csfdReviews = emptyList(),
                csfdGallery = emptyList(),
                isGalleryLoading = false,
                showGallery = false,
                collection = null,
                isOwnedInLibrary = false,
                ownedJellyfinId = null,
                matchingBoxSetId = null,
                jellyfinCollection = null,
                mergedCollection = null,
                isTraktLoggedIn = tokenProvider.getToken() != null,
                isInWatchlist = false,
                isTogglingWatchlist = false,
                cast = emptyList(),
                uploaderConfigured = uploaderBaseUrl.isNotBlank(),
                showStreamPicker = false,
                isLoadingStreams = false,
                streams = emptyList(),
                // SIEVE S3: připomeň zdroj, který pro tenhle film posledně fungoval (pin v pickeru).
                rememberedSource = workingSourceStore.get(item.imdbId, item.tmdbId)?.stream,
                // COMPASS C2: je tento film v Oblíbených?
                isFavorite = item.tmdbId?.let {
                    favoritesStore.isFavorite(com.github.jankoran90.showlyfin.data.uploader.FavoriteKind.MOVIE, it)
                } ?: false,
                pendingWorkingConfirm = null,
                streamError = null,
                isResolvingStream = false,
                showDownloadMenu = false,
                showSdilejPicker = false,
                isLoadingSdilej = false,
                sdilejStreams = emptyList(),
                sdilejError = null,
                captureMessage = null,
                pendingPlaybackUrl = null,
                pendingPlaybackTitle = "",
                requestStremioFallback = false,
                blockedDmcaMessage = null,
                directorName = null,
                directorMovies = null,
                studioName = null,
                studioMovies = null,
                // ENSEMBLE (SHW-45): reset sekce Tvůrci
                directors = emptyList(),
                writers = emptyList(),
                cinematographers = emptyList(),
                showPersonSheet = false,
                personSheetName = null,
                personSheetLoading = false,
                personFilmography = null,
                showCollections = prefs.getBoolean("detail_show_collections", true),
                showDirector = prefs.getBoolean("detail_show_director", true),
                showStudio = prefs.getBoolean("detail_show_studio", true),
                showCreators = prefs.getBoolean("detail_show_creators", true),
                plotCollapsedLines = prefs.getInt("detail_plot_lines", 5),
                actionOrder = parseActionOrder(prefs.getString("detail_action_order", null)),
                error = null,
            )
        }
        loadJob = viewModelScope.launch {
            launch { loadJellyfinOwned(item) }
            launch { loadWatchlistMembership(item) }
            launch { loadCast(item) }
            launch { loadRelated(item) }
            launch {
            try {
                val tmdbId = item.tmdbId
                var resolvedCzTitle: String? = item.titleCz?.takeIf { it.isNotBlank() }
                if (tmdbId != null) {
                    if (item.type == MediaType.MOVIE) {
                        coroutineScope {
                            val detailsDeferred = async { tmdbApi.fetchMovieDetails(tmdbId) }
                            val translationDeferred = async { tmdbApi.fetchMovieTranslation(tmdbId, "cs") }
                            val details = detailsDeferred.await()
                            val translation = translationDeferred.await()
                            val tmdbCzTitle = translation?.title?.takeIf { it.isNotBlank() }
                            if (tmdbCzTitle != null) resolvedCzTitle = tmdbCzTitle
                            // VISTA V4: pojistka proti micro-window — pokud uživatel mezitím
                            // překlikl na jiný film, NEpřepisuj (nepřevracej detail na původní).
                            _uiState.update { st ->
                                if (st.item?.isSameAs(item) != true) st
                                else st.copy(
                                    movieDetails = details,
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = tmdbCzTitle,
                                    // Backfill IMDB z TMDB → Stremio/Sdílej fungují i u filmů z knihovny
                                    // matchnutých jen přes TMDB (např. arthouse bez imdb v Jellyfinu).
                                    item = item.copy(
                                        imdbId = item.imdbId ?: details?.imdb_id?.takeIf { id -> id.isNotBlank() },
                                        posterPath = details?.poster_path,
                                        backdropPath = details?.backdrop_path,
                                    ),
                                    isLoading = false,
                                )
                            }
                            details?.belongs_to_collection?.id?.takeIf { it > 0 }?.let { collectionId ->
                                launch {
                                    val collection = tmdbApi.fetchCollection(collectionId)
                                    _uiState.update { it.copy(collection = collection) }
                                    recomputeMergedCollection(item)
                                }
                            }
                        }
                    } else {
                        coroutineScope {
                            val detailsDeferred = async { tmdbApi.fetchShowDetails(tmdbId) }
                            val translationDeferred = async { tmdbApi.fetchShowTranslation(tmdbId, "cs") }
                            val details = detailsDeferred.await()
                            val translation = translationDeferred.await()
                            val tmdbCzTitle = translation?.name?.takeIf { it.isNotBlank() }
                            if (tmdbCzTitle != null) resolvedCzTitle = tmdbCzTitle
                            _uiState.update { st ->
                                if (st.item?.isSameAs(item) != true) st
                                else st.copy(
                                    showDetails = details,
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = tmdbCzTitle,
                                    item = item.copy(posterPath = details?.poster_path, backdropPath = details?.backdrop_path),
                                    isLoading = false,
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                if (item.type == MediaType.MOVIE) {
                    loadCsfd(item, resolvedCzTitle)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e   // překlik na jiný film → zrušený load, NEhlas jako chybu
            } catch (e: Throwable) {
                // VISTA V4: chybu drž jen pro stále zobrazený film; síťový výpadek → srozumitelná hláška.
                _uiState.update { st ->
                    if (st.item?.isSameAs(item) != true) st
                    else st.copy(isLoading = false, isCsfdLoading = false, error = friendlyLoadError(e))
                }
            }
            }
        }
    }

    /** VISTA V4: síťové výpadky přelož na lidskou hlášku (jinak `UnknownHostException` apod.). */
    private fun friendlyLoadError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException,
        is java.io.IOException -> "Nepodařilo se načíst detail — zkontroluj připojení."
        else -> e.message ?: "Detail se nepodařilo načíst."
    }

    private suspend fun loadCast(item: MediaItem) {
        val tmdbId = item.tmdbId ?: return
        val people = runCatching {
            if (item.type == MediaType.MOVIE) tmdbApi.fetchMoviePeople(tmdbId)
            else tmdbApi.fetchShowPeople(tmdbId)
        }.getOrNull() ?: return
        val cast = people[com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson.Type.CAST].orEmpty().take(20)
        // ENSEMBLE (SHW-45): z crew vytáhni Režii / Scénář / Kameru (místo hudby). job (film) i jobs (TV agg).
        val crew = people[com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson.Type.CREW].orEmpty()
        _uiState.update {
            it.copy(
                cast = cast,
                directors = crewByRole(crew, dept = "Directing", jobs = setOf("director")),
                writers = crewByRole(crew, dept = "Writing", jobs = setOf("writer", "screenplay", "story", "author", "novel")),
                cinematographers = crewByRole(crew, dept = "Camera", jobs = setOf("director of photography", "cinematography", "cinematographer")),
            )
        }
    }

    /** ENSEMBLE: vyber z crew lidi dané role (podle department NEBO konkrétního job/jobs), dedup dle id, max 5. */
    private fun crewByRole(
        crew: List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson>,
        dept: String,
        jobs: Set<String>,
    ): List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson> {
        fun matches(p: com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson): Boolean {
            if (p.department?.equals(dept, ignoreCase = true) == true) return true
            if (p.job != null && jobs.any { it.equals(p.job, ignoreCase = true) }) return true
            return p.jobs?.any { j -> j.job != null && jobs.any { it.equals(j.job, ignoreCase = true) } } == true
        }
        return crew.filter { it.id > 0 && matches(it) }
            .distinctBy { it.id }
            .take(5)
    }

    /**
     * ENSEMBLE (SHW-45): klik na osobu (herec/režie/scénář/kamera) → její tvorba jako VALIDNÍ karty.
     * `discoverMoviesByPerson` → `moviesToCollection` (CollectionPart nese tmdbId → karta se otevře správně).
     */
    fun openPersonFilmography(
        person: TmdbPerson,
        kind: FavoriteKind? = null,
    ) {
        if (person.id <= 0) return
        val fav = kind != null && favoritesStore.isFavorite(kind, person.id)
        val role = personRole(person, kind)
        _uiState.update {
            it.copy(
                showPersonSheet = true, personSheetName = person.name, personSheetLoading = true,
                personFilmography = null, personSheetPerson = person, personSheetKind = kind,
                personSheetRoleLabel = role.czLabel(), isPersonFavorite = fav,
            )
        }
        viewModelScope.launch {
            // VANTAGE (SHW-48): rolově konkrétní tvorba (režisér → režíroval, herec → hrál, skladatel → hudba …)
            // místo generického `with_people` (cast i crew dohromady).
            val movies = runCatching { tmdbApi.moviesByPersonRole(person.id, role) }.getOrDefault(emptyList())
            // CANVAS C: celoobrazovková Tvorba → víc položek (60) než řádkové kolekce (20).
            val coll = moviesToCollection(person.name ?: "Tvorba", movies, _uiState.value.item?.tmdbId ?: -1L, limit = 60)
            _uiState.update { it.copy(personSheetLoading = false, personFilmography = coll) }
        }
    }

    /** Role osoby pro rolově konkrétní tvorbu — z [FavoriteKind] (Oblíbené / herec / režie) nebo
     *  z TMDB `department`/`job` (scénárista / kameraman bez vlastní kategorie Oblíbených). */
    private fun personRole(person: TmdbPerson, kind: FavoriteKind?): PersonRole {
        when (kind) {
            FavoriteKind.ACTOR -> return PersonRole.ACTING
            FavoriteKind.DIRECTOR -> return PersonRole.DIRECTING
            FavoriteKind.WRITER -> return PersonRole.WRITING
            FavoriteKind.PRODUCER -> return PersonRole.PRODUCING
            FavoriteKind.COMPOSER -> return PersonRole.COMPOSING
            else -> {}
        }
        val dept = person.department
        val jobs = listOfNotNull(person.job) + person.jobs?.mapNotNull { it.job }.orEmpty()
        fun jobHas(vararg s: String) = jobs.any { j -> s.any { j.contains(it, ignoreCase = true) } }
        return when {
            jobHas("Director of Photography", "Cinematograph") || dept.equals("Camera", true) -> PersonRole.CINEMATOGRAPHY
            jobHas("Director") || dept.equals("Directing", true) -> PersonRole.DIRECTING
            dept.equals("Writing", true) || jobHas("Writer", "Screenplay", "Story") -> PersonRole.WRITING
            dept.equals("Production", true) || jobHas("Producer") -> PersonRole.PRODUCING
            jobHas("Composer", "Music") || dept.equals("Sound", true) -> PersonRole.COMPOSING
            person.character != null -> PersonRole.ACTING
            else -> PersonRole.GENERIC
        }
    }

    fun closePersonSheet() = _uiState.update {
        it.copy(
            showPersonSheet = false, personFilmography = null, personSheetName = null,
            personSheetPerson = null, personSheetKind = null, personSheetRoleLabel = null, isPersonFavorite = false,
        )
    }

    /** COMPASS C2 (SHW-44): přidat/odebrat tento film do/z Oblíbených. */
    fun toggleFavorite() {
        val item = _uiState.value.item ?: return
        val tmdb = item.tmdbId ?: return
        val raw = _uiState.value.movieDetails?.poster_path ?: item.posterPath
        val poster = raw?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w185$it" }
        val now = favoritesStore.toggle(
            com.github.jankoran90.showlyfin.data.uploader.FavoriteItem(
                kind = com.github.jankoran90.showlyfin.data.uploader.FavoriteKind.MOVIE,
                id = tmdb,
                name = _uiState.value.tmdbCzTitle ?: item.title,
                imageUrl = poster,
                year = item.year,
            )
        )
        _uiState.update { it.copy(isFavorite = now) }
    }

    /** COMPASS C2 (SHW-44): přidat/odebrat osobu ze sheetu (herec/režisér) do/z Oblíbených. */
    fun togglePersonFavorite() {
        val person = _uiState.value.personSheetPerson ?: return
        val kind = _uiState.value.personSheetKind ?: return
        val img = person.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" }
        val now = favoritesStore.toggle(
            com.github.jankoran90.showlyfin.data.uploader.FavoriteItem(
                kind = kind, id = person.id, name = person.name ?: "", imageUrl = img,
            )
        )
        _uiState.update { it.copy(isPersonFavorite = now) }
    }

    /** Sekce „Od stejného režiséra" + „Od stejného studia" (TMDB, jen filmy). Univerzální (v knihovně i mimo). */
    private suspend fun loadRelated(item: MediaItem) {
        if (item.type != MediaType.MOVIE) return
        val tmdbId = item.tmdbId ?: return
        coroutineScope {
            val peopleDeferred = async { runCatching { tmdbApi.fetchMoviePeople(tmdbId) }.getOrNull() }
            val detailsDeferred = async { runCatching { tmdbApi.fetchMovieDetails(tmdbId) }.getOrNull() }
            val people = peopleDeferred.await()
            val details = detailsDeferred.await()

            val crew = people?.get(com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson.Type.CREW).orEmpty()
            val director = crew.firstOrNull { p ->
                p.job.equals("Director", ignoreCase = true) || p.jobs?.any { it.job.equals("Director", ignoreCase = true) } == true
            }
            if (director != null && director.id > 0) {
                val movies = tmdbApi.discoverMoviesByPerson(director.id)
                val header = "Od stejného režiséra" + (director.name?.let { ": $it" } ?: "")
                val coll = moviesToCollection(header, movies, tmdbId)
                if (coll != null) _uiState.update { it.copy(directorName = director.name, directorMovies = coll) }
            }

            val company = details?.production_companies?.firstOrNull { it.id > 0 }
            if (company != null) {
                val movies = tmdbApi.discoverMoviesByCompany(company.id)
                val header = "Od stejného studia" + (company.name?.let { ": $it" } ?: "")
                val coll = moviesToCollection(header, movies, tmdbId)
                if (coll != null) _uiState.update { it.copy(studioName = company.name, studioMovies = coll) }
            }
        }
    }

    private fun moviesToCollection(
        name: String,
        movies: List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem>,
        excludeTmdbId: Long,
        limit: Int = 20,
    ): MediaCollection? {
        val parts = movies
            .filter { it.id != excludeTmdbId && !it.poster_path.isNullOrBlank() }
            .take(limit)
            .map { m ->
                CollectionPart(
                    key = "tmdb_${m.id}",
                    tmdbId = m.id,
                    jellyfinId = _uiState.value.ownedTmdbToJellyfin[m.id],
                    title = m.title ?: "",
                    posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                    year = m.release_date?.take(4),
                    watched = _uiState.value.watchedTmdbIds.contains(m.id),
                    // CANVAS D: data pro řazení (hodnocení/oblíbenost) + žánrové štítky na kartě.
                    rating = m.vote_average,
                    popularity = m.popularity,
                    genres = com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres.names(m.genre_ids, isShow = false),
                )
            }
        return if (parts.isEmpty()) null else MediaCollection(name = name, parts = parts)
    }

    private suspend fun loadWatchlistMembership(item: MediaItem) {
        if (tokenProvider.getToken() == null) return
        // WINNOW: tituly z pásu režisér/studio nemají traktId → členství poznáme i podle tmdbId.
        if (item.traktId == 0L && item.tmdbId == null) return
        runCatching {
            val list = if (item.type == MediaType.MOVIE) {
                authorizedTrakt.fetchSyncMoviesWatchlist()
            } else {
                authorizedTrakt.fetchSyncShowsWatchlist()
            }
            list.any {
                (item.traktId != 0L && it.getTraktId() == item.traktId) ||
                    (item.tmdbId != null && it.getTmdbId() == item.tmdbId)
            }
        }.getOrNull()?.let { inWl ->
            _uiState.update { it.copy(isInWatchlist = inWl) }
        }
    }

    fun toggleWatchlist() {
        val item = _uiState.value.item ?: return
        if (tokenProvider.getToken() == null) return
        if (_uiState.value.isTogglingWatchlist) return
        // WINNOW (SHW-41): nesmí padnout na traktId==0 — tituly z pásu „od stejného režiséra/studia"
        // nesou jen tmdbId. Sestavíme položku z čehokoli, co máme (trakt/tmdb/imdb); Trakt to přijme.
        val exportItem = SyncExportItem.fromIds(item.traktId, item.tmdbId, item.imdbId)
        if (exportItem == null) {
            timber.log.Timber.w("[Watchlist] toggle: žádné použitelné id (trakt/tmdb/imdb) pro '${item.title}'")
            return
        }
        val currentlyIn = _uiState.value.isInWatchlist
        _uiState.update { it.copy(isTogglingWatchlist = true) }
        viewModelScope.launch {
            val request = if (item.type == MediaType.MOVIE) {
                SyncExportRequest(movies = listOf(exportItem))
            } else {
                SyncExportRequest(shows = listOf(exportItem))
            }
            val ok = runCatching {
                if (currentlyIn) authorizedTrakt.postDeleteWatchlist(request)
                else authorizedTrakt.postSyncWatchlist(request)
            }.isSuccess
            _uiState.update {
                it.copy(
                    isTogglingWatchlist = false,
                    isInWatchlist = if (ok) !currentlyIn else currentlyIn,
                )
            }
        }
    }

    // ── Stream / Stáhnout (Stremio + Sdílej.cz + Smart Remux hub) ──────────────

    private fun mediaTypeStr(item: MediaItem) = if (item.type == MediaType.MOVIE) "movie" else "series"

    /** ▶ Stream — otevře picker se Stremio streamy (jen přehrávání). */
    fun openStreamPicker() {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId
        if (imdb.isNullOrBlank() || uploaderBaseUrl.isBlank()) {
            timber.log.Timber.w("[Stremio] picker blocked: imdbBlank=${imdb.isNullOrBlank()} baseUrlBlank=${uploaderBaseUrl.isBlank()} tmdb=${item.tmdbId} title='${item.title}'")
            _uiState.update { it.copy(showStreamPicker = true, streamError = "Uploader není nastaven nebo film nemá IMDB ID.") }
            return
        }
        _uiState.update { it.copy(showStreamPicker = true) }
        loadStreams()
    }

    /** Přepínač „Přesné hledání / Vše" v pickeru — znovu načte streamy. */
    fun setStreamStrict(strict: Boolean) {
        if (_uiState.value.streamStrict == strict) return
        _uiState.update { it.copy(streamStrict = strict) }
        loadStreams()
    }

    private fun loadStreams() {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId ?: return
        val strict = _uiState.value.streamStrict
        _uiState.update { it.copy(isLoadingStreams = true, streamError = null, streams = emptyList()) }
        viewModelScope.launch {
            // RD-first režim (DebridSearch) z prefs: off | hash (server-side v /streams) | search | both.
            val rdMode = runCatching { uploaderDs.getStreamFilter(uploaderBaseUrl, uploaderCookie).rdFirstMode }.getOrDefault("both")
            // DebridSearch dle názvu (search/both) — prohledá RD účet i mimo addon výsledky, paralelně.
            val savedDeferred: kotlinx.coroutines.Deferred<List<UploaderStream>>? =
                if (rdMode == "search" || rdMode == "both") {
                    async { runCatching { uploaderDs.rdSearch(uploaderBaseUrl, uploaderCookie, item.title, item.year) }.getOrDefault(emptyList()) }
                } else null
            // Backend vrací už seřazené (rdSaved → cached → CZ/SK → fallbackOrder) a ořezané dle prefs.
            runCatching { uploaderDs.getStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb, strict = strict) }
                .onSuccess { list ->
                    val saved = savedDeferred?.await().orEmpty()
                    val savedHashes = saved.mapNotNull { it.infoHash?.lowercase() }.toSet()
                    val combined = saved + list.filterNot { (it.infoHash?.lowercase() ?: "") in savedHashes }
                    timber.log.Timber.i("[Stremio] streams=${list.size} rdSearch=${saved.size} strict=$strict (cached=${list.count { it.quality.rdReady }} dl=${list.count { it.quality.rdDownloadable }}) imdb=$imdb")
                    // Plan CASCADE Fáze 3: během probu ukaž JEN ověřené instant (rdSaved/rdReady),
                    // zbytek se reálně testuje (addMagnet) → po probu nahradíme jen smysluplnými.
                    val instantNow = combined.filter { it.quality.rdSaved || it.quality.rdReady }
                    _uiState.update { it.copy(isLoadingStreams = false, isProbingStreams = true, streams = instantNow, streamError = null) }
                    viewModelScope.launch {
                        runCatching { uploaderDs.getProbedStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb) }
                            .onSuccess { probed ->
                                timber.log.Timber.i("[Stremio] probe → ${probed.size} smysluplných (instant=${probed.count { it.quality.rdSaved || it.quality.rdReady }} dl=${probed.count { it.quality.rdDownloadable }})")
                                val finalList = if (probed.isNotEmpty()) probed else combined
                                val err = if (finalList.isEmpty()) "Žádný funkční zdroj nenalezen." else null
                                _uiState.update { it.copy(isProbingStreams = false, streams = finalList, streamError = err) }
                            }
                            .onFailure { e ->
                                timber.log.Timber.w(e, "[Stremio] probe FAILED imdb=$imdb → fallback na neprobnuty seznam")
                                val err = if (combined.isEmpty()) "Žádné streamy nenalezeny." else null
                                _uiState.update { it.copy(isProbingStreams = false, streams = combined, streamError = err) }
                            }
                    }
                }
                .onFailure { e ->
                    timber.log.Timber.w(e, "[Stremio] getStreams FAILED imdb=$imdb url=$uploaderBaseUrl")
                    val saved = savedDeferred?.await().orEmpty()
                    if (saved.isNotEmpty()) _uiState.update { it.copy(isLoadingStreams = false, streams = saved, streamError = null) }
                    else _uiState.update { it.copy(isLoadingStreams = false, streamError = e.message ?: "Chyba načtení streamů") }
                }
        }
    }

    fun dismissStreamPicker() = _uiState.update { it.copy(showStreamPicker = false) }

    // Plan SIEVE (SHW-38) S2 — paměť fungujícího zdroje.
    private fun sameSource(a: UploaderStream?, b: UploaderStream?): Boolean {
        if (a == null || b == null) return false
        val ka = a.cometPath ?: a.infoHash ?: a.url
        val kb = b.cometPath ?: b.infoHash ?: b.url
        return ka != null && ka == kb
    }

    /** Uživatel potvrdil „tohle sedí 👍" → ulož zdroj jako fungující pro tento film + připni ho. */
    fun confirmWorkingSource() {
        val st = _uiState.value
        val stream = st.pendingWorkingConfirm ?: return
        val imdb = st.item?.imdbId
        val title = st.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: st.item?.title.orEmpty()
        workingSourceStore.save(imdb, st.item?.tmdbId, title, stream)
        _uiState.update { it.copy(rememberedSource = stream, pendingWorkingConfirm = null) }
        cleanupRdKeepingSource(stream)
    }

    /**
     * Plan WINNOW (item 2, BEZPEČNĚ): trigger = uživatel potvrdil „zapamatovat torrent".
     * Smaž z RD účtu VŠECHNY ostatní verze TOHOTO filmu — kandidáti = co appka zkoušela
     * (`attemptedRdHashes`) ∪ všechny zdroje, co Comet pro film nabídl (`uiState.streams`) —
     * KROMĚ právě zapamatovaného. Zapamatovaný chráníme TROJITĚ: (1) ze seznamu verzí ho
     * vyřadíme přes `sameSource` (cometPath/infoHash/url) ještě před výpočtem hashů,
     * (2) odfiltrujeme `keepHash`, (3) backend `keep` znovu vyloučí. Mažeme jen podle hashů
     * tohoto filmu → nikdy nesáhne na nesouvisející torrenty. Best-effort, tiché.
     */
    private fun cleanupRdKeepingSource(keep: UploaderStream) {
        val keepHash = streamRdHash(keep)
        val filmHashes = _uiState.value.streams
            .filterNot { sameSource(it, keep) }   // vazba se zapamatovaným: ten ze seznamu vyřaď
            .mapNotNull { streamRdHash(it) }
        val others = (attemptedRdHashes + filmHashes)
            .filter { it != keepHash }
            .distinct()
        if (others.isEmpty() || uploaderBaseUrl.isBlank()) return
        viewModelScope.launch {
            runCatching { uploaderDs.rdCleanup(uploaderBaseUrl, uploaderCookie, keepHash, others) }
                .onSuccess { n -> timber.log.Timber.i("[WINNOW] RD úklid: smazáno %d torrentů (keep=%s, kandidátů=%d)", n, keepHash, others.size) }
                .onFailure { e -> timber.log.Timber.w(e, "[WINNOW] RD úklid selhal") }
        }
    }

    /** Skryj nabídku „tohle sedí?" (uživatel ji odmítl nebo to byl špatný zdroj). */
    fun dismissWorkingConfirm() = _uiState.update { it.copy(pendingWorkingConfirm = null) }

    /** Zapomenout připnutý fungující zdroj (zdroj přestal fungovat / chce vybrat jiný). */
    fun forgetWorkingSource() {
        workingSourceStore.clear(_uiState.value.item?.imdbId, _uiState.value.item?.tmdbId)
        _uiState.update { it.copy(rememberedSource = null) }
    }

    /**
     * Plan LEDGER (SHW-43): u zapamatovaného filmu „odstranit" = zruš pin A smaž jeho torrent
     * z RD účtu (na rozdíl od [forgetWorkingSource], které jen zapomene pin). Best-effort, tiché.
     */
    fun removeRememberedSource() {
        val remembered = _uiState.value.rememberedSource
        forgetWorkingSource()
        val hash = remembered?.let { streamRdHash(it) }
        if (hash != null && uploaderBaseUrl.isNotBlank()) {
            viewModelScope.launch {
                runCatching { uploaderDs.rdDelete(uploaderBaseUrl, uploaderCookie, listOf(hash)) }
                    .onSuccess { n -> timber.log.Timber.i("[LEDGER] zapamatovaný zdroj odstraněn z RD: %d (hash=%s)", n, hash) }
                    .onFailure { e -> timber.log.Timber.w(e, "[LEDGER] RD delete zapamatovaného selhal") }
            }
        }
    }

    /** Plan FERRY (SHW-37): zvolený stream pošli na TV (yellyfin) místo lokálního přehrání. */
    fun castStreamToTv(stream: UploaderStream) = playStream(stream, CastTarget.TV)

    /** Klik na konkrétní stream → přímé url / RD resolve → předá URL [target] (telefon / TV). */
    fun playStream(stream: UploaderStream, target: CastTarget = CastTarget.LOCAL) {
        if (_uiState.value.isResolvingStream || _uiState.value.rdDownload != null) return
        lastPlayedStream = stream   // CASCADE Fáze 4: zapamatuj pro případný auto-advance po chybě přehrávání
        val title = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() }
            ?: _uiState.value.item?.title.orEmpty()
        // CZ titulky query (Fáze E): orig+cz název, rok, runtime, release+fps zvoleného streamu.
        // BATON regrese: query stavíme VŽDY (dřív gate `if imdb != null` → při castu z doporučení je
        // imdbId ještě prázdné, dohledá se z TMDB později, stejný root cause jako SIEVE → query null →
        // `subs:[]` na TV). Backend hledá i bez imdb (podle title/origTitle/year); prázdné imdb řeší
        // API klient placeholderem. Postavíme když máme aspoň název.
        val st = _uiState.value
        val subTitle = st.tmdbCzTitle?.takeIf { t -> t.isNotBlank() } ?: st.item?.title.orEmpty()
        val subOrig = st.item?.title.orEmpty()
        if (subTitle.isNotBlank() || subOrig.isNotBlank()) {
            _uiState.update {
                it.copy(pendingSubtitleQuery = com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery(
                    imdb = st.item?.imdbId.orEmpty(),
                    title = subTitle,
                    origTitle = subOrig,
                    year = st.item?.year,
                    release = stream.name ?: stream.description,
                    fps = stream.quality.fps,
                    runtime = st.movieDetails?.runtime,
                ))
            }
        }
        val direct = stream.url
        val cometPath = stream.cometPath
        val infoHash = stream.infoHash
        // Plan CASCADE: fallback kontext — když vybraný RD zdroj je DMCA-blokovaný, backend
        // sám zkusí dalšího cached kandidáta STEJNÉ kvality a nejbližší velikosti (místo Stremio skoku).
        val resolveCtx = st.item?.let { item ->
            com.github.jankoran90.showlyfin.data.uploader.model.UploaderResolveContext(
                imdb = item.imdbId,
                mediaType = mediaTypeStr(item),
                resolution = stream.quality.resolution,
                sizeGB = stream.quality.sizeGB,
            )
        }
        // WINNOW item 2: zapamatuj RD hash tohoto pokusu (bezpečný úklid při „zapamatovat zdroj").
        streamRdHash(stream)?.let { attemptedRdHashes.add(it) }
        // 1) přímá url (Ready (RD)) → hraj rovnou (deliver napřed ověří, že to není návnada).
        if (!direct.isNullOrBlank()) {
            timber.log.Timber.i("[Stremio] play direct url addon=${stream.addon}")
            deliver(direct, title, target)
            return
        }
        // 2) cached Comet (rdReady) → rychlý resolve na RD direct (302) bez progress baru.
        if (!cometPath.isNullOrBlank() && stream.quality.rdReady) {
            _uiState.update { it.copy(isResolvingStream = true, streamError = null) }
            viewModelScope.launch {
                runCatching { uploaderDs.resolveCometStream(uploaderBaseUrl, uploaderCookie, cometPath, resolveCtx) }
                    .onSuccess { url -> deliver(url, title, target) }
                    .onFailure { e -> handleResolveFailure(e, "[Stremio] comet resolve FAILED") }
            }
            return
        }
        // 3) už uložené na RD (DebridSearch) / cached infoHash → rychlý resolve, bez progress baru.
        if (!infoHash.isNullOrBlank() && (stream.quality.rdSaved || stream.quality.rdReady)) {
            _uiState.update { it.copy(isResolvingStream = true, streamError = null) }
            viewModelScope.launch {
                runCatching { uploaderDs.resolveStream(uploaderBaseUrl, uploaderCookie, infoHash, stream.fileIdx, resolveCtx) }
                    .onSuccess { url -> deliver(url, title, target) }
                    .onFailure { e -> handleResolveFailure(e, "[Stremio] saved resolve FAILED infoHash=$infoHash") }
            }
            return
        }
        // 4) necachovaný torrent (infoHash / uncached Comet) → async add na RD + progress bar (Fáze F).
        if (!cometPath.isNullOrBlank() || !infoHash.isNullOrBlank()) {
            startRdDownload(stream, title, target)
            return
        }
        _uiState.update { it.copy(streamError = "Stream nemá URL, cometPath ani infoHash.") }
    }

    /** Necachovaný torrent: přidá na RD a pollí progress, dokud se nestáhne → pak přehraje (Fáze F). */
    private fun startRdDownload(stream: UploaderStream, title: String, target: CastTarget = CastTarget.LOCAL) {
        rdPollJob?.cancel()
        _uiState.update {
            it.copy(
                showStreamPicker = false,
                streamError = null,
                rdDownload = RdDownloadState(fileIdx = stream.fileIdx, title = title),
            )
        }
        rdPollJob = viewModelScope.launch {
            val add = runCatching {
                uploaderDs.rdAdd(uploaderBaseUrl, uploaderCookie, stream.infoHash, stream.fileIdx, stream.cometPath)
            }.getOrElse { e ->
                handleResolveFailure(e, "[RD] add FAILED infoHash=${stream.infoHash} comet=${!stream.cometPath.isNullOrBlank()}")
                return@launch
            }
            if (add.error != null || add.torrentId.isBlank()) {
                _uiState.update { it.copy(rdDownload = null, streamError = add.error ?: "RD nevrátil torrent_id") }
                return@launch
            }
            val fIdx = add.fileIdx
            timber.log.Timber.i("[RD] add ok torrent=${add.torrentId} status=${add.status} fileIdx=$fIdx")
            _uiState.update { it.copy(rdDownload = it.rdDownload?.copy(torrentId = add.torrentId, status = add.status, progress = add.progress)) }
            while (isActive) {
                val p = try {
                    uploaderDs.rdProgress(uploaderBaseUrl, uploaderCookie, add.torrentId, fIdx)
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "[RD] progress transient fail — retry"); delay(3000); null
                }
                if (p == null) continue
                if (p.error != null) {
                    _uiState.update { it.copy(rdDownload = null, streamError = p.error) }
                    return@launch
                }
                _uiState.update {
                    it.copy(rdDownload = it.rdDownload?.copy(status = p.status, progress = p.progress, speedBytesPerSec = p.speed, seeders = p.seeders))
                }
                val readyUrl = p.url
                if (p.status == "downloaded" && !readyUrl.isNullOrBlank()) {
                    timber.log.Timber.i("[RD] downloaded → play torrent=${add.torrentId}")
                    _uiState.update { it.copy(rdDownload = null) }
                    deliver(readyUrl, title, target)
                    return@launch
                }
                delay(2500)
            }
        }
    }

    fun cancelRdDownload() {
        rdPollJob?.cancel()
        rdPollJob = null
        _uiState.update { it.copy(rdDownload = null) }
    }

    fun consumePlayback() = _uiState.update { it.copy(pendingPlaybackUrl = null, pendingPlaybackTitle = "", pendingSubtitleQuery = null) }
    fun consumeStremioFallback() = _uiState.update { it.copy(requestStremioFallback = false) }
    fun consumeAutoAdvanceInfo() = _uiState.update { it.copy(autoAdvanceInfo = null) }
    fun consumeCastResult() = _uiState.update { it.copy(castToTvResult = null) }

    /**
     * Plan WINNOW (SHW-41, item 1b): než URL doručíme, ověř, že to není NÁVNADA — Comet/RD běžně
     * vrací „cached" položky, které se tváří jako film (deklarovaná velikost i 20 GB), ale reálně
     * servírují jen ~stovky KB (decoy). Takový zdroj ExoPlayer „přehraje" a hned skončí → matoucí.
     * Range-dotaz na skutečnou velikost; pod prahem → přeskoč na další kandidáta (CASCADE).
     */
    private fun deliver(url: String, title: String, target: CastTarget) {
        _uiState.update { it.copy(isResolvingStream = true) }
        viewModelScope.launch {
            val size = probePlayableSize(url)
            if (size != null && size in 1 until MIN_PLAYABLE_BYTES) {
                timber.log.Timber.w("[WINNOW] zdroj je návnada (%d B < %d) → přeskakuji", size, MIN_PLAYABLE_BYTES)
                advancePastSource("Zdroj je jen ukázka/nefunkční, zkouším další", target)
                return@launch
            }
            deliverNow(url, title, target)
        }
    }

    /** Range 0-1 dotaz → skutečná velikost obsahu v bajtech (Content-Range/Content-Length). null = neznámo. */
    private suspend fun probePlayableSize(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-1")
                connectTimeout = 8_000; readTimeout = 8_000
                instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                when {
                    code == 206 -> conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    code in 200..299 -> conn.getHeaderField("Content-Length")?.toLongOrNull()
                    else -> null
                }
            } finally { conn.disconnect() }
        }.getOrNull()
    }

    /** Resolvnutá URL → cíl: lokální přehrávač (telefon) nebo odeslání na TV (FERRY). */
    private fun deliverNow(url: String, title: String, target: CastTarget) {
        when (target) {
            CastTarget.LOCAL -> {
                // SIEVE S2: až teď (přehrávač se reálně spouští) nabídneme „tohle sedí? 👍" — po návratu
                // na Detail. Nenabízíme u zdroje, který je už uložený jako fungující (žádné opakování).
                val confirm = lastPlayedStream?.takeIf { !sameSource(it, _uiState.value.rememberedSource) }
                _uiState.update {
                    it.copy(
                        isResolvingStream = false, showStreamPicker = false,
                        pendingPlaybackUrl = url, pendingPlaybackTitle = title,
                        pendingWorkingConfirm = confirm,
                    )
                }
            }
            CastTarget.TV -> castToTv(url, title)
        }
    }

    /**
     * Plan FERRY: pošle resolvnutou URL + CZ titulky na běžící yellyfin session na TV.
     * Titulky jsou best-effort (selhání nebrání přehrání). Výsledek → hláška v UI.
     */
    private fun castToTv(url: String, title: String) {
        _uiState.update { it.copy(isCastingToTv = true, streamError = null) }
        viewModelScope.launch {
            val jfUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
            val jfToken = prefs.getString("jellyfin_token", "").orEmpty()
            val subs = runCatching { buildTvSubtitles() }.getOrDefault(emptyList())
            // BATON: endpoint pro hlášení pozice — box sem reportuje, Ovladač čte (posuvník). Stejný key
            // jako u titulek (samonosná URL bez cookie).
            val item = _uiState.value.item
            val reportUrl = if (uploaderBaseUrl.isNotBlank() && uploaderCookie.isNotBlank()) {
                "${uploaderBaseUrl.trimEnd('/')}/api/ferry/state?key=${java.net.URLEncoder.encode(uploaderCookie, "UTF-8")}"
            } else null
            val result = naTv.castFerry(jfUrl, jfToken, url, title, subs, reportUrl)
            // Po úspěšném spuštění na TV přepni appku rovnou na sekci „Ovladač" (parita s JF knihovnou
            // přes NaTvCoordinator) → telefon se hned stává dálkovým ovladačem běžícího streamu.
            // + zapamatuj cast (externí stream není JF NowPlaying) → Ovladač ukáže titul/cover + pozici.
            if (result == CastResult.SENT) {
                val poster = (item?.posterPath ?: _uiState.value.movieDetails?.poster_path)
                    ?.let { "https://image.tmdb.org/t/p/w342$it" }
                ListenNavSignal.setFerryCast(title, poster, item?.tmdbId, reportUrl)
                ListenNavSignal.requestOpenOvladac()
            }
            _uiState.update {
                it.copy(isCastingToTv = false, isResolvingStream = false, showStreamPicker = result != CastResult.SENT, castToTvResult = result)
            }
        }
    }

    /** Stáhne CZ titulkové kandidáty a sestaví box-dostupné SRT URL (`?key=<session>`) pro TV. */
    private suspend fun buildTvSubtitles(): List<FerrySubtitle> {
        val q = _uiState.value.pendingSubtitleQuery ?: return emptyList()
        if (uploaderBaseUrl.isBlank()) return emptyList()
        val resp = runCatching {
            uploaderDs.getSubtitles(
                uploaderBaseUrl, uploaderCookie, q.imdb, q.title, q.origTitle, q.year, q.season, q.episode, q.release, q.fps,
            )
        }.getOrNull() ?: return emptyList()
        val runtime = _uiState.value.movieDetails?.runtime ?: 0
        val base = uploaderBaseUrl.trimEnd('/')
        val keyParam = java.net.URLEncoder.encode(uploaderCookie, "UTF-8")
        // Pošli top kandidáty (nejlepší první) → yellyfin/MPV je nasideloaduje, výběr titulku na TV (F3).
        return resp.subtitles.asSequence()
            .filter { it.id.isNotBlank() }
            .take(MAX_TV_SUBTITLES)
            .mapIndexed { i, c ->
                val params = buildList {
                    q.season?.takeIf { it > 0 }?.let { add("season=$it") }
                    q.episode?.takeIf { it > 0 }?.let { add("episode=$it") }
                    runtime.takeIf { it > 0 }?.let { add("runtime=$it") }
                    add("key=$keyParam")
                }.joinToString("&")
                FerrySubtitle(
                    url = "$base/api/subtitles/download/${c.id}?$params",
                    language = c.lang.takeIf { it.isNotBlank() } ?: "cs",
                    label = c.release.takeIf { it.isNotBlank() } ?: c.title.takeIf { it.isNotBlank() } ?: "CZ titulky ${i + 1}",
                )
            }.toList()
    }

    /**
     * CASCADE Fáze 4 — auto-advance po chybě přehrávání v ExoPlayeru.
     * Když zdroj nejde přehrát (vadný kontejner/kodek, ne DMCA), zkus DALŠÍ probnutý zdroj
     * v UŽIVATELOVĚ pořadí (`streams` je už seřazený dle jeho `fallbackOrder` — NEPŘEŘAZUJEME!).
     * Po vyčerpání kandidátů spadni na Stremio (původní chování).
     */
    fun onPlaybackFailed(errorCode: String) =
        advancePastSource("Zdroj nešel přehrát, zkouším další", CastTarget.LOCAL)

    /**
     * Přeskoč na DALŠÍ okamžitě hratelný kandidát v UŽIVATELOVĚ pořadí (direct url / cached RD).
     * Volá CASCADE auto-advance po chybě přehrávání i WINNOW po detekci návnady. Cíl ([target])
     * se zachová, aby přeskočení při castu na TV pokračovalo zase na TV (ne lokálně).
     * Pure-downloadable přeskakujeme — auto-retry nemá tiše spustit víceminutový RD download.
     */
    private fun advancePastSource(message: String, target: CastTarget) {
        val list = _uiState.value.streams
        val prev = lastPlayedStream
        val curIdx = if (prev != null) list.indexOf(prev) else -1
        val nextIdx = ((curIdx + 1) until list.size).firstOrNull { i ->
            val s = list[i]
            !s.url.isNullOrBlank() || s.quality.rdReady || s.quality.rdSaved
        } ?: -1
        if (nextIdx >= 0) {
            val next = list[nextIdx]
            timber.log.Timber.i("[CASCADE] advance → zdroj ${nextIdx + 1}/${list.size} '${next.name ?: next.description}' ($message)")
            _uiState.update {
                it.copy(
                    isResolvingStream = false,
                    rdDownload = null,
                    streamError = null,
                    requestStremioFallback = false,
                    autoAdvanceInfo = "$message (${nextIdx + 1}/${list.size})…",
                )
            }
            playStream(next, target)
        } else {
            timber.log.Timber.w("[CASCADE] advance: žádný další hratelný zdroj (z idx=$curIdx/${list.size}) → Stremio fallback")
            _uiState.update { it.copy(isResolvingStream = false, rdDownload = null, requestStremioFallback = true) }
        }
    }

    /**
     * Plan WINNOW (item 1): sjednocené ošetření selhání resolve/RD-add. HTTP 451 = titul je na
     * RealDebridu blokovaný (DMCA) → jasný dialog místo TICHÉHO skoku do externí Stremio appky.
     * Ostatní chyby = původní chování (hláška + nabídka Stremia).
     */
    private fun handleResolveFailure(e: Throwable, logMsg: String) {
        timber.log.Timber.w(e, logMsg)
        val is451 = e is com.github.jankoran90.showlyfin.data.uploader.model.StreamBlockedException
        _uiState.update {
            if (is451) it.copy(
                isResolvingStream = false,
                rdDownload = null,
                blockedDmcaMessage = "Tenhle titul je na RealDebridu blokovaný (DMCA) — žádný dostupný zdroj nejde přehrát napřímo. Zkus jiný release, nebo titul otevři přímo ve Stremiu.",
            ) else it.copy(
                isResolvingStream = false,
                rdDownload = null,
                streamError = e.message ?: "RD resolve selhal",
                requestStremioFallback = true,
            )
        }
    }

    fun consumeBlockedDmca() = _uiState.update { it.copy(blockedDmcaMessage = null) }

    /** Stream → RD info_hash (infoHash, jinak první segment cometPath), lowercase. null = nemá. */
    private fun streamRdHash(stream: UploaderStream): String? {
        stream.infoHash?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        val cp = stream.cometPath?.trim().orEmpty()
        if (cp.isNotBlank()) {
            return cp.trim('/').substringBefore('?').substringBefore('/').lowercase().takeIf { it.isNotBlank() }
        }
        return null
    }

    // ── Stáhnout menu (Sdílej.cz + Smart Remux) ────────────────────────────────

    fun openDownloadMenu() = _uiState.update { it.copy(showDownloadMenu = true) }
    fun dismissDownloadMenu() = _uiState.update { it.copy(showDownloadMenu = false) }

    /** Stáhnout → Sdílej.cz: seznam souborů z sdilej.cz k zachycení do knihovny. */
    fun openSdilejPicker() {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId
        if (imdb.isNullOrBlank() || uploaderBaseUrl.isBlank()) {
            timber.log.Timber.w("[Sdilej] picker blocked: imdbBlank=${imdb.isNullOrBlank()} baseUrlBlank=${uploaderBaseUrl.isBlank()} tmdb=${item.tmdbId} title='${item.title}'")
            _uiState.update { it.copy(showDownloadMenu = false, showSdilejPicker = true, sdilejError = "Uploader není nastaven nebo film nemá IMDB ID.") }
            return
        }
        val titleCs = item.titleCz?.takeIf { it.isNotBlank() } ?: _uiState.value.tmdbCzTitle.orEmpty()
        _uiState.update { it.copy(showDownloadMenu = false, showSdilejPicker = true, isLoadingSdilej = true, sdilejError = null, sdilejStreams = emptyList()) }
        viewModelScope.launch {
            runCatching { uploaderDs.getSdillejStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb, item.title, titleCs, item.year) }
                .onSuccess { list -> timber.log.Timber.i("[Sdilej] streams=${list.size} imdb=$imdb"); _uiState.update { it.copy(isLoadingSdilej = false, sdilejStreams = list, sdilejError = if (list.isEmpty()) "Na Sdílej.cz nic nenalezeno." else null) } }
                .onFailure { e -> timber.log.Timber.w(e, "[Sdilej] getSdillejStreams FAILED imdb=$imdb url=$uploaderBaseUrl"); _uiState.update { it.copy(isLoadingSdilej = false, sdilejError = e.message ?: "Chyba Sdílej.cz") } }
        }
    }

    fun dismissSdilejPicker() = _uiState.update { it.copy(showSdilejPicker = false) }

    /** Zachytí vybraný Sdílej.cz stream do TMM pipeline (stažení do knihovny). */
    fun captureSdilej(stream: UploaderStream) {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId ?: ""
        viewModelScope.launch {
            runCatching {
                uploaderDs.captureSdillej(
                    uploaderBaseUrl, uploaderCookie,
                    UploaderCaptureRequest(stream, imdb, item.title, item.year, mediaTypeStr(item), tmm = true),
                )
            }
                .onSuccess { timber.log.Timber.i("[Sdilej] capture OK imdb=$imdb"); _uiState.update { it.copy(showSdilejPicker = false, captureMessage = "Staženo do fronty — dokonči v Uploaderu.") } }
                .onFailure { e -> timber.log.Timber.w(e, "[Sdilej] capture FAILED imdb=$imdb url=$uploaderBaseUrl"); _uiState.update { it.copy(sdilejError = e.message ?: "Chyba stažení") } }
        }
    }

    fun consumeCaptureMessage() = _uiState.update { it.copy(captureMessage = null) }

    private suspend fun loadJellyfinOwned(item: MediaItem) {
        val userIdString = prefs.getString("jellyfin_user_id", "")?.takeIf { it.isNotBlank() } ?: return
        val userUuid = runCatching { UUID.fromString(userIdString) }.getOrNull() ?: return
        val owned = runCatching { jellyfinLibraryService.getOwnedIds(userUuid) }.getOrNull() ?: return
        val matchedJellyfinId = item.imdbId?.let { owned.imdbToJellyfin[it] }
            ?: item.tmdbId?.let { owned.tmdbToJellyfin[it] }
        val isWatched = (item.imdbId?.let { owned.watchedImdbIds.contains(it) } ?: false)
            || (item.tmdbId?.let { owned.watchedTmdbIds.contains(it) } ?: false)
        val jfCollection = matchedJellyfinId?.let { jfId ->
            runCatching { jellyfinLibraryService.findBoxSetCollectionForItem(userUuid, jfId) }.getOrNull()
        }?.let { jf ->
            MediaCollection(
                name = jf.name,
                parts = jf.parts.map { p ->
                    CollectionPart(
                        key = "jellyfin_${p.jellyfinId}",
                        tmdbId = p.tmdbId,
                        jellyfinId = p.jellyfinId,
                        title = p.title,
                        posterUrl = p.posterUrl,
                        year = p.year?.toString(),
                        watched = p.watched,
                    )
                },
            )
        }
        _uiState.update {
            it.copy(
                ownedImdbToJellyfin = owned.imdbToJellyfin,
                ownedTmdbToJellyfin = owned.tmdbToJellyfin,
                watchedImdbIds = owned.watchedImdbIds,
                watchedTmdbIds = owned.watchedTmdbIds,
                isOwnedInLibrary = matchedJellyfinId != null,
                ownedJellyfinId = matchedJellyfinId,
                isWatched = isWatched,
                boxSets = owned.boxSets,
                boxSetByTmdbCollection = owned.boxSetByTmdbCollection,
                boxSetByNormalizedName = owned.boxSetByNormalizedName,
                jellyfinCollection = jfCollection,
            )
        }
        recomputeMergedCollection(item)
    }

    /** Jellyfin BoxSet má přednost; doplní TMDB díly mimo knihovnu; řadí nejstarší→nejnovější. */
    private fun recomputeMergedCollection(item: MediaItem) {
        val state = _uiState.value
        val jf = state.jellyfinCollection
        val tmdb = state.collection
        val ownedTmdb = state.ownedTmdbToJellyfin
        val watchedTmdb = state.watchedTmdbIds

        fun tmdbParts(): List<CollectionPart> = tmdb?.parts.orEmpty().map { part ->
            CollectionPart(
                key = "tmdb_${part.id}",
                tmdbId = part.id,
                jellyfinId = ownedTmdb[part.id],
                title = part.title ?: "",
                posterUrl = part.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                year = part.release_date?.take(4),
                watched = watchedTmdb.contains(part.id),
            )
        }
        fun sortByYear(parts: List<CollectionPart>) =
            parts.sortedBy { it.year?.toIntOrNull() ?: Int.MAX_VALUE }

        val merged: MediaCollection? = when {
            jf != null -> {
                val jfTmdbIds = jf.parts.mapNotNull { it.tmdbId }.toSet()
                val missing = tmdbParts().filter { it.tmdbId != null && it.tmdbId !in jfTmdbIds }
                MediaCollection(name = jf.name, parts = sortByYear(jf.parts + missing))
            }
            tmdb != null -> {
                val resolvedBoxSetId = state.boxSetByTmdbCollection[tmdb.id]
                    ?: tmdb.name?.takeIf { it.isNotBlank() }
                        ?.let { state.boxSetByNormalizedName[normalizeBoxSetName(it)] }
                val displayName = state.boxSets.firstOrNull { it.jellyfinId == resolvedBoxSetId }?.name
                    ?: tmdb.name ?: "Kolekce"
                MediaCollection(name = displayName, parts = sortByYear(tmdbParts()))
            }
            else -> null
        }
        _uiState.update { it.copy(mergedCollection = merged) }
    }

    private suspend fun loadCsfd(item: MediaItem, czTitle: String?) {
        val titles = buildList {
            czTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.title.takeIf { it.isNotBlank() }?.let { if (!contains(it)) add(it) }
        }
        val year = item.year ?: 0
        val imdbId = item.imdbId.orEmpty()
        val tmdbId = item.tmdbId
        try {
            var csfdId: Long? = null
            for (title in titles) {
                csfdId = csfdRepository.getCsfdId(imdbId, tmdbId, title, year)
                if (csfdId != null) break
            }
            if (csfdId == null) {
                csfdId = csfdRepository.getCsfdId(imdbId, tmdbId, "", year)
            }
            if (csfdId == null) {
                _uiState.update { it.copy(isCsfdLoading = false) }
                return
            }
            _uiState.update { it.copy(csfdId = csfdId) }
            coroutineScope {
                val infoDeferred = async { fetchCsfdInfo(csfdId) }
                val reviewsDeferred = async { fetchCsfdReviews(csfdId).take(20) }
                val info = infoDeferred.await()
                _uiState.update {
                    it.copy(
                        csfdPlot = info.plot,
                        csfdRating = info.rating,
                        csfdTitle = info.title,
                        csfdReviews = reviewsDeferred.await(),
                        isCsfdLoading = false,
                    )
                }
            }
        } catch (e: Throwable) {
            _uiState.update { it.copy(isCsfdLoading = false) }
        }
    }

    // ČSFD popis/recenze: PRIMÁRNĚ přes backend (server zvládá Anubis; on-device scrape padá kvůli
    // cookie-propagation bugu po pass-challenge). On-device scraper jen jako fallback, když uploader není nastaven.
    // Vrací popis + hodnocení (0–100 %) + český název. Backend primárně (rating přes
    // `.film-rating-average`; on-device scrapeRating padá kvůli cookie bugu). On-device fallback
    // jen když uploader není nastaven.
    private suspend fun fetchCsfdInfo(csfdId: Long): CsfdPlotResponse {
        if (uploaderBaseUrl.isNotBlank()) {
            runCatching { uploaderDs.getCsfdPlot(uploaderBaseUrl, uploaderCookie, csfdId) }
                .getOrNull()?.takeIf { !it.plot.isNullOrBlank() || it.rating != null }?.let { return it }
        }
        val plot = runCatching { csfdRepository.getCzechPlot(csfdId) }.getOrNull()
        val rating = runCatching { csfdScraper.scrapeRating(csfdId) }.getOrNull()
        return CsfdPlotResponse(plot = plot, rating = rating, title = null)
    }

    // ── ČSFD galerie (F3) — lazy: načte se až po kliku na fanart / button Galerie ──
    /** Otevře galerii; při prvním otevření lazy načte URL fotek z backendu. */
    fun openGallery() {
        if (_uiState.value.showGallery) return
        _uiState.update { it.copy(showGallery = true) }
        if (_uiState.value.csfdGallery.isNotEmpty() || _uiState.value.isGalleryLoading) return
        val csfdId = _uiState.value.csfdId ?: return
        if (uploaderBaseUrl.isBlank()) return
        _uiState.update { it.copy(isGalleryLoading = true) }
        viewModelScope.launch {
            val urls = runCatching { uploaderDs.getCsfdGallery(uploaderBaseUrl, uploaderCookie, csfdId) }
                .onFailure { timber.log.Timber.w(it, "[CSFD] gallery FAILED csfdId=$csfdId") }
                .getOrDefault(emptyList())
            timber.log.Timber.i("[CSFD] gallery csfdId=$csfdId → ${urls.size} fotek")
            _uiState.update { it.copy(isGalleryLoading = false, csfdGallery = urls) }
        }
    }

    fun dismissGallery() = _uiState.update { it.copy(showGallery = false) }

    private suspend fun fetchCsfdReviews(csfdId: Long): List<com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw> {
        if (uploaderBaseUrl.isNotBlank()) {
            runCatching { uploaderDs.getCsfdReviews(uploaderBaseUrl, uploaderCookie, csfdId) }
                .getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { list -> return list.map { com.github.jankoran90.showlyfin.data.csfd.CsfdReviewRaw(it.username, it.rating, it.text, it.date) } }
        }
        return runCatching { csfdScraper.scrapeReviews(csfdId) }.getOrDefault(emptyList())
    }

    private companion object {
        // Kolik titulkových kandidátů poslat na TV (MPV je nasideloaduje, výběr na TV = F3).
        const val MAX_TV_SUBTITLES = 3
        // WINNOW item 1b: minimální reálná velikost přehrávatelného filmu/epizody (30 MB). Pod tím
        // je to návnada/decoy (Comet/RD servíruje ~stovky KB navzdory deklarované velikosti).
        const val MIN_PLAYABLE_BYTES = 30_000_000L
    }
}

/** Plan FERRY: kam doručit resolvnutý stream — lokální přehrávač telefonu, nebo TV (yellyfin). */
enum class CastTarget { LOCAL, TV }
