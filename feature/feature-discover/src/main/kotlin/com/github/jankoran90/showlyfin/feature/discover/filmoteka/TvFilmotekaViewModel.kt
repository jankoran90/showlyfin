package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.core.domain.filmoteka.regionsOf
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import com.github.jankoran90.showlyfin.data.uploader.TraktSyncSignal
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
import kotlinx.coroutines.flow.drop
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
import java.time.ZoneOffset
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
    val axis: FilmotekaAxis = FilmotekaAxis.ALL,
    val rails: List<FilmotekaRail> = emptyList(),
    val loading: Boolean = true,
    /** Aktuální řazení osy „Vše" — pro telefonní chip (Nedávno / Abecedně). TV ho ignoruje. */
    val allSort: FilmotekaAllSort = FilmotekaAllSort.RECENT,
    /** Počet unikátních titulů v bázi (po dedup+gate, napříč osami stejný) — pro telefonní ukazatel „N filmů". */
    val total: Int = 0,
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
 * Osa GENRE = řady dle žánru; osa COUNTRY (F2) = řady dle regionální „kinematografie" ([CinematographyRegion]),
 * respektuje zapnuté regiony ([FilmotekaSettingsStore.enabledRegions]), OSTATNI vždy poslední.
 */
@HiltViewModel
class TvFilmotekaViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    private val traktLoader: TraktRowLoader,
    private val enricher: MediaEnricher,
    private val favorites: FavoritesRepository,
    private val workingSources: WorkingSourceStore,
    private val parentalControls: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    private val settings: FilmotekaSettingsStore,
    private val traktSyncSignal: TraktSyncSignal,
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

    /**
     * CONVERGE bug (2026-07-16) — „Chci vidět" se ve Filmotéce nezobrazovalo, i když v sekci Trakt ano.
     * Root cause: sekce Trakt volá `loader.watchlist("all")` BEZ guardu (autorizace přes interceptor +
     * [TraktTokenProvider], který čte pref `TRAKT_ACCESS_TOKEN`), kdežto tady jsme fetch podmiňovali JEN
     * per-profil `activeConfig.credentials.trakt.accessToken`. Na TV bývá tenhle config-token PRÁZDNÝ
     * (login běží na telefonu / device-flow zapíše token rovnou do prefs, do backend configu se ale
     * nepropíše) → guard=false → watchlist se do báze nikdy nesloučil (ani po reloadu/restartu).
     * Fix: přijmi i pref-token = STEJNÝ zdroj pravdy, jaký reálně autorizuje API (a jaký vidí sekce Trakt).
     * Dětský profil: [ProfileConfigApplier] při přepnutí na profil BEZ Traktu pref `TRAKT_ACCESS_TOKEN`
     * odstraní → fallback je null → watchlist se nezahrne (+ [ContentAgeGate] jako druhá pojistka).
     */
    private fun traktAllowed(): Boolean =
        !profileRepository.activeConfig.value.credentials.trakt?.accessToken.isNullOrBlank() ||
            !prefs.getString(KEY_TRAKT_ACCESS_TOKEN, null).isNullOrBlank()

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

        // CONVERGE V1 — změna řazení osy „Vše" v Nastavení → přeskup (bez fetch). drop(1) = ignoruj
        // iniciální emit (base ještě nemusí být načtená; reload/rebuild ho pokryjí).
        settings.allSort
            .drop(1)
            .onEach { if (_state.value.axis == FilmotekaAxis.ALL) rebuild(FilmotekaAxis.ALL) }
            .launchIn(viewModelScope)

        // CONVERGE — přidání/odebrání „Chci vidět" v detailu ([DetailViewModel.toggleWatchlist]) bumpne sdílený
        // Trakt signál → přenačti bázi, aby čerstvý titul naskočil i ve Filmotéce (ne jen v sekci Trakt/Domů;
        // watchlist NENÍ reaktivní store, proto tenhle pull). drop(1) = ignoruj iniciální hodnotu. Oblíbené jsou
        // řešené zvlášť reaktivně přes favorites.items výše.
        traktSyncSignal.version
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)

        // CELLULOID M2.4 — auto-cache backend zapíše uložený zdroj (WorkingSource) → savedKeys se změní →
        // přenačti, ať se titul objeví ve Filmotéce ŽIVĚ (dřív nutný restart). Padne i na TV = bonus.
        // drop(1) = ignoruj iniciální emit (base pokryje reload z profilu výše).
        workingSources.savedKeys
            .drop(1)
            .onEach { reload() }
            .launchIn(viewModelScope)
    }

    /** Přepnutí osy — jen přeskupí už-obohacenou bázi (bez fetch). Volá UI z přepínače osy. */
    fun setAxis(axis: FilmotekaAxis) {
        if (_state.value.axis != axis) rebuild(axis)
    }

    /**
     * CELLULOID M2.4 — telefonní chip řazení osy „Vše" (Nedávno / Abecedně). Uloží do Nastavení (per profil,
     * sdílené s TV) a hned přeskup, jsme-li na ose ALL. TV mění řazení v Nastavení, tady přímo z plochy.
     */
    fun setAllSort(sort: FilmotekaAllSort) {
        if (settings.allSort.value == sort) return
        settings.setAllSort(sort)
        if (_state.value.axis == FilmotekaAxis.ALL) rebuild(FilmotekaAxis.ALL)
    }

    /**
     * CONVERGE — vstup do sekce: obnov VÝCHOZÍ osu z Nastavení (default „Vše"). VM je retained na úrovni
     * shellu (TvShell přepíná sekce jen `when`em), takže bez tohoto by runtime přepnutí osy z minulé návštěvy
     * uvázlo. Iniciální reload nastaví osu sám (loading==true) → skip, ať neblikneme prázdnou bází.
     */
    fun applyDefaultAxis() {
        if (_state.value.loading) return
        val target = settings.defaultAxis.value
        if (_state.value.axis != target) rebuild(target)
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
            .map { fav -> stub(fav.id, null, fav.name, fav.year, isShow = false, addedAtMs = fav.addedAtMs.takeIf { it > 0L }) }
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
        val jf = jfD.await(); val ws = wsD.await(); val tk = tkD.await()
        // Precedence PRO OBSAH (metadata): JELLYFIN > WORKING > TRAKT_WATCHLIST (putIfAbsent v tomto pořadí).
        val merged = LinkedHashMap<String, MediaItem>()
        for (list in listOf(jf, ws, tk)) {
            for (item in list) { val k = dedupKey(item) ?: continue; merged.putIfAbsent(k, item) }
        }
        // CATALOGUE — stabilní „přidáno": priorita JF datum > Trakt listed_at > uložený zdroj savedAtMs. Datum
        // ČLENSTVÍ (JF/Trakt) MÁ PŘEDNOST před datem uložení zdroje → dohledání zdroje NEPŘESKLÁDÁ řazení
        // „Nedávno přidané" (film si drží svou pozici, i když mu právě naskočil WorkingSource). Vkládá se na
        // vítěze dedupu jako jeho `addedAtMs` (cestuje s položkou přes enrich → řadíme podle pole, ne mapy).
        val recency = HashMap<String, Long>()
        for (list in listOf(jf, tk, ws)) {
            for (item in list) { val k = dedupKey(item) ?: continue; item.addedAtMs?.let { recency.putIfAbsent(k, it) } }
        }
        merged.values.map { item ->
            val k = dedupKey(item)
            val r = if (k != null) recency[k] else null
            if (r != null) item.copy(addedAtMs = r) else item
        }
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
                        fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.GENRES, ItemFields.DATE_CREATED),
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
                addedAtMs = ws.savedAtMs.takeIf { it > 0L },
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
            FilmotekaAxis.ALL -> groupAll(all)
            FilmotekaAxis.GENRE -> groupByGenre(all)
            FilmotekaAxis.COUNTRY -> groupByCountry(all)
        }
        _state.value = FilmotekaUiState(
            axis = axis, rails = rails, loading = false,
            allSort = settings.allSort.value, total = all.size,
        )
    }

    /**
     * CONVERGE V1 / CATALOGUE — osa „Vše": JEDNA plochá řada všech titulů. RECENT (výchozí) = podle reálného
     * data přidání ([MediaItem.addedAtMs]: JF DateCreated / Trakt listed_at / uložený zdroj / Oblíbené), sestupně
     * napříč VŠEMI zdroji (ne jen pořadí bucketů). Datum je stabilní vůči uložení zdroje → dohledání nepřeskládá
     * seznam. Bez data → na konec. ALPHABETICAL = název A–Z přes český Collator. Věkový gate už proběhl v bázi.
     */
    private fun groupAll(items: List<MediaItem>): List<FilmotekaRail> {
        if (items.isEmpty()) return emptyList()
        val sorted = when (settings.allSort.value) {
            FilmotekaAllSort.RECENT -> items.sortedByDescending { it.addedAtMs ?: Long.MIN_VALUE }
            FilmotekaAllSort.ALPHABETICAL -> {
                val coll = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))
                items.sortedWith(Comparator { a, b -> coll.compare(a.displayTitle, b.displayTitle) })
            }
        }
        val title = when (settings.allSort.value) {
            FilmotekaAllSort.RECENT -> "Nedávno přidané"
            FilmotekaAllSort.ALPHABETICAL -> "Abecedně"
        }
        return listOf(FilmotekaRail(id = "filmo_all", title = title, items = sorted.map { it.toHomeRowItem("all") }))
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

    /**
     * F2 — řady podle „kinematografie" ([CinematographyRegion]). Titul může být ve víc regionech (klíč karty
     * nese region). Vypnuté regiony (mimo settings.enabledRegions) se skryjí; OSTATNI (fallback) vždy zobraz.
     * Řazení = pořadí enumu (OSTATNI poslední). Prázdné regiony nevznikají.
     */
    private fun groupByCountry(items: List<MediaItem>): List<FilmotekaRail> {
        val enabled = settings.enabledRegions.value
        val byRegion = LinkedHashMap<CinematographyRegion, MutableList<MediaItem>>()
        for (item in items) {
            for (region in regionsOf(item.originCountries)) {
                // Skryj vypnuté regiony; OSTATNI ukaž vždy.
                if (region != CinematographyRegion.OSTATNI && region !in enabled) continue
                byRegion.getOrPut(region) { mutableListOf() }.add(item)
            }
        }
        return CinematographyRegion.entries.mapNotNull { region ->
            val list = byRegion[region] ?: return@mapNotNull null
            FilmotekaRail(
                id = "filmo_country_${region.name}",
                title = region.label,
                items = list.map { it.toHomeRowItem("country_${region.name}") },
            )
        }.filter { it.items.isNotEmpty() }
    }

    // ── Mapování ────────────────────────────────────────────────────────────────

    private fun MediaItem.toHomeRowItem(axisValue: String) = HomeRowItem(
        // Klíč nese hodnotu osy → titul může být ve víc řadách bez Compose key kolize.
        key = "filmo_${axisValue}_${tmdbId ?: imdbId ?: traktId}",
        title = displayTitle,
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
            addedAtMs = dateCreated?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        )
    }

    private fun dedupKey(item: MediaItem): String? = when {
        item.tmdbId != null -> "tmdb:${item.tmdbId}"
        !item.imdbId.isNullOrBlank() -> "imdb:${item.imdbId}"
        else -> null
    }

    private fun stub(tmdbId: Long, imdbId: String?, title: String, year: Int?, isShow: Boolean, addedAtMs: Long? = null) = MediaItem(
        traktId = 0L,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        year = year,
        overview = null,
        rating = null,
        genres = null,
        type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
        addedAtMs = addedAtMs,
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

    private companion object {
        /** Zrcadlí `TraktTokenProvider.KEY_ACCESS_TOKEN` (data-trakt) — reálný autorizační token API. */
        const val KEY_TRAKT_ACCESS_TOKEN = "TRAKT_ACCESS_TOKEN"
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
