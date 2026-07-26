package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSource
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.isSavedPlayable
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import javax.inject.Singleton

/**
 * FOYER (SHW-107) — SBĚR BÁZE FILMOTÉKY jako sdílená, injektovatelná služba. Vytaženo 1:1 z
 * [TvFilmotekaViewModel] (CINEMATHEQUE), aby tutéž plochu mohl číst i domov ([HomeRowSourceType.FILMOTEKA_RECENT]
 * → řada „Filmotéka — nedávno přidané") bez duplikace logiky = žádný drift mezi sekcí a řadou.
 *
 * Báze = Jellyfin knihovna ∪ zapamatované zdroje ∪ Trakt „Chci vidět" (dedup tmdb→imdb, precedence
 * JELLYFIN > WORKING > TRAKT), obohacená ([MediaEnricher]) a prohnaná věkovým gate ([ContentAgeGate]).
 * Oblíbené jdou zvlášť ([loadFavorites]) — v sekci jsou reaktivní (StateFlow), tady se přimergují
 * v [mergeWithFavorites], které zároveň dopočítá datum „přidáno".
 *
 * CATALOGUE pravidlo (nechává se beze změny): „přidáno" = datum ČLENSTVÍ (JF `DateCreated` / Trakt
 * `listed_at`), NIKDY datum přiděleného working-source — jinak by dohledání zdroje přeskládalo pořadí.
 */
/**
 * FOYER (SHW-107) — jedna Jellyfin KOLEKCE (BoxSet) jako samostatná entita. Není to film: klik na ni
 * otevře její OBSAH (mřížka položek kolekce), ne detail s hledáním zdroje.
 */
data class FilmotekaCollection(
    val jellyfinId: String,
    val name: String,
    val posterUrl: String?,
)

@Singleton
class FilmotekaBaseLoader @Inject constructor(
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
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {

    private fun ageCap(): Int? = parentalControls.profile.value.effectiveAgeCap
    private fun hideUnrated(): Boolean = parentalControls.profile.value.hideUnratedForAge

    /**
     * CONVERGE bug (2026-07-16): Trakt token bývá na TV JEN v prefs (device-flow login), do backend configu
     * se nepropíše → guard jen podle configu watchlist nikdy nezahrnul. Ber OBOJE = stejný zdroj pravdy,
     * jaký reálně autorizuje API. Dětský profil má pref smazaný ([ProfileConfigApplier]) → false.
     */
    private fun traktAllowed(): Boolean =
        !profileRepository.activeConfig.value.credentials.trakt?.accessToken.isNullOrBlank() ||
            !prefs.getString(KEY_TRAKT_ACCESS_TOKEN, null).isNullOrBlank()

    /** Báze bez Oblíbených: JF ∪ working ∪ Trakt watchlist → dedup → enrich → věkový gate. */
    suspend fun loadBase(enabled: Set<FilmotekaSource> = settings.sources.value): List<MediaItem> {
        val cap = ageCap()
        val enriched = enricher.enrich(gather(enabled), withCertification = cap != null)
        return ContentAgeGate.filter(cap, enriched, hideUnrated())
    }

    /** Oblíbené (jen filmy) → enrich → věkový gate. Vypnutý zdroj = prázdno. */
    suspend fun loadFavorites(
        list: List<FavoriteItem>,
        enabled: Set<FilmotekaSource> = settings.sources.value,
    ): List<MediaItem> {
        if (FilmotekaSource.FAVORITES !in enabled) return emptyList()
        val cap = ageCap()
        val base = list.filter { it.kind == FavoriteKind.MOVIE && it.id > 0L }
            .map { fav -> stub(fav.id, null, fav.name, fav.year, isShow = false, addedAtMs = fav.addedAtMs.takeIf { it > 0L }) }
        val enriched = enricher.enrich(base, withCertification = cap != null)
        return ContentAgeGate.filter(cap, enriched, hideUnrated())
    }

    /**
     * Sloučí bázi s Oblíbenými (base vyhrává) a KAŽDÉ položce dopočítá `addedAtMs`: členské datum
     * (JF/Trakt) → datum přidání do Oblíbených → jinak MĚSÍC ZPĚT (film jen se staženým zdrojem zůstane
     * ve Filmotéce, ale nevynese se nad čerstvé z „Chci vidět"; user 2026-07-22).
     */
    fun mergeWithFavorites(base: List<MediaItem>, favoriteItems: List<MediaItem>): List<MediaItem> {
        val combined = LinkedHashMap<String, MediaItem>()
        for (item in base + favoriteItems) { val k = dedupKey(item) ?: continue; combined.putIfAbsent(k, item) }
        val favDates = HashMap<String, Long>()
        for (f in favoriteItems) { val k = dedupKey(f) ?: continue; f.addedAtMs?.let { favDates.putIfAbsent(k, it) } }
        val backdate = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return combined.values.map { item ->
            if (item.addedAtMs != null) item
            else item.copy(addedAtMs = (dedupKey(item)?.let { favDates[it] }) ?: backdate)
        }
    }

    /**
     * FOYER — hotová plocha pro řadu domova „Filmotéka — nedávno přidané": celá Filmotéka (vč. Oblíbených)
     * seřazená podle data přidání sestupně, ořezaná na [limit]. Tentýž obsah, jaký ukáže sekce Filmotéka
     * s osou „Vše" a řazením „Nedávno" → klik na „Celá filmotéka" navazuje bez překvapení.
     */
    suspend fun recentlyAdded(limit: Int): List<MediaItem> {
        // Krátká cache: řada domova by jinak při KAŽDÉM startu protáhla celou knihovnu enrichem (stovky TMDB
        // dotazů) — a to i když si sekci Filmotéka vůbec neotevřeš. Klíč nese ID profilu, takže přepnutí
        // profilu cache NIKDY nepoužije (žádný přelév obsahu mezi dospělým a dětským).
        val pid = profileRepository.activeProfile.value?.id
        val now = System.currentTimeMillis()
        recentCache
            ?.takeIf { it.profileId == pid && now - it.atMs < RECENT_CACHE_TTL_MS }
            ?.let { return it.items.take(limit.coerceAtLeast(1)) }

        val enabled = settings.sources.value
        val base = loadBase(enabled)
        val favs = loadFavorites(favorites.items.value, enabled)
        val all = mergeWithFavorites(base, favs).sortedByDescending { it.addedAtMs ?: 0L }
        recentCache = RecentCache(pid, now, all)
        return all.take(limit.coerceAtLeast(1))
    }

    /** Zahoď cache „nedávno přidané" (přepnutí profilu / ruční refresh domova). */
    fun invalidateRecent() { recentCache = null }

    private data class RecentCache(val profileId: Long?, val atMs: Long, val items: List<MediaItem>)

    @Volatile private var recentCache: RecentCache? = null

    // ── Sběr ────────────────────────────────────────────────────────────────────

    private suspend fun gather(enabled: Set<FilmotekaSource>): List<MediaItem> = coroutineScope {
        val jfD = async { if (FilmotekaSource.JELLYFIN in enabled) loadJellyfinLibrary() else emptyList() }
        val wsD = async { if (FilmotekaSource.WORKING in enabled) loadWorkingSources() else emptyList() }
        val tkD = async {
            if (FilmotekaSource.TRAKT_WATCHLIST in enabled && traktAllowed())
                runCatching { traktLoader.watchlist("all") }.getOrElse { emptyList() }
            else emptyList()
        }
        val jf = jfD.await(); val ws = wsD.await(); val tk = tkD.await()
        val merged = LinkedHashMap<String, MediaItem>()
        for (list in listOf(jf, ws, tk)) {
            for (item in list) { val k = dedupKey(item) ?: continue; merged.putIfAbsent(k, item) }
        }
        // Recency JEN z členských seznamů (JF/Trakt) — working-source datum sem nesmí (přeskládalo pořadí).
        val recency = HashMap<String, Long>()
        for (list in listOf(jf, tk)) {
            for (item in list) { val k = dedupKey(item) ?: continue; item.addedAtMs?.let { recency.putIfAbsent(k, it) } }
        }
        merged.values.map { item -> item.copy(addedAtMs = dedupKey(item)?.let { recency[it] }) }
    }

    private suspend fun loadJellyfinLibrary(): List<MediaItem> = coroutineScope {
        val session = prepareJellyfin() ?: return@coroutineScope emptyList()
        // ORCHARD (user 07-19) — Filmotéka respektuje SVŮJ výběr JF knihoven (null = všechny, prázdné = žádná).
        val filmoWhitelist = profileRepository.activeConfig.value.filmotekaJfLibraries
            ?.map { it.replace("-", "").lowercase() }?.toSet()
        val views = runCatching { apiClient.userViewsApi.getUserViews(session.userUuid).content.items }
            .getOrElse { Timber.w(it, "[Filmoteka] getUserViews selhalo"); emptyList() }
            .filter { it.isFilmotekaLibrary() }
            .let { list ->
                if (filmoWhitelist == null) list
                else list.filter { it.id.toString().replace("-", "").lowercase() in filmoWhitelist }
            }
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
        }.awaitAll().flatten().mapNotNull { it.toFilmotekaMediaItem(session.serverUrl, session.token) }
    }

    private suspend fun loadWorkingSources(): List<MediaItem> {
        workingSources.refresh()
        return workingSources.getAll().mapNotNull { ws ->
            if (ws.tmdb <= 0L && ws.imdb.isBlank()) return@mapNotNull null
            if (!ws.isSavedPlayable()) return@mapNotNull null   // SENTINEL bod 3 B — jen reálně cached
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
                // NEMĚNNÉ datum prvního uložení → working-only film v „Nedávno přidané" neskáče při re-cache.
                addedAtMs = ws.firstSavedAtMs.takeIf { it > 0L } ?: ws.savedAtMs.takeIf { it > 0L },
            )
        }
    }

    /**
     * FOYER (SHW-107) — KOLEKCE (Jellyfin BoxSet) jako VLASTNÍ karty, které umí otevřít svůj obsah
     * (`TvDestination.LibraryItems` s `parentItemType=BOX_SET`), ne jako fiktivní film. Vrací prázdno,
     * když je přepínač „Karty kolekcí" vypnutý (default) nebo Jellyfin zdroj není zapnutý.
     */
    suspend fun loadCollections(): List<FilmotekaCollection> = coroutineScope {
        if (!settings.showCollections.value) return@coroutineScope emptyList()
        if (FilmotekaSource.JELLYFIN !in settings.sources.value) return@coroutineScope emptyList()
        val session = prepareJellyfin() ?: return@coroutineScope emptyList()
        val filmoWhitelist = profileRepository.activeConfig.value.filmotekaJfLibraries
            ?.map { it.replace("-", "").lowercase() }?.toSet()
        val views = runCatching { apiClient.userViewsApi.getUserViews(session.userUuid).content.items }
            .getOrElse { emptyList() }
            .filter { it.isFilmotekaLibrary() }
            .let { list ->
                if (filmoWhitelist == null) list
                else list.filter { it.id.toString().replace("-", "").lowercase() in filmoWhitelist }
            }
        views.map { view ->
            async {
                runCatching {
                    apiClient.itemsApi.getItems(
                        userId = session.userUuid,
                        parentId = view.id,
                        includeItemTypes = listOf(BaseItemKind.BOX_SET),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        limit = 100,
                    ).content.items
                }.getOrElse { Timber.w(it, "[Filmoteka] kolekce '${view.name}' selhaly"); emptyList() }
            }
        }.awaitAll().flatten()
            .filter { it.type == BaseItemKind.BOX_SET }
            .map {
                FilmotekaCollection(
                    jellyfinId = it.id.toString(),
                    name = it.name.orEmpty(),
                    posterUrl = it.jellyfinPosterUrl(session.serverUrl, session.token),
                )
            }
            .distinctBy { it.jellyfinId }
    }

    // ── Mapování ────────────────────────────────────────────────────────────────

    /**
     * FOYER (user 2026-07-26 „některé filmy nemají načtené obrázky coveru"): plakát z Jellyfinu se nese
     * s položkou jako FALLBACK. Enricher plní [MediaItem.posterUrl] z TMDB; když titul na TMDB nesedí
     * (nebo tam plakát není), zůstala karta prázdná, přestože Jellyfin obrázek MÁ. Vyplněný `posterUrl`
     * si enricher nepřepisuje jen tak — a i kdyby, TMDB plakát je hezčí, tak je to výhra oběma směry.
     */
    private fun BaseItemDto.toFilmotekaMediaItem(serverUrl: String, token: String): MediaItem? {
        // FOYER (SHW-107, DŮKAZ 2026-07-26): Jellyfin vrací BOX_SET (kolekce) i na dotaz `includeItemTypes=
        // Movie,Series` (ověřeno proti serveru: „Harry Potter (kolekce)" Tmdb=1241 mezi 16 položkami knihovny
        // Rodinné filmy). Bez téhle pojistky se kolekce mapovala na FILM s TMDB id KOLEKCE → enrich stáhl
        // úplně jiný film → karta s cizím obsahem, u které není co přehrát (přesně userův report). Filmy
        // uvnitř kolekce v seznamu ZŮSTÁVAJÍ (rekurzivní dotaz je vrací zvlášť) — nic se neztratí.
        if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES) return null
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
            fallbackPosterUrl = jellyfinPosterUrl(serverUrl, token),
            addedAtMs = dateCreated?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        )
    }

    /** Plakát z Jellyfinu (Primary tag → cache-busting; bez tagu se obrázek stejně vrátí, jen bez cache klíče). */
    private fun BaseItemDto.jellyfinPosterUrl(serverUrl: String, token: String): String? {
        val tag = imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY) ?: return null
        return "$serverUrl/Items/$id/Images/Primary?tag=$tag&fillWidth=400&quality=90&api_key=$token"
    }

    private fun dedupKey(item: MediaItem): String? = when {
        item.tmdbId != null -> "tmdb:${item.tmdbId}"
        !item.imdbId.isNullOrBlank() -> "imdb:${item.imdbId}"
        else -> null
    }

    private fun stub(tmdbId: Long, imdbId: String?, title: String, year: Int?, isShow: Boolean, addedAtMs: Long? = null) =
        MediaItem(
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

    // ── Jellyfin session ────────────────────────────────────────────────────────

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
        /** Jak dlouho platí cache řady „Filmotéka — nedávno přidané" (10 min). */
        const val RECENT_CACHE_TTL_MS = 10L * 60 * 1000
    }
}

/** Filmové/seriálové/smíšené knihovny (vzor LibraryRowsViewModel.isMediaLibrary); RealDebrid vynech. */
internal fun BaseItemDto.isFilmotekaLibrary(): Boolean {
    val ct = collectionType?.name?.uppercase()
    val allowed = ct == null || ct == "MOVIES" || ct == "TVSHOWS" || ct == "MIXED"
    if (!allowed) return false
    val n = name?.lowercase() ?: return true
    return !n.contains("realdebrid") && !n.contains("real-debrid")
}
