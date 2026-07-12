package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
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
    private val tmdb: TmdbRemoteDataSource,
    private val favorites: FavoritesStore,
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

    /** Netflix immersive pozadí (fokusovaná karta řídí fanart). */
    val immersiveBackground: StateFlow<Boolean> = store.immersiveBackground

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

    private suspend fun loadOnce(config: HomeRowConfig): List<HomeRowItem> = when (config.source) {
        HomeRowSourceType.DISCOVER -> loadDiscover(config)
        HomeRowSourceType.CONTINUE_WATCHING -> loadJellyfin(config) { userUuid ->
            apiClient.itemsApi.getResumeItems(
                userId = userUuid,
                limit = config.limit,
                mediaTypes = listOf(JfMediaType.VIDEO),
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW),
                enableImages = true,
            ).content.items
        }
        HomeRowSourceType.NEXT_UP -> loadJellyfin(config) { userUuid ->
            apiClient.tvShowsApi.getNextUp(
                userId = userUuid,
                limit = config.limit,
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW),
            ).content.items
        }
        else -> emptyList()
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

    // ── Jellyfin (Pokračovat / Další díly) ─────────────────────────────────────

    private suspend fun loadJellyfin(
        config: HomeRowConfig,
        fetch: suspend (UUID) -> List<BaseItemDto>,
    ): List<HomeRowItem> {
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        val userId = prefs.getString("jellyfin_user_id", "").orEmpty()
        if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) return emptyList()
        apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
        val userUuid = UUID.fromString(userId)
        val dtos = runCatching { fetch(userUuid) }.getOrElse {
            Timber.w(it, "[TvHome] JF fetch '${config.id}' selhal"); emptyList()
        }
        return dtos.map { it.toHomeRowItem(serverUrl, token) }
    }

    private fun BaseItemDto.toHomeRowItem(serverUrl: String, token: String): HomeRowItem {
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
        )
    }

    /** Široký obrázek: backdrop → thumb → (u epizody) backdrop seriálu → null (poster fallback). */
    private fun BaseItemDto.landscapeUrl(serverUrl: String, token: String): String? {
        val backdropTag = backdropImageTags?.firstOrNull()
        val thumbTag = imageTags?.get(ImageType.THUMB)
        return when {
            backdropTag != null -> "$serverUrl/Items/$id/Images/Backdrop/0?fillWidth=640&quality=85&tag=$backdropTag&api_key=$token"
            thumbTag != null -> "$serverUrl/Items/$id/Images/Thumb?fillWidth=640&quality=85&tag=$thumbTag&api_key=$token"
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
