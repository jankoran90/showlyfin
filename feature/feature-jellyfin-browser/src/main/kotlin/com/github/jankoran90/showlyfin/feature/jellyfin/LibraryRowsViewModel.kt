package com.github.jankoran90.showlyfin.feature.jellyfin

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.tmdb.TmdbRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * „Knihovna" pohledem z Traktu: pro každou Jellyfin filmovou/seriálovou knihovnu jedna
 * horizontální řada. Match Jellyfin položky na TMDB: providerId Tmdb → fallback title+year
 * search → fallback (mediaItem=null) = proklik na Jellyfin kartu.
 */
@HiltViewModel
class LibraryRowsViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val tmdb: TmdbRemoteDataSource,
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryRowsUiState())
    val state: StateFlow<LibraryRowsUiState> = _state.asStateFlow()

    // Nastavení → Detail z knihovny: bohatý (Trakt detail) vs jednoduchý (Jellyfin karta).
    private val detailRich get() = prefs.getBoolean("detail_mode_rich", true)

    fun load() {
        viewModelScope.launch {
            val serverUrl = prefs.getString("jellyfin_server_url", "") ?: ""
            val token = prefs.getString("jellyfin_token", "") ?: ""
            val userId = prefs.getString("jellyfin_user_id", "") ?: ""
            if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) {
                _state.update { it.copy(isLoading = false, error = "Jellyfin není nastaven") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                apiClient.update(
                    baseUrl = serverUrl,
                    accessToken = token,
                    clientInfo = clientInfo,
                    deviceInfo = deviceInfo,
                )
                val userUuid = UUID.fromString(userId)
                val views = apiClient.userViewsApi.getUserViews(userId = userUuid).content
                // Plan PROFILES 1E: whitelist knihoven z aktivního profilu (null = všechny).
                // Plan VAULT: backend posílá ids BEZ pomlček, SDK UUID.toString() je S pomlčkami →
                // bez normalizace je průnik vždy prázdný (= prázdná Knihovna u profilů s whitelistem).
                val whitelist = profileRepository.activeConfig.value.jellyfinLibraryWhitelist
                    ?.map { it.replace("-", "").lowercase() }?.toSet()
                val mediaViews = views.items.filter { it.isMediaLibrary() }
                    .let { list ->
                        if (whitelist == null) list
                        else list.filter { it.id.toString().replace("-", "").lowercase() in whitelist }
                    }
                Timber.i("[VAULT] JF views=${views.items.map { it.name }} → po whitelistu=${mediaViews.map { it.name }}")
                // Plan STRATA Fáze E: pořadí knihovních řádků dle profilu (libraryOrder; norm. id bez pomlček).
                val norm = { id: String -> id.replace("-", "").lowercase() }
                val orderNorm = profileRepository.activeConfig.value.libraryOrder.map(norm)
                val orderedViews = if (orderNorm.isEmpty()) mediaViews else {
                    val known = orderNorm.mapNotNull { o -> mediaViews.firstOrNull { norm(it.id.toString()) == o } }
                    known + mediaViews.filterNot { it in known }
                }
                val rows = coroutineScope {
                    orderedViews.map { view ->
                        async { loadRow(view, userUuid, serverUrl, token) }
                    }.awaitAll()
                }.filter { it.items.isNotEmpty() }
                _state.update { it.copy(rows = rows, isLoading = false, error = null) }
            } catch (e: Throwable) {
                Timber.w(e, "[LibraryRows] load failed")
                _state.update { it.copy(isLoading = false, error = e.message ?: "Chyba načtení knihovny") }
            }
        }
    }

    private suspend fun loadRow(
        view: BaseItemDto,
        userUuid: UUID,
        serverUrl: String,
        token: String,
    ): LibraryRow = coroutineScope {
        val raw = runCatching {
            apiClient.itemsApi.getItems(
                userId = userUuid,
                parentId = view.id,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET),
                recursive = true,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                limit = 60,
            ).content.items
        }.getOrElse {
            Timber.w(it, "[LibraryRows] getItems failed for '${view.name}'")
            emptyList()
        }
        // Jellyfin vrací kolekce (BoxSet) místo filmů uvnitř — rozbal je na děti, ať mají
        // reálná movie/show TMDB id (kolekce sama nese collection-id → vedlo k prázdné kartě).
        val expanded = raw.flatMap { dto ->
            if (dto.type == BaseItemKind.BOX_SET) {
                runCatching {
                    apiClient.itemsApi.getItems(
                        userId = userUuid,
                        parentId = dto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                    ).content.items
                }.getOrElse { emptyList() }
            } else {
                listOf(dto)
            }
        }
        val rowItems = expanded.map { it.toLibraryRowItem(serverUrl, token) }.distinctBy { it.jellyfinId }
        LibraryRow(
            libraryId = view.id.toString(),
            libraryName = view.name ?: "Knihovna",
            collectionType = view.collectionType?.name,
            items = rowItems,
        )
    }

    private suspend fun BaseItemDto.toLibraryRowItem(serverUrl: String, token: String): LibraryRowItem {
        val jellyfinId = id.toString()
        val itemName = name ?: ""
        val itemYear = productionYear
        val isShow = type == BaseItemKind.SERIES
        val tmdbId = providerIds?.get("Tmdb")?.toLongOrNull()
        val imdbId = providerIds?.get("Imdb")?.takeIf { it.isNotBlank() }
        // Jednoduchý režim → mediaItem=null → klik otevře jednoduchou Jellyfin kartu.
        val media: MediaItem? = if (!detailRich) null else when {
            tmdbId != null -> stub(tmdbId, imdbId, itemName, itemYear, isShow)
            itemYear != null -> searchMatch(itemName, imdbId, itemYear, isShow)
            else -> null
        }
        return LibraryRowItem(
            jellyfinId = jellyfinId,
            name = itemName,
            year = itemYear,
            type = type?.name ?: "MOVIE",
            imageUrl = "$serverUrl/Items/$jellyfinId/Images/Primary?fillWidth=320&quality=85&api_key=$token",
            watched = userData?.played == true,
            progressPct = userData?.playedPercentage?.toInt(),
            mediaItem = media,
        )
    }

    /** Fallback: TMDB search dle názvu, přijmi výsledek se shodným rokem. */
    private suspend fun searchMatch(name: String, imdbId: String?, year: Int, isShow: Boolean): MediaItem? {
        if (name.isBlank()) return null
        return runCatching {
            if (isShow) {
                tmdb.searchShows(name).firstOrNull { it.first_air_date?.take(4)?.toIntOrNull() == year }
                    ?.let { stub(it.id, imdbId, name, year, true) }
            } else {
                tmdb.searchMovies(name).firstOrNull { it.release_date?.take(4)?.toIntOrNull() == year }
                    ?.let { stub(it.id, imdbId, name, year, false) }
            }
        }.getOrNull()
    }

    private fun stub(tmdbId: Long, imdbId: String?, title: String, year: Int?, isShow: Boolean) = MediaItem(
        traktId = 0L,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        year = year,
        overview = null,
        rating = null,
        genres = null,
        type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
        posterPath = null,
        backdropPath = null,
    )
}

/** Filmové / seriálové / smíšené knihovny (vč. None=mixed, např. „Dokumenty pro děti");
 *  RealDebrid (streamovaný zdroj) a hudbu/knihy/kolekce vynech. */
private fun BaseItemDto.isMediaLibrary(): Boolean {
    val ct = collectionType?.name?.uppercase()
    val allowed = ct == null || ct == "MOVIES" || ct == "TVSHOWS" || ct == "MIXED"
    if (!allowed) return false
    val n = name?.lowercase() ?: return true
    return !n.contains("realdebrid") && !n.contains("real-debrid")
}
