package com.github.jankoran90.showlyfin.feature.detail

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.data.csfd.CsfdRepository
import com.github.jankoran90.showlyfin.data.csfd.CsfdScraper
import com.github.jankoran90.showlyfin.data.jellyfin.CastResult
import com.github.jankoran90.showlyfin.data.jellyfin.FerrySubtitle
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinLibraryService
import com.github.jankoran90.showlyfin.data.jellyfin.CastTargetPrefs
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
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
    private val parentalControls: com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository,
    private val authorizedTrakt: AuthorizedTraktRemoteDataSource,
    private val tokenProvider: TokenProvider,
    private val uploaderDs: UploaderRemoteDataSource,
    private val naTv: NaTvService,
    private val workingSourceStore: com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore,
    // REPACK: paměť „tenhle zdroj se bez přebalu nepřehraje" → druhé spuštění nečeká na pád přehrávače.
    private val repackNeededStore: com.github.jankoran90.showlyfin.data.uploader.RepackNeededStore,
    private val traktSyncSignal: com.github.jankoran90.showlyfin.data.uploader.TraktSyncSignal,
    private val favoritesStore: com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository,
    // SEZONA f3k: lokální „Chci vidět" pro profily bez Traktu (dětské) — filmy i seriály.
    private val wantToSee: com.github.jankoran90.showlyfin.core.db.repository.WantToSeeRepository,
    // DINGO — per-zařízení preset přehrávání (preferuj H.264 pro slabé HEVC dekodéry v autě). Re-rank seznamu zdrojů.
    private val streamPresetStore: com.github.jankoran90.showlyfin.data.uploader.StreamPresetStore,
    // VLTAVA (SHW-110): ČT iVysílání — odkaz na video si tahá ZAŘÍZENÍ (playlist API je na server geoblokované).
    private val ctvResolver: com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver,
    private val offlineManager: com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager,
    // MAESTRO / D-c: probuzení domácí AV sestavy před „Přehrát na Filmy TV".
    private val homeTheaterScene: com.github.jankoran90.showlyfin.data.maestro.HomeTheaterScene,
    // SEZONA (SHW-113) f2: volba zvukové stopy per titul (chip na kartě); výchozí dává profil.
    private val audioPathStore: com.github.jankoran90.showlyfin.data.uploader.AudioPathStore,
    // user 2026-08-18 (Harry Potter 20 let / Splitsville): „chci CZ dabing / originál pro TENHLE
    // titul" — čte/zapisuje ProfileConfig.titleAudioChoice (synced appka↔web).
    private val profileRepository: com.github.jankoran90.showlyfin.core.data.ProfileRepository,
    // BACKLOG link mode: play-gate — na mobilních datech nahraď uložený velký zdroj menší alternativou.
    private val connectivity: com.github.jankoran90.showlyfin.core.network.ConnectivityObserver,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        // NOMAD (SHW-60): zrcadli stav offline stažení TOHOTO titulu do uiState (badge v menu Stáhnout).
        viewModelScope.launch {
            offlineManager.states.collect { map ->
                val key = currentOfflineKey()
                _uiState.update { it.copy(offlineState = key?.let { k -> map[k] } ?: com.github.jankoran90.showlyfin.data.offline.OfflineState()) }
            }
        }
    }

    /**
     * NOMAD: klíč offline stažení pro aktuální titul (film). Priorita: vlastněný JF titul → `jf_<id>`;
     * jinak HOARD (SHW-84): film mimo knihovnu se zapamatovaným zdrojem → stabilní klíč z imdb/tmdb,
     * ať badge „Stahuje se… / Staženo" a mazání sedí i na stažení ze zapamatovaného zdroje.
     */
    private fun currentOfflineKey(): String? {
        val s = _uiState.value
        if (s.item?.type != MediaType.MOVIE) return null
        val item = s.item ?: return null
        s.ownedJellyfinId?.let { return "jf_$it" }
        if (s.rememberedSource != null) return movieOfflineKey(item)
        return null
    }

    /** HOARD: stabilní offline klíč filmu mimo knihovnu (imdb, jinak tmdb). */
    private fun movieOfflineKey(item: MediaItem): String {
        item.imdbId?.takeIf { it.isNotBlank() }?.let { return "movie_$it" }
        return "movie_tmdb_${item.tmdbId ?: 0}"
    }

    /** SEZONA (SHW-113): offline klíč DÍLU — sufix „_s1e1", ať se díly ve frontě stahování nepřepisují. */
    private fun episodeOfflineKey(item: MediaItem, season: Int, episode: Int): String =
        movieOfflineKey(item) + "_s${season}e$episode"

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

    // RELAY (2026-07-19): zdroje, u kterých už proběhl auto re-resolve po IO/HTTP chybě (efemérní RD odkaz).
    // Gate = jen 1× re-resolve téhož zdroje, ať se necyklí, když je zdroj fakt mrtvý. Reset při `load`.
    private val ioRetriedKeys = LinkedHashSet<String>()

    // REPACK (SEZONA f3e): URL, které už jednou prošly přebalem — ať se to necyklí, když ani přebalený
    // soubor nehraje. Reset při `load` (jiný titul = čistý štít). A poslední URL doručená přehrávači:
    // přebal potřebuje PŘESNĚ ji (resolvnutou adresu), ne `UploaderStream` před resolve.
    private val repackTriedUrls = LinkedHashSet<String>()
    private var lastDeliveredUrl: String? = null

    // 🔴 REPACK (2026-08-02, userův Bleach E6) — přehrávací POPISKY drž mimo `pendingPlayback*`, protože
    // ten stav je JEDNORÁZOVÝ: `consumePlayback()` ho po spuštění přehrávače vynuluje. Přebal je ale
    // DRUHÝ pokus o přehrání téhož dílu — sáhl do už vyprázdněného stavu, takže lišta ukázala „Bleach"
    // místo „Bleach S1E6" a titulky se nehledaly vůbec (dotaz se sezónou/dílem byl pryč → přehrávač
    // dostal null). User: *„je vidět, že název je bleach a ne epizoda 6, titulky taky nejsou načteny."*
    private var lastPlaybackTitle: String = ""
    private var lastSubtitleQuery: com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery? = null

    // PROJECTOR (HUB-74): hlasový cast na TV/Zenbook. Latch (VM je Activity-scoped → přežije mezi filmy,
    // resetuje se v load()), preferovaný cíl (tv=null → automatika, zenbook=deviceId docku) a příznak,
    // že běžící cast je hlasový (na odmítnutí ukázat hlášku místo pickeru).
    private var autoCastPending = false
    // LAPIDARY S4b — one-click z řady „Uloženo k přehrání": po hydrataci detailu přehraj zapamatovaný
    // zdroj rovnou (jednou). Guard proti dvojímu spuštění (rekompozice / návrat na tentýž titul).
    private var autoplayRememberedPending = false
    private var voiceCastActive = false
    private var voiceCastDeviceId: String? = null

    fun load(item: MediaItem) = load(item, force = false)

    /** VISTA V4: znovunačtení po síťové chybě (obejde dedup guard). */
    fun retry() {
        val item = _uiState.value.item ?: return
        load(item, force = true)
    }

    /**
     * CELLULOID (SHW-98) — živé dotažení auto-nacachovaného zdroje ze serveru při otevření detailu, BEZ restartu
     * appky. Jen film, jen dospělý profil (`effectiveAgeCap == null`) a jen když je volba zapnutá v Nastavení
     * (klíč [KEY_AUTO_REFRESH_SOURCES], default zap). Po sync znovu načte zapamatovaný zdroj a — pokud jsme ho
     * ještě neměli a stále koukáme na tentýž titul — přepíše `rememberedSource` → film jde přehrát rovnou.
     */
    private fun maybeLiveRefreshSource(item: MediaItem) {
        if (item.type != MediaType.MOVIE) return
        if (!prefs.getBoolean(KEY_AUTO_REFRESH_SOURCES, true)) return
        if (parentalControls.profile.value.effectiveAgeCap != null) return   // jen dospělý účet
        viewModelScope.launch {
            runCatching { workingSourceStore.syncNow() }
            val fresh = workingSourceStore.get(item.imdbId, item.tmdbId)?.stream ?: return@launch
            val cur = _uiState.value
            if (cur.item?.tmdbId == item.tmdbId && cur.rememberedSource == null) {
                _uiState.update { it.copy(rememberedSource = fresh) }
            }
            // Bod 3 (2026-07-19): RD cachovaný torrent po čase EVIKUJE → zapamatovaný zdroj zvětrá, ale pořád
            // se tváří jako cached (rdReady/rdSaved) → přehrávač na něj skočí „instant" → tichý zásek. Ověř,
            // že je STÁLE stažený na RD; když ne, sesaď příznaky (→ playStream ukáže stahování místo záseku,
            // hvězda/badge přestane lhát) a znovu nacachuj na pozadí, ať je příště zase instant.
            reverifyRememberedCached(item)
        }
    }

    /** Bod 3 re-verify jednoho zapamatovaného zdroje proti živému RD účtu. Levné (backend cache 60 s). */
    private suspend fun reverifyRememberedCached(item: MediaItem) {
        val rem = _uiState.value.rememberedSource ?: return
        if (!(rem.quality.rdReady || rem.quality.rdSaved)) return  // downloadable/sdilej/url = není co ověřovat
        val hash = rem.infoHash?.takeIf { it.isNotBlank() } ?: return  // jen RD torrent má infoHash
        val stillCached = runCatching { uploaderDs.rdCached(uploaderBaseUrl, uploaderCookie, hash) }.getOrDefault(true)
        if (stillCached) return
        timber.log.Timber.w("[bod3] zapamatovaný zdroj hash=$hash už NENÍ cached na RD (evikován) → sesazuji na downloadable + re-cache")
        val downgraded = rem.copy(quality = rem.quality.copy(rdReady = false, rdSaved = false, rdDownloadable = true))
        _uiState.update { st ->
            if (st.item?.tmdbId == item.tmdbId && sameSource(st.rememberedSource, rem)) {
                st.copy(rememberedSource = downgraded)
            } else st
        }
        // RD re-add na pozadí (backend ensure_cached) → až dotáhne, zapíše čerstvý cached WorkingSource.
        // 🔴 SEZONA (2026-08-02): u SERIÁLU se musí re-cache spustit pro SEZÓNU. Dřív tu byl natvrdo
        // filmový `triggerAutoCache`, takže server zapsal náhradu BEZ `epKey` = řádek k nerozeznání od
        // filmu → ve Filmotéce naskočila cizí karta (tmdb 30984 jako film = „Dissection…", ne Bleach).
        val title = _uiState.value.tmdbCzTitle ?: item.title
        val season = _uiState.value.selectedSeason
            ?: _uiState.value.seasons.firstOrNull { s -> s.season_number >= 1 }?.season_number
        runCatching {
            if (item.type == MediaType.SHOW) {
                workingSourceStore.triggerSeasonCache(
                    item.imdbId, item.tmdbId, title, item.year, effectiveCachePolicy(item.imdbId), season ?: 1,
                )
            } else {
                workingSourceStore.triggerAutoCache(
                    item.imdbId, item.tmdbId, title, item.year, effectiveCachePolicy(item.imdbId),
                )
            }
        }
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
        repackTriedUrls.clear()
        lastDeliveredUrl = null
        lastPlaybackTitle = ""   // REPACK: popisky patří k předchozímu titulu → nesmí přetéct do nového
        lastSubtitleQuery = null
        episodeSelector = null   // TENFOOT WS-C: nový titul → zapomeň vybranou epizodu
        attemptedRdHashes.clear()
        ioRetriedKeys.clear()
        // PROJECTOR: nový film → resetuj hlasový cast latch (VM je Activity-scoped).
        autoCastPending = false
        voiceCastActive = false
        voiceCastDeviceId = null
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
                // TENFOOT KOLO2 (I): csfdTitle je fallback pro český název; bez resetu drží starý
                // titul předchozího filmu (u seriálů loadCsfd neběží → nikdy se nepřepíše) → hero „visí".
                csfdTitle = null,
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
                titleAudioOverride = item.imdbId?.let { profileRepository.activeConfig.value.titleAudioChoice[it] },
                hasSeasonSource = false,
                // U seriálu se `rememberedSource` naplní až po otevření dílu — tohle ví hned, takže
                // menu karty může nabídnout „zapomenout zdroje" bez proklikávání se k epizodě.
                hasAnyShowSource = workingSourceStore.getEpisodes(item.imdbId, item.tmdbId).isNotEmpty(),
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
                incompatibleFormatMessage = null,
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
                sectionStyle = readSectionStyle(),
                showSeasons = prefs.getBoolean("detail_show_seasons", true),
                seasons = emptyList(),
                selectedSeason = null,
                seasonEpisodes = emptyList(),
                isLoadingEpisodes = false,
                episodeWatched = emptySet(),
                episodeProgress = emptyMap(),
                nextUpEpisode = null,
                episodeJellyfinIds = emptyMap(),
                plotCollapsedLines = prefs.getInt("detail_plot_lines", 5),
                actionOrder = parseActionOrder(prefs.getString("detail_action_order", null)),
                tvDetailLayout = readTvDetailLayout(),
                plotAutoCompact = prefs.getBoolean("detail_plot_autocompact", true),
                actionsPlacement = readActionsPlacement(),
                error = null,
            )
        }
        maybeLiveRefreshSource(item)
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
                            val detailsDeferred = async { tmdbApi.fetchMovieDetails(tmdbId, "cs-CZ") }
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
                                        // FIX: NEpřepisuj null (transient TMDB fail / poisoned cache) přes už
                                        // dodaný poster/backdrop (z enricheru na kartě) → jinak zmizí fanart.
                                        posterPath = details?.poster_path ?: item.posterPath,
                                        backdropPath = details?.backdrop_path ?: item.backdropPath,
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
                            val detailsDeferred = async { tmdbApi.fetchShowDetails(tmdbId, "cs-CZ") }
                            val translationDeferred = async { tmdbApi.fetchShowTranslation(tmdbId, "cs") }
                            // SEZONA (SHW-113): imdb id seriálu. `tv/{id}` ho NENESE (film ano) → seriál
                            // otevřený z Hledat zůstal bez `imdbId` a stream flow ho odmítl hláškou
                            // „Uploader není nastaven nebo film nemá IMDB ID" (user, screenshot 11:11),
                            // i když zdroje existují. Tahá se paralelně, ať detail nečeká.
                            val imdbDeferred = async {
                                if (item.imdbId.isNullOrBlank()) tmdbApi.fetchShowImdbId(tmdbId) else null
                            }
                            val details = detailsDeferred.await()
                            val translation = translationDeferred.await()
                            val showImdb = imdbDeferred.await()
                            val tmdbCzTitle = translation?.name?.takeIf { it.isNotBlank() }
                            if (tmdbCzTitle != null) resolvedCzTitle = tmdbCzTitle
                            _uiState.update { st ->
                                if (st.item?.isSameAs(item) != true) st
                                else st.copy(
                                    showDetails = details,
                                    // TENFOOT WS-C: souhrn sezón (bez speciálů 0 nahoře — necháme, ale řadíme).
                                    seasons = details?.seasons?.filter { s -> s.season_number >= 0 }
                                        ?.sortedBy { s -> s.season_number }.orEmpty(),
                                    tmdbCzOverview = translation?.overview?.takeIf { o -> o.isNotBlank() },
                                    tmdbCzTitle = tmdbCzTitle,
                                    // TENFOOT: u seriálu otevřeného z resume/next-up je item.title název EPIZODY.
                                    // Přepiš na název seriálu (TMDB `name`), aby detail neukazoval epizodu. Dedup je
                                    // přes trakt/tmdb id (ne title), takže je to bezpečné.
                                    item = item.copy(
                                        title = details?.name?.takeIf { n -> n.isNotBlank() } ?: item.title,
                                        // SEZONA: backfill imdb z `external_ids` → zdroje i titulky dílů mají podle čeho hledat.
                                        imdbId = item.imdbId?.takeIf { id -> id.isNotBlank() } ?: showImdb,
                                        // FIX: viz movie větev — null z transient failu nepřepíše dodaný poster/backdrop.
                                        posterPath = details?.poster_path ?: item.posterPath,
                                        backdropPath = details?.backdrop_path ?: item.backdropPath,
                                    ),
                                    isLoading = false,
                                )
                            }
                            // TENFOOT WS-C: auto-vyber první „skutečnou" sezónu (season ≥ 1 s epizodami) a načti epizody.
                            val seasonList = details?.seasons?.filter { s -> s.season_number >= 0 }
                                ?.sortedBy { s -> s.season_number }.orEmpty()
                            val defaultSeason = seasonList.firstOrNull { s -> s.season_number >= 1 && (s.episode_count ?: 0) > 0 }?.season_number
                                ?: seasonList.firstOrNull()?.season_number
                            if (defaultSeason != null && _uiState.value.item?.isSameAs(item) == true) {
                                selectSeason(defaultSeason)
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
                directors = crewByRole(crew, dept = "Directing", jobs = setOf("director", "co-director"), deptFallback = false),
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
        // Oddělení „Directing" obsahuje i asistenty režie / script supervisory apod. — u režie
        // (deptFallback=false) ber JEN přesný job (Director), ať se nezobrazí špatní „režiséři".
        deptFallback: Boolean = true,
    ): List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson> {
        fun matches(p: com.github.jankoran90.showlyfin.data.tmdb.model.TmdbPerson): Boolean {
            if (p.job != null && jobs.any { it.equals(p.job, ignoreCase = true) }) return true
            if (p.jobs?.any { j -> j.job != null && jobs.any { it.equals(j.job, ignoreCase = true) } } == true) return true
            return deptFallback && p.department?.equals(dept, ignoreCase = true) == true
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

    // TENFOOT KOLO2 (K): návrat na otevřenou filmografii po Zpět z detailu filmu.
    // PersonFilmographySheet je transientní stav sdílené (Activity-scoped) VM, ne back-stack destinace —
    // klik na film sheet zavře a load(B) reset jej vynuluje. Uložíme ho do privátního pole (mimo uiState,
    // takže ho load() reset nesmaže) a po back() na PŮVODNÍ titul (stejný stableKey) znovu otevřeme z cache
    // (bez refetch → „zachovaný obsah"). Jednoúrovňový stash (pokrývá scénář otevři→jeden film→Zpět).
    private data class PendingPersonSheet(
        val ownerKey: String,
        val name: String?,
        val person: TmdbPerson?,
        val kind: FavoriteKind?,
        val roleLabel: String?,
        val filmography: MediaCollection?,
        val favorite: Boolean,
    )
    private var pendingPersonSheet: PendingPersonSheet? = null

    /** Stabilní klíč titulu (stejná priorita jako [isSameAs]) — kotví stash na konkrétní Detail. */
    private fun MediaItem.stableKey(): String = when {
        tmdbId != null -> "tmdb:$tmdbId"
        traktId != 0L -> "trakt:$traktId"
        !imdbId.isNullOrBlank() -> "imdb:$imdbId"
        else -> "title:$title"
    }

    /** K: místo [closePersonSheet] při prokliku z filmografie na film — ulož sheet pro pozdější re-open. */
    fun stashPersonSheetForReturn(owner: MediaItem) {
        val st = _uiState.value
        if (!st.showPersonSheet || st.personFilmography == null) return
        pendingPersonSheet = PendingPersonSheet(
            ownerKey = owner.stableKey(), name = st.personSheetName, person = st.personSheetPerson,
            kind = st.personSheetKind, roleLabel = st.personSheetRoleLabel,
            filmography = st.personFilmography, favorite = st.isPersonFavorite,
        )
    }

    /** K: po návratu (back) na původní titul znovu otevři stashnutou filmografii — jednorázově. */
    fun reopenPendingPersonSheet(owner: MediaItem) {
        val pending = pendingPersonSheet ?: return
        if (pending.ownerKey != owner.stableKey()) return
        pendingPersonSheet = null
        _uiState.update {
            it.copy(
                showPersonSheet = true, personSheetLoading = false, personSheetName = pending.name,
                personFilmography = pending.filmography, personSheetPerson = pending.person,
                personSheetKind = pending.kind, personSheetRoleLabel = pending.roleLabel,
                isPersonFavorite = pending.favorite,
            )
        }
    }

    /**
     * KOLO2 (J): long-pressem přepni „zhlédnuto" u epizody vlastněného seriálu. Zapíše stav ZPĚT do Jellyfin
     * UserData ([JellyfinLibraryService.markPlayed]) a aktualizuje lokální [DetailUiState.episodeWatched]
     * (fajfka na kartě). No-op u epizod mimo Jellyfin knihovnu (chybí episode id → nemáme kam zapsat).
     */
    fun toggleEpisodeWatched(season: Int, episode: Int) {
        val key = season to episode
        val jfId = _uiState.value.episodeJellyfinIds[key]
        val nowWatched = key !in _uiState.value.episodeWatched
        viewModelScope.launch {
            // SEZONA f3b: díl MIMO Jellyfin knihovnu (stream) jde do TRAKT historie. Dosud tu byl
            // `?: return` = fajfka jen ke čtení, takže u seriálu bez knihovny nešlo označit vůbec nic
            // (jediná cesta byla dokoukat ho v přehrávači).
            val ok = if (jfId != null) jellyfinLibraryService.markPlayed(jfId, nowWatched)
            else setEpisodeWatchedOnTrakt(season, episode, nowWatched)
            if (ok) {
                _uiState.update { st ->
                    val w = st.episodeWatched.toMutableSet()
                    if (nowWatched) w.add(key) else w.remove(key)
                    st.copy(episodeWatched = w)
                }
            }
        }
    }

    /**
     * SEZONA f3b — celá sezóna na Trakt najednou (seriál mimo Jellyfin knihovnu). Seznam dílů bereme
     * z TMDB ([DetailUiState.seasonEpisodes]) — u seriálu bez knihovny je to jediný zdroj pravdy o tom,
     * kolik dílů sezóna vlastně má.
     */
    private fun markSeasonWatchedOnTrakt(season: Int, watched: Boolean) {
        val imdb = _uiState.value.item?.imdbId?.takeIf { it.isNotBlank() } ?: return
        val episodes = _uiState.value.seasonEpisodes
            .map { it.episode_number }
            .filter { it > 0 }
        if (episodes.isEmpty()) return
        val item = SyncExportItem(
            ids = SyncExportItem.Ids(imdb = imdb),
            watched_at = null,
            hidden_at = null,
            seasons = listOf(
                SyncExportItem.Season(
                    number = season,
                    episodes = episodes.map { SyncExportItem.Episode(number = it, watched_at = "released") },
                ),
            ),
        )
        val req = SyncExportRequest(shows = listOf(item))
        viewModelScope.launch {
            val ok = runCatching {
                if (watched) authorizedTrakt.postSyncWatched(req) else authorizedTrakt.postDeleteProgress(req)
            }.onFailure { timber.log.Timber.w(it, "[SEZONA] Trakt sezóna %d selhala", season) }.isSuccess
            if (!ok) return@launch
            val keys = episodes.map { season to it }
            _uiState.update { st ->
                val w = st.episodeWatched.toMutableSet()
                if (watched) w.addAll(keys) else w.removeAll(keys.toSet())
                st.copy(episodeWatched = w)
            }
            timber.log.Timber.i("[SEZONA] Trakt sezóna %d: %d dílů → zhlédnuto=%b", season, episodes.size, watched)
        }
    }

    /**
     * SEZONA f3b — zapiš/zruš „zhlédnuto" u DÍLU na Traktu (`sync/history`, resp. `history/remove`).
     * Trakt chce díl jako `shows[{ids:{imdb:<SERIÁL>}, seasons[{number, episodes[…]}]}]` — `imdbId`
     * detailu je id seriálu, číslo dílu nese sezóna+epizoda. Tatáž konstrukce jako [WatchedReporter],
     * jen odsud ručně. Vrací true = Trakt volání prošlo.
     */
    private suspend fun setEpisodeWatchedOnTrakt(season: Int, episode: Int, watched: Boolean): Boolean {
        val imdb = _uiState.value.item?.imdbId?.takeIf { it.isNotBlank() } ?: run {
            timber.log.Timber.w("[SEZONA] fajfka dílu: seriál nemá imdb → nemám kam zapsat")
            return false
        }
        val item = SyncExportItem(
            ids = SyncExportItem.Ids(imdb = imdb),
            watched_at = null,
            hidden_at = null,
            seasons = listOf(
                SyncExportItem.Season(
                    number = season,
                    episodes = listOf(SyncExportItem.Episode(number = episode, watched_at = "released")),
                ),
            ),
        )
        val req = SyncExportRequest(shows = listOf(item))
        return runCatching {
            if (watched) authorizedTrakt.postSyncWatched(req) else authorizedTrakt.postDeleteProgress(req)
        }.onSuccess {
            timber.log.Timber.i("[SEZONA] Trakt %s %s S%02dE%02d",
                if (watched) "zhlédnuto" else "odznačeno", imdb, season, episode)
        }.onFailure {
            timber.log.Timber.w(it, "[SEZONA] Trakt fajfka dílu selhala (%s S%02dE%02d)", imdb, season, episode)
        }.isSuccess
    }

    /**
     * user 2026-07-28 („budu potřebovat něco jako označit řady a díly jako zhlédnuté") — označ/odznač
     * CELOU SEZÓNU. Bez toho je dohánění rozkoukaného seriálu klikání po jednom dílu.
     * Zapisuje se do Jellyfinu ([JellyfinLibraryService.markPlayed]) po epizodách, protože přesně ty
     * máme namapované na id ([DetailUiState.episodeJellyfinIds]); epizoda mimo knihovnu se přeskočí.
     */
    fun markSeasonWatched(season: Int, watched: Boolean) {
        val ids = _uiState.value.episodeJellyfinIds.filterKeys { it.first == season }
        if (ids.isEmpty()) {
            // SEZONA f3b: seriál MIMO knihovnu — celou sezónu zapíšeme na Trakt jedním voláním
            // (dřív se tu jen tiše vyskočilo a tlačítko „označit sezónu" nedělalo nic).
            markSeasonWatchedOnTrakt(season, watched)
            return
        }
        viewModelScope.launch {
            val done = mutableSetOf<Pair<Int, Int>>()
            for ((key, jfId) in ids) {
                if (jellyfinLibraryService.markPlayed(jfId, watched)) done += key
            }
            if (done.isEmpty()) return@launch
            _uiState.update { st ->
                val w = st.episodeWatched.toMutableSet()
                if (watched) w.addAll(done) else w.removeAll(done)
                st.copy(episodeWatched = w)
            }
            timber.log.Timber.i("[CURTAIN] sezóna %d: %d epizod → zhlédnuto=%b", season, done.size, watched)
        }
    }

    /**
     * CURTAIN (SHW-109): po návratu z přehrávače přenačti per-epizoda stav z Jellyfinu (fajfka + posun
     * odznaku „Pokračovat"). Nutné zvlášť: `load()` má na tentýž titul early-return a ViewModel žije dál
     * (Activity scope), takže by se dokoukaná epizoda projevila až po restartu appky.
     * Sezónu ZÁMĚRNĚ nepřepínáme (uživatel se dívá na tu svou), měníme jen příznaky.
     */
    fun refreshEpisodeStatus() {
        val item = _uiState.value.item ?: return
        if (item.type != MediaType.SHOW) return
        val jfId = _uiState.value.ownedJellyfinId
        if (jfId == null) {
            // SEZONA (SHW-113): seriál mimo knihovnu → přenačti fajfky z Traktu (dokoukaný díl se tam
            // zapsal ze streamu, vc126). Bez tohohle by se návrat z přehrávače u RD seriálu neprojevil.
            viewModelScope.launch { loadTraktEpisodeProgress(item, fresh = true) }
            return
        }
        viewModelScope.launch {
            val status = runCatching { jellyfinLibraryService.getSeriesEpisodeStatus(jfId) }.getOrNull() ?: return@launch
            if (_uiState.value.item?.isSameAs(item) != true) return@launch
            _uiState.update {
                it.copy(
                    episodeWatched = status.watched,
                    episodeProgress = status.progress,
                    nextUpEpisode = status.nextUp,
                    episodeJellyfinIds = status.episodeIds,
                )
            }
        }
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
        // LAPIDARY (SHW-96): přidání filmu do Oblíbených = vědomý signál → nacachuj zdroj na pozadí.
        if (now && item.type == MediaType.MOVIE) {
            // Zruš případný náhrobek z dřívějšího odebrání (viz forgetWorkingSource) — jinak čerstvě
            // dohledaný zdroj nepřežije nejbližší sync push.
            workingSourceStore.clearTombstoneFor(item.imdbId, item.tmdbId)
            viewModelScope.launch {
                workingSourceStore.triggerAutoCache(
                    item.imdbId, item.tmdbId, _uiState.value.tmdbCzTitle ?: item.title, item.year, effectiveCachePolicy(item.imdbId),
                )
            }
        }
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
                // BUG (2026-07-16): dřív `discoverMoviesByPerson` = TMDB with_people (cast+crew dohromady)
                // → do „Od stejného režiséra" prosakovaly filmy, kde ten člověk NEBYL režisér (jen herec/
                // producent). Role-specific `moviesByPersonRole(DIRECTING)` bere z person/movie_credits jen
                // crew s job Director (stejné pravidlo jako person sheet). Padne na TV i telefon.
                val movies = tmdbApi.moviesByPersonRole(director.id, PersonRole.DIRECTING)
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

    private suspend fun moviesToCollection(
        name: String,
        moviesRaw: List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem>,
        excludeTmdbId: Long,
        limit: Int = 20,
    ): MediaCollection? {
        // COUCH (SHW-88): věkový filtr dětského profilu i na sekcích režisér/studio (dřív se sem gate nedostal
        // → user je musel na deti vypínat). JF knihovna zůstává mimo (tohle jsou TMDB návrhy, ne knihovna).
        val movies = ageFilterMovies(moviesRaw)
        // COUCH (SHW-88): řazení + filtr „jen vydané" pro sekce režisér/studio (KOLEKCE má vlastní cestu).
        val releasedOnly = prefs.getBoolean("detail_section_released_only", false)
        val today = java.time.LocalDate.now().toString()   // "YYYY-MM-DD" — ISO datum jde porovnat lexikograficky
        val sorted = when (readSectionSort()) {
            com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.RATING -> movies.sortedByDescending { it.vote_average ?: 0f }
            com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.RECENT,
            com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.YEAR_DESC -> movies.sortedByDescending { it.release_date ?: "" }
            com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.ALPHA -> movies.sortedBy { (it.title ?: "").lowercase() }
            com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.RANDOM -> movies.shuffled()
            com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.DEFAULT -> movies
        }
        val parts = sorted
            .filter { it.id != excludeTmdbId && !it.poster_path.isNullOrBlank() }
            .filter { !releasedOnly || (it.release_date?.let { d -> d.isNotBlank() && d <= today } == true) }
            .take(limit)
            .map { m ->
                CollectionPart(
                    key = "tmdb_${m.id}",
                    tmdbId = m.id,
                    jellyfinId = _uiState.value.ownedTmdbToJellyfin[m.id],
                    title = m.title ?: "",
                    posterUrl = m.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                    backdropUrl = m.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" },
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

    /**
     * COUCH (SHW-88): odfiltruj z TMDB sekcí (režisér/studio) tituly nad věkovým stropem dětského profilu.
     * Cap null (dospělý) → beze změny. Certifikace se tahá per titul jen když je strop aktivní (paralelně).
     */
    private suspend fun ageFilterMovies(
        movies: List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem>,
    ): List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSearchMovieItem> {
        val cap = parentalControls.profile.value.effectiveAgeCap ?: return movies
        val hideUnrated = parentalControls.profile.value.hideUnratedForAge
        return coroutineScope {
            movies.map { m ->
                async {
                    val certAge = runCatching { tmdbApi.fetchMovieCertificationAge(m.id) }.getOrNull()
                    val probe = MediaItem(
                        traktId = 0L, tmdbId = m.id, imdbId = null, title = m.title ?: "", year = null,
                        overview = null, rating = null,
                        genres = com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres.names(m.genre_ids, isShow = false),
                        type = MediaType.MOVIE, certificationAge = certAge,
                    )
                    m.takeIf { com.github.jankoran90.showlyfin.core.domain.ContentAgeGate.isAllowed(cap, probe, hideUnrated) }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Styl karet sekcí detailu z prefs (uloženo jako enum name; neznámé/žádné → POSTER). */
    private fun readSectionStyle(): HomeCardStyle =
        prefs.getString("detail_section_style", null)
            ?.let { runCatching { HomeCardStyle.valueOf(it) }.getOrNull() }
            ?: HomeCardStyle.POSTER

    /** COUCH (SHW-88): řazení sekcí režisér/studio z prefs (neznámé/žádné → DEFAULT = pořadí z API). */
    private fun readSectionSort(): com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort =
        prefs.getString("detail_section_sort", null)
            ?.let { runCatching { com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.valueOf(it) }.getOrNull() }
            ?: com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort.DEFAULT

    /** TV DETAIL REDESIGN (OTA 299): rozvržení TV detailu z prefs (neznámé/žádné → IMMERSIVE_OVERLAY). */
    private fun readTvDetailLayout(): TvDetailLayout =
        prefs.getString("detail_tv_layout", null)
            ?.let { runCatching { TvDetailLayout.valueOf(it) }.getOrNull() }
            ?: TvDetailLayout.IMMERSIVE_OVERLAY

    /** TV DETAIL REDESIGN (OTA 299): umístění akčních tlačítek z prefs (neznámé/žádné → BELOW_PLOT). */
    private fun readActionsPlacement(): DetailActionsPlacement =
        prefs.getString("detail_actions_placement", null)
            ?.let { runCatching { DetailActionsPlacement.valueOf(it) }.getOrNull() }
            ?: DetailActionsPlacement.BELOW_PLOT

    private suspend fun loadWatchlistMembership(item: MediaItem) {
        if (tokenProvider.getToken() == null) {
            // SEZONA f3k — profil bez Traktu (dětský) má „Chci vidět" LOKÁLNĚ. Bez tohohle by fajfka
            // po návratu na detail zmizela, i když je titul v seznamu.
            val tmdb = item.tmdbId ?: return
            val inLocal = wantToSee.isWanted(tmdb, isShow = item.type == MediaType.SHOW)
            _uiState.update { it.copy(isInWatchlist = inLocal) }
            return
        }
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
        // BUG „Chci vidět bez fajfky" (2026-07-14): dřív tiché `return` bez zpětné vazby → user nevěděl,
        // proč fajfka nenaskočí. Teď: viditelná hláška (Toast) při každém blokujícím stavu + OPTIMISTICKÝ
        // překlop (fajfka hned) s revertem při selhání Trakt POSTu.
        // SEZONA f3k (user 2026-08-02 13:27): profil BEZ Traktu (dětský) si „Chci vidět" vede lokálně —
        // dřív tu jen vyskočila hláška „přihlas se k Traktu", což dítě nemá jak udělat, takže si o film
        // říkalo přes Oblíbené a ty se staly náhradním watchlistem. Lokální seznam umí i SERIÁLY.
        if (tokenProvider.getToken() == null) {
            toggleLocalWantToSee(item)
            return
        }
        if (_uiState.value.isTogglingWatchlist) return
        // WINNOW (SHW-41): nesmí padnout na traktId==0 — tituly z pásu „od stejného režiséra/studia"
        // nesou jen tmdbId. Sestavíme položku z čehokoli, co máme (trakt/tmdb/imdb); Trakt to přijme.
        val exportItem = SyncExportItem.fromIds(item.traktId, item.tmdbId, item.imdbId)
        if (exportItem == null) {
            timber.log.Timber.w("[Watchlist] toggle: žádné použitelné id (trakt/tmdb/imdb) pro '${item.title}'")
            _uiState.update { it.copy(autoCastMessage = "Film zatím nemá ID pro Trakt seznam — zkus to za chvíli.") }
            return
        }
        val currentlyIn = _uiState.value.isInWatchlist
        // Optimistický překlop: fajfka naskočí okamžitě, backend doběhne na pozadí.
        _uiState.update { it.copy(isTogglingWatchlist = true, isInWatchlist = !currentlyIn) }
        viewModelScope.launch {
            val request = if (item.type == MediaType.MOVIE) {
                SyncExportRequest(movies = listOf(exportItem))
            } else {
                SyncExportRequest(shows = listOf(exportItem))
            }
            val result = runCatching {
                if (currentlyIn) authorizedTrakt.postDeleteWatchlist(request)
                else authorizedTrakt.postSyncWatchlist(request)
            }
            val ok = result.isSuccess
            // Diagnostika (2026-07-14): při selhání ukázat SKUTEČNÝ důvod — HttpException.message nese
            // „HTTP <kód>…" (420=plný watchlist/limit, 401=token, 429=rate limit), IOException=síť.
            val reason = result.exceptionOrNull()?.let { e ->
                timber.log.Timber.w(e, "[Watchlist] POST selhal (currentlyIn=$currentlyIn) pro '${item.title}'")
                e.message?.take(90) ?: e.javaClass.simpleName
            }
            // 🐞 Trakt 401 self-heal: TraktAuthenticator při 401 zkusí obnovit token; když je refresh_token
            // definitivně mrtvý (Trakt neaktivní ~3 měsíce), zavolá revokeToken() → getToken()==null =
            // odhlášení. Matoucí syrové „HTTP 401" nahraď jasnou výzvou k re-loginu (ne „zkus znovu" —
            // opakování bez přihlášení nepomůže). Ostatní chyby (429/420/síť) zůstávají „zkus znovu".
            val loggedOut = tokenProvider.getToken() == null
            val authExpired = loggedOut || reason?.contains("401") == true
            _uiState.update {
                it.copy(
                    isTogglingWatchlist = false,
                    // Při selhání revert na původní stav + hláška; při úspěchu ponech optimistický.
                    isInWatchlist = if (ok) !currentlyIn else currentlyIn,
                    autoCastMessage = when {
                        ok -> it.autoCastMessage
                        authExpired -> "Trakt přihlášení vypršelo — titul se neuložil. Přihlas se znovu: " +
                            "Nastavení → Účty → Trakt."
                        else -> "Trakt seznam se neuložil: ${reason ?: "neznámá chyba"}. Zkus znovu."
                    },
                )
            }
            // SEZONA f3k (user 2026-08-03: *„2) b)"*) — i s Traktem se píše do MÍSTNÍHO seznamu.
            // Trakt zůstává hlavní, tohle je jeho zrcadlo: „Chci vidět" pak funguje i když Trakt mlčí
            // (vypršelý token, výpadek) a na všech profilech to je jedna a táž mechanika.
            if (ok) mirrorWantToSeeLocally(item, add = !currentlyIn)
            if (ok && !currentlyIn) triggerWantSourceSearch(item)
            // COUCH: watchlist se změnil → domov přenačte Trakt řady (jinak čerstvý titul naskočí jen v sekci Trakt).
            if (ok) traktSyncSignal.bump()
        }
    }

    /**
     * „Chci vidět" = PŘÍKAZ NAJÍT ZDROJ (LAPIDARY SHW-96, u seriálů SEZONA f3). Sdílené pro Trakt
     * i lokální seznam — jinak by dětský profil sice titul přidal, ale nikdo by mu zdroj nehledal.
     */
    private suspend fun triggerWantSourceSearch(item: MediaItem) {
        markAutoSearching()
        // Přidání do „Chci vidět" je vědomý signál „chci ho zpátky" — zruš případný náhrobek
        // z dřívějšího odebrání, ať čerstvě dohledaný zdroj přežije nejbližší sync (viz forgetWorkingSource).
        workingSourceStore.clearTombstoneFor(item.imdbId, item.tmdbId)
        if (item.type == MediaType.MOVIE) {
            workingSourceStore.triggerAutoCache(
                item.imdbId, item.tmdbId, _uiState.value.tmdbCzTitle ?: item.title, item.year, effectiveCachePolicy(item.imdbId),
            )
            return
        }
        // SERIÁL: hledá se zdroj pro CELOU SEZÓNU (přednostně nacachovaný balík) — jeden release na
        // sezónu, protože jiný release u každého dílu = pokaždé jiné titulky a znovu ladit timing.
        // Sezóna = ta právě vybraná, jinak první.
        val season = _uiState.value.selectedSeason
            ?: _uiState.value.seasons.firstOrNull { s -> s.season_number >= 1 }?.season_number
            ?: 1
        workingSourceStore.triggerSeasonCache(
            item.imdbId, item.tmdbId, _uiState.value.tmdbCzTitle ?: item.title,
            item.year, effectiveCachePolicy(item.imdbId), season,
        )
        // SEZONA f3b: otevřená sezóna se hledá HNED (výše), ZBYTEK seriálu jde do persistentní fronty
        // s retry — jinak by druhá a další sezóna zůstaly bez zdroje, dokud si je někdo ručně neotevře.
        runCatching {
            workingSourceStore.cacheBatch(
                listOf(
                    com.github.jankoran90.showlyfin.data.uploader.BackfillItem(
                        imdb = item.imdbId.orEmpty(),
                        tmdb = item.tmdbId ?: 0L,
                        title = _uiState.value.tmdbCzTitle ?: item.title,
                        year = item.year,
                        kind = "show",
                    ),
                ),
                effectiveCachePolicy(item.imdbId),
            )
        }.onFailure { timber.log.Timber.w(it, "[SEZONA] fronta pro zbylé sezóny selhala") }
    }

    /** Zrcadlo Trakt „Chci vidět" do místního seznamu (aby fungoval i bez Traktu). Bez tmdb id nic. */
    private fun mirrorWantToSeeLocally(item: MediaItem, add: Boolean) {
        val tmdb = item.tmdbId?.takeIf { it > 0L } ?: return
        val isShow = item.type == MediaType.SHOW
        if (!add) {
            wantToSee.remove(tmdb, isShow)
            return
        }
        val raw = _uiState.value.movieDetails?.poster_path ?: item.posterPath
        val poster = raw?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w185$it" }
        wantToSee.add(tmdb, isShow, _uiState.value.tmdbCzTitle ?: item.title, poster, item.year)
    }

    /**
     * SEZONA f3k — „Chci vidět" pro profil BEZ Traktu (dětský). Lokální, per profil, synchronizovaný
     * přes tabulku oblíbených; přidání je zároveň PŘÍKAZ najít zdroj. Do Filmotéky se titul dostane
     * až se zdrojem (volba „Jen s dohledaným zdrojem", u dětí zapnutá).
     * 🔴 Bez `tmdbId` to nemá identitu — a hádat ji podle názvu je přesně ta cesta, kterou už jednou
     * vznikla cizí karta ve Filmotéce. Radši nic než špatně.
     */
    private fun toggleLocalWantToSee(item: MediaItem) {
        val tmdb = item.tmdbId
        if (tmdb == null || tmdb <= 0L) {
            _uiState.update { it.copy(autoCastMessage = "Titul zatím nemá ID — zkus to za chvíli.") }
            return
        }
        val isShow = item.type == MediaType.SHOW
        val raw = _uiState.value.movieDetails?.poster_path ?: item.posterPath
        val poster = raw?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w185$it" }
        val now = wantToSee.toggle(
            tmdbId = tmdb,
            isShow = isShow,
            name = _uiState.value.tmdbCzTitle ?: item.title,
            posterUrl = poster,
            year = item.year,
        )
        _uiState.update {
            it.copy(
                isInWatchlist = now,
                autoCastMessage = if (now) "Přidáno do „Chci vidět\" — hledám zdroj." else it.autoCastMessage,
            )
        }
        if (now) viewModelScope.launch { triggerWantSourceSearch(item) }
    }

    /** LAPIDARY (SHW-96): politika výběru zdroje dle aktivního profilu — dětský (CHILDREN/FAMILY) chce
     *  CZ dabing + 5.1 + sdilej.cz, jinak originál zvuk. */
    private fun cachePolicy(): String =
        when (parentalControls.profile.value.effectiveAgeRating) {
            com.github.jankoran90.showlyfin.core.domain.AgeRating.CHILDREN,
            com.github.jankoran90.showlyfin.core.domain.AgeRating.FAMILY -> "child"
            else -> "original"
        }

    /** SEZONA f2: je aktivní profil dětský? Určuje VÝCHOZÍ zvukovou stopu (dětský CZ, dospělý originál).
     * ZÁMĚRNĚ jen věk profilu — per-titul CZ přání ([effectiveCachePolicy]) do tohohle NEVSTUPUJE,
     * jinak by zapnutí u jednoho filmu tiše přepnulo výchozí zvuk pro celý zbytek appky. */
    private fun isChildProfile(): Boolean = cachePolicy() == "child"

    /** User 2026-08-18 (Harry Potter 20 let → Splitsville, sloučeno po zpětné vazbě usera „obojí se
     * týká jen CZ dabingu") — pro AUTO-HLEDÁNÍ na pozadí (na rozdíl od [cachePolicy] samotné) navíc
     * zohlední PER-TITUL přebití (`ProfileConfig.titleAudioChoice`, stejné pole jako [audioChoice]):
     * "CZ" dá přednost sdilej.cz/CZ zvuku jako dětský profil; "ORIGINAL"/chybí = beze změny (dospělý
     * profil hledá originálem stejně, tam není co přebíjet). Dětský profil je VŽDY CZ-first,
     * nepřebitelné — bezpečnostní záměr, ne jen defaultní chování. */
    private fun effectiveCachePolicy(imdb: String?): String {
        val base = cachePolicy()
        if (base == "child" || imdb == null) return base
        return if (profileRepository.activeConfig.value.titleAudioChoice[imdb] == "CZ") "child" else base
    }

    /** Znovu nastartuje auto-hledání s AKTUÁLNÍ efektivní politikou pro tenhle titul — volá se po
     * každém přepnutí [cycleTitleAudioOverride], ať se projeví hned, ne až při dalším nesouvisejícím
     * signálu (přidání do Chci vidět apod.). */
    private suspend fun retriggerSourceSearch(item: MediaItem, imdb: String) {
        val title = _uiState.value.tmdbCzTitle ?: item.title
        if (item.type == MediaType.SHOW) {
            val season = _uiState.value.selectedSeason
                ?: _uiState.value.seasons.firstOrNull { s -> s.season_number >= 1 }?.season_number
                ?: 1
            runCatching {
                workingSourceStore.triggerSeasonCache(
                    item.imdbId, item.tmdbId, title, item.year, effectiveCachePolicy(imdb), season,
                )
            }
        } else {
            runCatching {
                workingSourceStore.triggerAutoCache(
                    item.imdbId, item.tmdbId, title, item.year, effectiveCachePolicy(imdb),
                )
            }
        }
    }

    /** SEZONA f2: platná volba stopy = PER-TITUL přebití ([titleAudioOverride], user 2026-08-18
     * Splitsville), jinak chip profilu, jinak výchozí podle věku. Používá se pro SKUTEČNÝ výběr
     * stopy při přehrání ([publishPreferredAudioLanguages]) — přebití tedy reálně řídí přehrávač,
     * ne jen popisek v menu. */
    // imdb SCHVÁLNĚ jako parametr, ne čtení z `_uiState.value.item` — v `load()` se volá UVNITŘ
    // `_uiState.update { it.copy(...) }`, kde `_uiState.value` ještě drží STARÝ (nebo null) titul.
    private fun audioChoice(imdb: String?): com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice {
        titleAudioOverrideChoice(imdb)?.let { return it }
        return audioPathStore.effective(isChildProfile(), profileRepository.activeConfig.value.audioChoice)
    }

    private fun titleAudioOverrideChoice(imdb: String?): com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice? {
        if (imdb == null) return null
        return when (profileRepository.activeConfig.value.titleAudioChoice[imdb]) {
            "CZ" -> com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.CZ
            "ORIGINAL" -> com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.ORIGINAL
            else -> null
        }
    }

    /**
     * User 2026-08-18 (Splitsville: „tady ten film má být v originále") — PER-TITUL přebití
     * profilového jazykového chipu. Cyklus Profil → CZ dabing → Originál → Profil…, uložené do
     * `ProfileConfig.titleAudioChoice` (synced appka↔web). „Profil" = smaž přebití, dál se řídí
     * profilovým výchozím (`AudioPathStore`, nastavuje se teď v Nastavení → Obraz a zvuk).
     */
    fun cycleTitleAudioOverride() {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId ?: return
        val profileId = profileRepository.activeProfile.value?.id ?: return
        val current = profileRepository.activeConfig.value.titleAudioChoice[imdb]
        val next = when (current) {
            null -> "CZ"
            "CZ" -> "ORIGINAL"
            else -> null
        }
        _uiState.update { it.copy(titleAudioOverride = next) }
        viewModelScope.launch {
            profileRepository.updateConfig(profileId) { cfg ->
                cfg.copy(
                    titleAudioChoice = if (next == null) cfg.titleAudioChoice - imdb
                    else cfg.titleAudioChoice + (imdb to next),
                )
            }
            publishPreferredAudioLanguages()
            // sloučeno s dřívějším samostatným "Hledat zdroje" přepínačem (user 2026-08-18: „obojí
            // se týká jen CZ dabingu") — jeden klik teď řídí OBOJÍ, hledání i výběr stopy.
            retriggerSourceSearch(item, imdb)
        }
    }

    /**
     * SEZONA f2 — řekni přehrávači, JAKÝ JAZYK chce divák slyšet.
     * 🔴 Bez tohohle bral přehrávač prostě první stopu v pořadí — u Breaking Bad německou, protože
     * „originál" v souboru označený není a zařízení má české locale, které se netrefí (user 16:44:
     * „breaking bad není německy seriál"). Originál se pozná až podle TMDB `original_language`.
     * Předává se přes sdílené `traktPreferences`, které přehrávač už čte — bez zásahu do navigace.
     */
    private fun publishPreferredAudioLanguages() {
        val st = _uiState.value
        val orig = st.movieDetails?.original_language ?: st.showDetails?.original_language
        val langs = com.github.jankoran90.showlyfin.data.uploader.AudioPathStore
            .languagesFor(audioChoice(st.item?.imdbId), orig)
        prefs.edit().putString(
            com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.PREF_PREFERRED_AUDIO_LANGS,
            langs.joinToString(","),
        ).apply()
        timber.log.Timber.i("[SEZONA] preferovaný zvuk: %s (originál titulu=%s)", langs.take(3), orig ?: "?")
    }

    // ── Stream / Stáhnout (Stremio + Sdílej.cz + Smart Remux hub) ──────────────

    private fun mediaTypeStr(item: MediaItem) = if (item.type == MediaType.MOVIE) "movie" else "series"

    // ── TENFOOT WS-C (SHW-87): sezóny / epizody seriálu ──────────────────────────
    /** Vybraná epizoda pro stream flow — season/episode se propíšou do dotazů zdrojů i titulků. */
    private data class EpisodeSelector(val season: Int, val episode: Int, val label: String)
    private var episodeSelector: EpisodeSelector? = null

    /** WS-C: vyber sezónu → lazy-load seznamu epizod z TMDB (jen pokud se změnila / je prázdný). */
    fun selectSeason(seasonNumber: Int) {
        val item = _uiState.value.item ?: return
        val tmdbId = item.tmdbId ?: return
        if (_uiState.value.selectedSeason == seasonNumber && _uiState.value.seasonEpisodes.isNotEmpty()) return
        _uiState.update { it.copy(selectedSeason = seasonNumber, isLoadingEpisodes = true, seasonEpisodes = emptyList()) }
        viewModelScope.launch {
            val details = tmdbApi.fetchSeason(tmdbId, seasonNumber)
            _uiState.update { st ->
                if (st.item?.isSameAs(item) != true || st.selectedSeason != seasonNumber) st
                else st.copy(seasonEpisodes = details?.episodes.orEmpty(), isLoadingEpisodes = false)
            }
        }
    }

    /** WS-C: přehraj epizodu seriálu přes stream flow (uploader query nese season/episode). */
    fun playEpisode(season: Int, episode: Int, episodeTitle: String?) {
        val base = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: _uiState.value.item?.title.orEmpty()
        val label = buildString {
            append(base); append(" S"); append(season); append("E"); append(episode)
            episodeTitle?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
        }
        val sel = EpisodeSelector(season, episode, label)
        episodeSelector = sel
        // SEZONA (SHW-113): zapamatovaný zdroj patří DÍLU, ne seriálu. Bez tohohle přepnutí by picker
        // připnul zdroj z posledního otevřeného dílu (dřív dokonce z celého titulu → S1E1 svítilo u S3E7).
        val item = _uiState.value.item
        val remembered = workingSourceStore.get(item?.imdbId, item?.tmdbId, season, episode)?.stream
        val hasSeason = workingSourceStore.getSeason(item?.imdbId, item?.tmdbId, season) != null
        _uiState.update { it.copy(rememberedSource = remembered, hasSeasonSource = hasSeason) }
        // SEZONA f2: zdroj dílu > zdroj sezóny > picker. Vlastní volba u konkrétního dílu má přednost
        // (divák ji udělal vědomě a později), receptura sezóny je záchrana pro díly, kde nic není.
        if (remembered != null && com.github.jankoran90.showlyfin.data.uploader.SeasonSourceMatcher.playsNow(remembered)) {
            playStream(remembered)
            return
        }
        if (hasSeason) {
            // SEZONA f3d: dohledání trvá i deset vteřin (dotaz na addony) a dosud se přitom NIC neukázalo
            // — user 2026-08-01: „asi 10 vteřin se nic neděje, nic nikde neběží". `isLoadingStreams` svítí
            // jen uvnitř pickeru, který je tu zavřený, takže sáhneme po toastu.
            _uiState.update { it.copy(isLoadingStreams = true, autoAdvanceInfo = "Hledám zdroj sezóny…") }
            viewModelScope.launch {
                if (tryPlaySeasonSource(sel)) return@launch
                // Receptura na tenhle díl nesedla (jiná kvalita/necachováno) → normální cesta,
                // ať divák vidí, co je k dispozici, místo tichého selhání.
                timber.log.Timber.i("[SEZONA] zdroj sezóny na S%dE%d nesedl → otevírám výběr", season, episode)
                _uiState.update { it.copy(isLoadingStreams = false) }
                openStreamPathChooser()
            }
            return
        }
        openStreamPathChooser()
    }

    /** CONDUIT (SHW-58): ▶ Přehrát — nejdřív rozcestník CZ dabing / Originál, pak filtrovaný picker. */
    fun openStreamPathChooser() {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId
        if (imdb.isNullOrBlank() || uploaderBaseUrl.isBlank()) {
            timber.log.Timber.w("[CONDUIT] chooser blocked: imdbBlank=${imdb.isNullOrBlank()} baseUrlBlank=${uploaderBaseUrl.isBlank()}")
            _uiState.update { it.copy(showStreamPicker = true, streamAudioPath = null, streamError = "Uploader není nastaven nebo film nemá IMDB ID.") }
            return
        }
        // SEZONA f2 — 🔴 OPRAVA po zpětné vazbě (user 2026-08-01 17:24 se screenshotem: „co ten originál
        // a český dabing chip dělá? Stejně vyskakuje výběr na hledání zdroje česky/originál"). Chip cestu
        // původně jen PŘEDVYBÍRAL, ale rozcestník se pořád ptal — tedy na otázku, kterou divák už zodpověděl.
        // Teď se rozcestník PŘESKOČÍ a jde se rovnou na zdroje ve zvolené stopě; jednorázová výjimka
        // zůstává dostupná tlačítkem „← Změnit dabing / originál" přímo v seznamu.
        val path = when (audioChoice(imdb)) {
            com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.CZ -> StreamAudioPath.CZ_DUB
            com.github.jankoran90.showlyfin.data.uploader.AudioPathStore.Choice.ORIGINAL -> StreamAudioPath.ORIGINAL
        }
        _uiState.update { it.copy(showStreamPathChooser = false, showStreamPicker = true, streamAudioPath = path) }
        loadStreams()
    }

    /** CONDUIT: zvolená cesta (audio) → zavři rozcestník, otevři filtrovaný stream picker. */
    fun chooseStreamPath(path: StreamAudioPath) {
        _uiState.update { it.copy(streamAudioPath = path, showStreamPathChooser = false, showStreamPicker = true) }
    }

    /** CONDUIT: zpět z pickeru na rozcestník cest (změna dabing/originál). */
    fun backToStreamPathChooser() {
        _uiState.update { it.copy(showStreamPicker = false, showStreamPathChooser = true) }
    }

    fun dismissStreamPathChooser() = _uiState.update { it.copy(showStreamPathChooser = false) }

    /** ▶ Stream — otevře picker se Stremio streamy (jen přehrávání). */
    fun openStreamPicker() {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId
        if (imdb.isNullOrBlank() || uploaderBaseUrl.isBlank()) {
            timber.log.Timber.w("[Stremio] picker blocked: imdbBlank=${imdb.isNullOrBlank()} baseUrlBlank=${uploaderBaseUrl.isBlank()} tmdb=${item.tmdbId} title='${item.title}'")
            _uiState.update { it.copy(showStreamPicker = true, streamError = "Uploader není nastaven nebo film nemá IMDB ID.") }
            return
        }
        // CONDUIT: přímé otevření (REPRISE „zkusit jiný zdroj") → cesta null = ukázat všechny zdroje.
        _uiState.update { it.copy(showStreamPicker = true, streamAudioPath = null) }
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
        // TENFOOT WS-C: pro epizodu seriálu předej season/episode do všech dotazů (uploader je podporuje).
        val epSeason = episodeSelector?.season
        val epEpisode = episodeSelector?.episode
        // QUARRY (SHW-79): předvyplnění ruční úpravy hledání na Sdílej.cz i pro play cestu.
        val sdilejDefault = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() }
            ?: item.titleCz?.takeIf { it.isNotBlank() }
            ?: _uiState.value.csfdTitle?.takeIf { it.isNotBlank() }
            ?: item.title
        _uiState.update {
            it.copy(
                isLoadingStreams = true, streamError = null, streams = emptyList(),
                sdilejDefaultTitle = sdilejDefault, sdilejDefaultYear = item.year,
            )
        }
        viewModelScope.launch {
            // RD-first režim (DebridSearch) z prefs: off | hash (server-side v /streams) | search | both.
            val rdMode = runCatching { uploaderDs.getStreamFilter(uploaderBaseUrl, uploaderCookie).rdFirstMode }.getOrDefault("both")
            // BACKLOG link mode: na mobilních datech (away) řekni serveru, ať preferuje nejmenší zdroje.
            val linkHint = if (com.github.jankoran90.showlyfin.core.network.LinkModePrefs.effectiveMode(prefs, connectivity.currentLinkKind()) == com.github.jankoran90.showlyfin.core.network.LinkMode.AWAY) "away" else null
            // DebridSearch dle názvu (search/both) — prohledá RD účet i mimo addon výsledky, paralelně.
            val savedDeferred: kotlinx.coroutines.Deferred<List<UploaderStream>>? =
                if (rdMode == "search" || rdMode == "both") {
                    async { runCatching { uploaderDs.rdSearch(uploaderBaseUrl, uploaderCookie, item.title, item.year) }.getOrDefault(emptyList()) }
                } else null
            // CONDUIT (SHW-58): české úložiště (sdílej.cz) paralelně — sloučí se do seznamu, do cesty
            // CZ dabing / Originál se rozřadí dle audia (isCzDub) až v UI filtru. Hraje přes náš proxy.
            val sdilejDeferred = async {
                // QUARRY (SHW-79): rok z metadat bývá o rok mimo → při nule zkus ±1 (dle prefs).
                // PASSPORT (SHW-93) A2 — originální/romanizovaný název jako další kandidát (asijské klenoty).
                sdilejStreamsWithRetry(mediaTypeStr(item), imdb, item.title, _uiState.value.tmdbCzTitle ?: item.title, item.year, epSeason, epEpisode, origTitle = item.originalTitle.orEmpty())
            }
            // Backend vrací už seřazené (rdSaved → cached → CZ/SK → fallbackOrder) a ořezané dle prefs.
            runCatching { uploaderDs.getStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb, season = epSeason, episode = epEpisode, strict = strict, link = linkHint) }
                .onSuccess { list ->
                    val saved = savedDeferred?.await().orEmpty()
                    val sdilej = sdilejDeferred.await()
                    val savedHashes = saved.mapNotNull { it.infoHash?.lowercase() }.toSet()
                    val combined = saved + list.filterNot { (it.infoHash?.lowercase() ?: "") in savedHashes }
                    timber.log.Timber.i("[Stremio] streams=${list.size} rdSearch=${saved.size} sdilej=${sdilej.size} strict=$strict (cached=${list.count { it.quality.rdReady }} dl=${list.count { it.quality.rdDownloadable }}) imdb=$imdb")
                    // Plan CASCADE Fáze 3: během probu ukaž JEN ověřené instant (rdSaved/rdReady) + sdílej
                    // (hraje přes proxy hned), zbytek se reálně testuje (addMagnet) → po probu nahradíme.
                    val instantNow = combined.filter { it.quality.rdSaved || it.quality.rdReady } + sdilej
                    _uiState.update { it.copy(isLoadingStreams = false, isProbingStreams = true, streams = streamPresetStore.orderStreams(instantNow), streamError = null) }
                    viewModelScope.launch {
                        runCatching { uploaderDs.getProbedStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb, season = epSeason, episode = epEpisode) }
                            .onSuccess { probed ->
                                timber.log.Timber.i("[Stremio] probe → ${probed.size} smysluplných (instant=${probed.count { it.quality.rdSaved || it.quality.rdReady }} dl=${probed.count { it.quality.rdDownloadable }})")
                                // CONDUIT: sdílej (instant, přes proxy) drž v seznamu i po probu — probe vrací jen torrent/RD.
                                val finalList = (if (probed.isNotEmpty()) probed else combined) + sdilej
                                val err = if (finalList.isEmpty()) "Žádný funkční zdroj nenalezen." else null
                                _uiState.update { it.copy(isProbingStreams = false, streams = streamPresetStore.orderStreams(finalList), streamError = err) }
                            }
                            .onFailure { e ->
                                timber.log.Timber.w(e, "[Stremio] probe FAILED imdb=$imdb → fallback na neprobnuty seznam")
                                val fb = combined + sdilej
                                val err = if (fb.isEmpty()) "Žádné streamy nenalezeny." else null
                                _uiState.update { it.copy(isProbingStreams = false, streams = streamPresetStore.orderStreams(fb), streamError = err) }
                            }
                    }
                }
                .onFailure { e ->
                    timber.log.Timber.w(e, "[Stremio] getStreams FAILED imdb=$imdb url=$uploaderBaseUrl")
                    val saved = savedDeferred?.await().orEmpty()
                    val sdilej = sdilejDeferred.await()
                    val fb = saved + sdilej
                    if (fb.isNotEmpty()) _uiState.update { it.copy(isLoadingStreams = false, streams = streamPresetStore.orderStreams(fb), streamError = null) }
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
        // SEZONA: u epizody ukládej pod identitu DÍLU (jinak by zdroj přebil ostatní díly seriálu).
        workingSourceStore.save(
            imdb, st.item?.tmdbId, title, stream,
            season = episodeSelector?.season, episode = episodeSelector?.episode,
        )
        rememberSeasonRecipeFrom(stream)
        _uiState.update { it.copy(rememberedSource = stream, pendingWorkingConfirm = null) }
        cleanupRdKeepingSource(stream)
    }

    fun openManualUrlDialog() = _uiState.update { it.copy(showManualUrlDialog = true) }
    fun dismissManualUrlDialog() = _uiState.update { it.copy(showManualUrlDialog = false) }

    /**
     * User 2026-08-18 (Harry Potter 20 let): auto-hledání někdy netrefí přesně tu CZ dabovanou
     * verzi, kterou si user sám ověří na sdilej.cz — ruční vložení odkazu STEJNOU cestou jako
     * u jiných zapamatovaných zdrojů (parita s webem, viz `episodes.js` `vlozitVlastniZdroj`).
     * sdilej.cz odkaz se převede na naši `sdilej://` proxy schéma, jiná přímá URL se uloží beze
     * změny (backend `_resolve_stream_obj` nechá „obyčejnou" https url beze změny projít).
     */
    fun saveManualSource(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) return
        val item = _uiState.value.item ?: return
        val sdilejMatch = Regex("""^https?://sdilej\.cz/(\d+)/([^?#]+)""", RegexOption.IGNORE_CASE).find(url)
        val (parsedUrl, name) = if (sdilejMatch != null) {
            val (fileId, slug) = sdilejMatch.destructured
            "sdilej://$fileId/$slug" to slug
        } else if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url to (url.substringAfterLast('/').ifBlank { url })
        } else {
            _uiState.update { it.copy(streamError = "Tohle nevypadá jako platná http(s) URL.") }
            return
        }
        val stream = UploaderStream(
            name = name, description = "Ručně vložený odkaz", url = parsedUrl, addon = "Ruční odkaz",
        )
        val title = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: item.title
        workingSourceStore.save(
            item.imdbId, item.tmdbId, title, stream,
            season = episodeSelector?.season, episode = episodeSelector?.episode,
        )
        rememberSeasonRecipeFrom(stream)
        _uiState.update { it.copy(rememberedSource = stream, showManualUrlDialog = false) }
    }

    /**
     * D-b (user 07-19, screenshot): PŘÍMÝ výběr jiného cached zdroje v pickeru → OKAMŽITĚ se stane
     * zapamatovaným (výchozím) zdrojem filmu, bez čekání na „přehraj → 👍" ([confirmWorkingSource]).
     * ZÁMĚRNĚ NEuklízí ostatní RD torrenty ([cleanupRdKeepingSource]) — user chce mezi nacachovanými
     * zdroji volně přepínat (zvolit jiný a klidně se vrátit), takže je necháváme na RD dostupné.
     */
    fun pinWorkingSource(stream: UploaderStream) {
        val st = _uiState.value
        val imdb = st.item?.imdbId
        val title = st.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: st.item?.title.orEmpty()
        workingSourceStore.save(
            imdb, st.item?.tmdbId, title, stream,
            season = episodeSelector?.season, episode = episodeSelector?.episode,
        )
        rememberSeasonRecipeFrom(stream)
        _uiState.update { it.copy(rememberedSource = stream) }
    }

    /**
     * SEZONA (user 2026-08-02: *„zapamatování zdroje je u epizod asi zbytečné, hlavně má fungovat auto
     * zdroj"*) — u DÍLU se zdroj ukládá POTICHU, bez dialogu „Fungoval tenhle zdroj?".
     *
     * Nesmí to ale zahodit mechaniku f3d: přes potvrzovací dialog dosud vedla JEDINÁ cesta, jak se
     * osvědčený zdroj stal novou recepturou sezóny. Proto ji volá i tahle tichá varianta —
     * `rememberSeasonRecipeFrom` si sám ohlídá, že jde o zdroj, který reálně hraje (`playsNow`),
     * a že seriál nějakou recepturu už má.
     */
    private fun rememberEpisodeSourceSilently() {
        val stream = lastPlayedStream ?: return
        val selector = episodeSelector ?: return
        if (sameSource(stream, _uiState.value.rememberedSource)) return
        val st = _uiState.value
        val title = st.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: st.item?.title.orEmpty()
        workingSourceStore.save(
            st.item?.imdbId, st.item?.tmdbId, title, stream,
            season = selector.season, episode = selector.episode,
        )
        rememberSeasonRecipeFrom(stream)
        _uiState.update { it.copy(rememberedSource = stream) }
        timber.log.Timber.i("[SEZONA] zdroj dílu S%dE%d uložen potichu (bez dotazu)", selector.season, selector.episode)
    }

    /**
     * SEZONA f3d (user 2026-08-01: *„nerad bych každý díl řešil fallbacky"*) — zdroj, který se u DÍLU
     * osvědčil, se stává novou RECEPTUROU SEZÓNY.
     *
     * Proč: původní receptura mohla být nehratelná (userův Bleach: anime rip, na kterém přehrávač padl)
     * nebo ji addon u dalších dílů vůbec nenabízí. Bez tohohle by se u KAŽDÉHO dílu opakoval týž fallback,
     * protože receptura by dál ukazovala na to, co nefunguje. Ukládá se jen to, co reálně HRAJE
     * (`playsNow`) — a jen když už seriál nějakou recepturu má, tedy k automatice sezóny patří.
     */
    private fun rememberSeasonRecipeFrom(stream: UploaderStream) {
        val season = episodeSelector?.season ?: return
        val st = _uiState.value
        val imdb = st.item?.imdbId
        val tmdb = st.item?.tmdbId
        if (workingSourceStore.getSeason(imdb, tmdb, season) == null) return
        if (!com.github.jankoran90.showlyfin.data.uploader.SeasonSourceMatcher.playsNow(stream)) return
        val title = st.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: st.item?.title.orEmpty()
        workingSourceStore.saveSeason(imdb, tmdb, title, stream, season)
        timber.log.Timber.i("[SEZONA] receptura sezóny S%d přepsána zdrojem, který reálně hrál (%s)",
            season, stream.name ?: stream.description ?: "?")
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

    /**
     * SEZONA (SHW-113) f2 — „použij tenhle zdroj pro CELOU SEZÓNU" (user 2026-08-01 16:37: *„aby se rovnou
     * dokázal najít ten season pack a ověřením, že je funkční se zapamatuje a promítne do všech epizod
     * sezóny, aby při přehrát tlačítku se rovnou streamoval"*).
     *
     * Neukládá se URL, ale RECEPTURA — u pravého season packu (SK/CZ Torrents) sedne otisk torrentu a
     * addon si soubor dílu dohledá sám; u AIOStreams, kde otisk chybí, se pozná stejná release grupa
     * a rozlišení. Detail v [SeasonSourceMatcher].
     */
    fun pinSeasonSource(stream: UploaderStream) {
        val st = _uiState.value
        val season = episodeSelector?.season ?: st.selectedSeason ?: return
        val title = st.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: st.item?.title.orEmpty()
        workingSourceStore.saveSeason(st.item?.imdbId, st.item?.tmdbId, title, stream, season)
        _uiState.update { it.copy(hasSeasonSource = true, captureMessage = "Zdroj platí pro celou $season. sezónu.") }
    }

    /** SEZONA f2 — zruš recepturu sezóny (zdroje jednotlivých dílů zůstanou). */
    fun forgetSeasonSource() {
        val st = _uiState.value
        val season = episodeSelector?.season ?: st.selectedSeason ?: return
        workingSourceStore.clearSeason(st.item?.imdbId, st.item?.tmdbId, season)
        _uiState.update { it.copy(hasSeasonSource = false, captureMessage = "Zdroj sezóny zrušen.") }
    }

    /**
     * SEZONA f2 — zkus pro právě vybraný díl použít zdroj sezóny a přehrát HNED (bez pickeru).
     * Vrací true, když se to povedlo. Bere jen zdroje, které hrají okamžitě (cached na RD / sdilej) —
     * necachovaný torrent by znamenal čekání na stažení, a to není „Přehrát a jede".
     */
    private suspend fun tryPlaySeasonSource(sel: EpisodeSelector): Boolean {
        val st = _uiState.value
        val item = st.item ?: return false
        val recipe = workingSourceStore.getSeason(item.imdbId, item.tmdbId, sel.season)?.stream ?: return false
        val imdb = item.imdbId ?: return false
        val list = runCatching {
            uploaderDs.getStreams(
                uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb,
                season = sel.season, episode = sel.episode, strict = false,
            )
        }.getOrNull().orEmpty()
        if (list.isEmpty()) return false
        val match = com.github.jankoran90.showlyfin.data.uploader.SeasonSourceMatcher
            .pick(recipe, list) ?: return false
        timber.log.Timber.i(
            "[SEZONA] zdroj sezóny S%d → E%d: shoda %s (%s)",
            sel.season, sel.episode, match.confidence, match.stream.name ?: "?",
        )
        // 🔴 SEZONA f3d (device test 2026-08-01, user u Bleach E6: „spustí se fullscreen s nulovou
        // stopáží, nic se nepřehrává a nakonec se objeví okno"): seznam zdrojů si MUSÍME nechat.
        // `advancePastSource` (CASCADE) hledá další kandidáty právě v `streams` — a při přehrání přes
        // zdroj sezóny se picker vůbec neotevřel, takže tam bylo PRÁZDNO. Selhání zdroje proto neskočilo
        // na další release, ale rovnou na dialog „soubor nejde přehrát". Zdroje přitom máme načtené tady.
        // Vybraný kandidát jde NAHORU, aby auto-postup pokračoval až za ním (CASCADE hledá podle indexu).
        val ordered = listOf(match.stream) + list.filter { it !== match.stream }
        _uiState.update {
            it.copy(showStreamPathChooser = false, showStreamPicker = false, streams = ordered)
        }
        playStream(match.stream)
        return true
    }

    /** Skryj nabídku „tohle sedí?" (uživatel ji odmítl nebo to byl špatný zdroj). */
    fun dismissWorkingConfirm() = _uiState.update { it.copy(pendingWorkingConfirm = null) }

    /**
     * Zapomenout připnutý fungující zdroj (zdroj přestal fungovat / chce vybrat jiný).
     *
     * 🔴 A ROVNOU HLEDAT NOVÝ. User 2026-08-03: *„odebraný zdroj — teď nevidím kartu a když ji vyhledám,
     * tak nevidím, že by se hledal zdroj, a nevím, zda mám čekat na autodohledání."* U seriálu se nové
     * hledání spouštělo už dřív ([forgetShowSources]), u FILMU se jen smazal záznam a nestalo se nic —
     * a protože dětský profil ukazuje ve Filmotéce jen tituly se zdrojem, karta zmizela a divák neměl
     * podle čeho poznat, jestli čekat. *Odebrání zdroje je žádost o jiný, ne o žádný.*
     */
    fun forgetWorkingSource() {
        val item = _uiState.value.item
        workingSourceStore.clear(
            item?.imdbId, item?.tmdbId,
            season = episodeSelector?.season, episode = episodeSelector?.episode,
        )
        _uiState.update { it.copy(rememberedSource = null) }
        if (item == null || item.type != MediaType.MOVIE) return
        _uiState.update { it.copy(autoAdvanceInfo = "Zdroj zapomenut, hledám nový…") }
        markAutoSearching()
        // 🔴 User 2026-08-15 (After the Storm): `clear()` o řádek výš založí 90denní náhrobek (SHW-107,
        // ať se smazaný film sám nevrací) — ale tahle funkce O VTEŘINU POZDĚJI sama spustí nové hledání.
        // Bez zrušení náhrobku server auto-search zdroj sice najde a zapíše, jenže nejbližší sync push
        // ho zase smaže (chybí ve snapshotu → tombstone). *Odebrání zdroje je žádost o jiný, ne o žádný*
        // platí i pro náhrobek samotný — jinak ho vlastní re-search sabotuje.
        workingSourceStore.clearTombstoneFor(
            item.imdbId, item.tmdbId,
            season = episodeSelector?.season, episode = episodeSelector?.episode,
        )
        viewModelScope.launch {
            workingSourceStore.triggerAutoCache(
                item.imdbId, item.tmdbId, _uiState.value.tmdbCzTitle ?: item.title, item.year, effectiveCachePolicy(item.imdbId),
            )
        }
    }

    /**
     * SEZONA — zapomeň zdroje CELÉHO seriálu (všechny sezóny i díly), ne jen otevřeného dílu.
     *
     * User 2026-08-02 (Arcane): appce se ráno uložil zdroj, který se teprve stahoval na RD, a od té
     * doby po něm sahala znovu. Odebrat ho ale nešlo — v menu karty volba nebyla (u seriálu je
     * `rememberedSource` prázdný, dokud neotevřeš díl) a k seznamu zdrojů se u dílu se zapamatovaným
     * zdrojem nedostaneš, protože ten hraje rovnou.
     */
    fun forgetShowSources() {
        val item = _uiState.value.item ?: return
        val n = workingSourceStore.clearShow(item.imdbId, item.tmdbId)
        _uiState.update {
            it.copy(rememberedSource = null, hasSeasonSource = false, hasAnyShowSource = false,
                autoAdvanceInfo = if (n > 0) "Zdroje zapomenuty, hledám nové…" else null)
        }
        if (n > 0) markAutoSearching()
        if (n == 0) return
        // 🔴 Zapomenutí MUSÍ rovnou nastartovat nové hledání (user 2026-08-02: *„Zapomenuto mám, ale
        // dotáhne se automaticky?"* → *„to jsme chtěli automatizovat přeci, na pack season ideálně
        // nebo celý seriál"*). Bez tohohle zůstal divák u ručního seznamu: zdroj zmizel, ale nic
        // nespustilo auto-hledání, které jinak běží při přidání do „Chci vidět".
        viewModelScope.launch {
            val season = _uiState.value.selectedSeason
                ?: _uiState.value.seasons.firstOrNull { s -> s.season_number >= 1 }?.season_number
                ?: 1
            runCatching {
                workingSourceStore.triggerSeasonCache(
                    item.imdbId, item.tmdbId, _uiState.value.tmdbCzTitle ?: item.title,
                    item.year, effectiveCachePolicy(item.imdbId), season,
                )
            }.onFailure { timber.log.Timber.w(it, "[SEZONA] re-hledání po zapomenutí selhalo") }
            // Zbytek seriálu do fronty s retry — jinak by se dohledala jen ta právě otevřená sezóna
            // (týž vzor jako u přidání do „Chci vidět", viz `toggleWatchlist`).
            runCatching {
                workingSourceStore.cacheBatch(
                    listOf(
                        com.github.jankoran90.showlyfin.data.uploader.BackfillItem(
                            imdb = item.imdbId.orEmpty(),
                            tmdb = item.tmdbId ?: 0L,
                            title = _uiState.value.tmdbCzTitle ?: item.title,
                            year = item.year,
                            kind = "show",
                        ),
                    ),
                    effectiveCachePolicy(item.imdbId),
                )
            }.onFailure { timber.log.Timber.w(it, "[SEZONA] fronta pro zbylé sezóny po zapomenutí selhala") }
        }
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

    /**
     * FILMYCAST — „Přehrát na Filmy TV": pošli zapamatovaný zdroj filmu do Filmy appky na TV. Reuse celého
     * resolve pipeline ([playStream] → [deliver]), takže na TV odejde vždy plně resolvnutá přehratelná URL
     * (https / sdilej-proxy / RD-resolved), ne syrový infoHash/sdilej://. Bez zapamatovaného zdroje jen
     * hláška (dohledání zdroje TV-side = follow-up). Jen film.
     */
    fun castToFilmyTv() {
        val item = _uiState.value.item ?: return
        if (item.type != MediaType.MOVIE) return
        val stream = _uiState.value.rememberedSource
        if (stream == null) {
            _uiState.update { it.copy(autoCastMessage = "Film zatím nemá uložený zdroj — nejdřív ho jednou přehraj, pak ho pošlu na Filmy TV.") }
            return
        }
        playStream(stream, CastTarget.FILMY_TV)
    }

    /**
     * D-c (user 2026-07-19): „probudit celou sestavu" jako to uměl showlyfin. Před zařazením cast příkazu
     * zapni AV receiver, televizi i box a spusť Filmy appku na boxu do popředí (jinak její cast poller, který
     * běží jen v popředí, čekající příkaz nevyzvedne). Gate: sestava nakonfigurovaná (`avr_enabled` + IP) —
     * jinak tiše přeskoč (uživatel má vše zapnuté / wake vypnutý v Nastavení → Domácí sestava). Best-effort.
     */
    private fun maybeWakeHomeTheater() {
        val cfg = com.github.jankoran90.showlyfin.data.maestro.HomeTheaterConfig.from(prefs)
        if (!cfg.configured) return
        viewModelScope.launch {
            runCatching { homeTheaterScene.wakeAndLaunch(cfg, FILMY_TV_PACKAGE) }
                .onFailure { timber.log.Timber.w(it, "[MAESTRO] wake sestavy pro Filmy TV selhal") }
        }
    }

    /**
     * FILMYCAST: resolvnutou URL zařaď jako cast příkaz na backend pod aktivním profilem. `subtitleQuery`
     * složíme z originálního názvu (TV má imdb/title/year z příkazu → dohledá CZ titulky). positionMs = 0
     * (telefonní resume store se sem zatím netahá; TV resumuje z vlastního lokálního prefu). Výsledek → hláška.
     */
    private fun sendFilmyCastCommand(url: String, title: String) {
        // D-c: probuď domácí sestavu PARALELNĚ (AVR→TV→box→spusť Filmy appku), ať je box v popředí a jeho
        // cast poller níže zařazený příkaz vyzvedne. Fire-and-forget; když sestava není nakonfigurovaná, no-op.
        maybeWakeHomeTheater()
        _uiState.update { it.copy(isCastingToTv = true, isResolvingStream = false, showStreamPicker = false, streamError = null) }
        viewModelScope.launch {
            val st = _uiState.value
            val item = st.item
            val poster = (item?.posterPath ?: st.movieDetails?.poster_path)
                ?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w342$it" }
            val subQuery = st.pendingSubtitleQuery?.origTitle?.takeIf { it.isNotBlank() }
                ?: item?.originalTitle?.takeIf { it.isNotBlank() }
                ?: st.movieDetails?.original_title
            // CROSS-DEVICE RESUME: telefonní pozice přehrávání filmu (kde jsi skončil) → pošli TV, ať naváže.
            // Klíč = `resume_<imdb>` (shodný s `PlaybackViewModel.resumeKeyOf` pro film s imdb; ukládá
            // `saveExternalPosition`). Bez imdb / bez uložené pozice = 0 → TV spustí od začátku / z vlastní pozice.
            val resumePosMs = item?.imdbId?.takeIf { it.isNotBlank() }
                ?.let { prefs.getLong("resume_$it", 0L) } ?: 0L
            val ok = runCatching {
                workingSourceStore.castToTv(
                    imdb = item?.imdbId, tmdb = item?.tmdbId, title = title, year = item?.year,
                    sourceUrl = url, positionMs = resumePosMs, posterUrl = poster, subtitleQuery = subQuery,
                )
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    isCastingToTv = false,
                    autoCastMessage = if (ok) "Odesláno na Filmy TV ▶" else "Odeslání na Filmy TV selhalo — zkontroluj připojení a přihlášení.",
                )
            }
        }
    }

    /**
     * Zadání pro dohledání CZ titulků k PRÁVĚ spouštěnému zdroji (název CZ i originál, rok, díl, release).
     * Vytaženo zvlášť, aby ho měl kdo postavit i mimo [playStream] (REPACK doručuje URL podruhé).
     */
    private fun subtitleQueryOf(
        subTitle: String,
        subOrig: String,
        stream: UploaderStream,
        czDub: Boolean,
    ): com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery {
        val st = _uiState.value
        return com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery(
            imdb = st.item?.imdbId.orEmpty(),
            title = subTitle,
            origTitle = subOrig,
            year = st.item?.year,
            season = episodeSelector?.season,
            episode = episodeSelector?.episode,
            // 🔴 SEZONA f3b: OBA texty, ne jen `name`. U AIOStreams je `name` jen odznak („🚀 FHD",
            // „🔥4K UHD") a skutečný název releasu (`Breaking.Bad.S01E01…2160p.WEB…`) je až
            // v `description` — server tedy dostával k porovnání s titulky prázdnou informaci
            // a `_release_affinity` neměla podle čeho vybírat. Tohle je jedna z příčin
            // userova „titulky nesedí na release".
            release = listOfNotNull(stream.name, stream.description)
                .joinToString(" ").trim().take(300).takeIf { it.isNotBlank() },
            fps = stream.quality.fps,
            runtime = st.movieDetails?.runtime,
            autoSearch = !czDub && !isChildProfile(),   // dětský profil = CZ dabing → titulky NIKDY nehledat (user 2026-08-12: Bluey mělo dohledané špatné titulky, protože lang audia u zdroje nebyl detekovaný CZ)
        )
    }

    /** Klik na konkrétní stream → přímé url / RD resolve → předá URL [target] (telefon / TV). */
    fun playStream(stream: UploaderStream, target: CastTarget = CastTarget.LOCAL) {
        if (_uiState.value.isResolvingStream || _uiState.value.rdDownload != null) return
        lastPlayedStream = stream   // CASCADE Fáze 4: zapamatuj pro případný auto-advance po chybě přehrávání
        publishPreferredAudioLanguages()   // SEZONA f2: přehrávač musí vědět, jakou stopu pustit
        // TENFOOT WS-C: u epizody použij popisek „Seriál S1E4 · název" jako titul přehrávače.
        val title = episodeSelector?.label
            ?: _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() }
            ?: _uiState.value.item?.title.orEmpty()
        // CZ titulky query (Fáze E): orig+cz název, rok, runtime, release+fps zvoleného streamu.
        // BATON regrese: query stavíme VŽDY (dřív gate `if imdb != null` → při castu z doporučení je
        // imdbId ještě prázdné, dohledá se z TMDB později, stejný root cause jako SIEVE → query null →
        // `subs:[]` na TV). Backend hledá i bez imdb (podle title/origTitle/year); prázdné imdb řeší
        // API klient placeholderem. Postavíme když máme aspoň název.
        val st = _uiState.value
        val subTitle = st.tmdbCzTitle?.takeIf { t -> t.isNotBlank() } ?: st.item?.title.orEmpty()
        // PASSPORT (SHW-93) A2 — skutečný originální název pro titulky (OS/titulky.com indexují i podle originálu);
        // dřív = item.title (duplikoval subTitle). Fallback: MediaItem.originalTitle → movieDetails → title.
        val subOrig = st.item?.originalTitle?.takeIf { it.isNotBlank() }
            ?: st.movieDetails?.original_title?.takeIf { it.isNotBlank() }
            ?: st.item?.title.orEmpty()
        // CONDUIT (SHW-58): český dabing (CZ/SK audio nebo sdílej bez detekce) → NEhledat automaticky
        // titulky (film je dabovaný), ale `SubtitleQuery` postavíme dál (drží resume klíč `resumeKeyOf`).
        // LINGUA (user 2026-07-20): TITULKOVÝ release (originál audio + CZ TITULKY, název nese „titulky"/„subs")
        // NENÍ dabing → titulky HLEDAT. Ida = polský originál + CZ titulky ze sdilej („…titulky.cz…") se dřív
        // bral jako dabovaný (sdilej + lang=null) → auto-search titulků VYPNUTÝ → přehrávač nenabídl žádné,
        // i když CZ titulky reálně existují. Tady je gate JEN pro rozhodnutí o titulcích (audio routing pickeru
        // řeší isCzDubStream/isCzDub zvlášť — ty se nemění).
        val czDub = run {
            val lang = stream.quality.audioLanguage?.uppercase()
            val name = ((stream.name ?: "") + " " + (stream.url ?: "")).lowercase()
            val subtitled = "titulky" in name || "subs" in name || "subtitle" in name || "-tit" in name || ".tit" in name
            // RUBRIC-follow (user 2026-07-21): sdílej zdroj s nedetekovaným audiem (lang==null) se DŘÍV bral jako
            // dabing → titulky vypnuté (Tatík 2024, cizojazyčný film ze sdílej = 0 titulků, na rozdíl od RD). Sdílej
            // nabízí zdroje i pro cizojazyčné filmy → titulková vrstva (dohledání OpenSubtitles + AI překlad) musí
            // běžet STEJNĚ jako u RD. Gate teď JEN na explicitní CZ/SK audio (jako RD). Reálně-dabovaný sdílej film
            // dostane nabídku titulků navíc (neškodí — výběr „OFF" je per-zdroj zapamatovaný).
            !subtitled && (lang == "CZ" || lang == "SK")
        }
        if (subTitle.isNotBlank() || subOrig.isNotBlank()) {
            val query = subtitleQueryOf(subTitle, subOrig, stream, czDub)
            lastSubtitleQuery = query   // REPACK: přežije `consumePlayback()` → druhý pokus má titulky
            _uiState.update { it.copy(pendingSubtitleQuery = query) }
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
                season = episodeSelector?.season,
                episode = episodeSelector?.episode,
                resolution = stream.quality.resolution,
                sizeGB = stream.quality.sizeGB,
            )
        }
        // WINNOW item 2: zapamatuj RD hash tohoto pokusu (bezpečný úklid při „zapamatovat zdroj").
        streamRdHash(stream)?.let { attemptedRdHashes.add(it) }
        // 0) CONDUIT: české úložiště (sdilej://) → přehraj přes náš proxy (samonosná ?key= URL, funguje
        //    i na TV, kde box nemá sdílej login). MUSÍ být PŘED přímou url — sdilej:// je taky `direct`.
        if (direct != null && direct.startsWith("sdilej://")) {
            val proxy = buildSdilejProxyUrl(direct)
            if (proxy != null) {
                timber.log.Timber.i("[sdilej] play přes proxy $direct")
                deliver(proxy, title, target)
            } else {
                _uiState.update { it.copy(streamError = "Neplatné sdilej:// URL.") }
            }
            return
        }
        // 0b) VLTAVA: ČT iVysílání (`ctv:<idec>`) → hotovou adresu si vytáhne TOHLE zařízení (playlist API
        //     je geoblokované na náš server; doma na české IP projde). Musí být PŘED přímou url — `ctv:`
        //     je taky `direct`. Uložený zdroj drží jen `idec`, takže nezestárne (CDN odkaz je krátkodobý).
        val ctvIdec = com.github.jankoran90.showlyfin.data.uploader.ctvIdecOrNull(direct)
        if (ctvIdec != null) {
            _uiState.update { it.copy(isResolvingStream = true, streamError = null) }
            viewModelScope.launch {
                when (val r = ctvResolver.resolve(ctvIdec)) {
                    is com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result.Ok -> {
                        timber.log.Timber.i("[VLTAVA] ČT play idec=%s", ctvIdec)
                        deliver(r.url, title, target)
                    }
                    else -> _uiState.update { it.copy(isResolvingStream = false, streamError = ctvError(r)) }
                }
            }
            return
        }
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

    /** VLTAVA: proč ČT zdroj nehraje — pravdivá hláška místo černé obrazovky. */
    private fun ctvError(r: com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result): String =
        when (r) {
            is com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result.DrmRequired ->
                "Česká televize u tohohle titulu nevrátila ani chráněnou variantu. Zkus jiný zdroj."
            is com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result.OutsideCz ->
                "Česká televize pouští tenhle titul jen z Česka. Jsi mimo domácí síť?"
            is com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result.Failed ->
                "Zdroj z České televize se nepodařilo otevřít: ${r.reason}"
            is com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result.Ok -> ""
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
            // ③ (2026-07-14) stall-timeout: když se necachovaný zdroj NEZAČNE stahovat (progress visí na 0 %)
            // po konfigurovanou dobu (pref `rd_stall_timeout_sec`, default 120 s), vzdej a vyzvi k jinému zdroji.
            // Jakmile progress poskočí nad 0 % (stahuje se), timeout se vypne — dál ruší jen user tlačítkem Zrušit.
            val stallTimeoutMs = prefs.getInt("rd_stall_timeout_sec", 120).coerceAtLeast(1) * 1000L
            val startedAt = System.currentTimeMillis()
            var everMoved = add.progress > 0.0
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
                if (p.progress > 0.0) everMoved = true
                if (!everMoved && p.status != "downloaded" &&
                    System.currentTimeMillis() - startedAt >= stallTimeoutMs) {
                    timber.log.Timber.w("[RD] stall-abort: 0 %% po ${stallTimeoutMs / 1000}s → vzdávám torrent=${add.torrentId}")
                    _uiState.update {
                        it.copy(
                            rdDownload = null,
                            autoCastMessage = "Zdroj se za ${stallTimeoutMs / 1000} s nezačal stahovat (0 %). Nejspíš mrtvý — zkus jiný zdroj.",
                        )
                    }
                    return@launch
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
    fun consumeAutoCastMessage() = _uiState.update { it.copy(autoCastMessage = null) }

    /**
     * PROJECTOR (HUB-74): hlasový cast filmu na TV/Zenbook. Po otevření detailu z hlasového deep-linku
     * (`showlyfin://detail?tmdb=…&cast=tv|zenbook&path=cz|orig`) vybere zdroj v pořadí, které si přál
     * uživatel: (1) zapamatovaný (připnutý) zdroj filmu, (2) film ve Jellyfin knihovně (jednoznačné),
     * (3) sdílej/RD stream podle [audioPath] (cz = dabing/čes. film, orig = originál + CZ titulky).
     * Stažená OFFLINE kopie se necastuje (lokální soubor telefonu si mpv na TV/Zenbooku nepřehraje) →
     * když se žádný přehratelný stream nenajde, cast se ODMÍTNE hláškou (bez fallbacku na telefon).
     */
    fun autoCastToTarget(castTarget: String, audioPath: String?) {
        if (autoCastPending) return
        autoCastPending = true
        viewModelScope.launch {
            // 1) počkej na hydrataci detailu (metadata + imdb; rememberedSource je k dispozici hned).
            val ready = withTimeoutOrNull(20_000) { uiState.first { !it.isLoading && it.item != null } }
            if (ready == null) { failAutoCast("Film se nepodařilo načíst, na televizi ho teď nepustím."); return@launch }
            voiceCastDeviceId = resolveVoiceCastDeviceId(castTarget)

            val item = _uiState.value.item
            // 2) zapamatovaný (připnutý) zdroj má přednost.
            _uiState.value.rememberedSource?.let { castStreamViaVoice(it); return@launch }
            // 3) Jellyfin knihovna — jednoznačný zdroj (deterministicky, bez race s paralelním loadem).
            if (item != null) {
                resolveOwnedJellyfinId(item)?.let { jfId -> castLibraryViaVoice(jfId, item); return@launch }
            }
            // 4) sdílej / RD stream podle zvolené cesty (dabing vs originál).
            val path = when (audioPath) {
                "cz" -> StreamAudioPath.CZ_DUB
                "orig" -> StreamAudioPath.ORIGINAL
                else -> null
            }
            val stream = resolveFirstStreamForCast(path)
            if (stream == null) { failAutoCast("Na televizi jsem pro tenhle film nenašel žádný přehratelný zdroj."); return@launch }
            castStreamViaVoice(stream)
        }
    }

    /**
     * LAPIDARY S4b — one-click z řady „Uloženo k přehrání": po hydrataci detailu přehraj rovnou zapamatovaný
     * (připnutý) zdroj lokálně (přes [playStream] → `pendingPlaybackUrl` → TV přehrávač). Když se zdroj mezitím
     * ztratil (odepnut / smazán), tiše NEuděláme nic — zůstane otevřený detail (bezpečný fallback).
     */
    fun autoplayRemembered() {
        if (autoplayRememberedPending) return
        autoplayRememberedPending = true
        viewModelScope.launch {
            // Počkej na hydrataci detailu — rememberedSource se plní v load() (workingSourceStore.get).
            val ready = withTimeoutOrNull(20_000) { uiState.first { !it.isLoading && it.item != null } }
            if (ready == null) return@launch
            _uiState.value.rememberedSource?.let { playRemembered() }
        }
    }

    // ── BACKLOG (autodetekce rychlosti linky, user 2026-08-03) ─────────────────────────────────
    // Zdroje jsou per-PROFIL (sdílené TV↔telefon), takže link mode je per-ZAŘÍZENÍ a ovlivňuje jen
    // PŘEHRÁVÁNÍ: na mobilních datech („venku") se uložený velký zdroj nahradí menší alternativou,
    // aby přehrávání nestagovalo. Doma (WiFi/ethernet; TV vždy) = uložený zdroj rovnou (dnešní chování).
    /** Play-gate: na mobilních datech nahraď uložený velký zdroj menší alternativou, jinak hraj rovnou. */
    fun playRemembered() {
        val src = _uiState.value.rememberedSource ?: return
        val mode = com.github.jankoran90.showlyfin.core.network.LinkModePrefs.effectiveMode(
            prefs, connectivity.currentLinkKind(),
        )
        if (mode == com.github.jankoran90.showlyfin.core.network.LinkMode.AWAY && isTooBigForAway(src)) {
            seekSmallerAlternative(src)
        } else {
            playStream(src)
        }
    }

    /** Je uložený zdroj nad prahem „venkovského" režimu? (bitrate, fallback velikost; neznámé = fail-open). */
    private fun isTooBigForAway(stream: UploaderStream): Boolean {
        val maxBps = com.github.jankoran90.showlyfin.core.network.LinkModePrefs.awayMaxBitrateMbps(prefs)
        val maxGb = com.github.jankoran90.showlyfin.core.network.LinkModePrefs.awayMaxSizeGB(prefs)
        val bps = stream.quality.bitrateMbps
        val gb = stream.quality.sizeGB
        return when {
            bps != null && bps > 0 -> bps > maxBps
            gb != null && gb > 0 -> gb > maxGb
            else -> false  // neznámá velikost → nerušit, hraj uložený
        }
    }

    /** Na mobilních datech dohledat menší alternativu; při selhání nebo ničem menším padni na uložený zdroj. */
    private fun seekSmallerAlternative(fallback: UploaderStream) {
        val item = _uiState.value.item
        val imdb = item?.imdbId
        if (item == null || imdb.isNullOrBlank() || uploaderBaseUrl.isBlank()) { playStream(fallback); return }
        viewModelScope.launch {
            val epSeason = episodeSelector?.season
            val epEpisode = episodeSelector?.episode
            val list = runCatching {
                uploaderDs.getStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb, season = epSeason, episode = epEpisode, strict = false, link = "away")
            }.getOrDefault(emptyList())
            val best = pickSmallerAlternative(fallback, list)
            timber.log.Timber.i(
                "[linkmode] AWAY: remembered %.1f Mbps/%.1f GB → alternative %.1f/%.1f (%d kandidátů)".format(
                    fallback.quality.bitrateMbps ?: -1.0, fallback.quality.sizeGB ?: -1.0,
                    best.quality.bitrateMbps ?: -1.0, best.quality.sizeGB ?: -1.0, list.size,
                )
            )
            playStream(best)
        }
    }

    /** Nejmenší přijatelná alternativa (pod prahem); fail-open na [fallback]. Preferuj bitrate, pak velikost. */
    private fun pickSmallerAlternative(fallback: UploaderStream, list: List<UploaderStream>): UploaderStream {
        if (list.isEmpty()) return fallback
        val maxBps = com.github.jankoran90.showlyfin.core.network.LinkModePrefs.awayMaxBitrateMbps(prefs).toDouble()
        val maxGb = com.github.jankoran90.showlyfin.core.network.LinkModePrefs.awayMaxSizeGB(prefs)
        fun UploaderStream.score(): Double =
            quality.bitrateMbps?.takeIf { it > 0 } ?: quality.sizeGB?.takeIf { it > 0 } ?: Double.MAX_VALUE
        fun UploaderStream.acceptable(): Boolean {
            val b = quality.bitrateMbps
            val g = quality.sizeGB
            return when {
                b != null && b > 0 -> b <= maxBps
                g != null && g > 0 -> g <= maxGb
                else -> false
            }
        }
        return (list + fallback).filter { it.acceptable() }.minByOrNull { it.score() } ?: fallback
    }
    // ── konec BACKLOG link mode ────────────────────────────────────────────────────────────────

    /** Cíl hlasového castu → preferredDeviceId: tv = null (automatika → TV/Yellyfin), zenbook = deviceId docku. */
    private suspend fun resolveVoiceCastDeviceId(castTarget: String): String? {
        if (castTarget != "zenbook") return null
        val jfUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val jfToken = prefs.getString("jellyfin_token", "").orEmpty()
        if (jfUrl.isBlank() || jfToken.isBlank()) return CastTargetPrefs.defaultDeviceId(prefs)
        val sessions = runCatching { naTv.getSessions(jfUrl, jfToken) }.getOrDefault(emptyList())
        val zen = sessions.firstOrNull {
            val n = "${it.client.orEmpty()} ${it.deviceName}".lowercase()
            n.contains("zenbook") || n.contains("dock")
        }
        return zen?.deviceId ?: CastTargetPrefs.defaultDeviceId(prefs)
    }

    /** Je film ve Jellyfin knihovně? Vrátí jeho jellyfin id (nebo null). Mirror [loadJellyfinOwned] matchingu. */
    private suspend fun resolveOwnedJellyfinId(item: MediaItem): String? {
        _uiState.value.ownedJellyfinId?.let { return it }   // už dořešeno paralelním loadem
        val uid = prefs.getString("jellyfin_user_id", "")?.takeIf { it.isNotBlank() } ?: return null
        val uuid = runCatching { UUID.fromString(uid) }.getOrNull() ?: return null
        val owned = runCatching { jellyfinLibraryService.getOwnedIds(uuid) }.getOrNull() ?: return null
        return item.imdbId?.let { owned.imdbToJellyfin[it] } ?: item.tmdbId?.let { owned.tmdbToJellyfin[it] }
    }

    /** Načte streamy a vybere první přehratelný podle zvolené cesty (dabing/originál); null = žádný. */
    private suspend fun resolveFirstStreamForCast(path: StreamAudioPath?): UploaderStream? {
        val imdb = _uiState.value.item?.imdbId
        if (imdb.isNullOrBlank() || uploaderBaseUrl.isBlank()) return null
        _uiState.update { it.copy(streamAudioPath = path) }
        loadStreams()
        // počkej na doběh načítání (instant vlna) — nebo dokud neskončí i probe (žádný instant zdroj).
        val settled = withTimeoutOrNull(45_000) {
            uiState.first { !it.isLoadingStreams && (it.streams.isNotEmpty() || !it.isProbingStreams) }
        } ?: return null
        val all = settled.streams
        if (all.isEmpty()) return null
        val cz = all.filter { isCzDubStream(it) }
        val orig = all.filterNot { isCzDubStream(it) }
        return when (path) {
            StreamAudioPath.CZ_DUB -> cz.firstOrNull() ?: orig.firstOrNull()
            StreamAudioPath.ORIGINAL -> orig.firstOrNull() ?: cz.firstOrNull()
            null -> all.firstOrNull()
        }
    }

    /** Stejné kritérium českého dabingu jako [playStream] / `isCzDub` v UI (drž v synchru). */
    private fun isCzDubStream(stream: UploaderStream): Boolean {
        val lang = stream.quality.audioLanguage?.uppercase()
        return lang == "CZ" || lang == "SK" || (stream.url?.startsWith("sdilej://") == true && lang == null)
    }

    /** Pošli vybraný stream na TV/Zenbook přes FERRY (reuse playStream cesty; příznak hlasový cast). */
    private fun castStreamViaVoice(stream: UploaderStream) {
        voiceCastActive = true
        playStream(stream, CastTarget.TV)
    }

    /** Film ve Jellyfin knihovně → přímá stream URL na FERRY (uniformně s ostatními zdroji). */
    private fun castLibraryViaVoice(jellyfinId: String, item: MediaItem) {
        val jfUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val jfToken = prefs.getString("jellyfin_token", "").orEmpty()
        if (jfUrl.isBlank() || jfToken.isBlank()) { failAutoCast("Jellyfin není přihlášený, na televizi to nepustím."); return }
        val streamUrl = "${jfUrl.trimEnd('/')}/Videos/$jellyfinId/stream?static=true&api_key=$jfToken"
        val title = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() } ?: item.title
        voiceCastActive = true
        castToTv(streamUrl, title)
    }

    /** Hlasový cast se nepovedl → zobraz hlášku (Toast v DetailScreen), ukliď příznaky. */
    private fun failAutoCast(message: String) {
        voiceCastActive = false
        _uiState.update { it.copy(autoCastMessage = message, isCastingToTv = false, isResolvingStream = false) }
    }

    /**
     * Plan WINNOW (SHW-41, item 1b): než URL doručíme, ověř, že to není NÁVNADA — Comet/RD běžně
     * vrací „cached" položky, které se tváří jako film (deklarovaná velikost i 20 GB), ale reálně
     * servírují jen ~stovky KB (decoy). Takový zdroj ExoPlayer „přehraje" a hned skončí → matoucí.
     * Range-dotaz na skutečnou velikost; pod prahem → přeskoč na další kandidáta (CASCADE).
     */
    private fun deliver(url: String, title: String, target: CastTarget) {
        // 🔴 REPACK zkratka (user 2026-08-02: *„pořád hledám zdroje a po 20 s přebaluju… a když se vracím
        // zpět, tak celý proces běží znovu"*): u zdroje, o kterém UŽ VÍME, že se bez přebalu nepřehraje,
        // nemá smysl znovu čekat, až přehrávač spadne na formátové chybě. Jdeme rovnou na přebal — a ten
        // je díky stabilnímu `job_id` typicky hotový, takže se jen stáhne hlavička a hraje se.
        val known = lastPlayedStream?.let { streamIdentity(it) }
        if (target == CastTarget.LOCAL && known != null && repackNeededStore.needsRepack(known) &&
            repackAllowed() && uploaderBaseUrl.isNotBlank() && repackTriedUrls.add(url)
        ) {
            timber.log.Timber.i("[REPACK] zdroj je známý jako nehratelný → rovnou přebal, bez čekání na pád")
            lastDeliveredUrl = url
            lastPlaybackTitle = title
            repackAndPlay(url)
            return
        }
        // 🔴 A KDYŽ JSOU PŘEBALY VYPNUTÉ, takový zdroj vůbec NEPOUŠTĚJ — jdi na další.
        // User 2026-08-03 12:52: *„přehrává se, co vteřina to skoky o desítky vteřin, jakoby seeking
        // chování pro play"*. Přesně tohle dělá soubor, který přehrávač neumí: nespadne (takže se
        // nespustí ani přeskok na další zdroj), jen hraje nesmysly. *Vypnout záchranu neznamená
        // pouštět rozbité — znamená rovnou sáhnout po jiném.*
        if (target == CastTarget.LOCAL && known != null && repackNeededStore.needsRepack(known) &&
            !repackAllowed()
        ) {
            timber.log.Timber.i("[REPACK] zdroj je známý jako nehratelný a přebal je vypnutý → další zdroj")
            advancePastSource("Tenhle zdroj přehrávač neumí, zkouším další", target)
            return
        }
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
        // REPACK (f3e): přebal potřebuje PŘESNĚ tuhle (už resolvnutou) adresu — z `UploaderStream`
        // by se musela resolvovat znovu a u efemérních odkazů by to nemuselo vyjít stejně.
        lastDeliveredUrl = url
        lastPlaybackTitle = title    // REPACK: `pendingPlaybackTitle` se spotřebuje, tohle zůstává
        when (target) {
            CastTarget.LOCAL -> {
                // SIEVE S2: až teď (přehrávač se reálně spouští) nabídneme „tohle sedí? 👍" — po návratu
                // na Detail. Nenabízíme u zdroje, který je už uložený jako fungující (žádné opakování).
                // 🔴 SEZONA (user 2026-08-02): *„Objevuje se zapamatování zdroje, což je u epizod asi
                // zbytečné, hlavně má fungovat auto zdroj."* U DÍLU se tedy neptáme — u seriálu je paměť
                // věcí RECEPTURY SEZÓNY, kterou si automatika drží sama (f3d), a ptát se u každého dílu
                // je jen otrava. Uložení proto proběhne potichu (viz níže), ne dialogem.
                val isEpisode = episodeSelector != null
                val confirm = lastPlayedStream
                    ?.takeIf { !isEpisode && !sameSource(it, _uiState.value.rememberedSource) }
                if (isEpisode) rememberEpisodeSourceSilently()
                _uiState.update {
                    it.copy(
                        isResolvingStream = false, showStreamPicker = false,
                        pendingPlaybackUrl = url, pendingPlaybackTitle = title,
                        // REPACK: druhé doručení téhož dílu (po přebalu) staví na zapamatovaném zadání —
                        // `playStream` se už podruhé nevolá, takže by titulky jinak zůstaly bez dotazu.
                        pendingSubtitleQuery = it.pendingSubtitleQuery ?: lastSubtitleQuery,
                        pendingWorkingConfirm = confirm,
                    )
                }
            }
            CastTarget.TV -> castToTv(url, title)
            CastTarget.FILMY_TV -> sendFilmyCastCommand(url, title)
        }
    }

    /**
     * CONDUIT (SHW-58): `sdilej://<file_id>/<slug>` → samonosná proxy URL na náš backend (auth `?key=`,
     * stejně jako titulky/ferry). Backend resolvne přímý odkaz a přepošle bajty s Range → ExoPlayer
     * seek + WINNOW probe; funguje i pro MPV na TV (box nemá sdílej login). slug je URL-safe (`[a-z0-9-]`).
     */
    private fun buildSdilejProxyUrl(sdilejUrl: String): String? {
        if (uploaderBaseUrl.isBlank()) return null
        val rest = sdilejUrl.removePrefix("sdilej://")
        val parts = rest.split("/", limit = 2)
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        val base = uploaderBaseUrl.trimEnd('/')
        val key = java.net.URLEncoder.encode(uploaderCookie, "UTF-8")
        return "$base/api/sdilej/stream/${parts[0]}/${parts[1]}?key=$key"
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
            // PROJECTOR (HUB-74): u hlasového castu použij zvolený cíl (tv=null → automatika, zenbook=dock).
            val voice = voiceCastActive
            val preferred = if (voice) voiceCastDeviceId else CastTargetPrefs.defaultDeviceId(prefs)
            val result = naTv.castFerry(jfUrl, jfToken, url, title, subs, reportUrl, preferredDeviceId = preferred)
            // Po úspěšném spuštění na TV přepni appku rovnou na sekci „Ovladač" (parita s JF knihovnou
            // přes NaTvCoordinator) → telefon se hned stává dálkovým ovladačem běžícího streamu.
            // + zapamatuj cast (externí stream není JF NowPlaying) → Ovladač ukáže titul/cover + pozici.
            if (result == CastResult.SENT) {
                val poster = (item?.posterPath ?: _uiState.value.movieDetails?.poster_path)
                    ?.let { "https://image.tmdb.org/t/p/w342$it" }
                ListenNavSignal.setFerryCast(title, poster, item?.tmdbId, reportUrl)
                ListenNavSignal.requestOpenOvladac()
            }
            voiceCastActive = false
            _uiState.update {
                it.copy(
                    isCastingToTv = false, isResolvingStream = false, castToTvResult = result,
                    // Hlasový cast: na odmítnutí ukaž hlášku (Toast), NE stream picker.
                    showStreamPicker = if (voice) false else result != CastResult.SENT,
                    autoCastMessage = if (voice && result != CastResult.SENT) castFailMessage(result) else it.autoCastMessage,
                )
            }
        }
    }

    /** PROJECTOR: lidská hláška při neúspěšném hlasovém castu. */
    private fun castFailMessage(result: CastResult): String = when (result) {
        CastResult.NO_SESSION -> "Televize teď není dostupná, nemám kam to poslat."
        CastResult.NO_CREDS -> "Jellyfin není přihlášený, na televizi to nepustím."
        else -> "Na televizi se film nepodařilo spustit."
    }

    /** Stáhne CZ titulkové kandidáty a sestaví box-dostupné SRT URL (`?key=<session>`) pro TV. */
    private suspend fun buildTvSubtitles(): List<FerrySubtitle> {
        val q = _uiState.value.pendingSubtitleQuery ?: return emptyList()
        if (!q.autoSearch) return emptyList()   // CONDUIT: dabovaný zdroj → na TV taky bez auto-titulků
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
    fun onPlaybackFailed(errorCode: String) {
        // RELAY (2026-07-19): RD/CDN odkaz je EFEMÉRNÍ — po čase vyprší nebo „zchladne" (RD evikce z cache) →
        // ExoPlayer `ERROR_CODE_IO_BAD_HTTP_STATUS` (HTTP 404) / jiná IO chyba. Zapamatovaný zdroj, co včera hrál,
        // pak „přestane jít" na VŠECH zařízeních. Manuální retry TÉHOŽ zdroje ale typicky projde (RD se zahřeje).
        // Proto: u IO/HTTP chyby zkus 1× RE-RESOLVE téhož zdroje (čerstvý odkaz z infoHash/comet) PŘED skokem
        // na jiný zdroj. Formát/kodek chyby (`isFormatError`) sem nepatří — soubor je fakt nehratelný.
        val cur = lastPlayedStream
        val key = cur?.let { ioRetryKey(it) }
        if (isRetriableIoError(errorCode) && cur != null && !key.isNullOrBlank() &&
            key !in ioRetriedKeys && isReResolvable(cur)) {
            ioRetriedKeys.add(key)
            timber.log.Timber.i("[RELAY] IO chyba ($errorCode) → 1× re-resolve téhož zdroje (efemérní odkaz)")
            replayFreshResolve(cur, CastTarget.LOCAL)
            return
        }
        // REPACK (SEZONA f3e, user 2026-08-01: „nedají se tyhle zdroje přesto použít a jen je nějak
        // obejít? Určitě jsou kvalitní"): vadný KONTEJNER/KODEK neznamená vadný film. 🔬 Změřeno na jeho
        // Bleach E6 — HEVC + AAC jsou v pořádku, padá to na 2× PGS titulcích a vloženém fontu. Server to
        // umí přebalit BEZ překódování (`-c copy`, 235 MB za 10 s), takže než utečeme na jiný release,
        // zkusíme zachránit ten, který si divák vybral. Jen JEDNOU na zdroj (guard) a jen u formátové chyby.
        val playedUrl = lastDeliveredUrl
        if (isFormatError(errorCode) && repackAllowed() && !playedUrl.isNullOrBlank() &&
            uploaderBaseUrl.isNotBlank() && repackTriedUrls.add(playedUrl)
        ) {
            timber.log.Timber.i("[REPACK] $errorCode → zkouším přebalit tentýž zdroj místo skoku na jiný")
            // Že se zdroj bez přebalu nepřehraje si poznamenáme AŽ podle verdiktu serveru (viz
            // [repackAndPlay]) — ne tady. Formátová chyba totiž nemusí znamenat vadný soubor:
            // 🔴 u Arcane S2E1 (2026-08-02) padl přehrávač na zdroji, který se teprve stahoval na RD,
            // a appka si z toho odnesla trvalé „tenhle se musí přebalovat" na souboru, který je zdravý.
            repackAndPlay(playedUrl)
            return
        }
        advancePastSource("Zdroj nešel přehrát, zkouším další", CastTarget.LOCAL, formatErrorCode = errorCode)
    }

    /**
     * Rozsviť „Hledám zdroj…" a nech ho svítit, dokud se zdroj reálně neobjeví (nebo do stropu času).
     * User 2026-08-03: *„Promazáno. Asi hledá. Nevidím nikde progress."* — jednorázová hláška v liště
     * nestačí, hledání běží na serveru desítky vteřin. *Když necháváme diváka čekat, musíme mu to říct
     * po celou dobu čekání, ne jednou na začátku.*
     */
    private fun markAutoSearching() {
        val item = _uiState.value.item ?: return
        _uiState.update { it.copy(autoSearching = true) }
        autoSearchJob?.cancel()
        autoSearchJob = viewModelScope.launch {
            val until = System.currentTimeMillis() + AUTO_SEARCH_MAX_MS
            while (System.currentTimeMillis() < until) {
                kotlinx.coroutines.delay(3_000)
                // User 2026-08-15: „ta hláška tam pořád běží a nepřekreslí se sama bez odchodu a
                // příchodu do appky" — `get()`/`savedKeys` čtou jen LOKÁLNÍ cache, tu nic samo
                // neobčerstvuje. Server auto-search doběhne na pozadí, ale appka se to dozví až při
                // příštím otevření karty (`syncNow` v `load()`). Zatímco běží banner, dotahuj aktivně.
                runCatching { workingSourceStore.syncNow() }
                val fresh = workingSourceStore.get(item.imdbId, item.tmdbId)
                val found = fresh != null ||
                    workingSourceStore.savedKeys.value.any { k -> item.tmdbId?.let { k.startsWith("tmdb:$it") } == true }
                if (found) {
                    // Ne jen zhasnout banner — rovnou ukázat, co se našlo (jinak spinner zmizí a
                    // karta dál tváří "Hledat zdroje" místo "Přehrát", dokud user neopustí a nevrátí se).
                    if (fresh != null && _uiState.value.item?.tmdbId == item.tmdbId && _uiState.value.rememberedSource == null) {
                        _uiState.update { it.copy(rememberedSource = fresh.stream) }
                    }
                    break
                }
            }
            _uiState.update { it.copy(autoSearching = false) }
        }
    }

    /**
     * Smí se čekat na serverový přebal? User 2026-08-03: *„Já žádný přebaly nechci!!!"* — přebal je
     * ticho a černá obrazovka na desítky vteřin (naměřeno 31 s), a divák většinou radši dostane jiný
     * zdroj hned. Výchozí VYPNUTO; kdo chce zachraňovat konkrétní release, zapne si to v Nastavení.
     */
    private var autoSearchJob: kotlinx.coroutines.Job? = null

    private fun repackAllowed(): Boolean = prefs.getBoolean(
        com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs.ALLOW_REPACK_KEY,
        com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs.DEFAULT_ALLOW_REPACK,
    )

    /**
     * REPACK (SEZONA f3e) — nech server přebalit zdroj a přehraj výsledek. Video i zvuk zůstávají 1:1
     * (`-c copy`), zahazují se jen obrazové titulky, fonty a metadata, na kterých přehrávač padá.
     * Průběh jde do UI (`autoAdvanceInfo`), takže divák nekouká na němou obrazovku. Selhání → původní
     * cesta (skok na další zdroj), aby nevznikla nová slepá ulička.
     */
    private fun repackAndPlay(srcUrl: String) {
        _uiState.update {
            it.copy(isResolvingStream = true, streamError = null,
                autoAdvanceInfo = "Tenhle soubor přehrávač neumí — přebaluji ho…")
        }
        viewModelScope.launch {
            // Stabilní identita → server trefí UŽ HOTOVÝ přebal místo toho, aby ho dělal znovu (playback
            // adresa se po re-resolve mění, takže sama o sobě je jako klíč k ničemu).
            val stableId = lastPlayedStream?.let { streamIdentity(it) }
            val started = uploaderDs.repackStart(uploaderBaseUrl, uploaderCookie, srcUrl, stableId)
            val jobId = started?.jobId?.takeIf { it.isNotBlank() }
            if (started == null || jobId == null || started.isFailed) {
                timber.log.Timber.w("[REPACK] start selhal (%s)", started?.error ?: "bez odpovědi")
                advancePastSource("Přebal se nepovedl, zkouším další zdroj", CastTarget.LOCAL)
                return@launch
            }
            var job: com.github.jankoran90.showlyfin.data.uploader.model.RepackJob = started
            var waited = 0L
            while (!job.isDone && !job.isFailed && waited < REPACK_TIMEOUT_MS) {
                delay(REPACK_POLL_MS)
                waited += REPACK_POLL_MS
                job = uploaderDs.repackStatus(uploaderBaseUrl, uploaderCookie, jobId) ?: job
                if (job.pct in 1..99) {
                    _uiState.update { it.copy(autoAdvanceInfo = "Přebaluji soubor… ${job.pct} %") }
                }
            }
            if (!job.isDone) {
                timber.log.Timber.w("[REPACK] neúspěch (status=%s, %s)", job.status, job.error ?: "-")
                _uiState.update { it.copy(isResolvingStream = false) }
                advancePastSource("Přebal se nepovedl, zkouším další zdroj", CastTarget.LOCAL)
                return@launch
            }
            // Teprve teď víme, jestli byl na vstupu opravdu vadný soubor. Když ano, příště jdeme rovnou
            // na přebal a divák nečeká na pád (user: „to mi vadí, že to není plynulé"). Když ne, paměť
            // naopak PROMAŽEME — jinak bychom zdravý zdroj přebalovali navždy kvůli jedné cizí příčině.
            lastPlayedStream?.let { s ->
                val identity = streamIdentity(s)
                when (job.inputClean) {
                    false -> repackNeededStore.remember(identity)
                    true -> {
                        repackNeededStore.forget(identity)
                        timber.log.Timber.i("[REPACK] vstupní soubor byl v pořádku → zdroj si NEpamatuji jako vadný")
                    }
                    null -> Unit   // server neví (starší přebal) → paměť nechme, jak je
                }
            }
            // `?key=` schválně — TV shell nemá cookie (týž vzor jako titulky / sdilej proxy).
            val base = uploaderBaseUrl.trimEnd('/')
            val key = java.net.URLEncoder.encode(uploaderCookie, "UTF-8")
            val url = "$base/api/repack/file/$jobId?key=$key"
            timber.log.Timber.i("[REPACK] hotovo → přehrávám přebalený soubor")
            _uiState.update { it.copy(autoAdvanceInfo = "Přebaleno — spouštím přehrávání") }
            // Popisek ber ze zapamatovaného (`pendingPlaybackTitle` je po spuštění přehrávače prázdný) —
            // jinak by lišta u dílu ukázala holý název seriálu místo „Bleach S1E6".
            deliverNow(url, lastPlaybackTitle.ifBlank { _uiState.value.item?.title.orEmpty() }, CastTarget.LOCAL)
        }
    }

    /**
     * REPACK — STABILNÍ otisk zdroje pro paměť „vyžaduje přebal" i pro serverový `job_id` přebalu.
     *
     * 🔴 Nesmí stát na playback URL: ta je u AIOStreams/RD podepsaná a po re-resolve JINÁ, takže by
     * paměť nikdy netrefila a server by týž díl přebaloval pořád dokola. Bere se proto identita obsahu:
     * torrent (`infoHash` + index souboru) → comet cesta → u proxy zdrojů addon + velikost + popis
     * (release string), doplněné o titul a díl.
     */
    private fun streamIdentity(s: UploaderStream): String {
        val item = _uiState.value.item
        val ep = episodeSelector?.let { "s${it.season}e${it.episode}" }.orEmpty()
        val core = s.infoHash?.takeIf { it.isNotBlank() }?.let { "ih:$it/${s.fileIdx}" }
            ?: s.cometPath?.takeIf { it.isNotBlank() }?.let { "cp:$it" }
            ?: ("rel:" + s.addon.orEmpty() + "/" + (s.quality.sizeGB ?: 0.0) + "/" +
                (s.description ?: s.name).orEmpty().replace("\n", " ").trim().take(120))
        val id = item?.imdbId?.takeIf { it.isNotBlank() } ?: item?.tmdbId?.toString().orEmpty()
        return "$id:$ep:$core"
    }

    /** RELAY: IO/HTTP chyba přehrávače = mrtvý/zchladlý odkaz nebo síť (NE vadný kontejner/kodek → to `isFormatError`). */
    private fun isRetriableIoError(code: String): Boolean = code.startsWith("ERROR_CODE_IO_")

    /** RELAY: klíč zdroje pro gate „už jsem re-resolvnul" (infoHash → comet → url). */
    private fun ioRetryKey(s: UploaderStream): String =
        s.infoHash?.takeIf { it.isNotBlank() }
            ?: s.cometPath?.takeIf { it.isNotBlank() }
            ?: s.url.orEmpty()

    /** RELAY: lze získat ČERSTVÝ odkaz? infoHash/comet (RD resolve), sdilej:// nebo ctv: (obojí samonosné). */
    private fun isReResolvable(s: UploaderStream): Boolean =
        !s.infoHash.isNullOrBlank() || !s.cometPath.isNullOrBlank() ||
            (s.url?.startsWith("sdilej://") == true) ||
            com.github.jankoran90.showlyfin.data.uploader.ctvIdecOrNull(s.url) != null

    /** RELAY: kontext pro RD resolve (stejný jako v [playStream]). */
    private fun buildResolveCtx(stream: UploaderStream): com.github.jankoran90.showlyfin.data.uploader.model.UploaderResolveContext? =
        _uiState.value.item?.let { item ->
            com.github.jankoran90.showlyfin.data.uploader.model.UploaderResolveContext(
                imdb = item.imdbId, mediaType = mediaTypeStr(item),
                season = episodeSelector?.season, episode = episodeSelector?.episode,
                resolution = stream.quality.resolution, sizeGB = stream.quality.sizeGB,
            )
        }

    /**
     * RELAY: přehraj TENTÝŽ zdroj, ale s ČERSTVÝM odkazem — obejde uloženou (možná vypršelou) direct URL a
     * vynutí nový resolve z infoHash/comet (sdilej:// = jen přestav samonosnou proxy). Když nelze / selže,
     * spadni na skok na další zdroj. `lastPlayedStream` NEmění (drží ho pro případný následný advance).
     */
    private fun replayFreshResolve(stream: UploaderStream, target: CastTarget) {
        val title = episodeSelector?.label
            ?: _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() }
            ?: _uiState.value.item?.title.orEmpty()
        val direct = stream.url
        if (direct?.startsWith("sdilej://") == true) {
            buildSdilejProxyUrl(direct)?.let { deliver(it, title, target); return }
        }
        // VLTAVA: ČT odkaz je krátkodobý ze své podstaty → obnova = prostě resolvnout `idec` znovu.
        com.github.jankoran90.showlyfin.data.uploader.ctvIdecOrNull(direct)?.let { idec ->
            _uiState.update { it.copy(isResolvingStream = true, streamError = null, autoAdvanceInfo = "Obnovuji zdroj…") }
            viewModelScope.launch {
                when (val r = ctvResolver.resolve(idec)) {
                    is com.github.jankoran90.showlyfin.data.uploader.CtvStreamResolver.Result.Ok ->
                        deliver(r.url, title, target)
                    else -> _uiState.update { it.copy(isResolvingStream = false, streamError = ctvError(r)) }
                }
            }
            return
        }
        _uiState.update { it.copy(isResolvingStream = true, streamError = null, autoAdvanceInfo = "Obnovuji zdroj…") }
        val ctx = buildResolveCtx(stream)
        viewModelScope.launch {
            // krátká pauza dá RD čas „zahřát" znovu zpřístupněný soubor (typický důvod přechodného 404).
            delay(1200)
            val fresh = runCatching {
                when {
                    !stream.cometPath.isNullOrBlank() -> uploaderDs.resolveCometStream(uploaderBaseUrl, uploaderCookie, stream.cometPath!!, ctx)
                    !stream.infoHash.isNullOrBlank() -> uploaderDs.resolveStream(uploaderBaseUrl, uploaderCookie, stream.infoHash!!, stream.fileIdx, ctx)
                    else -> null
                }
            }.getOrElse { e -> timber.log.Timber.w(e, "[RELAY] re-resolve selhal"); null }
            if (fresh.isNullOrBlank()) advancePastSource("Zdroj nešel obnovit, zkouším další", target)
            else deliver(fresh, title, target)
        }
    }

    /**
     * REPRISE (SHW-54): chyba přehrávače je v KONTEJNERU/KODEKU souboru (ne mrtvý zdroj / síť)?
     * `ERROR_CODE_PARSING_*` = vadný/nekompatibilní kontejner (např. Criterion MKV se zlib-komprimovanou
     * stopou `ContentCompAlgo 0`), `ERROR_CODE_DECOD*` = nepodporovaný kodek. U těchto NEskákat tiše
     * do Stremia — soubor je nehratelný, ale zdrojů bývá víc → nabídnout jiný release.
     */
    private fun isFormatError(code: String): Boolean =
        code.startsWith("ERROR_CODE_PARSING_") || code.startsWith("ERROR_CODE_DECOD")

    /**
     * Přeskoč na DALŠÍ okamžitě hratelný kandidát v UŽIVATELOVĚ pořadí (direct url / cached RD).
     * Volá CASCADE auto-advance po chybě přehrávání i WINNOW po detekci návnady. Cíl ([target])
     * se zachová, aby přeskočení při castu na TV pokračovalo zase na TV (ne lokálně).
     * Pure-downloadable přeskakujeme — auto-retry nemá tiše spustit víceminutový RD download.
     */
    private fun advancePastSource(message: String, target: CastTarget, formatErrorCode: String? = null) {
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
        } else if (list.isEmpty() && _uiState.value.item?.imdbId?.isNotBlank() == true) {
            // 🔴 SEZONA f3d: zdroj se pouštěl MIMO picker (zapamatovaný zdroj dílu/filmu, zdroj sezóny),
            // takže seznam alternativ vůbec neexistuje — CASCADE tak neměla kam postoupit a rovnou padala
            // na dialog / Stremio. Dotáhni zdroje a nech uživatele vybrat, místo slepé uličky.
            timber.log.Timber.i("[CASCADE] zdroj hrál mimo picker (prázdný seznam) → dotahuji alternativy ($message)")
            _uiState.update {
                it.copy(isResolvingStream = false, rdDownload = null, streamError = null,
                    // strict=false rovnou tady (ne přes `setStreamStrict`, ten by načetl zdroje podruhé).
                    streamStrict = false,
                    autoAdvanceInfo = "$message — hledám jiný zdroj…")
            }
            openStreamPicker()
        } else if (formatErrorCode != null && isFormatError(formatErrorCode)) {
            // REPRISE (SHW-54): Media3 selhal na KONTEJNERU/KODEKU (ne mrtvý zdroj) a žádný další
            // cached zdroj není → soubor je nehratelný (např. Criterion MKV se zlib stopou). Tichý skok
            // do Stremia mate ("vyskočí Stremio") → jasný dialog: zkus jiný release (zdrojů bývá víc,
            // jen necacheované — viz Old Joy: 25 zdrojů, jen 1 cached = ten nehratelný).
            timber.log.Timber.w("[REPRISE] $formatErrorCode = nehratelný kontejner/kodek, žádný další cached zdroj → dialog 'zkus jiný release' (z idx=$curIdx/${list.size})")
            _uiState.update { it.copy(
                isResolvingStream = false,
                rdDownload = null,
                incompatibleFormatMessage = "Tenhle soubor přehrávač neumí přehrát (nekompatibilní kontejner nebo kodek — třeba MKV s komprimovanou stopou, časté u Criterion / anime ripů).\n\nZdrojů bývá víc — zkus jiný release. Většina hraje i na TV a s našimi titulky; jen je často potřeba ho nejdřív stáhnout na RealDebrid.",
            ) }
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

    fun consumeIncompatibleFormat() = _uiState.update { it.copy(incompatibleFormatMessage = null) }

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

    /**
     * NOMAD (SHW-60) + HOARD (SHW-84): stáhnout TENTO film do telefonu (offline „na chatu“).
     * Priorita: vlastněný v Jellyfin knihovně → přímý JF static stream; jinak film se ZAPAMATOVANÝM
     * zdrojem → resolvni tentýž zdroj (co hraje přes Přehrát) na stažitelnou URL a stáhni ho.
     */
    fun downloadCurrentToDevice() {
        val s = _uiState.value
        val item = s.item ?: return
        val jfId = s.ownedJellyfinId
        if (item.type != MediaType.MOVIE) {
            // SEZONA (SHW-113): seriál se stahuje PO DÍLECH — díl musí být vybraný (a mít zapamatovaný
            // zdroj, stejná podmínka jako u filmu mimo knihovnu). Dřív tu byla natvrdo hláška „jen filmy".
            val sel = episodeSelector
            if (sel == null) {
                _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "U seriálu nejdřív otevři díl (Přehrát), pak ho půjde stáhnout.") }
                return
            }
            downloadEpisodeToDevice(item, sel)
            return
        }
        if (jfId == null) {
            // HOARD: mimo knihovnu → stáhni zapamatovaný zdroj.
            downloadRememberedToDevice(item, s.rememberedSource)
            return
        }
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        if (serverUrl.isBlank() || token.isBlank()) {
            _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Jellyfin není přihlášený.") }
            return
        }
        offlineManager.enqueue(
            com.github.jankoran90.showlyfin.data.offline.OfflineRequest(
                key = "jf_$jfId",
                title = item.title,
                subtitle = item.year?.toString(),
                type = com.github.jankoran90.showlyfin.data.offline.OfflineRequest.TYPE_MOVIE,
                sourceLabel = "Knihovna",
                videoUrl = "$serverUrl/Videos/$jfId/stream?static=true&api_key=$token",
                posterUrl = "$serverUrl/Items/$jfId/Images/Primary?api_key=$token",
                imdb = item.imdbId,
                tmdb = item.tmdbId?.toInt(),
            ),
        )
        _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Stahuji do telefonu — sleduj v sekci Stažené.") }
    }

    /**
     * SEZONA (SHW-113): stáhni JEDEN DÍL seriálu do telefonu. Tenký wrapper nad [enqueueOneEpisode]
     * (sdílené jádro s dávkovým stahováním sezóny/řady, viz [downloadSeasonToDevice]) — jen řeší
     * UX zprávu pro jednotlivý klik z menu karty.
     */
    private fun downloadEpisodeToDevice(item: MediaItem, sel: EpisodeSelector) {
        _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Připravuji stahování dílu…") }
        viewModelScope.launch {
            val result = enqueueOneEpisode(item, sel.season, sel.episode)
            _uiState.update {
                it.copy(
                    captureMessage = when (result) {
                        EpisodeEnqueueResult.LIBRARY -> "Stahuji díl z knihovny — sleduj v sekci Stažené."
                        EpisodeEnqueueResult.REMEMBERED -> "Stahuji díl do telefonu — sleduj v sekci Stažené."
                        EpisodeEnqueueResult.NO_SOURCE -> "Nejdřív díl přehraj a zapamatuj zdroj (⭐), pak půjde stáhnout do telefonu."
                    },
                )
            }
        }
    }

    /**
     * SEZONA-DÁVKA (user 2026-08-21: „udělej mi long press stažení na epizodě") — stáhni KONKRÉTNÍ
     * díl ze seznamu epizod (dlouhý stisk na řádku), bez ohledu na to, jestli je zrovna „otevřený"/
     * vybraný přes Přehrát (na rozdíl od [downloadEpisodeToDevice], co potřebuje [episodeSelector]).
     */
    fun downloadEpisode(season: Int, episode: Int) {
        val item = _uiState.value.item ?: return
        _uiState.update { it.copy(captureMessage = "Připravuji stahování dílu…") }
        viewModelScope.launch {
            val result = enqueueOneEpisode(item, season, episode)
            _uiState.update {
                it.copy(
                    captureMessage = when (result) {
                        EpisodeEnqueueResult.LIBRARY -> "Stahuji díl z knihovny — sleduj v sekci Stažené."
                        EpisodeEnqueueResult.REMEMBERED -> "Stahuji díl do telefonu — sleduj v sekci Stažené."
                        EpisodeEnqueueResult.NO_SOURCE -> "Tenhle díl ještě nemáš přehraný/zapamatovaný — nejdřív ho spusť a zapamatuj zdroj (⭐)."
                    },
                )
            }
        }
    }

    /**
     * SEZONA-DÁVKA (user 2026-08-21: „stahovat filmy a seriály celé i po sezónach i po epizodách") —
     * stáhni VŠECHNY díly JEDNÉ sezóny. [seasonNumber] = null → aktuálně vybraná (nebo první, není-li
     * žádná vybraná). Sdílí frontu s jednotlivým stahováním ([OfflineDownloadManager.enqueue] je samo
     * idempotentní + gatuje souběh) — díly BEZ zdroje (nikdy nepřehrané/nezapamatované) se prostě
     * přeskočí, ne že by celá dávka spadla.
     */
    fun downloadSeasonToDevice(seasonNumber: Int? = null) {
        val item = _uiState.value.item ?: return
        val tmdbId = item.tmdbId
        val season = seasonNumber ?: _uiState.value.selectedSeason
            ?: _uiState.value.seasons.firstOrNull { it.season_number >= 1 }?.season_number
        if (season == null) return
        _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Připravuji stahování sezóny…") }
        viewModelScope.launch {
            val episodes = if (season == _uiState.value.selectedSeason && _uiState.value.seasonEpisodes.isNotEmpty()) {
                _uiState.value.seasonEpisodes
            } else {
                tmdbId?.let { tmdbApi.fetchSeason(it, season)?.episodes }.orEmpty()
            }
            enqueueEpisodesBatch(item, episodes)
        }
    }

    /** SEZONA-DÁVKA: stáhni VŠECHNY díly VŠECH sezón (celá řada). Postupně, sezóna po sezóně. */
    fun downloadAllEpisodesToDevice() {
        val item = _uiState.value.item ?: return
        val tmdbId = item.tmdbId ?: return
        val seasonNumbers = _uiState.value.seasons.map { it.season_number }.filter { it >= 1 }
        if (seasonNumbers.isEmpty()) return
        _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Připravuji stahování celé řady…") }
        viewModelScope.launch {
            val episodes = seasonNumbers.flatMap { s ->
                if (s == _uiState.value.selectedSeason && _uiState.value.seasonEpisodes.isNotEmpty()) {
                    _uiState.value.seasonEpisodes
                } else {
                    tmdbApi.fetchSeason(tmdbId, s)?.episodes.orEmpty()
                }
            }
            enqueueEpisodesBatch(item, episodes)
        }
    }

    /** Společné jádro obou dávek výš — enqueue každého dílu zvlášť, sečti výsledky do jedné zprávy. */
    private suspend fun enqueueEpisodesBatch(item: MediaItem, episodes: List<com.github.jankoran90.showlyfin.data.tmdb.model.TmdbEpisode>) {
        var enqueued = 0
        var skipped = 0
        for (ep in episodes.sortedWith(compareBy({ it.season_number ?: 0 }, { it.episode_number }))) {
            val season = ep.season_number ?: continue
            if (enqueueOneEpisode(item, season, ep.episode_number) == EpisodeEnqueueResult.NO_SOURCE) skipped++ else enqueued++
        }
        _uiState.update {
            it.copy(
                captureMessage = if (enqueued > 0) {
                    "Do fronty přidáno $enqueued dílů" +
                        (if (skipped > 0) " ($skipped bez zapamatovaného zdroje přeskočeno)" else "") +
                        " — sleduj v sekci Stažené."
                } else {
                    "Žádný díl nešlo stáhnout — zkus je nejdřív jednotlivě přehrát a zapamatovat zdroj (⭐)."
                },
            )
        }
    }

    private enum class EpisodeEnqueueResult { LIBRARY, REMEMBERED, NO_SOURCE }

    /**
     * SEZONA (SHW-113) + SEZONA-DÁVKA: sdílené jádro stažení JEDNOHO dílu — volá ho jak
     * [downloadEpisodeToDevice] (jednotlivý klik), tak dávkové stahování sezóny/řady.
     * Pořadí: **díl z Jellyfin knihovny** (hraje ze serveru, tedy nejjistější zdroj) → jinak
     * zapamatovaný zdroj dílu ([WorkingSourceStore.get] se season/episode) → jinak (jen pokud jde
     * o PRÁVĚ otevřený díl) rozehraný `rememberedSource` z uiState. Bez UI zpráv — ty si řeší volající.
     */
    private suspend fun enqueueOneEpisode(item: MediaItem, season: Int, episode: Int): EpisodeEnqueueResult {
        val epJfId = _uiState.value.episodeJellyfinIds[season to episode]
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        if (epJfId != null && serverUrl.isNotBlank() && token.isNotBlank()) {
            offlineManager.enqueue(
                com.github.jankoran90.showlyfin.data.offline.OfflineRequest(
                    key = "jf_$epJfId",
                    title = item.title,
                    subtitle = "S${season}E${episode}",
                    type = com.github.jankoran90.showlyfin.data.offline.OfflineRequest.TYPE_EPISODE,
                    sourceLabel = "Knihovna",
                    videoUrl = "$serverUrl/Videos/$epJfId/stream?static=true&api_key=$token",
                    posterUrl = "$serverUrl/Items/$epJfId/Images/Primary?api_key=$token",
                    imdb = item.imdbId,
                    tmdb = item.tmdbId?.toInt(),
                    season = season,
                    episode = episode,
                ),
            )
            return EpisodeEnqueueResult.LIBRARY
        }
        val currentSel = episodeSelector
        val source = workingSourceStore.get(item.imdbId, item.tmdbId, season, episode)?.stream
            ?: (_uiState.value.rememberedSource.takeIf { currentSel?.season == season && currentSel?.episode == episode })
        if (source == null) return EpisodeEnqueueResult.NO_SOURCE
        val url = resolveDownloadUrl(item, source) ?: return EpisodeEnqueueResult.NO_SOURCE
        val poster = (item.posterPath ?: _uiState.value.movieDetails?.poster_path)?.let {
            if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w342$it"
        }
        offlineManager.enqueue(
            com.github.jankoran90.showlyfin.data.offline.OfflineRequest(
                key = episodeOfflineKey(item, season, episode),
                title = item.title,
                subtitle = "S${season}E${episode}",
                type = com.github.jankoran90.showlyfin.data.offline.OfflineRequest.TYPE_EPISODE,
                sourceLabel = "Zapamatovaný zdroj",
                videoUrl = url,
                posterUrl = poster,
                imdb = item.imdbId,
                tmdb = item.tmdbId?.toInt(),
                season = season,
                episode = episode,
            ),
        )
        return EpisodeEnqueueResult.REMEMBERED
    }

    /** HOARD (SHW-84): stáhni zapamatovaný zdroj filmu (týž, co hraje přes Přehrát) do telefonu. */
    private fun downloadRememberedToDevice(item: MediaItem, source: UploaderStream?) {
        if (source == null) {
            _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Nejdřív zdroj přehraj a zapamatuj (⭐), pak půjde stáhnout do telefonu.") }
            return
        }
        _uiState.update { it.copy(showDownloadMenu = false, captureMessage = "Připravuji stahování zdroje…") }
        viewModelScope.launch {
            val url = resolveDownloadUrl(item, source)
            if (url.isNullOrBlank()) {
                _uiState.update { it.copy(captureMessage = "Zdroj se nepodařilo připravit ke stažení — zkus ho nejdřív přehrát a zapamatovat znovu.") }
                return@launch
            }
            val poster = (item.posterPath ?: _uiState.value.movieDetails?.poster_path)?.let {
                if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w342$it"
            }
            offlineManager.enqueue(
                com.github.jankoran90.showlyfin.data.offline.OfflineRequest(
                    key = movieOfflineKey(item),
                    title = item.title,
                    subtitle = item.year?.toString(),
                    type = com.github.jankoran90.showlyfin.data.offline.OfflineRequest.TYPE_MOVIE,
                    sourceLabel = "Zapamatovaný zdroj",
                    videoUrl = url,
                    posterUrl = poster,
                    imdb = item.imdbId,
                    tmdb = item.tmdbId?.toInt(),
                ),
            )
            _uiState.update { it.copy(captureMessage = "Stahuji do telefonu — sleduj v sekci Stažené.") }
        }
    }

    /**
     * HOARD: resolvni zapamatovaný [source] na PŘÍMOU stažitelnou HTTP URL — stejné cesty jako `playStream`,
     * ale bez CASCADE/probe (zapamatovaný zdroj už prokazatelně hrál): sdilej:// proxy → přímá url →
     * cached Comet (RD) → uložený/cached infoHash (RD). Necachovaný torrent = null (vrátí hlášku).
     * Nikdy nehází.
     */
    private suspend fun resolveDownloadUrl(item: MediaItem, source: UploaderStream): String? {
        val direct = source.url
        if (direct != null && direct.startsWith("sdilej://")) return buildSdilejProxyUrl(direct)
        if (!direct.isNullOrBlank()) return direct
        val ctx = com.github.jankoran90.showlyfin.data.uploader.model.UploaderResolveContext(
            imdb = item.imdbId,
            mediaType = mediaTypeStr(item),
            resolution = source.quality.resolution,
            sizeGB = source.quality.sizeGB,
        )
        val cometPath = source.cometPath
        if (!cometPath.isNullOrBlank()) {
            runCatching { uploaderDs.resolveCometStream(uploaderBaseUrl, uploaderCookie, cometPath, ctx) }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val infoHash = source.infoHash
        if (!infoHash.isNullOrBlank()) {
            return runCatching { uploaderDs.resolveStream(uploaderBaseUrl, uploaderCookie, infoHash, source.fileIdx, ctx) }
                .getOrNull()?.takeIf { it.isNotBlank() }
        }
        return null
    }

    /** NOMAD: smaž offline stažení tohoto titulu (z menu Stáhnout, když je už staženo). */
    fun deleteOfflineCurrent() {
        currentOfflineKey()?.let { offlineManager.delete(it) }
        _uiState.update { it.copy(showDownloadMenu = false) }
    }

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
        // QUARRY (SHW-79): předvyplnění ruční úpravy — český název (pro Sdílej.cz autoritativní) + rok z metadat.
        val defaultTitle = _uiState.value.tmdbCzTitle?.takeIf { it.isNotBlank() }
            ?: item.titleCz?.takeIf { it.isNotBlank() }
            ?: _uiState.value.csfdTitle?.takeIf { it.isNotBlank() }
            ?: item.title
        _uiState.update {
            it.copy(
                showDownloadMenu = false, showSdilejPicker = true, isLoadingSdilej = true,
                sdilejError = null, sdilejStreams = emptyList(),
                sdilejDefaultTitle = defaultTitle, sdilejDefaultYear = item.year,
            )
        }
        viewModelScope.launch {
            runSdilejSearch(mediaTypeStr(item), imdb, item.title, titleCs, item.year, allowYearRetry = prefs.getBoolean("sdilej_year_pm1", true))
        }
    }

    /** QUARRY: ruční přehledání s uživatelem upraveným názvem/rokem (přesně, bez ±1). */
    fun researchSdilej(title: String, year: Int?) {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId?.takeIf { it.isNotBlank() } ?: return
        if (uploaderBaseUrl.isBlank()) return
        val q = title.trim().ifBlank { item.title }
        _uiState.update {
            it.copy(
                isLoadingSdilej = true, sdilejError = null, sdilejStreams = emptyList(),
                sdilejDefaultTitle = q, sdilejDefaultYear = year,
            )
        }
        viewModelScope.launch {
            // ruční dotaz: upravený název jde jako český i originální, rok přesně dle zadání
            runSdilejSearch(mediaTypeStr(item), imdb, q, q, year, allowYearRetry = false)
        }
    }

    /**
     * QUARRY: sdílené hledání zdroje na Sdílej.cz (pro Stáhnout picker). Při [allowYearRetry] použije
     * automatickou korekci roku ±1 (viz [sdilejStreamsWithRetry]).
     */
    private suspend fun runSdilejSearch(
        mediaType: String, imdb: String, title: String, titleCs: String, year: Int?, allowYearRetry: Boolean,
    ) {
        runCatching {
            if (allowYearRetry) sdilejStreamsWithRetry(mediaType, imdb, title, titleCs, year)
            else uploaderDs.getSdillejStreams(uploaderBaseUrl, uploaderCookie, mediaType, imdb, title, titleCs, year)
        }
            .onSuccess { list -> timber.log.Timber.i("[Sdilej] streams=${list.size} imdb=$imdb yearRetry=$allowYearRetry"); _uiState.update { it.copy(isLoadingSdilej = false, sdilejStreams = list, sdilejError = if (list.isEmpty()) "Na Sdílej.cz nic nenalezeno." else null) } }
            .onFailure { e -> timber.log.Timber.w(e, "[Sdilej] getSdillejStreams FAILED imdb=$imdb url=$uploaderBaseUrl"); _uiState.update { it.copy(isLoadingSdilej = false, sdilejError = e.message ?: "Chyba Sdílej.cz") } }
    }

    /**
     * QUARRY (SHW-79): sdilej streamy s automatickou korekcí roku ±1 při nule nálezech
     * (metadata roku z TMDB/IMDB bývají o rok mimo). Řídí pref `sdilej_year_pm1`. Nikdy nehází.
     */
    private suspend fun sdilejStreamsWithRetry(
        mediaType: String, imdb: String, title: String, titleCs: String, year: Int?,
        season: Int? = null, episode: Int? = null, origTitle: String = "",
    ): List<UploaderStream> {
        val primary = runCatching {
            uploaderDs.getSdillejStreams(uploaderBaseUrl, uploaderCookie, mediaType, imdb, title, titleCs, year, season, episode, origTitle)
        }.getOrDefault(emptyList())
        if (primary.isNotEmpty() || year == null || !prefs.getBoolean("sdilej_year_pm1", true)) return primary
        val merged = primary.toMutableList()
        val seen = merged.mapNotNull { it.url ?: it.name }.toMutableSet()
        for (y in listOf(year - 1, year + 1)) {
            val extra = runCatching {
                uploaderDs.getSdillejStreams(uploaderBaseUrl, uploaderCookie, mediaType, imdb, title, titleCs, y, season, episode, origTitle)
            }.getOrDefault(emptyList())
            for (s in extra) {
                val k = s.url ?: s.name
                if (k != null && seen.add(k)) merged.add(s)
            }
        }
        return merged
    }

    /**
     * QUARRY (SHW-79): ruční přehledání Sdílej.cz z play pickeru (cesta CZ dabing) — sloučí nově
     * nalezené zdroje do seznamu streamů, takže se objeví ve filtrovaném pickeru.
     */
    fun researchSdilejStreams(title: String, year: Int?) {
        val item = _uiState.value.item ?: return
        val imdb = item.imdbId ?: return
        if (uploaderBaseUrl.isBlank()) return
        val q = title.trim().ifBlank { item.title }
        _uiState.update { it.copy(sdilejDefaultTitle = q, sdilejDefaultYear = year) }
        viewModelScope.launch {
            val extra = runCatching {
                uploaderDs.getSdillejStreams(uploaderBaseUrl, uploaderCookie, mediaTypeStr(item), imdb, q, q, year)
            }.getOrDefault(emptyList())
            _uiState.update { st ->
                val seen = st.streams.mapNotNull { it.url ?: it.name }.toMutableSet()
                val add = extra.filter { val k = it.url ?: it.name; k != null && seen.add(k) }
                st.copy(streams = streamPresetStore.orderStreams(st.streams + add), streamError = if ((st.streams + add).isEmpty()) "Na Sdílej.cz nic nenalezeno." else null)
            }
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
                // NOMAD: badge offline stažení hned po načtení knihovny (film).
                offlineState = matchedJellyfinId
                    ?.takeIf { item.type == MediaType.MOVIE }
                    ?.let { offlineManager.stateFor("jf_$it") }
                    ?: com.github.jankoran90.showlyfin.data.offline.OfflineState(),
            )
        }
        recomputeMergedCollection(item)

        // TV DETAIL REDESIGN (OTA 299): per-epizoda watched z Jellyfinu (jen seriál v knihovně) → horizontální
        // řada epizod se zhlédnutým/progress + auto-scroll na první nezhlédnutou (getNextUp).
        if (item.type == MediaType.SHOW && matchedJellyfinId != null) {
            val status = runCatching { jellyfinLibraryService.getSeriesEpisodeStatus(matchedJellyfinId) }.getOrNull()
            if (status != null && _uiState.value.item?.isSameAs(item) == true) {
                _uiState.update {
                    it.copy(
                        episodeWatched = status.watched,
                        episodeProgress = status.progress,
                        nextUpEpisode = status.nextUp,
                        episodeJellyfinIds = status.episodeIds,
                    )
                }
                // Otevři rovnou sezónu s další nezhlédnutou epizodou, pokud je už načtená.
                val nextSeason = status.nextUp?.first
                if (nextSeason != null &&
                    _uiState.value.seasons.any { s -> s.season_number == nextSeason } &&
                    _uiState.value.selectedSeason != nextSeason
                ) {
                    selectSeason(nextSeason)
                }
            }
        }
        // SEZONA (SHW-113): seriál MIMO Jellyfin knihovnu (RD/torrent) neměl fajfky odkud vzít — Jellyfin
        // je jediný, kdo je uměl. Sledovanost po dílech dotáhneme z Traktu přes server.
        if (item.type == MediaType.SHOW && matchedJellyfinId == null) {
            loadTraktEpisodeProgress(item)
        }
    }

    /**
     * SEZONA (SHW-113): fajfky u dílů + „další díl" z Trakt historie (server endpoint `trakt/show-progress`).
     * Používá se, když seriál NENÍ v Jellyfin knihovně — jinak má přednost Jellyfin (drží i rozkoukanost).
     * `episodeJellyfinIds` ZÁMĚRNĚ nechává prázdné: bez id nemá `toggleEpisodeWatched` kam zapsat, takže
     * fajfka zůstane jen ke čtení (zápis do Traktu jede při dokoukání ze streamu, vydáno ve vc126).
     * Chyba/offline → NEsaháme na stav (mazat fajfky kvůli výpadku sítě je horší než je mít staré).
     */
    private suspend fun loadTraktEpisodeProgress(item: MediaItem, fresh: Boolean = false) {
        val profile = prefs.getString("jellyfin_user_id", "").orEmpty()
        if (profile.isBlank() || uploaderBaseUrl.isBlank()) return
        if (item.imdbId.isNullOrBlank() && (item.tmdbId ?: 0L) <= 0L) return
        val res = runCatching {
            uploaderDs.showProgress(uploaderBaseUrl, uploaderCookie, profile, item.imdbId, item.tmdbId, fresh)
        }.getOrNull()
        if (res == null || !res.ok) {
            timber.log.Timber.i("[SEZONA] Trakt progress nedostupný (%s) — fajfky nechávám být", res?.error ?: "chyba")
            return
        }
        // Trakt vrací 200 s prázdným výsledkem i pro seriál, který NEZNÁ (ověřeno). `aired == 0` = nedohledáno
        // → prázdnem bychom přepsali platné fajfky (přesně ta „tichá ztráta dat" jako `getOrElse { emptyList() }`).
        if (res.aired <= 0) {
            timber.log.Timber.i("[SEZONA] Trakt seriál nezná (aired=0) — stav nechávám beze změny")
            return
        }
        if (_uiState.value.item?.isSameAs(item) != true) return
        val watched = HashSet<Pair<Int, Int>>()
        for (s in res.seasons) for (e in s.episodes) if (e.completed) watched.add(s.number to e.number)
        val next = res.nextEpisode?.let { n ->
            val s = n.season; val e = n.number
            if (s != null && e != null) s to e else null
        }
        timber.log.Timber.i("[SEZONA] Trakt progress: %d zhlédnutých dílů, další=%s", watched.size, next?.toString() ?: "-")
        _uiState.update { it.copy(episodeWatched = watched, nextUpEpisode = next) }
        // Otevři rovnou sezónu s dalším nezhlédnutým dílem (parita s Jellyfin větví výš).
        val nextSeason = next?.first
        if (nextSeason != null &&
            _uiState.value.seasons.any { s -> s.season_number == nextSeason } &&
            _uiState.value.selectedSeason != nextSeason
        ) {
            selectSeason(nextSeason)
        }
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
                backdropUrl = part.backdrop_path?.let { "https://image.tmdb.org/t/p/w780$it" },
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

    /** „Zkusit ČSFD znovu" (⋮ menu, user 2026-08-20 — obrázky/recenze u ČSFD ukazovaly jiný titul):
     * zahodí appkou lokálně vyřešené (a jednou třeba špatně) ČSFD id a vynutí čerstvé hledání. */
    fun retryCsfd() {
        val item = _uiState.value.item ?: return
        val czTitle = _uiState.value.tmdbCzTitle
        viewModelScope.launch {
            csfdRepository.forceRefreshCsfdId(
                item.imdbId.orEmpty(), item.tmdbId,
                czTitle?.takeIf { it.isNotBlank() } ?: item.title, item.year ?: 0,
            )
            _uiState.update {
                it.copy(isCsfdLoading = true, csfdId = null, csfdRating = null, csfdPlot = null, csfdReviews = emptyList(), csfdGallery = emptyList())
            }
            loadCsfd(item, czTitle)
        }
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
        // Jak dlouho nechat svítit „Hledám zdroj…", než to vzdáme (auto-hledání běží na serveru).
        const val AUTO_SEARCH_MAX_MS = 5 * 60 * 1000L
        // Kolik titulkových kandidátů poslat na TV (MPV je nasideloaduje, výběr na TV = F3).
        const val MAX_TV_SUBTITLES = 3
        // WINNOW item 1b: minimální reálná velikost přehrávatelného filmu/epizody (30 MB). Pod tím
        // je to návnada/decoy (Comet/RD servíruje ~stovky KB navzdory deklarované velikosti).
        const val MIN_PLAYABLE_BYTES = 30_000_000L
        // CELLULOID (SHW-98) — musí sedět s SettingsViewModel.KEY_AUTO_REFRESH_SOURCES (jiný modul, sdílený jen string).
        const val KEY_AUTO_REFRESH_SOURCES = "auto_refresh_sources_enabled"
        // D-c: applicationId Filmy TV appky (release) na boxu — kterou probouzí wake scéna do popředí.
        const val FILMY_TV_PACKAGE = "com.github.jankoran90.filmy"
        // REPACK (SEZONA f3e): jak často se ptát na průběh přebalu a kdy to vzdát. Anime díl (~240 MB)
        // je otázka desítek vteřin, u velkého souboru se čeká déle — proto štědrý strop.
        const val REPACK_POLL_MS = 2_000L
        const val REPACK_TIMEOUT_MS = 8L * 60 * 1000
    }
}

/** Plan FERRY: kam doručit resolvnutý stream — lokální přehrávač telefonu, TV (yellyfin box), nebo
 *  FILMYCAST = do Filmy appky na TV (fronta příkazů na backendu, TV shell ji pollí). */
enum class CastTarget { LOCAL, TV, FILMY_TV }
