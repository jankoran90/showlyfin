package com.github.jankoran90.showlyfin.feature.detail

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.csfd.CsfdScraper
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.normalizeBoxSetName
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbCollection
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportItem
import com.github.jankoran90.showlyfin.data.trakt.model.SyncExportRequest
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.CsfdPlotResponse
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderCaptureRequest
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import dagger.hilt.android.lifecycle.HiltViewModel
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
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val uploaderBaseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private var rdPollJob: Job? = null

    // CASCADE Fáze 4: poslední přehrávaný stream z pickeru → po chybě přehrávání víme,
    // odkud v seznamu `streams` pokračovat dalším kandidátem (v UŽIVATELOVĚ pořadí, bez přeřazování).
    private var lastPlayedStream: UploaderStream? = null

    fun load(item: MediaItem) {
        val current = _uiState.value.item
        if (current != null) {
            val sameTrakt = current.traktId != 0L && current.traktId == item.traktId
            val sameTmdb = current.tmdbId != null && item.tmdbId != null && current.tmdbId == item.tmdbId
            if (sameTrakt || sameTmdb) return
        }
        lastPlayedStream = null
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
                directorName = null,
                directorMovies = null,
                studioName = null,
                studioMovies = null,
                showCollections = prefs.getBoolean("detail_show_collections", true),
                showDirector = prefs.getBoolean("detail_show_director", true),
                showStudio = prefs.getBoolean("detail_show_studio", true),
                plotCollapsedLines = prefs.getInt("detail_plot_lines", 5),
                error = null,
            )
        }
        viewModelScope.launch { loadJellyfinOwned(item) }
        viewModelScope.launch { loadWatchlistMembership(item) }
        viewModelScope.launch { loadCast(item) }
        viewModelScope.launch { loadRelated(item) }
        viewModelScope.launch {
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
                            _uiState.update {
                                it.copy(
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
                            _uiState.update {
                                it.copy(
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
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false, isCsfdLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadCast(item: MediaItem) {
        val tmdbId = item.tmdbId ?: return
        runCatching {
            val people = if (item.type == MediaType.MOVIE) {
                tmdbApi.fetchMoviePeople(tmdbId)
            } else {
                tmdbApi.fetchShowPeople(tmdbId)
            }
            people[com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson.Type.CAST].orEmpty().take(20)
        }.getOrNull()?.let { cast ->
            _uiState.update { it.copy(cast = cast) }
        }
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
    ): MediaCollection? {
        val parts = movies
            .filter { it.id != excludeTmdbId && !it.poster_path.isNullOrBlank() }
            .take(20)
            .map { m ->
                CollectionPart(
                    key = "tmdb_${m.id}",
                    tmdbId = m.id,
                    jellyfinId = _uiState.value.ownedTmdbToJellyfin[m.id],
                    title = m.title ?: "",
                    posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                    year = m.release_date?.take(4),
                    watched = _uiState.value.watchedTmdbIds.contains(m.id),
                )
            }
        return if (parts.isEmpty()) null else MediaCollection(name = name, parts = parts)
    }

    private suspend fun loadWatchlistMembership(item: MediaItem) {
        if (tokenProvider.getToken() == null || item.traktId == 0L) return
        runCatching {
            val list = if (item.type == MediaType.MOVIE) {
                authorizedTrakt.fetchSyncMoviesWatchlist()
            } else {
                authorizedTrakt.fetchSyncShowsWatchlist()
            }
            list.any { it.getTraktId() == item.traktId }
        }.getOrNull()?.let { inWl ->
            _uiState.update { it.copy(isInWatchlist = inWl) }
        }
    }

    fun toggleWatchlist() {
        val item = _uiState.value.item ?: return
        if (tokenProvider.getToken() == null || item.traktId == 0L) return
        if (_uiState.value.isTogglingWatchlist) return
        val currentlyIn = _uiState.value.isInWatchlist
        _uiState.update { it.copy(isTogglingWatchlist = true) }
        viewModelScope.launch {
            val exportItem = SyncExportItem.create(item.traktId)
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

    /** Klik na konkrétní stream → přímé url / RD resolve → předá URL navigaci k přehrání. */
    fun playStream(stream: UploaderStream) {
        if (_uiState.value.isResolvingStream || _uiState.value.rdDownload != null) return
        lastPlayedStream = stream   // CASCADE Fáze 4: zapamatuj pro případný auto-advance po chybě přehrávání
        val title = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() }
            ?: _uiState.value.item?.title.orEmpty()
        // CZ titulky query (Fáze E): orig+cz název, rok, runtime, release+fps zvoleného streamu.
        val st = _uiState.value
        val imdbForSub = st.item?.imdbId
        if (!imdbForSub.isNullOrBlank()) {
            _uiState.update {
                it.copy(pendingSubtitleQuery = com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery(
                    imdb = imdbForSub,
                    title = st.tmdbCzTitle?.takeIf { t -> t.isNotBlank() } ?: st.item?.title.orEmpty(),
                    origTitle = st.item?.title.orEmpty(),
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
        // 1) přímá url (Ready (RD)) → hraj rovnou.
        if (!direct.isNullOrBlank()) {
            timber.log.Timber.i("[Stremio] play direct url addon=${stream.addon}")
            _uiState.update { it.copy(showStreamPicker = false, pendingPlaybackUrl = direct, pendingPlaybackTitle = title) }
            return
        }
        // 2) cached Comet (rdReady) → rychlý resolve na RD direct (302) bez progress baru.
        if (!cometPath.isNullOrBlank() && stream.quality.rdReady) {
            _uiState.update { it.copy(isResolvingStream = true, streamError = null) }
            viewModelScope.launch {
                runCatching { uploaderDs.resolveCometStream(uploaderBaseUrl, uploaderCookie, cometPath, resolveCtx) }
                    .onSuccess { url -> _uiState.update { it.copy(isResolvingStream = false, showStreamPicker = false, pendingPlaybackUrl = url, pendingPlaybackTitle = title) } }
                    .onFailure { e -> timber.log.Timber.w(e, "[Stremio] comet resolve FAILED"); _uiState.update { it.copy(isResolvingStream = false, streamError = e.message ?: "RD resolve selhal", requestStremioFallback = true) } }
            }
            return
        }
        // 3) už uložené na RD (DebridSearch) / cached infoHash → rychlý resolve, bez progress baru.
        if (!infoHash.isNullOrBlank() && (stream.quality.rdSaved || stream.quality.rdReady)) {
            _uiState.update { it.copy(isResolvingStream = true, streamError = null) }
            viewModelScope.launch {
                runCatching { uploaderDs.resolveStream(uploaderBaseUrl, uploaderCookie, infoHash, stream.fileIdx, resolveCtx) }
                    .onSuccess { url -> _uiState.update { it.copy(isResolvingStream = false, showStreamPicker = false, pendingPlaybackUrl = url, pendingPlaybackTitle = title) } }
                    .onFailure { e -> timber.log.Timber.w(e, "[Stremio] saved resolve FAILED infoHash=$infoHash"); _uiState.update { it.copy(isResolvingStream = false, streamError = e.message ?: "RD resolve selhal", requestStremioFallback = true) } }
            }
            return
        }
        // 4) necachovaný torrent (infoHash / uncached Comet) → async add na RD + progress bar (Fáze F).
        if (!cometPath.isNullOrBlank() || !infoHash.isNullOrBlank()) {
            startRdDownload(stream, title)
            return
        }
        _uiState.update { it.copy(streamError = "Stream nemá URL, cometPath ani infoHash.") }
    }

    /** Necachovaný torrent: přidá na RD a pollí progress, dokud se nestáhne → pak přehraje (Fáze F). */
    private fun startRdDownload(stream: UploaderStream, title: String) {
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
                timber.log.Timber.w(e, "[RD] add FAILED infoHash=${stream.infoHash} comet=${!stream.cometPath.isNullOrBlank()}")
                _uiState.update { it.copy(rdDownload = null, streamError = e.message ?: "RD: přidání torrentu selhalo", requestStremioFallback = true) }
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
                if (p.status == "downloaded" && !p.url.isNullOrBlank()) {
                    timber.log.Timber.i("[RD] downloaded → play torrent=${add.torrentId}")
                    _uiState.update { it.copy(rdDownload = null, pendingPlaybackUrl = p.url, pendingPlaybackTitle = title) }
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

    /**
     * CASCADE Fáze 4 — auto-advance po chybě přehrávání v ExoPlayeru.
     * Když zdroj nejde přehrát (vadný kontejner/kodek, ne DMCA), zkus DALŠÍ probnutý zdroj
     * v UŽIVATELOVĚ pořadí (`streams` je už seřazený dle jeho `fallbackOrder` — NEPŘEŘAZUJEME!).
     * Po vyčerpání kandidátů spadni na Stremio (původní chování).
     */
    fun onPlaybackFailed(errorCode: String) {
        val list = _uiState.value.streams
        val prev = lastPlayedStream
        val curIdx = if (prev != null) list.indexOf(prev) else -1
        // Další OKAMŽITĚ hratelný kandidát v UŽIVATELOVĚ pořadí (direct url / cached RD).
        // Pure-downloadable přeskakujeme — auto-retry nemá tiše spustit vícеminutový RD download.
        val nextIdx = ((curIdx + 1) until list.size).firstOrNull { i ->
            val s = list[i]
            !s.url.isNullOrBlank() || s.quality.rdReady || s.quality.rdSaved
        } ?: -1
        if (nextIdx >= 0) {
            val next = list[nextIdx]
            timber.log.Timber.i("[CASCADE] auto-advance po chybě přehrávání code=$errorCode → zdroj ${nextIdx + 1}/${list.size} '${next.name ?: next.description}'")
            _uiState.update {
                it.copy(
                    isResolvingStream = false,
                    rdDownload = null,
                    streamError = null,
                    requestStremioFallback = false,
                    autoAdvanceInfo = "Zdroj nešel přehrát, zkouším další (${nextIdx + 1}/${list.size})…",
                )
            }
            playStream(next)
        } else {
            timber.log.Timber.w("[CASCADE] auto-advance: žádný další hratelný zdroj (z idx=$curIdx/${list.size}) code=$errorCode → Stremio fallback")
            _uiState.update { it.copy(isResolvingStream = false, rdDownload = null, requestStremioFallback = true) }
        }
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
}
