package com.github.jankoran90.showlyfin.feature.listen

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.network.ConnectivityObserver
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.download.AudiobookDownloadManager
import com.github.jankoran90.showlyfin.data.abs.download.EpisodeDownloadManager
import android.net.Uri
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.toAudiobook
import com.github.jankoran90.showlyfin.feature.listen.service.AudiobookBatchDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.AudiobookOwnershipRepository
import com.github.jankoran90.showlyfin.data.uploader.PodcastSourcesRepository
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import com.github.jankoran90.showlyfin.feature.listen.player.PlayerState
import com.github.jankoran90.showlyfin.feature.listen.player.DirectAudio
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ListenViewModel @Inject constructor(
    private val repo: AbsRepository,
    private val downloadManager: EpisodeDownloadManager,
    private val audiobookDownloads: AudiobookDownloadManager,
    private val offline: OfflineDownloadManager,
    private val connection: AudiobookPlayerConnection,
    private val connectivity: ConnectivityObserver,
    private val profileRepository: ProfileRepository,
    private val sourcesRepo: PodcastSourcesRepository,
    private val audiobookOwnership: AudiobookOwnershipRepository,
    private val linkStore: com.github.jankoran90.showlyfin.feature.listen.player.PodcastLinkStore,
    private val absPrefs: AbsPreferences,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    // TWINE (SHW-74 / plán F7): propojení zdrojů „týž pořad jako audio+video". Lokální, reaktivní.
    /** Reaktivní seznam propojených pořadů → Sledované grid sloučí slinkované zdroje do 1 karty. */
    val sourceLinks = linkStore.links

    /** Klíč zdroje shodný s [PodcastLinkStore] (`type:ref`) — pro dedup ve Sledovaných. */
    fun sourceKey(source: PodcastSource) = linkStore.key(source)

    /** Propoj dva zdroje jako týž pořad (audio+video). */
    fun linkSources(a: PodcastSource, b: PodcastSource) = linkStore.link(a, b)

    /** Zruš propojení celé skupiny. */
    fun unlinkGroup(groupId: String) = linkStore.unlink(groupId)

    // ───────────────────────── WEFT (SHW-75/W5): per-profil skrytí pořadů ─────────────────────────
    /** Config aktivního profilu (reaktivně) → Sledované/Timeline se přefiltrují při změně skrytí. */
    val profileConfig = profileRepository.activeConfig

    /** Profily (2026-08-15) — aktivní profil (`isAdmin` rozhoduje dospělý/dětský vzhled sekce Poslech). */
    val activeProfile = profileRepository.activeProfile

    /**
     * User (2026-08-15) „na kartě podcastu při long pressu možnost ukázat u dětí" — reaktivně
     * skrytí podcastů PRO DĚTSKÝ profil (první ne-admin profil; generické napříč appkami s 2
     * profily, ne natvrdo Slovo UUID — ty žijí výš v `ui-slovo-phone`, sem by šel cyklus).
     */
    val kidsHiddenPodcastIds: StateFlow<Set<String>> = profileRepository.observeAll()
        .map { profiles ->
            profiles.firstOrNull { !it.isAdmin }
                ?.let { com.github.jankoran90.showlyfin.core.domain.ProfileConfig.fromJson(it.configJson).hiddenPodcastIds }
                .orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Admin (Dospělý) — přepne, jestli podcast [podcastId] vidí dětský profil. No-op bez dětského profilu. */
    fun setPodcastVisibleForKids(podcastId: String, visible: Boolean) {
        viewModelScope.launch {
            val kids = profileRepository.getAll().firstOrNull { !it.isAdmin } ?: return@launch
            profileRepository.updateConfig(kids.id) { cfg ->
                cfg.copy(
                    hiddenPodcastIds = if (visible) cfg.hiddenPodcastIds - podcastId else cfg.hiddenPodcastIds + podcastId,
                )
            }
        }
    }

    /**
     * SLOVO-KIDS-EPISODE — reaktivně schválené vlastní zdroje (RSS/YouTube/ČT) PRO DĚTSKÝ profil
     * (whitelist [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.visibleForKidsSourceKeys]).
     * Vzor [kidsHiddenPodcastIds], ale opačná sémantika (whitelist, ne blacklist).
     */
    val kidsVisibleSourceKeys: StateFlow<Set<String>> = profileRepository.observeAll()
        .map { profiles ->
            profiles.firstOrNull { !it.isAdmin }
                ?.let { com.github.jankoran90.showlyfin.core.domain.ProfileConfig.fromJson(it.configJson).visibleForKidsSourceKeys }
                .orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Admin (Dospělý) — přepne, jestli vlastní zdroj (klíče `type:ref`, u sloučeného páru všichni
     * členové) vidí dětský profil. Whitelist → visible=true PŘIDÁ klíče, false je odebere. No-op bez
     * dětského profilu.
     */
    fun setSourceVisibleForKids(keys: Set<String>, visible: Boolean) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            val kids = profileRepository.getAll().firstOrNull { !it.isAdmin } ?: return@launch
            profileRepository.updateConfig(kids.id) { cfg ->
                val s = cfg.visibleForKidsSourceKeys.toMutableSet()
                    .also { if (visible) it.addAll(keys) else it.removeAll(keys) }
                cfg.copy(visibleForKidsSourceKeys = s)
            }
        }
    }

    /**
     * PROFIL (2026-08-16, user „Honza muze dat zobrazit podcast uzivateli Nel a naopak") — ostatní
     * DOSPĚLÉ profily (bez mě, bez Dětí — ty mají vlastní whitelist [kidsVisibleSourceKeys]/
     * [setSourceVisibleForKids]) → cíle pro „Sdílet s…" v kontext menu karty zdroje.
     */
    val otherAdultProfiles: StateFlow<List<com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity>> =
        profileRepository.observeAll()
            .combine(activeProfile) { profiles, active ->
                profiles.filter { it.isAdmin && it.id != active?.id }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * User (2026-08-16 14:42, „Sdílet s Nel u dětské knihy nedává smysl") — vzor [HomeViewModel.kidsLibraryIds].
     * Vlastnictví/sdílení se knih z čistě dětské ABS knihovny netýká.
     */
    val kidsLibraryIds: StateFlow<Set<String>> =
        profileRepository.observeAll()
            .map { profiles ->
                profiles.filterNot { it.isAdmin }
                    .flatMap { com.github.jankoran90.showlyfin.core.domain.ProfileConfig.fromJson(it.configJson).absLibraryWhitelist.orEmpty() }
                    .toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * true = zdroj (klíč `type:ref`, u sloučeného páru kterýkoli z [keys]) je nasdílený profilu
     * [target] (viz [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.sharedSourceKeys]).
     */
    fun isSourceSharedWith(keys: Set<String>, target: com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity): Boolean {
        val cfg = com.github.jankoran90.showlyfin.core.domain.ProfileConfig.fromJson(target.configJson)
        return keys.any { it in cfg.sharedSourceKeys }
    }

    /** Nasdílí/odebere sdílení zdroje ([keys] = `type:ref`, u sloučeného páru všichni členové) profilu [targetId]. */
    fun setSourceSharedWith(keys: Set<String>, targetId: Long, shared: Boolean) {
        if (keys.isEmpty()) return
        viewModelScope.launch {
            profileRepository.updateConfig(targetId) { cfg ->
                val s = cfg.sharedSourceKeys.toMutableSet()
                    .also { if (shared) it.addAll(keys) else it.removeAll(keys) }
                cfg.copy(sharedSourceKeys = s)
            }
        }
    }

    /** PROFIL (2026-08-16) — audiokniha (vzor [isSourceSharedWith], klíč = ABS itemId). */
    fun isBookSharedWith(itemId: String, target: com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity): Boolean =
        itemId in com.github.jankoran90.showlyfin.core.domain.ProfileConfig.fromJson(target.configJson).sharedAudiobookIds

    /**
     * User (2026-08-16, „chci vidět na long-pressu, jestli je to sdíleno a s kým / kdo jiný to
     * má v knihovně") — jméno profilu k `profileUuid` ("já" pro aktivní profil), jen dospělí.
     */
    fun adultProfileName(uuid: String?): String? {
        if (uuid.isNullOrBlank()) return null
        if (uuid == activeProfile.value?.profileUuid) return "já"
        return otherAdultProfiles.value.firstOrNull { it.profileUuid == uuid }?.name
    }

    /** Vlastník (profileUuid) zdroje podle klíče `type:ref` — pro info řádek sdílení sloučených karet. */
    fun ownerOfSourceKey(key: String): String? =
        sourcesRepo.sources.value.firstOrNull { "${it.type}:${it.ref}" == key }?.addedBy

    /** Vlastník (profileUuid) audioknihy podle ABS itemId — pro info řádek sdílení. */
    fun ownerOfBook(itemId: String): String? = audiobookOwnership.ownership.value[itemId]

    /** Postaví text „V knihovně: …" pro sdílecí sheet (owner + komu je nasdíleno kromě vlastníka). */
    fun ownershipInfoLine(ownerUuid: String?, sharedWithProfiles: List<com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity>): String {
        val owner = adultProfileName(ownerUuid) ?: "já"
        val sharedNames = sharedWithProfiles.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
        return if (sharedNames.isEmpty()) "V knihovně: $owner"
        else "V knihovně: $owner · sdíleno s: ${sharedNames.joinToString(", ")}"
    }

    fun setBookSharedWith(itemId: String, targetId: Long, shared: Boolean) {
        viewModelScope.launch {
            profileRepository.updateConfig(targetId) { cfg ->
                val s = cfg.sharedAudiobookIds.toMutableSet()
                    .also { if (shared) it.add(itemId) else it.remove(itemId) }
                cfg.copy(sharedAudiobookIds = s)
            }
        }
    }

    /** Klíče skrytí karty knihovny: sloučená = všichni členové, samostatný zdroj = `type:ref`. */
    fun followingKeysForGroup(memberKeys: Collection<String>): Set<String> = memberKeys.toSet()

    /**
     * Skryj/odkryj pořad ve Sledovaných NEBO na časové ose (dvě nezávislé dimenze, per profil).
     * Zapisuje do aktivního profilu (write-through → DB + backend). [keys] = `type:ref` zdrojů
     * (u sloučeného pořadu všichni členové) nebo `abs:<id>`.
     */
    fun setHidden(keys: Set<String>, timeline: Boolean, hidden: Boolean) {
        if (keys.isEmpty()) return
        val profileId = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            profileRepository.updateConfig(profileId) { c ->
                if (timeline) {
                    val s = c.hiddenTimelineSourceKeys.toMutableSet().also { if (hidden) it.addAll(keys) else it.removeAll(keys) }
                    c.copy(hiddenTimelineSourceKeys = s)
                } else {
                    val s = c.hiddenFollowingSourceKeys.toMutableSet().also { if (hidden) it.addAll(keys) else it.removeAll(keys) }
                    c.copy(hiddenFollowingSourceKeys = s)
                }
            }
        }
    }

    /**
     * Auto-návrh kandidáta k propojení pro [source]: nejpodobnější zdroj OPAČNÉHO typu mezi aktuálně
     * sledovanými, který ještě není ve skupině se [source]. null = žádná dost silná shoda.
     */
    fun suggestLinkMatch(source: PodcastSource): PodcastSource? {
        val sameGroup = linkStore.groupForSource(source)?.members.orEmpty().toSet()
        val others = _uiState.value.customSources.filter {
            sourceKey(it) != sourceKey(source) && sourceKey(it) !in sameGroup
        }
        return PodcastPairing.suggestMatch(source, others)
    }

    /** Kandidáti k propojení se [source] (opačný typ není podmínka, ale návrh ano) — bez sebe a bez své skupiny. */
    fun linkCandidates(source: PodcastSource): List<PodcastSource> {
        val sameGroup = linkStore.groupForSource(source)?.members.orEmpty().toSet()
        return _uiState.value.customSources.filter {
            sourceKey(it) != sourceKey(source) && sourceKey(it) !in sameGroup
        }
    }

    /** PRESET (SHW-65) — seřaď knihovny dle ručního pořadí ([order] = ID knihoven); neznámé na konec. */
    private fun List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>.ordered(order: List<String>):
        List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary> {
        if (order.isEmpty()) return this
        val idx = order.withIndex().associate { (i, id) -> id to i }
        return sortedBy { idx[it.id] ?: Int.MAX_VALUE }
    }

    /**
     * Profilový whitelist ABS knihoven (Plan PROFILES Fáze 4E). null = bez omezení (vidět vše).
     * Filtruje audioknihy i podcasty police podle aktivního profilu.
     */
    private fun List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>.applyProfileWhitelist():
        List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary> {
        val wl = profileRepository.activeConfig.value.absLibraryWhitelist
        Timber.i("[VAULT] ABS whitelist=$wl libs=${this.map { it.id to it.name }}")
        if (wl == null) return this
        return filter { it.id in wl }
    }

    /**
     * Per-profil skrytí jednotlivých podcastů (admin authoring ve Správě). Odfiltruje pořady, jejichž
     * id je ve [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.hiddenPodcastIds].
     */
    private fun List<com.github.jankoran90.showlyfin.data.abs.model.Podcast>.applyProfileHidden():
        List<com.github.jankoran90.showlyfin.data.abs.model.Podcast> {
        val hidden = profileRepository.activeConfig.value.hiddenPodcastIds
        if (hidden.isEmpty()) return this
        return filter { it.id !in hidden }
    }

    private val _uiState = MutableStateFlow(ListenUiState())
    val uiState = _uiState.asStateFlow()

    /** Všechny stažené ABS epizody (správa offline stažení). */
    val downloads = downloadManager.downloads

    /**
     * LEVER (SHW-61) L3: stažené RSS/YouTube podcasty (generický offline manager, `TYPE_PODCAST`).
     * Drží se i offline → „na chatu bez wifi" je najdeš v sekci Stažené a pustíš z lokálního souboru.
     *
     * RESONANCE (SHW-81) D: parita dětského profilu — odfiltruj epizody pořadů skrytých na časové ose /
     * ve Sledovaných (klíč zdroje `type:ref`). `sourceKey == null` (staré stažené / neznámý zdroj) se
     * NEfiltruje = zůstane vidět (bezpečný default, aby se stará stažení „neztratila").
     */
    private fun List<OfflineDownload>.podcastsVisible(cfg: com.github.jankoran90.showlyfin.core.domain.ProfileConfig): List<OfflineDownload> {
        val hidden = cfg.hiddenTimelineSourceKeys + cfg.hiddenFollowingSourceKeys
        return filter { it.type == OfflineRequest.TYPE_PODCAST && (it.sourceKey == null || it.sourceKey !in hidden) }
    }
    val offlinePodcasts: StateFlow<List<OfflineDownload>> =
        combine(offline.downloads, profileRepository.activeConfig) { list, cfg -> list.podcastsVisible(cfg) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                offline.downloads.value.podcastsVisible(profileRepository.activeConfig.value),
            )

    /** RESONANCE (SHW-81): stav přehrávače → zvýraznění + progress právě hrané epizody v offline detailu. */
    val playerState: StateFlow<PlayerState> = connection.state

    /** RESONANCE (SHW-81): má stažená epizoda i lokální VIDEO? (offline podcast = zatím jen audio → false). */
    fun hasLocalVideo(key: String): Boolean = offline.localVideo(key) != null

    /**
     * RESONANCE (SHW-81): žádost otevřít offline detail pořadu z prokliku na cover v přehrávači.
     * Pair(showTitle, highlightEpisodeKey). ListenScreen ji spotřebuje → otevře kartu + zvýrazní epizodu.
     */
    private val _requestedOfflineShow = MutableStateFlow<Pair<String, String?>?>(null)
    val requestedOfflineShow: StateFlow<Pair<String, String?>?> = _requestedOfflineShow.asStateFlow()
    fun openOfflinePodcast(showTitle: String, highlightEpisodeKey: String?) {
        _requestedOfflineShow.value = showTitle to highlightEpisodeKey
    }
    fun consumeOfflineRequest() { _requestedOfflineShow.value = null }

    /** RESONANCE (SHW-81): řazení epizod offline detailu — true = nejnovější nahoře (default). Perzistentní. */
    var offlinePodcastNewestFirst: Boolean
        get() = absPrefs.offlinePodcastNewestFirst
        set(value) { absPrefs.offlinePodcastNewestFirst = value }

    fun deleteDownload(episodeId: String) = downloadManager.delete(episodeId)

    /** „Smazat vše" v sekci Stažené (Poslech) = ABS epizody i stažené RSS/YT podcasty (ne filmy). */
    fun deleteAllDownloads() {
        downloadManager.deleteAll()
        offline.deleteAll(setOf(OfflineRequest.TYPE_PODCAST))
    }

    /** L3: smaž stažený podcast (RSS/YT) z telefonu. */
    fun deleteOfflinePodcast(key: String) = offline.delete(key)

    /** L3: přehraj stažený podcast offline z lokálního `file://` souboru přes poslechový přehrávač. */
    fun playOfflinePodcast(dl: OfflineDownload) {
        val file = File(dl.videoPath).takeIf { it.exists() } ?: return
        connection.playDirectEpisode(
            QueuedEpisode(
                itemId = "offline",
                episodeId = dl.key,
                title = dl.title,
                coverUrl = dl.posterPath ?: dl.posterUrl,
                podcastTitle = dl.subtitle,
                direct = DirectAudio(
                    url = Uri.fromFile(file).toString(),
                    durationSec = dl.durationSec,
                    author = dl.subtitle,
                ),
            ),
        )
    }

    init {
        // Plan VAULT — refresh řízený configem aktivního profilu, ne jen vznikem VM. StateFlow emitne
        // hned (= původní init refresh) a pak při každé změně whitelistu/ABS creds (přepnutí profilu,
        // sync z backendu). Applier zapisuje prefs PŘED emisí configu (ProfileRepository), takže tady
        // už čteme správné přihlášení — řeší závod „fetch knihoven se starým tokenem → prázdné libs".
        profileRepository.activeConfig
            .map { Triple(it.absLibraryWhitelist, it.credentials.abs, it.hiddenPodcastIds) }
            .distinctUntilChanged()
            .onEach {
                refresh()
                // PRESET FIX — po aplikaci profilu (ProfileConfigApplier ZAHODÍ uploader cookie kvůli
                // reloginu) přenačti i sdílené vlastní zdroje. Bez toho zůstaly po cold startu / přepnutí
                // profilu prázdné, dokud user nepřepnul záložku Poslechu.
                loadSources()
                if (_uiState.value.podcastsLoaded || _uiState.value.mode == ListenMode.PODCASTS) {
                    loadPodcastLibraries()
                }
            }
            .launchIn(viewModelScope)

        // Plan CASTAWAY — offline police: drž množinu stažených knih (badge „staženo") a v offline
        // režimu jimi naplň seznam, aby šly otevřít i bez sítě.
        audiobookDownloads.downloads
            .onEach { dls ->
                _uiState.update { it.copy(downloadedBookIds = dls.map { d -> d.itemId }.toSet()) }
                if (!connectivity.isCurrentlyOnline()) refresh()
            }
            .launchIn(viewModelScope)

        // Reaguj na změnu konektivity: offline → degraduj na stažené; návrat online → načti znovu.
        // (StateFlow už emituje jen distinct hodnoty, proto bez distinctUntilChanged.)
        connectivity.isOnline
            .onEach { online ->
                _uiState.update { it.copy(isOffline = !online) }
                refresh()
                if (online && (_uiState.value.podcastsLoaded || _uiState.value.mode == ListenMode.PODCASTS)) {
                    loadPodcastLibraries()
                }
                // PRESET — vlastní zdroje (sdílené ze serveru) jsou nezávislé na ABS; načti při návratu online.
                if (online) loadSources()
            }
            .launchIn(viewModelScope)

        // PRESET (SHW-65) — reaktivně zrcadli sdílený seznam zdrojů do UI (přidání/odebrání kdekoli se
        // okamžitě projeví v sekci Podcasty). Nezávislé na ABS přihlášení.
        sourcesRepo.sources
            // PRESET FIX: vlastní zdroje řaď ABECEDNĚ dle názvu (ne v pořadí přidání — joe rogan
            // se jinak lepil nahoru jako poslední přidaný). Diakritika-insensitivně.
            // EXODUS (SHW-67): prémiové zdroje rodiny (NaVýbornou) pinni NAHORU, pak abecedně.
            .onEach { srcs ->
                val sorted = srcs.sortedWith(
                    compareByDescending<com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource> { it.premium }
                        .thenBy { it.title.lowercase(java.util.Locale("cs")) },
                )
                _uiState.update { it.copy(customSources = sorted) }
            }
            .launchIn(viewModelScope)
        loadSources()
        // Výchozí sekce Poslechu MUSÍ následovat preferenci pořadí (Nastavení → Poslech). Dřív se `mode`
        // držel natvrdo na BOOKS → při „podcasty první" se stejně otevřely Audioknihy (pager `initialPage`
        // = indexOf(mode) = 1). Inicializuj `mode` z `booksFirst`, ať se otevře PRVNÍ nastavená sekce.
        val booksFirst = absPrefs.listenBooksFirst
        _uiState.update {
            it.copy(
                booksFirst = booksFirst,
                mode = if (booksFirst) ListenMode.BOOKS else ListenMode.PODCASTS,
            )
        }
    }

    /** AGORA-TABS: výchozí záložka sekce Podcasty (timeline|following|discover) z Nastavení. */
    val podcastDefaultTab: String get() = absPrefs.podcastDefaultTab

    /** PRESET — načti/obnov sdílený seznam vlastních zdrojů (YouTube/RSS) ze serveru. */
    fun loadSources() {
        viewModelScope.launch { sourcesRepo.refresh() }
    }

    /**
     * PRESET (SHW-65) — znovu načti pořadí Poslechu z Nastavení (po návratu z Nastavení) a přeřaď
     * už načtené knihovny. Volá ListenScreen při vstupu.
     */
    fun reloadOrderPrefs() {
        val booksFirst = absPrefs.listenBooksFirst
        _uiState.update {
            // Když se preference pořadí ZMĚNILA (user ji přepnul v Nastavení), otevři po návratu PRVNÍ
            // nastavenou sekci — jinak by se po změně „podcasty první" stejně držela stará `mode`.
            val mode = if (booksFirst != it.booksFirst) {
                if (booksFirst) ListenMode.BOOKS else ListenMode.PODCASTS
            } else {
                it.mode
            }
            it.copy(
                booksFirst = booksFirst,
                mode = mode,
                libraries = it.libraries.ordered(absPrefs.audiobookLibraryOrder),
                podcastLibraries = it.podcastLibraries.ordered(absPrefs.podcastLibraryOrder),
            )
        }
    }

    /** PRESET — odeber vlastní zdroj ze sdíleného store (projeví se u celé rodiny). */
    fun removeSource(id: String) {
        viewModelScope.launch { sourcesRepo.remove(id) }
    }

    /** Stažené audioknihy jako UI police (Plan CASTAWAY CA-2). */
    private fun downloadedBooks(): List<Audiobook> =
        audiobookDownloads.downloads.value.map { it.toAudiobook() }

    /** Načte knihovny audioknih a knihy ve vybrané (či první) knihovně. */
    fun refresh() {
        val offlineBooks = downloadedBooks()
        // Plan CASTAWAY — bez přihlášení k ABS ukaž aspoň stažené knihy (offline police), ať jdou hrát.
        if (!repo.isConfigured) {
            _uiState.update {
                it.copy(
                    isConfigured = offlineBooks.isNotEmpty(),
                    isLoading = false,
                    isOffline = !connectivity.isCurrentlyOnline(),
                    libraries = emptyList(),
                    books = offlineBooks,
                    error = null,
                )
            }
            return
        }
        // Plan CASTAWAY — offline: nestreamuj seznam, rovnou ukaž stažené knihy.
        if (!connectivity.isCurrentlyOnline()) {
            _uiState.update {
                it.copy(
                    isConfigured = true, isLoading = false, isOffline = true,
                    libraries = emptyList(), books = offlineBooks, error = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConfigured = true, isLoading = true, isOffline = false, error = null) }
            runCatching { repo.getAudiobookLibraries().applyProfileWhitelist().ordered(absPrefs.audiobookLibraryOrder) }
                .onSuccess { libs ->
                    if (libs.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, libraries = emptyList(), books = downloadedBooks()) }
                        return@onSuccess
                    }
                    val selected = _uiState.value.selectedLibraryId?.takeIf { id -> libs.any { it.id == id } }
                        ?: libs.first().id
                    _uiState.update { it.copy(libraries = libs, selectedLibraryId = selected) }
                    loadBooks(selected)
                }
                .onFailure { e ->
                    // Síť/server selhaly i přes „online" stav → degraduj na stažené knihy místo prázdna.
                    Timber.w(e, "[Listen] knihovny selhaly")
                    val books = downloadedBooks()
                    _uiState.update {
                        it.copy(
                            isLoading = false, libraries = emptyList(), books = books, isOffline = true,
                            error = if (books.isEmpty()) "Načtení knihoven selhalo. Zkontroluj přihlášení k Audiobookshelf v Nastavení." else null,
                        )
                    }
                }
        }
    }

    /** Přepnutí Audioknihy ↔ Podcasty. Podcasty se načtou líně při prvním přepnutí. */
    fun setMode(mode: ListenMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update { it.copy(mode = mode, error = null) }
        if (mode == ListenMode.PODCASTS) {
            if (!_uiState.value.podcastsLoaded) loadPodcastLibraries()
            loadSources()   // PRESET — obnov vlastní zdroje při vstupu do Podcastů (mohly přibýt z jiného telefonu)
        }
    }

    /**
     * Profily (2026-08-15) — dětský profil nemá záložku Podcasty ([setMode] se nikdy nezavolá s
     * PODCASTS), ale [ListenUiState.podcasts] (admin-schválené) v mergnuté sekci Poslech potřebuje.
     * Idempotentní no-op, když už jsou načtené.
     */
    fun ensurePodcastsLoaded() {
        if (!_uiState.value.podcastsLoaded) loadPodcastLibraries()
    }

    /** Stáhne jednu audioknihu (long-press menu v gridu — "Stáhnout"). */
    fun downloadBook(book: Audiobook) {
        audiobookDownloads.download(book.id, book.title, book.author, book.coverUrl, book.libraryId, book.seriesName)
    }

    /**
     * User (2026-08-15 16:49) — „Ukončit poslech" (long-press menu): smaže progress.
     * User (2026-08-16 13:19, „chci live akci, ať je to hned po potvrzení provedené a vidím změnu")
     * — knize se progress vynuluje v `_uiState.books` OKAMŽITĚ, server volání + [refresh] na pozadí.
     */
    fun resetBookProgress(book: Audiobook) {
        _uiState.update { s ->
            s.copy(books = s.books.map { if (it.id == book.id) it.copy(progress = 0.0, currentTimeSec = 0.0) else it })
        }
        audiobookDownloads.clearLocalProgress(book.id)
        viewModelScope.launch {
            repo.endListening(book.id, book.progressId)
            refresh()
        }
    }

    /** User (2026-08-15 16:49) — „Označit jako poslechnuté" (long-press menu), viz [resetBookProgress]. */
    fun markBookFinished(book: Audiobook) {
        _uiState.update { s ->
            s.copy(books = s.books.map { if (it.id == book.id) it.copy(isFinished = true, progress = 1.0) else it })
        }
        viewModelScope.launch {
            repo.setBookFinished(book.id, finished = true)
            refresh()
        }
    }

    /**
     * "Stáhnout vše" — všechny knihy aktuálně viditelné police, co ještě nejsou stažené. User
     * (2026-08-15) „stahuje se i na pozadí?" — dávka může trvat déle než appka zůstane v popředí,
     * proto foreground služba (vzor upload F2d), ať Android proces nezabije.
     */
    fun downloadAllBooks() {
        audiobookDownloads.downloadAll(_uiState.value.books)
        ContextCompat.startForegroundService(context, AudiobookBatchDownloadService.intent(context))
    }

    /** Souhrnný postup "Stáhnout vše" (null = žádná dávka neběží) — pro UI "staženo X/Y". */
    val batchDownloadProgress = audiobookDownloads.batchProgress

    fun selectLibrary(libraryId: String) {
        if (libraryId == _uiState.value.selectedLibraryId) return
        _uiState.update { it.copy(selectedLibraryId = libraryId) }
        viewModelScope.launch { loadBooks(libraryId) }
    }

    /**
     * PROFIL (2026-08-16, user „audioknihy taky per profil, nahrávám je já Honza") — Dospělý vidí
     * jen svoje nahrané audioknihy + co mu kdo nasdílel (legacy bez záznamu vlastnictví = viditelné
     * všem). Děti mají VLASTNÍ ABS knihovnu (`absLibraryWhitelist`), tenhle koncept se jich netýká.
     */
    private suspend fun filterVisibleBooks(books: List<Audiobook>): List<Audiobook> {
        val active = profileRepository.activeProfile.value ?: return books
        if (!active.isAdmin) return books
        audiobookOwnership.refresh()
        val shared = profileRepository.activeConfig.value.sharedAudiobookIds
        return books.filter { audiobookOwnership.isVisible(it.id, active.profileUuid, shared) }
    }

    private suspend fun loadBooks(libraryId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.getAudiobooks(libraryId) }
            .onSuccess { books -> _uiState.update { it.copy(isLoading = false, books = filterVisibleBooks(books)) } }
            .onFailure { e ->
                Timber.w(e, "[Listen] knihy selhaly")
                val offline = downloadedBooks()
                _uiState.update {
                    it.copy(
                        isLoading = false, books = offline, isOffline = true,
                        error = if (offline.isEmpty()) "Načtení audioknih selhalo." else null,
                    )
                }
            }
    }

    // ──────────────────────────── Podcasty ────────────────────────────

    private fun loadPodcastLibraries() {
        if (!repo.isConfigured) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.getPodcastLibraries().applyProfileWhitelist().ordered(absPrefs.podcastLibraryOrder) }
                .onSuccess { libs ->
                    if (libs.isEmpty()) {
                        _uiState.update {
                            it.copy(isLoading = false, podcastLibraries = emptyList(), podcasts = emptyList(), podcastsLoaded = true)
                        }
                        return@onSuccess
                    }
                    val selected = _uiState.value.selectedPodcastLibraryId?.takeIf { id -> libs.any { it.id == id } }
                        ?: libs.first().id
                    _uiState.update { it.copy(podcastLibraries = libs, selectedPodcastLibraryId = selected, podcastsLoaded = true) }
                    loadPodcasts(selected)
                }
                .onFailure { e ->
                    Timber.w(e, "[Listen] podcast knihovny selhaly")
                    _uiState.update { it.copy(isLoading = false, error = "Načtení podcastů selhalo. Zkontroluj přihlášení k Audiobookshelf v Nastavení.") }
                }
        }
    }

    fun selectPodcastLibrary(libraryId: String) {
        if (libraryId == _uiState.value.selectedPodcastLibraryId) return
        _uiState.update { it.copy(selectedPodcastLibraryId = libraryId) }
        viewModelScope.launch { loadPodcasts(libraryId) }
    }

    private suspend fun loadPodcasts(libraryId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.getPodcasts(libraryId) }
            .onSuccess { ps -> _uiState.update { it.copy(isLoading = false, podcasts = ps.applyProfileHidden()) } }
            .onFailure { e ->
                Timber.w(e, "[Listen] podcasty selhaly")
                _uiState.update { it.copy(isLoading = false, error = "Načtení podcastů selhalo.") }
            }
    }
}
