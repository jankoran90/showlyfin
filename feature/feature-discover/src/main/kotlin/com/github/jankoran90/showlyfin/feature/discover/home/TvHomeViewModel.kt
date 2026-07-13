package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeLayoutStore
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams.boolParam
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType
import com.github.jankoran90.showlyfin.core.domain.home.LibrarySummary
import com.github.jankoran90.showlyfin.core.domain.home.SidebarEntry
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.AuthorizedTraktRemoteDataSource
import com.github.jankoran90.showlyfin.data.trakt.TraktRemoteDataSource
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.feature.discover.mapper.toMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaType as JfMediaType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * TENFOOT — TV DOMOV REDESIGN. Agreguje obsah konfigurovatelných řad domova podle [HomeLayoutStore].
 * Umístěn ve feature-discover (má Trakt+TMDB+Jellyfin+Favorites deps i `toMediaItem` mappery).
 *
 * Jellyfin knihovny (JELLYFIN_LIBRARIES) tu NEŘEŠÍME — ty render interleavuje přes existující
 * `LibraryRowsViewModel` (feature-jellyfin-browser). Tady jen CONTINUE_WATCHING / NEXT_UP / DISCOVER /
 * FAVORITES. Lazy per řada ([ensureRowLoaded]) — TMDB enrich je drahý, nenačítat vše naráz.
 */
@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val store: HomeLayoutStore,
    private val traktApi: TraktRemoteDataSource,
    private val authorizedTraktApi: AuthorizedTraktRemoteDataSource,
    private val traktLoader: TraktRowLoader,
    private val tmdb: TmdbRemoteDataSource,
    private val favorites: FavoritesStore,
    private val workingSources: WorkingSourceStore,
    private val profileRepository: ProfileRepository,
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** Řady k vykreslení (jen zapnuté, v uživatelově pořadí). JF knihovny render řeší zvlášť. */
    val rowConfigs: StateFlow<List<HomeRowConfig>> = store.rows
        .map { list -> list.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.rows.value.filter { it.enabled })

    /** VŠECHNY řady (i vypnuté) pro inline editor. */
    val allRows: StateFlow<List<HomeRowConfig>> = store.rows
    val sidebar: StateFlow<List<SidebarEntry>> = store.sidebar

    // COUCH per-profil — Trakt sekce/řady jen když AKTIVNÍ profil má vlastní Trakt token v balíku
    // (deti až po vlastním device-loginu → dětský Trakt). Konzumenti (TvShell, loadOnce) beze změny.
    private fun hasTrakt(c: ProfileConfig): Boolean = !c.credentials.trakt?.accessToken.isNullOrBlank()
    val traktAllowed: StateFlow<Boolean> = profileRepository.activeConfig
        .map { hasTrakt(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), hasTrakt(profileRepository.activeConfig.value))

    /** Id aktivního profilu — TvHomeScreen na jeho změnu přenačte i JF knihovní řady (LibraryRowsViewModel). */
    val activeProfileId: StateFlow<Long?> = profileRepository.activeProfile
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profileRepository.activeProfile.value?.id)

    /** Netflix immersive pozadí (fokusovaná karta řídí fanart). */
    val immersiveBackground: StateFlow<Boolean> = store.immersiveBackground

    /** OTA 299: immersive hlavička nahoře (název/rok/popis fokusované karty) — oddělený přepínač od pozadí. */
    val immersiveHeader: StateFlow<Boolean> = store.immersiveHeader
    fun setImmersiveHeader(enabled: Boolean) = store.setImmersiveHeader(enabled)

    // ── Inline editor (Kodi-like) — pass-through na [HomeLayoutStore] ──
    fun moveRow(id: String, up: Boolean) = store.move(id, up)
    fun setRowEnabled(id: String, enabled: Boolean) = store.setEnabled(id, enabled)
    fun updateRow(config: HomeRowConfig) = store.updateRow(config)
    fun addRow(config: HomeRowConfig) = store.addRow(config)
    fun removeRow(id: String) = store.removeRow(id)
    fun resetRows() = store.resetRows()
    fun moveSidebar(item: String, up: Boolean) = store.moveSidebar(item, up)
    fun setSidebarEnabled(item: String, enabled: Boolean) = store.setSidebarEnabled(item, enabled)
    fun setImmersiveBackground(enabled: Boolean) = store.setImmersiveBackground(enabled)

    /** Seed-once per Jellyfin knihovna (řady per knihovna). Voláno z UI po načtení seznamu knihoven. */
    fun syncLibraries(libraries: List<LibrarySummary>) = store.syncLibraries(libraries)

    private val _states = MutableStateFlow<Map<String, HomeRowState>>(emptyMap())
    val states: StateFlow<Map<String, HomeRowState>> = _states.asStateFlow()

    private val loadedHash = mutableMapOf<String, Int>()
    private val jobs = mutableMapOf<String, Job>()

    init {
        // COUCH per-profil: každý profil má vlastní layout domova. Nejdřív přepni store na layout profilu
        // (i iniciálně), pak (na ZMĚNU) přenačti obsah — jeden collector = pořadí switchProfile → reload.
        var firstProfileEmit = true
        profileRepository.activeProfile
            .onEach { p ->
                store.switchProfile(p?.id)
                if (firstProfileEmit) firstProfileEmit = false
                else {
                    android.util.Log.i("COUCH_Home", "profil změněn → ${p?.name} (id=${p?.id}, trakt=${traktAllowed.value}) → reload")
                    reloadAllRows()
                }
            }
            .launchIn(viewModelScope)
    }

    /** Zahoď cache řad a přenačti všechny aktuálně zapnuté (po přepnutí profilu / vynuceně). */
    fun reloadAllRows() {
        android.util.Log.i("COUCH_Home", "reloadAllRows: ${rowConfigs.value.size} řad")
        loadedHash.clear()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        _states.value = emptyMap()
        rowConfigs.value.forEach { ensureRowLoaded(it) }
    }

    /** Zavolej z UI, když řada vstoupí do viewportu. Reaguje na změnu configu (editor) = reload. */
    fun ensureRowLoaded(config: HomeRowConfig) {
        if (loadedHash[config.id] == config.hashCode()) return
        loadedHash[config.id] = config.hashCode()
        jobs.remove(config.id)?.cancel()
        _states.update { it + (config.id to (it[config.id]?.copy(config = config, loading = true)
            ?: HomeRowState(config, loading = true))) }
        jobs[config.id] = viewModelScope.launch {
            when (config.source) {
                // FAVORITES = reaktivní (per-profil sync běží asynchronně) → sleduj tok.
                HomeRowSourceType.FAVORITES -> {
                    favorites.refresh()
                    favorites.items.collect { list ->
                        val items = list.filter { it.kind == FavoriteKind.MOVIE }.map { fav ->
                            HomeRowItem(
                                key = "fav_${fav.id}",
                                title = fav.name,
                                year = fav.year,
                                posterUrl = fav.imageUrl,
                                landscapeUrl = null,
                                mediaItem = stub(fav.id, fav.name, fav.year, isShow = false),
                            )
                        }
                        emit(config, applyOps(items, config))
                    }
                }
                else -> {
                    val items = runCatching { loadOnce(config) }
                        .onFailure { Timber.w(it, "[TvHome] load '${config.id}' selhal") }
                        .getOrElse { emptyList() }
                    emit(config, applyOps(items, config))
                }
            }
        }
    }

    private fun emit(config: HomeRowConfig, items: List<HomeRowItem>) {
        _states.update { it + (config.id to HomeRowState(config, items = items, loading = false)) }
    }

    private suspend fun loadOnce(config: HomeRowConfig): List<HomeRowItem> {
        // COUCH R2: zamčený/dětský profil nevidí žádné Trakt řady (watchlist/historie/seznam/couchmonkey).
        if (config.source in TRAKT_SOURCES && !traktAllowed.value) {
            android.util.Log.i("COUCH_Home", "Trakt řada '${config.id}' skryta — zamčený profil")
            return emptyList()
        }
        return when (config.source) {
        HomeRowSourceType.DISCOVER -> loadDiscover(config)
        HomeRowSourceType.CONTINUE_WATCHING -> loadJellyfin(config) { userUuid ->
            resumeItems(userUuid, config.limit)
        }
        HomeRowSourceType.NEXT_UP -> loadJellyfin(config) { userUuid ->
            nextUpItems(userUuid, config.limit)
        }
        // Sloučené Pokračovat + Další díly — resume má přednost, dedup dle seriálu/položky.
        HomeRowSourceType.CONTINUE_WATCHING_COMBINED -> loadJellyfin(config) { userUuid ->
            val seen = mutableSetOf<String>()
            (resumeItems(userUuid, config.limit) + nextUpItems(userUuid, config.limit))
                .filter { dto -> seen.add((dto.seriesId ?: dto.id).toString()) }
                .take(config.limit)
        }
        // „Nejnovější v <knihovna>" — getLatestMedia pro konkrétní knihovnu.
        HomeRowSourceType.RECENTLY_ADDED -> {
            val parent = config.params[HomeRowParams.LIBRARY_ID].toUuidOrNull()
            if (parent == null) emptyList() else loadJellyfin(config) { userUuid ->
                apiClient.userLibraryApi.getLatestMedia(
                    userId = userUuid,
                    parentId = parent,
                    limit = config.limit,
                    fields = ROW_ITEM_FIELDS,
                    enableImages = true,
                ).content
            }
        }
        // Libovolná Jellyfin kolekce / playlist (ByParent).
        HomeRowSourceType.COLLECTION -> {
            val parent = config.params[HomeRowParams.COLLECTION_ID].toUuidOrNull()
            if (parent == null) emptyList() else loadJellyfin(config) { userUuid ->
                apiClient.itemsApi.getItems(
                    userId = userUuid,
                    parentId = parent,
                    recursive = true,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    limit = config.limit,
                    fields = ROW_ITEM_FIELDS,
                    enableImages = true,
                ).content.items
            }
        }
        // NOVÝ zdroj: tituly se zapamatovaným zdrojem přehrávání (WorkingSourceStore).
        HomeRowSourceType.SAVED_FOR_PLAYBACK -> loadSavedForPlayback(config)
        // COUCH T1/T2 — Trakt řady přes sdílený loader (OAuth; nepřihlášený/prázdný → řada se nezobrazí).
        HomeRowSourceType.TRAKT_WATCHLIST ->
            traktLoader.watchlist(config.params[HomeRowParams.WATCHLIST_KIND] ?: "all").map { it.toHomeRowItem(config) }
        HomeRowSourceType.TRAKT_HISTORY ->
            traktLoader.history(config.params[HomeRowParams.WATCHLIST_KIND] ?: "all").map { it.toHomeRowItem(config) }
        HomeRowSourceType.TRAKT_LIST ->
            config.params[HomeRowParams.LIST_ID]?.toLongOrNull()?.let { id -> traktLoader.list(id).map { it.toHomeRowItem(config) } } ?: emptyList()
        HomeRowSourceType.COUCHMONKEY_RECOMMENDATIONS ->
            traktLoader.couchmonkeyRecommendations().map { it.toHomeRowItem(config) }
        // LIBRARY_TILES / GENRES / STUDIOS = dlaždicové navigační řady → 2. vlna (viz Known gaps).
        else -> emptyList()
        }
    }

    private suspend fun resumeItems(userUuid: UUID, limit: Int): List<BaseItemDto> =
        apiClient.itemsApi.getResumeItems(
            userId = userUuid,
            limit = limit,
            mediaTypes = listOf(JfMediaType.VIDEO),
            fields = ROW_ITEM_FIELDS,
            enableImages = true,
        ).content.items

    private suspend fun nextUpItems(userUuid: UUID, limit: Int): List<BaseItemDto> =
        apiClient.tvShowsApi.getNextUp(
            userId = userUuid,
            limit = limit,
            fields = ROW_ITEM_FIELDS,
            // OTA 299: bez enableImages nechodí imageTags → landscape karta „Další díly" neměla still dílu.
            enableImages = true,
        ).content.items

    // ── SAVED_FOR_PLAYBACK (zapamatované zdroje) ───────────────────────────────

    /**
     * Řada „Uloženo k přehrání": tituly z [WorkingSourceStore.getAll] (nejnovější první). WorkingSource nenese
     * poster → dohledáme přes TMDB paralelně. Klik → karta filmu, kde už je zapamatovaný zdroj předvybraný
     * ([DetailViewModel] `rememberedSource`) = přehrání bez nového hledání. One-click z domova viz Known gaps.
     */
    private suspend fun loadSavedForPlayback(config: HomeRowConfig): List<HomeRowItem> {
        workingSources.refresh()
        val saved = workingSources.getAll().take(config.limit.coerceIn(1, 60))
        return coroutineScope {
            saved.map { ws ->
                async {
                    val details = runCatching { tmdb.fetchMovieDetails(ws.tmdb) }.getOrNull()
                    val tr = runCatching { tmdb.fetchMovieTranslation(ws.tmdb, "cs") }.getOrNull()
                    val item = stub(ws.tmdb, ws.title, year = null, isShow = false).copy(
                        title = ws.title,
                        posterPath = details?.poster_path,
                        backdropPath = details?.backdrop_path,
                        titleCz = tr?.title?.takeIf { it.isNotBlank() },
                        imdbId = ws.imdb.takeIf { it.isNotBlank() },
                    )
                    HomeRowItem(
                        key = "saved_${ws.tmdb}",
                        title = item.titleCz?.takeIf { it.isNotBlank() } ?: item.title,
                        posterUrl = item.posterUrl("w342"),
                        landscapeUrl = item.backdropUrl("w780"),
                        mediaItem = item,
                    )
                }
            }.awaitAll()
        }
    }

    // ── DISCOVER (Trakt + TMDB) ────────────────────────────────────────────────

    private suspend fun loadDiscover(config: HomeRowConfig): List<HomeRowItem> {
        val isShow = config.params[HomeRowParams.TAB].equals("shows", ignoreCase = true)
        val filter = config.params[HomeRowParams.FILTER]?.lowercase() ?: "trending"
        val limit = config.limit.coerceIn(1, 60)
        val raw: List<MediaItem> = runCatching {
            when (filter) {
                "popular" -> if (isShow) traktApi.fetchPopularShows("", "", limit, 1).map { it.toMediaItem() }
                    else traktApi.fetchPopularMovies("", "", limit, 1).map { it.toMediaItem() }
                "anticipated" -> if (isShow) traktApi.fetchAnticipatedShows("", "", limit, 1).map { it.toMediaItem() }
                    else traktApi.fetchAnticipatedMovies("", "", limit, 1).map { it.toMediaItem() }
                "recommended" -> if (isShow) authorizedTraktApi.fetchRecommendedShows(limit).map { it.toMediaItem() }
                    else authorizedTraktApi.fetchRecommendedMovies(limit).map { it.toMediaItem() }
                else -> if (isShow) traktApi.fetchTrendingShows("", "", limit, 1).map { it.toMediaItem() }
                    else traktApi.fetchTrendingMovies("", "", limit, 1).map { it.toMediaItem() }
            }
        }.getOrElse { emptyList() }
        return enrich(raw, isShow).map { item ->
            HomeRowItem(
                key = "disc_${item.type}_${item.tmdbId ?: item.traktId}",
                title = item.titleCz?.takeIf { it.isNotBlank() } ?: item.title,
                year = item.year,
                posterUrl = item.posterUrl("w342"),
                landscapeUrl = item.backdropUrl("w780"),
                mediaItem = item,
            )
        }
    }

    /** TMDB obohacení (poster/backdrop + CZ titulek) paralelně — zjednodušené z DiscoverViewModel. */
    private suspend fun enrich(items: List<MediaItem>, isShow: Boolean): List<MediaItem> = coroutineScope {
        items.map { item ->
            async {
                val tmdbId = item.tmdbId ?: return@async item
                if (isShow) {
                    val details = runCatching { tmdb.fetchShowDetails(tmdbId) }.getOrNull()
                    val tr = runCatching { tmdb.fetchShowTranslation(tmdbId, "cs") }.getOrNull()
                    item.copy(
                        posterPath = details?.poster_path,
                        backdropPath = details?.backdrop_path,
                        titleCz = tr?.name?.takeIf { it.isNotBlank() },
                    )
                } else {
                    val details = runCatching { tmdb.fetchMovieDetails(tmdbId) }.getOrNull()
                    val tr = runCatching { tmdb.fetchMovieTranslation(tmdbId, "cs") }.getOrNull()
                    item.copy(
                        posterPath = details?.poster_path,
                        backdropPath = details?.backdrop_path,
                        titleCz = tr?.title?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }.awaitAll()
    }

    /** COUCH T1/T2 — obohacené Trakt [MediaItem] (z [TraktRowLoader]) → [HomeRowItem] pro řadu domova. */
    private fun MediaItem.toHomeRowItem(config: HomeRowConfig) = HomeRowItem(
        key = "trakt_${config.id}_${type}_${tmdbId ?: traktId}",
        title = titleCz?.takeIf { it.isNotBlank() } ?: title,
        year = year,
        posterUrl = posterUrl("w342"),
        landscapeUrl = backdropUrl("w780"),
        mediaItem = this,
    )

    // ── Jellyfin (Pokračovat / Další díly) ─────────────────────────────────────

    private suspend fun loadJellyfin(
        config: HomeRowConfig,
        fetch: suspend (UUID) -> List<BaseItemDto>,
    ): List<HomeRowItem> {
        val session = prepareJellyfin() ?: return emptyList()
        val dtos = runCatching { fetch(session.userUuid) }.getOrElse {
            Timber.w(it, "[TvHome] JF fetch '${config.id}' selhal"); emptyList()
        }
        // U epizod (Další díly / Pokračovat) nese providerIds id EPIZODY, ale ČSFD hodnotí SERIÁL →
        // dohledej providerIds seriálů batchem (unikátní seriesId), ať karta dostane tmdb/imdb pro ČSFD badge.
        val seriesProviders = fetchSeriesProviderIds(session.userUuid, dtos)
        return dtos.map { it.toHomeRowItem(session.serverUrl, session.token, seriesProviders) }
    }

    /** Batch dohledání providerIds seriálů pro epizodní položky (1 request pro všechny unikátní seriesId). */
    private suspend fun fetchSeriesProviderIds(
        userUuid: UUID,
        dtos: List<BaseItemDto>,
    ): Map<UUID, Map<String, String?>> {
        val seriesIds = dtos.filter { it.type == BaseItemKind.EPISODE }
            .mapNotNull { it.seriesId }.distinct()
        if (seriesIds.isEmpty()) return emptyMap()
        return runCatching {
            apiClient.itemsApi.getItems(
                userId = userUuid,
                ids = seriesIds,
                fields = listOf(ItemFields.PROVIDER_IDS),
            ).content.items.mapNotNull { s -> s.providerIds?.let { s.id to it } }.toMap()
        }.getOrElse { Timber.w(it, "[TvHome] dohledání providerIds seriálů selhalo"); emptyMap() }
    }

    /** Přihlašovací údaje Jellyfinu z prefs + [ApiClient] nastavený na server. Null = nepřihlášen. */
    private data class JfSession(val serverUrl: String, val token: String, val userUuid: UUID)

    private fun prepareJellyfin(): JfSession? {
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        val userId = prefs.getString("jellyfin_user_id", "").orEmpty()
        if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) return null
        apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
        val userUuid = userId.toUuidOrNull() ?: return null
        return JfSession(serverUrl, token, userUuid)
    }

    /**
     * Import domovské konfigurace z Jellyfin serveru (synergie yellyfin↔showlyfin). Čte web-client
     * DisplayPreferences (`usersettings`/`emby`) klíče `homesection0..9` a mapuje je na [HomeRowConfig] řady;
     * `latestmedia` rozgeneruje na „Nejnovější v <knihovna>" per knihovnu. Nové řady se přidají ([addRows]),
     * existující (dle id) se nepřepíšou. Vrací počet přidaných řad (0 = nic k importu / nepřihlášen).
     */
    suspend fun importFromJellyfin(): Int {
        val session = prepareJellyfin() ?: return 0
        val customPrefs = runCatching {
            apiClient.displayPreferencesApi.getDisplayPreferences(
                displayPreferencesId = "usersettings",
                userId = session.userUuid,
                client = "emby",
            ).content.customPrefs
        }.getOrElse { Timber.w(it, "[TvHome] import: čtení DisplayPreferences selhalo"); emptyMap() }
        if (customPrefs.isEmpty()) return 0

        val views = runCatching { apiClient.userViewsApi.getUserViews(session.userUuid).content.items }
            .getOrElse { emptyList() }
        val imported = mutableListOf<HomeRowConfig>()
        for (idx in 0..9) {
            when (customPrefs["homesection$idx"]?.lowercase()) {
                "resume" -> imported += HomeRowConfig(
                    id = "imp_resume", source = HomeRowSourceType.CONTINUE_WATCHING,
                    title = "Pokračovat ve sledování", cardStyle = HomeCardStyle.LANDSCAPE,
                )
                "nextup" -> imported += HomeRowConfig(
                    id = "imp_nextup", source = HomeRowSourceType.NEXT_UP,
                    title = "Další díly", cardStyle = HomeCardStyle.LANDSCAPE,
                )
                "latestmedia" -> views.forEach { v ->
                    val libId = v.id.toString()
                    imported += HomeRowConfig(
                        id = "imp_latest_$libId", source = HomeRowSourceType.RECENTLY_ADDED,
                        title = "Nejnovější — ${v.name.orEmpty()}",
                        params = mapOf(
                            HomeRowParams.LIBRARY_ID to libId,
                            HomeRowParams.COLLECTION_TYPE to (v.collectionType?.serialName ?: ""),
                        ),
                    )
                }
                else -> Unit // livetv/recordings/audio/book/tiles: showlyfin zatím nemapuje (viz Known gaps)
            }
        }
        store.addRows(imported)
        return imported.size
    }

    private fun BaseItemDto.toHomeRowItem(
        serverUrl: String,
        token: String,
        seriesProviders: Map<UUID, Map<String, String?>> = emptyMap(),
    ): HomeRowItem {
        val jfId = id.toString()
        val isEpisode = type == BaseItemKind.EPISODE
        val displayTitle = if (isEpisode) (seriesName ?: name ?: "") else (name ?: "")
        val epLabel = if (isEpisode) {
            val s = parentIndexNumber?.let { "S$it" }.orEmpty()
            val e = indexNumber?.let { "E$it" }.orEmpty()
            listOf("$s$e".takeIf { it.isNotBlank() }, name).filterNotNull().joinToString(" · ")
                .takeIf { it.isNotBlank() }
        } else null
        // Klik na epizodu → otevři kartu SERIÁLU (fixnutý next-up flow → Pokračovat); film → karta filmu.
        val targetId = if (isEpisode) (seriesId?.toString() ?: jfId) else jfId
        // ČSFD/CZ badge potřebuje tmdb/imdb: epizoda → id seriálu (dohledané), jinak vlastní providerIds.
        val ids = if (isEpisode) seriesId?.let { seriesProviders[it] } else providerIds
        val tmdb = ids?.get("Tmdb")?.toLongOrNull()
        val imdb = ids?.get("Imdb")?.takeIf { it.isNotBlank() }
        val media = if (tmdb != null || imdb != null) MediaItem(
            traktId = 0L,
            tmdbId = tmdb,
            imdbId = imdb,
            title = displayTitle,
            year = productionYear,
            overview = null,
            rating = null,
            genres = null,
            type = if (isEpisode || type == BaseItemKind.SERIES) MediaType.SHOW else MediaType.MOVIE,
        ) else null
        return HomeRowItem(
            key = "jf_$jfId",
            title = displayTitle,
            subtitle = epLabel,
            year = productionYear,
            posterUrl = "$serverUrl/Items/$jfId/Images/Primary?fillWidth=320&quality=85&api_key=$token",
            landscapeUrl = landscapeUrl(serverUrl, token),
            progressPct = userData?.playedPercentage?.toInt(),
            watched = userData?.played == true,
            jellyfinId = targetId,
            mediaItem = media,
        )
    }

    /**
     * Široký obrázek. U EPIZODY (řada „Další díly") preferuj NÁHLED KONKRÉTNÍHO DÍLU (still = Primary
     * epizody) před fanartem seriálu (OTA 299 — dřív karta ukazovala jen fanart seriálu, ne díl).
     * Jinak: backdrop → thumb → (u epizody bez stillu) backdrop seriálu → null (poster fallback).
     */
    private fun BaseItemDto.landscapeUrl(serverUrl: String, token: String): String? {
        val backdropTag = backdropImageTags?.firstOrNull()
        val thumbTag = imageTags?.get(ImageType.THUMB)
        val primaryTag = imageTags?.get(ImageType.PRIMARY)
        return when {
            // Epizoda: still dílu (Primary epizody) = reálný náhled té epizody, ne fanart seriálu.
            type == BaseItemKind.EPISODE && primaryTag != null ->
                "$serverUrl/Items/$id/Images/Primary?fillWidth=640&quality=85&tag=$primaryTag&api_key=$token"
            backdropTag != null -> "$serverUrl/Items/$id/Images/Backdrop/0?fillWidth=640&quality=85&tag=$backdropTag&api_key=$token"
            thumbTag != null -> "$serverUrl/Items/$id/Images/Thumb?fillWidth=640&quality=85&tag=$thumbTag&api_key=$token"
            // Fallback (epizoda bez stillu): fanart seriálu.
            type == BaseItemKind.EPISODE && seriesId != null ->
                "$serverUrl/Items/$seriesId/Images/Backdrop/0?fillWidth=640&quality=85&api_key=$token"
            else -> null
        }
    }

    // ── Klientské operace (řazení / limit / skryj zhlédnuté) ───────────────────

    private fun applyOps(items: List<HomeRowItem>, config: HomeRowConfig): List<HomeRowItem> {
        var r = items
        if (config.params.boolParam(HomeRowParams.HIDE_WATCHED)) r = r.filter { !it.watched }
        r = when (config.sort) {
            HomeRowSort.RATING -> r.sortedByDescending { it.mediaItem?.rating ?: -1f }
            HomeRowSort.YEAR_DESC -> r.sortedByDescending { it.year ?: 0 }
            HomeRowSort.ALPHA -> r.sortedBy { it.title.lowercase() }
            HomeRowSort.RANDOM -> r.shuffled()
            HomeRowSort.RECENT, HomeRowSort.DEFAULT -> r
        }
        return r.take(config.limit.coerceIn(1, 60))
    }

    private fun stub(tmdbId: Long, title: String, year: Int?, isShow: Boolean) = MediaItem(
        traktId = 0L,
        tmdbId = tmdbId,
        imdbId = null,
        title = title,
        year = year,
        overview = null,
        rating = null,
        genres = null,
        type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
    )
}

/** Styl karty pro řadu (helper pro render). */
fun HomeCardStyle.isLandscape(): Boolean = this == HomeCardStyle.LANDSCAPE

/** COUCH R2: Trakt zdroje řad — skryté pro zamčený/dětský profil. */
private val TRAKT_SOURCES = setOf(
    HomeRowSourceType.TRAKT_WATCHLIST,
    HomeRowSourceType.TRAKT_HISTORY,
    HomeRowSourceType.TRAKT_LIST,
    HomeRowSourceType.COUCHMONKEY_RECOMMENDATIONS,
)

/** Sdílená sada Jellyfin ItemFields pro řady domova (providerIds kvůli klik-mapování, overview kvůli immersive). */
private val ROW_ITEM_FIELDS = listOf(
    ItemFields.PROVIDER_IDS,
    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
    ItemFields.OVERVIEW,
)

/** Bezpečný parse Jellyfin UUID z volného params stringu (prázdné/neplatné → null místo pádu). */
private fun String?.toUuidOrNull(): UUID? =
    this?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
