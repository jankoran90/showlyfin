package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

/** Jedna řada Filmotéky (hodnota osy → tituly). Neutrální model — UI (ui-tv) ho mapuje na `TvRail`. */
data class FilmotekaRail(
    val id: String,
    val title: String,
    val items: List<HomeRowItem>,
)

/** Stav sekce Filmotéka. */
data class FilmotekaUiState(
    val axis: FilmotekaAxis = FilmotekaAxis.GENRE,
    val rails: List<FilmotekaRail> = emptyList(),
    val loading: Boolean = true,
)

/**
 * CINEMATHEQUE (SHW-90) — agregační VM Filmotéky. Sjednocuje 4 zdroje ([FilmotekaSource]) do jedné plochy
 * přeskupitelné podle osy ([FilmotekaAxis]). Konstrukce zrcadlí [com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel].
 *
 * Tok: JF knihovna + zapamatované zdroje + Trakt watchlist se jednorázově sloučí do báze (dedup podle
 * tmdb→imdb, precedence JELLYFIN>WORKING>TRAKT); Oblíbené se mergují REAKTIVNĚ (StateFlow). Vše se obohatí
 * ([MediaEnricher]) a projde věkovým gate ([ContentAgeGate]) PŘED grupováním. Přepnutí osy jen přeskupí
 * už-obohacenou bázi (bez fetch). Reload na změnu profilu.
 *
 * **F1** = jen osa GENRE. Osa COUNTRY vrací prázdno (dodělá F2).
 */
@HiltViewModel
class TvFilmotekaViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val traktLoader: TraktRowLoader,
    private val enricher: MediaEnricher,
    private val favorites: FavoritesStore,
    private val workingSources: WorkingSourceStore,
    private val parentalControls: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    private val settings: FilmotekaSettingsStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(FilmotekaUiState())
    val state: StateFlow<FilmotekaUiState> = _state.asStateFlow()

    // Enrichnutá + věkově gatovaná báze (JF+Working+Trakt) a zvlášť Oblíbené (reaktivní). Grupování je merguje.
    @Volatile private var baseItems: List<MediaItem> = emptyList()
    @Volatile private var favoriteItems: List<MediaItem> = emptyList()

    private var loadJob: Job? = null

    private fun ageCap(): Int? = parentalControls.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parentalControls.profile.value.hideUnratedForAge
    private fun traktAllowed(): Boolean =
        !profileRepository.activeConfig.value.credentials.trakt?.accessToken.isNullOrBlank()

    init {
        // Per-profil: přepni nastavení Filmotéky na profil, pak přenačti obsah (jeden collector = pořadí).
        profileRepository.activeProfile
            .onEach { p ->
                settings.switchProfile(p?.id)
                reload()
            }
            .launchIn(viewModelScope)

        // Oblíbené reaktivně (per-profil sync běží async) → přemerguj bez plného reloadu.
        favorites.items
            .onEach { list -> refreshFavorites(list) }
            .launchIn(viewModelScope)
    }

    /** Přepnutí osy — jen přeskupí už-obohacenou bázi (bez fetch). Volá UI z přepínače osy. */
    fun setAxis(axis: FilmotekaAxis) {
        if (_state.value.axis != axis) rebuild(axis)
    }

    /** Zahoď bázi a přenačti (po přepnutí profilu). */
    private fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            val enabled = settings.sources.value
            val cap = ageCap()
            val base = gatherBase(enabled)
            val enriched = enricher.enrich(base, withCertification = cap != null)
            baseItems = ContentAgeGate.filter(cap, enriched, hideUnrated())
            rebuild(settings.defaultAxis.value)
        }
    }

    /** Obohaď + gatuj Oblíbené (jen filmy) a přeskup. Vypnutý zdroj → prázdný bucket. */
    private suspend fun refreshFavorites(list: List<FavoriteItem>) {
        if (FilmotekaSource.FAVORITES !in settings.sources.value) {
            favoriteItems = emptyList()
            rebuild(_state.value.axis)
            return
        }
        val cap = ageCap()
        val base = list.filter { it.kind == FavoriteKind.MOVIE && it.id > 0L }
            .map { fav -> stub(fav.id, null, fav.name, fav.year, isShow = false) }
        val enriched = enricher.enrich(base, withCertification = cap != null)
        favoriteItems = ContentAgeGate.filter(cap, enriched, hideUnrated())
        rebuild(_state.value.axis)
    }

    // ── Sběr báze ───────────────────────────────────────────────────────────────

    private suspend fun gatherBase(enabled: Set<FilmotekaSource>): List<MediaItem> = coroutineScope {
        val jfD = async { if (FilmotekaSource.JELLYFIN in enabled) loadJellyfinLibrary() else emptyList() }
        val wsD = async { if (FilmotekaSource.WORKING in enabled) loadWorkingSources() else emptyList() }
        val tkD = async {
            if (FilmotekaSource.TRAKT_WATCHLIST in enabled && traktAllowed())
                runCatching { traktLoader.watchlist("all") }.getOrElse { emptyList() }
            else emptyList()
        }
        // Precedence = pořadí seznamů (putIfAbsent): JELLYFIN > WORKING > TRAKT_WATCHLIST.
        val merged = LinkedHashMap<String, MediaItem>()
        for (list in listOf(jfD.await(), wsD.await(), tkD.await())) {
            for (item in list) { val k = dedupKey(item) ?: continue; merged.putIfAbsent(k, item) }
        }
        merged.values.toList()
    }

    private suspend fun loadJellyfinLibrary(): List<MediaItem> = coroutineScope {
        val session = prepareJellyfin() ?: return@coroutineScope emptyList()
        val views = runCatching { apiClient.userViewsApi.getUserViews(session.userUuid).content.items }
            .getOrElse { Timber.w(it, "[Filmoteka] getUserViews selhalo"); emptyList() }
            .filter { it.isFilmotekaLibrary() }
        views.map { view ->
            async {
                runCatching {
                    apiClient.itemsApi.getItems(
                        userId = session.userUuid,
                        parentId = view.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.GENRES),
                        limit = 400,
                    ).content.items
                }.getOrElse { Timber.w(it, "[Filmoteka] getItems '${view.name}' selhalo"); emptyList() }
            }
        }.awaitAll().flatten().mapNotNull { it.toFilmotekaMediaItem() }
    }

    private suspend fun loadWorkingSources(): List<MediaItem> {
        workingSources.refresh()
        return workingSources.getAll().mapNotNull { ws ->
            if (ws.tmdb <= 0L && ws.imdb.isBlank()) return@mapNotNull null
            MediaItem(
                traktId = 0L,
                tmdbId = ws.tmdb.takeIf { it > 0L },
                imdbId = ws.imdb.takeIf { it.isNotBlank() },
                title = ws.title,
                year = null,
                overview = null,
                rating = null,
                genres = null,
                type = MediaType.MOVIE,
            )
        }
    }

    // ── Grupování (osa) ───────────────────────────────────────────────────────────

    private fun rebuild(axis: FilmotekaAxis) {
        // Base (JF+Working+Trakt) má přednost před Oblíbenými (putIfAbsent v pořadí base → favorites).
        val combined = LinkedHashMap<String, MediaItem>()
        for (item in baseItems + favoriteItems) { val k = dedupKey(item) ?: continue; combined.putIfAbsent(k, item) }
        val all = combined.values.toList()
        val rails = when (axis) {
            FilmotekaAxis.GENRE -> groupByGenre(all)
            FilmotekaAxis.COUNTRY -> emptyList() // F2 — osa Země
        }
        _state.value = FilmotekaUiState(axis = axis, rails = rails, loading = false)
    }

    /** Řady podle žánru, seřazené sestupně dle četnosti. Prázdné žánry nevznikají. */
    private fun groupByGenre(items: List<MediaItem>): List<FilmotekaRail> {
        val byGenre = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in items) {
            for (raw in item.genres.orEmpty()) {
                val g = raw.trim()
                if (g.isNotBlank()) byGenre.getOrPut(g) { mutableListOf() }.add(item)
            }
        }
        return byGenre.entries
            .sortedByDescending { it.value.size }
            .map { (genre, list) ->
                FilmotekaRail(
                    id = "filmo_genre_$genre",
                    title = genre,
                    items = list.map { it.toHomeRowItem(genre) },
                )
            }
            .filter { it.items.isNotEmpty() }
    }

    // ── Mapování ────────────────────────────────────────────────────────────────

    private fun MediaItem.toHomeRowItem(axisValue: String) = HomeRowItem(
        // Klíč nese hodnotu osy → titul může být ve víc řadách bez Compose key kolize.
        key = "filmo_${axisValue}_${tmdbId ?: imdbId ?: traktId}",
        title = titleCz?.takeIf { it.isNotBlank() } ?: title,
        year = year,
        posterUrl = posterUrl("w342"),
        landscapeUrl = backdropUrl("w780"),
        mediaItem = this,
    )

    private fun BaseItemDto.toFilmotekaMediaItem(): MediaItem? {
        val tmdb = providerIds?.get("Tmdb")?.toLongOrNull()
        val imdb = providerIds?.get("Imdb")?.takeIf { it.isNotBlank() }
        if (tmdb == null && imdb == null) return null
        val isShow = type == BaseItemKind.SERIES
        return MediaItem(
            traktId = 0L,
            tmdbId = tmdb,
            imdbId = imdb,
            title = name ?: "",
            year = productionYear,
            overview = null,
            rating = null,
            genres = genres?.takeIf { it.isNotEmpty() },
            type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
        )
    }

    private fun dedupKey(item: MediaItem): String? = when {
        item.tmdbId != null -> "tmdb:${item.tmdbId}"
        !item.imdbId.isNullOrBlank() -> "imdb:${item.imdbId}"
        else -> null
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
    )

    // ── Jellyfin session (vzor TvHomeViewModel) ────────────────────────────────────

    private data class JfSession(val serverUrl: String, val token: String, val userUuid: UUID)

    private fun prepareJellyfin(): JfSession? {
        val serverUrl = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        val userId = prefs.getString("jellyfin_user_id", "").orEmpty()
        if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) return null
        apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
        val userUuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        return JfSession(serverUrl, token, userUuid)
    }
}

/** Filmové/seriálové/smíšené knihovny (vzor LibraryRowsViewModel.isMediaLibrary); RealDebrid vynech. */
private fun BaseItemDto.isFilmotekaLibrary(): Boolean {
    val ct = collectionType?.name?.uppercase()
    val allowed = ct == null || ct == "MOVIES" || ct == "TVSHOWS" || ct == "MIXED"
    if (!allowed) return false
    val n = name?.lowercase() ?: return true
    return !n.contains("realdebrid") && !n.contains("real-debrid")
}
