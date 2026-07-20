package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.ContentAgeGate
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.feature.discover.enrich.MediaEnricher
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
import com.github.jankoran90.showlyfin.core.db.repository.FavoritesRepository
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.isSavedPlayable
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
import kotlinx.coroutines.sync.withLock
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
    private val curatorLoader: com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader,
    private val tmdb: TmdbRemoteDataSource,
    private val enricher: MediaEnricher,
    private val parentalControls: ParentalControlsRepository,
    private val favorites: FavoritesRepository,
    private val workingSources: WorkingSourceStore,
    private val traktSyncSignal: com.github.jankoran90.showlyfin.data.uploader.TraktSyncSignal,
    private val profileRepository: ProfileRepository,
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    /** LAPIDARY (SHW-96) — klíče titulů s uloženým zdrojem (odznak „hraje hned" na kartách; poskytnuto shellem). */
    val savedSourceKeys: StateFlow<Set<String>> = workingSources.savedKeys

    /** Řady k vykreslení (jen zapnuté, v uživatelově pořadí). JF knihovny render řeší zvlášť. */
    val rowConfigs: StateFlow<List<HomeRowConfig>> = store.rows
        .map { list -> list.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.rows.value.filter { it.enabled })

    /** VŠECHNY řady (i vypnuté) pro inline editor. */
    val allRows: StateFlow<List<HomeRowConfig>> = store.rows
    val sidebar: StateFlow<List<SidebarEntry>> = store.sidebar

    // COUCH per-profil — Trakt sekce/řady jen když AKTIVNÍ profil má vlastní Trakt token v balíku
    // (deti až po vlastním device-loginu → dětský Trakt). Konzumenti (TvShell, loadOnce) beze změny.
    // WEATHER (user 2026-07-16): na TV se Trakt přihlašuje z TELEFONU → token je v pref `TRAKT_ACCESS_TOKEN`
    // (odtud ho čte i TraktTokenProvider pro reálné API), ale config `credentials.trakt` bývá na TV prázdný
    // → původní hasTrakt(config) vracel false → traktAllowed=false → SKRYLY se VŠECHNY Trakt řady (watchlist,
    // historie, kurátor), i když Trakt reálně funguje. Ber Trakt jako dostupný, když má token BUĎ config NEBO
    // pref. Per-profil bezpečné: ProfileConfigApplier při přepnutí na profil bez Traktu (děti) pref smaže →
    // hasTrakt=false → řady skryté (stejný vzor jako TvFilmotekaViewModel, OTA 337).
    private fun hasTrakt(c: ProfileConfig): Boolean =
        !c.credentials.trakt?.accessToken.isNullOrBlank() ||
            !prefs.getString(KEY_TRAKT_ACCESS_TOKEN, null).isNullOrBlank()
    val traktAllowed: StateFlow<Boolean> = profileRepository.activeConfig
        .map { hasTrakt(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), hasTrakt(profileRepository.activeConfig.value))

    // CELLULOID M2.4 fix — ŽIVÉ čtení dostupnosti Traktu pro guardy načítání. `traktAllowed` je
    // WhileSubscribed StateFlow, který na telefonním shellu (Filmy) NIKDO nekolektuje (odebírá ho jen ui-tv) →
    // jeho `.value` zamrzne na konstrukční hodnotě a po Trakt device-loginu se guardy nepustí bez restartu.
    // Čti stejný zdroj pravdy přímo (jako TvFilmotekaViewModel.traktAllowed()).
    private fun traktAllowedNow(): Boolean = hasTrakt(profileRepository.activeConfig.value)

    // COUCH (SHW-88) — věkový strop dětského profilu (null = bez omezení). Řídí enrich (tahat certifikace)
    // i filtr v applyOps. Reaktivní na přepnutí profilu.
    private val ageCap: StateFlow<Int?> = parentalControls.profile
        .map { it.effectiveAgeCap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parentalControls.profile.value.effectiveAgeCap)
    private fun hideUnrated(): Boolean = parentalControls.profile.value.hideUnratedForAge

    // PARITA POČTŮ (SHW-98, user 2026-07-18 „v sekci domů míň výsledků v podsekci než Chci vidět"): globální
    // uživatelský strop počtu položek v řadě (Nastavení Filmy → prefs). 0/absent = per-řada default z configu.
    // Aplikuje se jako CENTRÁLNÍ ořez v applyOps (platí pro všechny řady jdoucí přes něj).
    private fun rowLimit(config: HomeRowConfig): Int {
        val user = prefs.getInt(KEY_HOME_ROW_LIMIT, 0)
        return (if (user > 0) user else config.limit).coerceIn(1, 60)
    }
    // Over-fetch: natáhni VÍC kandidátů, ať po gate/owned/known filtru zbude plný počet (řešení „děr" pod
    // deklarovaný počet u objevovacích řad Discover/Weighted/Brain, kde filtr běží až po ořezu).
    private fun rowFetch(config: HomeRowConfig): Int = (rowLimit(config) * HOME_ROW_OVERFETCH).coerceIn(1, 60)

    // Owned trakt id (viděné ∪ hodnocené ∪ watchlist) — pro filtr „skryj co už mám" na reco/discover řadách.
    // Cache per profil; vyčištěno v [reloadAllRows]. Prázdné pro profil bez Traktu.
    @Volatile private var ownedIdsCache: Set<Long>? = null
    private suspend fun ownedIds(): Set<Long> {
        ownedIdsCache?.let { return it }
        val ids = if (traktAllowedNow()) runCatching { traktLoader.ownedTraktIds() }.getOrElse { emptySet() } else emptySet()
        ownedIdsCache = ids
        return ids
    }

    /** Id aktivního profilu — TvHomeScreen na jeho změnu přenačte i JF knihovní řady (LibraryRowsViewModel). */
    val activeProfileId: StateFlow<Long?> = profileRepository.activeProfile
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profileRepository.activeProfile.value?.id)

    /** Netflix immersive pozadí (fokusovaná karta řídí fanart). */
    val immersiveBackground: StateFlow<Boolean> = store.immersiveBackground

    /** OTA 299: immersive hlavička nahoře (název/rok/popis fokusované karty) — oddělený přepínač od pozadí. */
    val immersiveHeader: StateFlow<Boolean> = store.immersiveHeader
    fun setImmersiveHeader(enabled: Boolean) = store.setImmersiveHeader(enabled)

    /** Přepínač „auto-přehrát u karty se zapamatovaným zdrojem" (řada „Uloženo k přehrání", `playDirectly`).
     * Default OFF (user 07-19: „nechci autoplay při otevření karty") → klik jen otevře detail; opt-in v Nastavení.
     * Čte se přímo při kliku (vždy aktuální hodnota), sdílené phone+TV (raw `trakt_prefs`). */
    fun autoplayRememberedEnabled(): Boolean = prefs.getBoolean(KEY_AUTOPLAY_REMEMBERED, false)

    /** CONVERGE (SHW-97): počet řádků popisu v immersive hlavičce (0 = auto). */
    val immersiveHeaderLines: StateFlow<Int> = store.immersiveHeaderLines
    fun setImmersiveHeaderLines(lines: Int) = store.setImmersiveHeaderLines(lines)

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
    // Progresivní load Trakt řad: serializuj Trakt API volání (jinak paralelní salva → 429). Viz ensureRowLoaded.
    private val traktLoadMutex = kotlinx.coroutines.sync.Mutex()

    init {
        // COUCH per-profil: každý profil má vlastní layout domova. Nejdřív přepni store na layout profilu
        // (i iniciálně), pak (na ZMĚNU) přenačti obsah — jeden collector = pořadí switchProfile → reload.
        var firstProfileEmit = true
        profileRepository.activeProfile
            .onEach { p ->
                store.switchProfile(p?.id)
                // PARITA s telefonem (FilmySourceAvailabilityViewModel.init → store.refresh()): TV dosud NIKDY
                // proaktivně netáhla uložené zdroje ze serveru → savedSourceKeys odrážely jen lokální disk TV
                // (prázdný, zdroje uložil telefon/backend) → odznaky/„Přehrát" na TV chyběly. Táhni ze serveru
                // per profil (i iniciálně) — savedKeys i lokální get() se naplní pro odznaky i detail (user 2026-07-18).
                workingSources.refresh()
                if (firstProfileEmit) firstProfileEmit = false
                else {
                    android.util.Log.i("COUCH_Home", "profil změněn → ${p?.name} (id=${p?.id}, trakt=${traktAllowed.value}) → reload")
                    reloadAllRows()
                }
            }
            .launchIn(viewModelScope)

        // COUCH: watchlist se změnil v detailu (přidání/odebrání „Chci vidět") → přenačti Trakt řady, aby se
        // čerstvý titul objevil i v DOMOVSKÉ řadě (ne jen v sekci Trakt). `drop(1)` = ignoruj iniciální hodnotu.
        traktSyncSignal.version
            .drop(1)
            .onEach { invalidateTraktRows() }
            .launchIn(viewModelScope)
    }

    /** Přenačti Trakt řady (watchlist/historie/seznamy/reco) — jejich data závisí na Trakt sync stavu. */
    private fun invalidateTraktRows() {
        ownedIdsCache = null // „skryj co mám" filtr (owned ∋ watchlist) zastaral
        val traktConfigs = rowConfigs.value.filter { it.source in TRAKT_SOURCES }
        traktConfigs.forEach { loadedHash.remove(it.id) }
        traktConfigs.forEach { ensureRowLoaded(it) }
    }

    /** Zahoď cache řad a přenačti všechny aktuálně zapnuté (po přepnutí profilu / vynuceně). */
    fun reloadAllRows() {
        android.util.Log.i("COUCH_Home", "reloadAllRows: ${rowConfigs.value.size} řad")
        loadedHash.clear()
        ownedIdsCache = null
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
                    // 🐞 Progresivní load Trakt řad: watchlist/historie/seznamy/reco se dřív načítaly VŠECHNY
                    // současně (reloadAllRows i invalidateTraktRows spouští každou vlastním launch) → paralelní
                    // salva na Trakt API → 429 rate-limit → některé řady zůstaly prázdné. Serializuj Trakt
                    // volání jedním mutexem: řady doskakují postupně (progresivně) a bez burstu. Ostatní
                    // zdroje (Jellyfin/TMDB) běží dál plně paralelně.
                    val items = runCatching {
                        if (config.source in TRAKT_SOURCES) traktLoadMutex.withLock { loadOnce(config) }
                        else loadOnce(config)
                    }
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
        if (config.source in TRAKT_SOURCES && !traktAllowedNow()) {
            android.util.Log.i("COUCH_Home", "Trakt řada '${config.id}' skryta — zamčený profil")
            return emptyList()
        }
        return when (config.source) {
        HomeRowSourceType.DISCOVER -> loadDiscover(config)
        HomeRowSourceType.CONTINUE_WATCHING -> loadJellyfin(config) { userUuid ->
            resumeItems(userUuid, config.limit, jellyfinLibraryUuids())
        }
        HomeRowSourceType.NEXT_UP -> loadJellyfin(config) { userUuid ->
            nextUpItems(userUuid, config.limit, jellyfinLibraryUuids())
        }
        // Sloučené Pokračovat + Další díly — resume má přednost, dedup dle seriálu/položky.
        HomeRowSourceType.CONTINUE_WATCHING_COMBINED -> loadJellyfin(config) { userUuid ->
            val libs = jellyfinLibraryUuids()
            val seen = mutableSetOf<String>()
            (resumeItems(userUuid, config.limit, libs) + nextUpItems(userUuid, config.limit, libs))
                .filter { dto -> seen.add((dto.seriesId ?: dto.id).toString()) }
                .take(config.limit)
        }
        // „Nejnovější v <knihovna>" — getLatestMedia pro konkrétní knihovnu. Gate na whitelist (nezobrazuj
        // nezvolené knihovny — user 07-19).
        HomeRowSourceType.RECENTLY_ADDED -> {
            val parent = config.params[HomeRowParams.LIBRARY_ID].toUuidOrNull()
            val libs = jellyfinLibraryUuids()
            if (parent == null || (libs != null && parent !in libs)) emptyList()
            else loadJellyfin(config) { userUuid ->
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
        // COUCH (SHW-88) play-count vážená doporučení „na míru dle sledování".
        HomeRowSourceType.WEIGHTED_RECOMMENDATIONS ->
            traktLoader.weightedRecommendations(rowFetch(config)).map { it.toHomeRowItem(config) }
        // AUTEUR (SHW-91) kurátorský mozek „Pro tebe". Prázdné (mozek pending/down/studený vkus) → řada se
        // NEzobrazí (viz filtr prázdných řad). ŽÁDNÝ fallback na weightedRecommendations — dělal duplicitní
        // řadu se samostatnou „Na míru podle sledování" (WEIGHTED_RECOMMENDATIONS = totéž), navíc REFLEX
        // wait=false to zhoršil (první load je vždy pending). Mechanická doporučení má vlastní řada.
        HomeRowSourceType.BRAIN_FOR_YOU ->
            curatorLoader.forYou(rowFetch(config)).map { it.toHomeRowItem(config) }
        // LIBRARY_TILES / GENRES / STUDIOS = dlaždicové navigační řady → 2. vlna (viz Known gaps).
        else -> emptyList()
        }
    }

    /**
     * ORCHARD (user 07-19) — knihovny povolené na home = whitelist aktivního profilu. `null` = bez omezení
     * (showlyfin / profil bez výběru → všechny, zpětná kompatibilita); prázdné = ŽÁDNÝ JF na home (Filmy opt-in,
     * dokud user nevybere knihovnu); jinak scope resume/next-up jen na vybrané knihovny. Bez tohoto „Pokračovat"/
     * „Další díly" táhly z CELÉHO serveru napříč nezvolenými knihovnami (spam home).
     */
    private fun jellyfinLibraryUuids(): List<UUID>? {
        val cfg = profileRepository.activeConfig.value
        // Home výběr; fallback na Knihovnu (showlyfin paritu) když home-výběr není nastaven (null).
        return (cfg.homeJfLibraries ?: cfg.jellyfinLibraryWhitelist)?.mapNotNull { it.toUuidOrNull() }
    }

    private suspend fun resumeItems(userUuid: UUID, limit: Int, libraries: List<UUID>?): List<BaseItemDto> {
        if (libraries == null) {
            return apiClient.itemsApi.getResumeItems(
                userId = userUuid,
                limit = limit,
                mediaTypes = listOf(JfMediaType.VIDEO),
                fields = ROW_ITEM_FIELDS,
                enableImages = true,
            ).content.items
        }
        if (libraries.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return libraries.flatMap { lib ->
            runCatching {
                apiClient.itemsApi.getResumeItems(
                    userId = userUuid,
                    parentId = lib,
                    limit = limit,
                    mediaTypes = listOf(JfMediaType.VIDEO),
                    fields = ROW_ITEM_FIELDS,
                    enableImages = true,
                ).content.items
            }.getOrElse { emptyList() }
        }.filter { seen.add(it.id.toString()) }.take(limit)
    }

    private suspend fun nextUpItems(userUuid: UUID, limit: Int, libraries: List<UUID>?): List<BaseItemDto> {
        if (libraries == null) {
            return apiClient.tvShowsApi.getNextUp(
                userId = userUuid,
                limit = limit,
                fields = ROW_ITEM_FIELDS,
                // OTA 299: bez enableImages nechodí imageTags → landscape karta „Další díly" neměla still dílu.
                enableImages = true,
            ).content.items
        }
        if (libraries.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return libraries.flatMap { lib ->
            runCatching {
                apiClient.tvShowsApi.getNextUp(
                    userId = userUuid,
                    parentId = lib,
                    limit = limit,
                    fields = ROW_ITEM_FIELDS,
                    enableImages = true,
                ).content.items
            }.getOrElse { emptyList() }
        }.filter { seen.add(it.id.toString()) }.take(limit)
    }

    // ── SAVED_FOR_PLAYBACK (zapamatované zdroje) ───────────────────────────────

    /**
     * Řada „Uloženo k přehrání": tituly z [WorkingSourceStore.getAll] (nejnovější první). WorkingSource nenese
     * poster → dohledáme přes TMDB paralelně. S4b: položky nesou `playDirectly=true` → klik přehraje
     * zapamatovaný zdroj rovnou (one-click), detail se otevře až po BACK z přehrávače.
     */
    private suspend fun loadSavedForPlayback(config: HomeRowConfig): List<HomeRowItem> {
        workingSources.refresh()
        // SENTINEL bod 3 B — „Uloženo k přehrání" je one-click přehrání (playDirectly) → jen reálně cached,
        // jinak by klik na evikovaný/stahující se zdroj skončil zásekem.
        val saved = workingSources.getAll().filter { it.isSavedPlayable() }.take(config.limit.coerceIn(1, 60))
        // CELLULOID (SHW-98) FIX: working source nenese poster/backdrop. Dřív si loadSavedForPlayback dělal
        // VLASTNÍ neškrcený TMDB burst bez jazyka (`fetchMovieDetails(id)` → cache klíč „id|") → při cold-startu
        // se stovkou souběžných dotazů rozstřelil o transient/rate-limit a CachedTmdbRemoteDataSource ten null
        // NATRVALO zacacheoval; detail (stejný null-jazyk klíč) pak zdědil prázdno = řada bez coverů + černý
        // fanart. Reuse SDÍLENÉHO MediaEnricheru (Semaphore(6), cs-CZ, cache sdílená s Objevit) — tatáž cesta,
        // co plakáty na „Objevit" spolehlivě plní. Enricher navíc dohledá tmdbId z imdb (imdb-keyed working source).
        val base = saved.map { ws ->
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
        val enriched = enricher.enrich(base, withCertification = ageCap.value != null)
        val withPoster = enriched.count { it.posterPath != null }
        Timber.i("[TvHome] Uloženo k přehrání: %d titulů, %d s posterem (enricher)", enriched.size, withPoster)
        return saved.zip(enriched).map { (ws, item) ->
            HomeRowItem(
                key = "saved_${ws.tmdb}",
                title = item.displayTitle,
                posterUrl = item.posterUrl("w342"),
                landscapeUrl = item.backdropUrl("w780"),
                mediaItem = item,
                // S4b: každý titul tady MÁ zapamatovaný zdroj → klik přehraje rovnou (one-click).
                playDirectly = true,
            )
        }
    }

    // ── DISCOVER (Trakt + TMDB) ────────────────────────────────────────────────

    private suspend fun loadDiscover(config: HomeRowConfig): List<HomeRowItem> {
        val isShow = config.params[HomeRowParams.TAB].equals("shows", ignoreCase = true)
        val filter = config.params[HomeRowParams.FILTER]?.lowercase() ?: "trending"
        val limit = rowFetch(config)   // over-fetch → applyOps ořízne na rowLimit (bez „děr" po owned filtru)
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
        // Sdílený enricher (poster/backdrop + CZ titulek + žánry + certifikace jen když aktivní strop).
        return enricher.enrich(raw, withCertification = ageCap.value != null).map { item ->
            HomeRowItem(
                key = "disc_${item.type}_${item.tmdbId ?: item.traktId}",
                title = item.displayTitle,
                year = item.year,
                posterUrl = item.posterUrl("w342"),
                landscapeUrl = item.backdropUrl("w780"),
                mediaItem = item,
            )
        }
    }

    /** COUCH T1/T2 — obohacené Trakt [MediaItem] (z [TraktRowLoader]) → [HomeRowItem] pro řadu domova. */
    private fun MediaItem.toHomeRowItem(config: HomeRowConfig) = HomeRowItem(
        key = "trakt_${config.id}_${type}_${tmdbId ?: traktId}",
        title = displayTitle,
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

    private suspend fun applyOps(items: List<HomeRowItem>, config: HomeRowConfig): List<HomeRowItem> {
        var r = items
        if (config.params.boolParam(HomeRowParams.HIDE_WATCHED)) r = r.filter { !it.watched }
        // COUCH (SHW-88) — věkový strop dětského profilu na OBJEVOVACÍCH řadách (JF knihovna vyňata).
        val cap = ageCap.value
        if (cap != null && config.source !in AGE_EXEMPT_SOURCES) {
            val strict = hideUnrated()
            r = r.filter { item -> item.mediaItem?.let { ContentAgeGate.isAllowed(cap, it, strict) } ?: true }
        }
        // „Skryj co už mám" na reco/discover řadách (Trakt owned). Trakt řady řeší owned už v loaderu.
        if (config.source in OWNED_FILTER_SOURCES) {
            val owned = ownedIds()
            if (owned.isNotEmpty()) r = r.filter { item -> item.mediaItem?.traktId?.let { it !in owned } ?: true }
        }
        r = when (config.sort) {
            HomeRowSort.RATING -> r.sortedByDescending { it.mediaItem?.rating ?: -1f }
            HomeRowSort.YEAR_DESC -> r.sortedByDescending { it.year ?: 0 }
            HomeRowSort.ALPHA -> r.sortedBy { it.title.lowercase() }
            HomeRowSort.RANDOM -> r.shuffled()
            HomeRowSort.RECENT, HomeRowSort.DEFAULT -> r
        }
        return r.take(rowLimit(config))
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

/** WEATHER: pref klíč Trakt tokenu (zrcadlí TraktTokenProvider); zdroj pravdy pro „přihlášen k Traktu" na TV. */
private const val KEY_TRAKT_ACCESS_TOKEN = "TRAKT_ACCESS_TOKEN"

/** PARITA POČTŮ (SHW-98): globální uživatelský počet položek v řadě Home (Nastavení Filmy). 0 = per-řada default. */
const val KEY_HOME_ROW_LIMIT = "home_row_item_limit"
/** Opt-in auto-přehrání u karty se zapamatovaným zdrojem (řada „Uloženo k přehrání"). Default false. */
const val KEY_AUTOPLAY_REMEMBERED = "autoplay_remembered_enabled"
/** Kolikrát víc kandidátů natáhnout, ať po filtrech (gate/owned/known) zbude plný počet (žádné „díry"). */
private const val HOME_ROW_OVERFETCH = 2

/** COUCH R2: Trakt zdroje řad — skryté pro zamčený/dětský profil. */
private val TRAKT_SOURCES = setOf(
    HomeRowSourceType.TRAKT_WATCHLIST,
    HomeRowSourceType.TRAKT_HISTORY,
    HomeRowSourceType.TRAKT_LIST,
    HomeRowSourceType.COUCHMONKEY_RECOMMENDATIONS,
    HomeRowSourceType.WEIGHTED_RECOMMENDATIONS,
    HomeRowSourceType.BRAIN_FOR_YOU,
)

/** COUCH (SHW-88) — zdroje z Jellyfin knihovny (pro děti schválené) → věkový filtr se NEaplikuje. */
private val AGE_EXEMPT_SOURCES = setOf(
    HomeRowSourceType.CONTINUE_WATCHING,
    HomeRowSourceType.NEXT_UP,
    HomeRowSourceType.CONTINUE_WATCHING_COMBINED,
    HomeRowSourceType.RECENTLY_ADDED,
    HomeRowSourceType.COLLECTION,
    HomeRowSourceType.JELLYFIN_LIBRARY,
    HomeRowSourceType.SAVED_FOR_PLAYBACK,
)

/** Řady, kde skrýváme co už mám (doporučovací/objevovací). */
private val OWNED_FILTER_SOURCES = setOf(
    HomeRowSourceType.DISCOVER,
    HomeRowSourceType.WEIGHTED_RECOMMENDATIONS,
    HomeRowSourceType.BRAIN_FOR_YOU,
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
