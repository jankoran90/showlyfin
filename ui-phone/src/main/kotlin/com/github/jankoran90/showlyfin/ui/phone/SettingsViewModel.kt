package com.github.jankoran90.showlyfin.ui.phone

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import com.github.jankoran90.showlyfin.data.trakt.TraktDeviceAuthManager
import com.github.jankoran90.showlyfin.data.trakt.TraktDevicePollResult
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.StreamFilterPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class SettingsUiState(
    val traktLoggedIn: Boolean = false,
    // Plan FUSE F5 — Trakt device-code login (TV: bez browseru, uživatel zadá kód na jiném zařízení).
    val traktUserCode: String? = null,
    val traktVerificationUrl: String? = null,
    val traktStatus: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val jellyfinServerUrl: String = "",
    val jellyfinConnected: Boolean = false,
    val jellyfinUserName: String = "",
    val parentalAgeRating: AgeRating = AgeRating.UNRESTRICTED,
    val parentalLocked: Boolean = false,
    val maxParentalRating: Int? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfileId: Long? = null,
    /** Plan WARDEN W3c — šablony pro in-app admin authoring. */
    val templates: List<TemplateEntity> = emptyList(),
    /** Plan WARDEN W2: zamčené klíče efektivního configu aktivního profilu ([ProfileConfig.LockKeys]).
     * Ne-admin user needituje zamčené bloky Nastavení. Prázdné = nic zamčené (admin/legacy/bez šablony). */
    val lockedKeys: Set<String> = emptySet(),
    // Stremio / Comet filtr výsledků
    val streamFilter: StreamFilterPrefs? = null,
    val streamFilterLoading: Boolean = false,
    val streamFilterError: String? = null,
    // Živé logování (Debug)
    val liveLogging: Boolean = false,
    // Plan MAESTRO — ovládání domácí sestavy (AVR hlasitost + scéna „spustit z vypnuté TV").
    val avrEnabled: Boolean = false,
    val avrHost: String = "",
    val avrBoxHost: String = "",
    val avrBoxMac: String = "",
    val avrTvHost: String = "",
    // Plan PROFILES Fáze 2 — web admin profilů (uploader backend)
    val uploaderBaseUrl: String = "",
    // Poslech / Audiobookshelf
    val absConfigured: Boolean = false,
    val absBaseUrl: String = "",
    val absLoading: Boolean = false,
    val absError: String? = null,
    val hideFinishedEpisodes: Boolean = false,
    // Plan PROFILES Fáze 4E — seznam ABS knihoven (audioknihy+podcasty) pro admin authoring whitelistu
    val absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary> = emptyList(),
    // Skrývání jednotlivých podcastů per profil (admin authoring) — seznam dostupných pořadů (id+název+cover).
    val adminPodcasts: List<com.github.jankoran90.showlyfin.data.abs.model.Podcast> = emptyList(),
    // Plan HELM — seznam Jellyfin knihoven (z backendu) pro in-app admin editor whitelistu.
    val adminJellyfinLibraries: List<com.github.jankoran90.showlyfin.core.domain.JellyfinLibraryRef> = emptyList(),
    val listen: ListenSettings = ListenSettings(),
    // Stahování na ABS server — per-podcast auto-download (přesunuto z detailu)
    val serverPodcasts: List<com.github.jankoran90.showlyfin.data.abs.model.PodcastServerAutoDownload> = emptyList(),
    val serverPodcastsLoading: Boolean = false,
    val serverPodcastsBusyIds: Set<String> = emptySet(),
    /** Plan VAULT — výsledek uložení creds v admin editoru (profileId → zpráva). Viditelný feedback. */
    val adminCredsStatus: Pair<Long, String>? = null,
)

/** Nastavení poslechové sekce (přehrávač, fronta, stahování, zobrazení, sync). */
data class ListenSettings(
    val skipSeconds: Int = 30,
    val rememberSpeed: Boolean = true,
    val defaultSpeed: Float = 1f,
    val autoAdvanceQueue: Boolean = true,
    val autoMarkFinished: Boolean = true,
    val continuePodcastAfterQueue: Boolean = false,
    val persistQueue: Boolean = true,
    val downloadWifiOnly: Boolean = false,
    val deleteDownloadAfterFinish: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val autoDownloadNewest: Int = 0,
    val autoDownloadScope: Int = 0,
    val episodeSortNewestFirst: Boolean = true,
    val episodeListLimit: Int = 0,
    val episodeTitleLines: Int = 2,
    val episodeDescriptionLines: Int = 3,
    val highlightGuest: Boolean = true,
    val episodeFontScale: Float = 1f,
    val rssHideDownloaded: Boolean = false,
    val episodeQuickAction: Int = 0,
    val showRemainingTime: Boolean = false,
    val showSpeedButton: Boolean = true,
    val showSleepButton: Boolean = true,
    val queueSwipeAction: Int = 0,
    val syncIntervalSeconds: Int = 15,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val traktAuthManager: TraktAuthManager,
    private val traktDeviceAuth: TraktDeviceAuthManager,
    private val parentalControlsRepository: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val absRepo: AbsRepository,
    private val absPrefs: AbsPreferences,
    private val jellyfinAuth: com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService,
    @ApplicationContext private val appContext: Context,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val KEY_URL = "jellyfin_server_url"
        private const val KEY_TOKEN = "jellyfin_token"
        private const val KEY_USER_ID = "jellyfin_user_id"
        const val KEY_LIVE_LOGGING = "live_logging_enabled"
        const val KEY_AVR_ENABLED = "avr_enabled"
        const val KEY_AVR_HOST = "avr_host"
        const val KEY_AVR_BOX_HOST = "avr_box_host"
        const val KEY_AVR_BOX_MAC = "avr_box_mac"
        const val KEY_AVR_TV_HOST = "avr_tv_host"
    }

    private val uploaderBase get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Plan VAULT — aktivní profil má v balíku JF heslo (auto-login schopný i bez tokenu). */
    private var activeJfHasPassword = false

    init {
        refreshJellyfinState()
        _uiState.update { it.copy(traktLoggedIn = traktAuthManager.isLoggedIn()) }
        viewModelScope.launch {
            traktAuthManager.authCodeFlow.collect { code ->
                _uiState.update { it.copy(isLoading = true, error = null) }
                try {
                    traktAuthManager.authorize(code)
                    _uiState.update { it.copy(traktLoggedIn = true, isLoading = false) }
                    captureTraktIntoActiveProfile() // Plan VAULT — Trakt per-profil
                } catch (e: Throwable) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Chyba autorizace") }
                }
            }
        }
        parentalControlsRepository.profile
            .onEach { profile ->
                _uiState.update {
                    it.copy(
                        jellyfinUserName = profile.userInfo?.userName ?: "",
                        parentalAgeRating = profile.effectiveAgeRating,
                        parentalLocked = profile.isLocked,
                        maxParentalRating = profile.userInfo?.maxParentalRating,
                    )
                }
            }
            .launchIn(viewModelScope)
        profileRepository.observeAll()
            .onEach { list -> _uiState.update { it.copy(profiles = list) } }
            .launchIn(viewModelScope)
        profileRepository.activeProfile
            .onEach { active -> _uiState.update { it.copy(activeProfileId = active?.id) } }
            .launchIn(viewModelScope)
        profileRepository.activeConfig
            .onEach { cfg ->
                // Plan VAULT — „Jellyfin nastaven" toleruje i uložené heslo bez tokenu (token se
                // mintuje při vstupu přes bránu / 401 reloginem), jako ABS isConfigured.
                activeJfHasPassword = !cfg.credentials.jellyfin?.password.isNullOrBlank()
                _uiState.update { it.copy(lockedKeys = cfg.lockedKeys) }
                refreshJellyfinState()
            }
            .launchIn(viewModelScope)
        profileRepository.observeTemplates()
            .onEach { list -> _uiState.update { it.copy(templates = list) } }
            .launchIn(viewModelScope)
        _uiState.update {
            it.copy(
                liveLogging = prefs.getBoolean(KEY_LIVE_LOGGING, false),
                uploaderBaseUrl = uploaderBase,
                avrEnabled = prefs.getBoolean(KEY_AVR_ENABLED, false),
                avrHost = prefs.getString(KEY_AVR_HOST, "").orEmpty(),
                avrBoxHost = prefs.getString(KEY_AVR_BOX_HOST, "").orEmpty(),
                avrBoxMac = prefs.getString(KEY_AVR_BOX_MAC, "").orEmpty(),
                avrTvHost = prefs.getString(KEY_AVR_TV_HOST, "").orEmpty(),
            )
        }
        refreshAbsState()
        loadAbsLibraries()
        loadStreamFilter()
    }

    /** Načte ABS knihovny (audioknihy + podcasty, dedup dle id) pro admin authoring whitelistu Poslechu. */
    fun loadAbsLibraries() {
        if (!absRepo.isConfigured) return
        viewModelScope.launch {
            val libs = runCatching {
                (absRepo.getAudiobookLibraries() + absRepo.getPodcastLibraries())
                    .distinctBy { it.id }
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(absLibraries = libs) }
        }
    }

    /** Načte všechny podcasty napříč ABS podcast knihovnami pro admin authoring skrývání per profil. */
    fun loadAdminPodcasts() {
        if (!absRepo.isConfigured) return
        viewModelScope.launch {
            val pods = runCatching {
                absRepo.getPodcastLibraries()
                    .flatMap { absRepo.getPodcasts(it.id) }
                    .distinctBy { it.id }
                    .sortedBy { it.title.lowercase() }
            }.getOrElse { emptyList() }
            _uiState.update { it.copy(adminPodcasts = pods) }
        }
    }

    // ── Poslech / Audiobookshelf ──────────────────────────────────────────────

    private fun refreshAbsState() {
        _uiState.update {
            it.copy(
                absConfigured = absRepo.isConfigured,
                absBaseUrl = absRepo.baseUrl,
                hideFinishedEpisodes = absRepo.hideFinishedEpisodes,
                listen = readListenSettings(),
            )
        }
    }

    private fun readListenSettings() = ListenSettings(
        skipSeconds = absPrefs.skipSeconds,
        rememberSpeed = absPrefs.rememberSpeed,
        defaultSpeed = absPrefs.defaultSpeed,
        autoAdvanceQueue = absPrefs.autoAdvanceQueue,
        autoMarkFinished = absPrefs.autoMarkFinished,
        continuePodcastAfterQueue = absPrefs.continuePodcastAfterQueue,
        persistQueue = absPrefs.persistQueue,
        downloadWifiOnly = absPrefs.downloadWifiOnly,
        deleteDownloadAfterFinish = absPrefs.deleteDownloadAfterFinish,
        maxConcurrentDownloads = absPrefs.maxConcurrentDownloads,
        autoDownloadNewest = absPrefs.autoDownloadNewest,
        autoDownloadScope = absPrefs.autoDownloadScope,
        episodeSortNewestFirst = absPrefs.episodeSortNewestFirst,
        episodeListLimit = absPrefs.episodeListLimit,
        episodeTitleLines = absPrefs.episodeTitleLines,
        episodeDescriptionLines = absPrefs.episodeDescriptionLines,
        highlightGuest = absPrefs.highlightGuest,
        episodeFontScale = absPrefs.episodeFontScale,
        rssHideDownloaded = absPrefs.rssHideDownloaded,
        episodeQuickAction = absPrefs.episodeQuickAction,
        showRemainingTime = absPrefs.showRemainingTime,
        showSpeedButton = absPrefs.showSpeedButton,
        showSleepButton = absPrefs.showSleepButton,
        queueSwipeAction = absPrefs.queueSwipeAction,
        syncIntervalSeconds = absPrefs.syncIntervalSeconds,
    )

    /** Zapíše změnu nastavení poslechu a obnoví uiState. */
    private fun updateListen(mutate: AbsPreferences.() -> Unit) {
        absPrefs.mutate()
        _uiState.update { it.copy(listen = readListenSettings()) }
    }

    fun setHideFinishedEpisodes(value: Boolean) {
        absRepo.hideFinishedEpisodes = value
        _uiState.update { it.copy(hideFinishedEpisodes = value) }
    }

    fun setSkipSeconds(v: Int) = updateListen { skipSeconds = v }
    fun setRememberSpeed(v: Boolean) = updateListen { rememberSpeed = v }
    fun setDefaultSpeed(v: Float) = updateListen { defaultSpeed = v }
    fun setAutoAdvanceQueue(v: Boolean) = updateListen { autoAdvanceQueue = v }
    fun setAutoMarkFinished(v: Boolean) = updateListen { autoMarkFinished = v }
    fun setContinuePodcastAfterQueue(v: Boolean) = updateListen { continuePodcastAfterQueue = v }
    fun setPersistQueue(v: Boolean) = updateListen { persistQueue = v }
    fun setDownloadWifiOnly(v: Boolean) = updateListen { downloadWifiOnly = v }
    fun setDeleteDownloadAfterFinish(v: Boolean) = updateListen { deleteDownloadAfterFinish = v }
    fun setMaxConcurrentDownloads(v: Int) = updateListen { maxConcurrentDownloads = v }
    fun setAutoDownloadNewest(v: Int) = updateListen { autoDownloadNewest = v }
    fun setAutoDownloadScope(v: Int) = updateListen { autoDownloadScope = v }
    fun setEpisodeSortNewestFirst(v: Boolean) = updateListen { episodeSortNewestFirst = v }
    fun setEpisodeListLimit(v: Int) = updateListen { episodeListLimit = v }
    fun setEpisodeTitleLines(v: Int) = updateListen { episodeTitleLines = v }
    fun setEpisodeDescriptionLines(v: Int) = updateListen { episodeDescriptionLines = v }
    fun setHighlightGuest(v: Boolean) = updateListen { highlightGuest = v }
    fun setEpisodeFontScale(v: Float) = updateListen { episodeFontScale = v }
    fun setRssHideDownloaded(v: Boolean) = updateListen { rssHideDownloaded = v }
    fun setEpisodeQuickAction(v: Int) = updateListen { episodeQuickAction = v }

    // ── Stahování na ABS server: per-podcast auto-download (seznam v Nastavení) ──

    /** Načte seznam podcastů + jejich aktuální server auto-download stav (lazy, na vyžádání). */
    fun loadServerPodcasts() {
        if (_uiState.value.serverPodcastsLoading) return
        _uiState.update { it.copy(serverPodcastsLoading = true) }
        viewModelScope.launch {
            runCatching { absRepo.getPodcastsWithServerAutoDownload() }
                .onSuccess { list -> _uiState.update { it.copy(serverPodcastsLoading = false, serverPodcasts = list) } }
                .onFailure { _uiState.update { it.copy(serverPodcastsLoading = false) } }
        }
    }

    /** Přepne ABS server auto-download pro konkrétní podcast (PATCH media). */
    fun toggleServerPodcast(itemId: String, enabled: Boolean) {
        _uiState.update { it.copy(serverPodcastsBusyIds = it.serverPodcastsBusyIds + itemId) }
        viewModelScope.launch {
            absRepo.setServerAutoDownload(itemId, enabled)
                .onSuccess {
                    _uiState.update { st ->
                        st.copy(
                            serverPodcasts = st.serverPodcasts.map { if (it.itemId == itemId) it.copy(autoDownload = enabled) else it },
                            serverPodcastsBusyIds = st.serverPodcastsBusyIds - itemId,
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(serverPodcastsBusyIds = it.serverPodcastsBusyIds - itemId) } }
        }
    }

    fun setShowRemainingTime(v: Boolean) = updateListen { showRemainingTime = v }
    fun setShowSpeedButton(v: Boolean) = updateListen { showSpeedButton = v }
    fun setShowSleepButton(v: Boolean) = updateListen { showSleepButton = v }
    fun setQueueSwipeAction(v: Int) = updateListen { queueSwipeAction = v }
    fun setSyncIntervalSeconds(v: Int) = updateListen { syncIntervalSeconds = v }

    fun absLogin(url: String, username: String, password: String) {
        _uiState.update { it.copy(absLoading = true, absError = null) }
        viewModelScope.launch {
            absRepo.login(url, username, password)
                .onSuccess {
                    _uiState.update { it.copy(absLoading = false, absConfigured = true, absBaseUrl = absRepo.baseUrl) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(absLoading = false, absError = e.message ?: "Přihlášení selhalo") }
                }
        }
    }

    fun absLogout() {
        absRepo.logout()
        refreshAbsState()
    }

    // ── Stremio / Comet filtr ─────────────────────────────────────────────────

    fun loadStreamFilter() {
        if (uploaderBase.isBlank()) return
        _uiState.update { it.copy(streamFilterLoading = true, streamFilterError = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.getStreamFilter(uploaderBase, uploaderCookie) }
                .onSuccess { sf -> _uiState.update { it.copy(streamFilter = sf, streamFilterLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(streamFilterLoading = false, streamFilterError = e.message) } }
        }
    }

    /** Lokálně updatuje + uloží na backend (merge-safe endpoint). */
    fun updateStreamFilter(transform: (StreamFilterPrefs) -> StreamFilterPrefs) {
        val current = _uiState.value.streamFilter ?: return
        val updated = transform(current)
        _uiState.update { it.copy(streamFilter = updated) }
        if (uploaderBase.isBlank()) return
        viewModelScope.launch {
            runCatching { uploaderDs.putStreamFilter(uploaderBase, uploaderCookie, updated) }
                .onFailure { e -> _uiState.update { it.copy(streamFilterError = e.message) } }
        }
    }

    /** Posun položky ve fallbackOrder (dir = -1 nahoru, +1 dolů). */
    fun moveFallback(index: Int, dir: Int) {
        val sf = _uiState.value.streamFilter ?: return
        val list = sf.fallbackOrder.toMutableList()
        val target = index + dir
        if (index !in list.indices || target !in list.indices) return
        val tmp = list[index]; list[index] = list[target]; list[target] = tmp
        updateStreamFilter { it.copy(fallbackOrder = list) }
    }

    fun toggleFallback(key: String, enabled: Boolean) {
        val sf = _uiState.value.streamFilter ?: return
        val list = sf.fallbackOrder.toMutableList()
        if (enabled) { if (key !in list) list.add(key) } else list.remove(key)
        updateStreamFilter { it.copy(fallbackOrder = list) }
    }

    fun setLiveLogging(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_LOGGING, enabled).apply()
        _uiState.update { it.copy(liveLogging = enabled) }
    }

    fun setAvrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AVR_ENABLED, enabled).apply()
        _uiState.update { it.copy(avrEnabled = enabled) }
    }

    fun setAvrHost(host: String) {
        val clean = host.trim()
        prefs.edit().putString(KEY_AVR_HOST, clean).apply()
        _uiState.update { it.copy(avrHost = clean) }
    }

    fun setAvrBoxHost(host: String) {
        val clean = host.trim()
        prefs.edit().putString(KEY_AVR_BOX_HOST, clean).apply()
        _uiState.update { it.copy(avrBoxHost = clean) }
    }

    fun setAvrBoxMac(mac: String) {
        val clean = mac.trim()
        prefs.edit().putString(KEY_AVR_BOX_MAC, clean).apply()
        _uiState.update { it.copy(avrBoxMac = clean) }
    }

    fun setAvrTvHost(host: String) {
        val clean = host.trim()
        prefs.edit().putString(KEY_AVR_TV_HOST, clean).apply()
        _uiState.update { it.copy(avrTvHost = clean) }
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setActive(profileId) }
    }

    fun setDefaultProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setDefault(profileId) }
    }

    fun setTvDefaultProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setTvDefault(profileId) }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileRepository.delete(profile) }
    }

    /**
     * Odhlášení / přepnutí profilu (Plan PROFILES 1C). Zruší aktivní profil → startovní brána
     * (ProfileGateViewModel) ukáže ProfilePicker. Profil ZŮSTÁVÁ uložený vč. přihlášení.
     */
    fun logoutProfile() {
        profileRepository.clearActive()
    }

    /** Přidat profil (Plan PROFILES 1D) — odhlásí aktivní → brána ukáže picker s „Přidat profil". */
    fun addProfile() {
        profileRepository.clearActive()
    }

    /** Přejmenování profilu (Plan PROFILES 1D). */
    fun renameProfile(profileId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { profileRepository.rename(profileId, trimmed) }
    }

    /**
     * Nastaví vlastní fotku profilu (Plan PROFILES 1D). Zkopíruje obsah [uri] do
     * filesDir/avatars/<id>-<ts>.jpg (timestamp → Coil cache invalidace) a uloží cestu do profilu.
     */
    fun setProfileAvatar(profileId: Long, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(appContext.filesDir, "avatars").apply { mkdirs() }
                    dir.listFiles { f -> f.name.startsWith("$profileId-") }?.forEach { it.delete() }
                    val dest = java.io.File(dir, "$profileId-${System.currentTimeMillis()}.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Nelze otevřít obrázek")
                    dest.absolutePath
                }.getOrNull()
            }
            if (path != null) profileRepository.setAvatarPath(profileId, path)
        }
    }

    fun updateProfileAgeRating(profileId: Long, rating: AgeRating?) {
        viewModelScope.launch {
            profileRepository.updateMaxAgeRating(profileId, rating?.name)
        }
    }

    /** Admin write-through editace config balíku profilu (Plan PROFILES 1E): sekce/žánry. */
    fun updateProfileConfig(profileId: Long, transform: (ProfileConfig) -> ProfileConfig) {
        viewModelScope.launch { profileRepository.updateConfig(profileId, transform) }
    }

    /**
     * Plan VAULT — uložení přihlašovacích údajů profilu z admin editoru S viditelným výsledkem
     * ([SettingsUiState.adminCredsStatus]) a okamžitým ověřením Jellyfin loginu:
     * 1. Změněné JF/ABS creds → **vyčistí starý token** (patří starým údajům; dřív se přenášel dál
     *    a brána pak AuthenticateByName přeskočila → 401 „není nastaven").
     * 2. Uloží balík (write-through na backend).
     * 3. JF url+jméno+heslo → rovnou zkusí AuthenticateByName; úspěch = token+userId do balíku,
     *    odmítnutí = jasná chyba adminovi (tohle dřív nešlo zjistit jinak než vstupem do profilu).
     */
    fun saveProfileCredentials(profileId: Long, bundle: com.github.jankoran90.showlyfin.core.domain.CredentialBundle) {
        viewModelScope.launch {
            _uiState.update { it.copy(adminCredsStatus = profileId to "Ukládám…") }
            val profile = _uiState.value.profiles.firstOrNull { it.id == profileId }
            val old = ProfileConfig.fromJson(profile?.configJson).credentials

            val jfChanged = bundle.jellyfin?.let { n ->
                val o = old.jellyfin
                o == null || n.url != o.url || n.username != o.username || n.password != o.password
            } ?: false
            val absChanged = bundle.abs?.let { n ->
                val o = old.abs
                o == null || n.url != o.url || n.username != o.username || n.password != o.password
            } ?: false
            // Normalizuj URL VŠECH domén před uložením (ne až pro ověření) — jinak se do backendu uloží
            // syrová „video.jankoran.cz" / „/video…" a profil má rozbitou URL (root cause `/video.jankoran.cz`).
            val cleaned = bundle.copy(
                jellyfin = bundle.jellyfin?.let {
                    val u = it.copy(url = normalizeUrl(it.url))
                    if (jfChanged) u.copy(token = "", userId = "") else u
                },
                abs = bundle.abs?.let {
                    val u = it.copy(url = normalizeUrl(it.url))
                    if (absChanged) u.copy(token = null) else u
                },
                uploader = bundle.uploader?.let { it.copy(url = normalizeUrl(it.url)) },
            )
            profileRepository.updateConfig(profileId) { c -> c.copy(credentials = cleaned) }

            val jf = cleaned.jellyfin
            val status = if (jf != null && jf.url.isNotBlank() && jf.username.isNotBlank() && !jf.password.isNullOrBlank()) {
                val url = normalizeUrl(jf.url)
                when (val outcome = jellyfinAuth.authenticate(url, jf.username, jf.password!!)) {
                    is com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService.AuthOutcome.Success -> {
                        profileRepository.updateConfig(profileId) { c ->
                            c.copy(
                                credentials = c.credentials.copy(
                                    jellyfin = jf.copy(token = outcome.login.token, userId = outcome.login.userId),
                                ),
                            )
                        }
                        "Uloženo ✓ — Jellyfin přihlášení ověřeno (${outcome.login.userName})"
                    }
                    is com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService.AuthOutcome.Rejected ->
                        "Uloženo, ale Jellyfin jméno/heslo ODMÍTNUTO (HTTP ${outcome.status}) — oprav údaje"
                    is com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService.AuthOutcome.Unavailable ->
                        "Uloženo ✓ — Jellyfin teď nešlo ověřit (${outcome.message ?: "síť"}), přihlásí se při vstupu"
                }
            } else {
                "Uloženo ✓ (Jellyfin bez kompletních údajů — přihlášení proběhne při vstupu do profilu)"
            }
            _uiState.update { it.copy(adminCredsStatus = profileId to status) }
        }
    }

    /** Doplní https:// když chybí scheme, odřízne koncové i úvodní „/" (zrcadlí ProfileConfigApplier). */
    private fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://${t.trimStart('/')}"
    }

    /**
     * Plan VAULT — po úspěšném Trakt loginu/odhlášení promítni globální Trakt tokeny do balíku
     * AKTIVNÍHO profilu (Trakt je per-profil pod adminem) a pushni na backend. Login zapisuje token
     * do sdílených `traktPreferences`; tady ho zrcadlíme do profilu, ať přežije přepnutí i fresh-install.
     * Odhlášení (prázdný token) → trakt = null (z balíku se vyčistí).
     */
    private fun captureTraktIntoActiveProfile() {
        val activeId = _uiState.value.activeProfileId ?: return
        val access = prefs.getString("TRAKT_ACCESS_TOKEN", "").orEmpty()
        val refresh = prefs.getString("TRAKT_REFRESH_TOKEN", "").orEmpty()
        val created = prefs.getLong("TRAKT_ACCESS_TOKEN_TIMESTAMP", 0L)
        val expires = prefs.getLong("TRAKT_ACCESS_TOKEN_EXPIRES_TIMESTAMP", 0L)
        val trakt = if (access.isNotBlank())
            com.github.jankoran90.showlyfin.core.domain.TraktCreds(
                accessToken = access, refreshToken = refresh,
                createdAtMillis = created, expiresAtMillis = expires,
            )
        else null
        updateProfileConfig(activeId) { c -> c.copy(credentials = c.credentials.copy(trakt = trakt)) }
    }

    // ── Plan HELM — in-app admin editor (knihovny + PIN) ─────────────────────────

    /** Plan HELM — načte seznam Jellyfin knihoven z backendu pro editor whitelistu (admin tab). */
    fun loadAdminJellyfinLibraries() {
        viewModelScope.launch {
            val uid = _uiState.value.profiles.firstOrNull { it.isAdmin }?.jellyfinUserId
            val libs = profileRepository.fetchJellyfinLibraries(uid).orEmpty()
            _uiState.update { it.copy(adminJellyfinLibraries = libs) }
        }
    }

    /** Plan HELM — nastaví app-login PIN profilu (hash). Prázdné = zrušit PIN. */
    fun setProfilePin(profileId: Long, pin: String) {
        viewModelScope.launch {
            val trimmed = pin.trim()
            val hash = if (trimmed.isBlank()) null
            else com.github.jankoran90.showlyfin.core.domain.PinHasher.hash(trimmed)
            profileRepository.setLoginPinHash(profileId, hash)
        }
    }

    /** Plan HELM — zruší PIN profilu. */
    fun clearProfilePin(profileId: Long) {
        viewModelScope.launch { profileRepository.setLoginPinHash(profileId, null) }
    }

    // ── Plan HELM — záloha (export/import balíku profilů+šablon z backendu) ───────

    /** Stáhne balík profilů+šablon z backendu (raw JSON); [onReady] dostane null při selhání. */
    fun exportProfiles(onReady: (String?) -> Unit) {
        viewModelScope.launch { onReady(profileRepository.exportProfiles()) }
    }

    /** Nahraje balík profilů+šablon na backend; [onDone] true = úspěch. */
    fun importProfiles(json: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(profileRepository.importProfiles(json)) }
    }

    // ── Šablony — in-app admin authoring (Plan WARDEN W3c část 2) ──────────────

    /** Vytvoří novou prázdnou šablonu (lokál + backend). */
    fun createTemplate(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            profileRepository.saveTemplateAuthored(
                TemplateEntity(name = trimmed, configJson = ProfileConfig.toJson(ProfileConfig())),
            )
        }
    }

    /** Uloží editovanou šablonu — název, věk, config (vč. lockedKeys); write-through na backend. */
    fun saveTemplate(template: TemplateEntity, name: String, ageRating: AgeRating?, config: ProfileConfig) {
        viewModelScope.launch {
            profileRepository.saveTemplateAuthored(
                template.copy(
                    name = name.trim().ifBlank { template.name },
                    maxAgeRating = ageRating?.name,
                    configJson = ProfileConfig.toJson(config),
                ),
            )
        }
    }

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch { profileRepository.deleteTemplateAuthored(template) }
    }

    /** Přiřadí (uuid != null) / zruší (null) šablonu profilu (lokál + backend). */
    fun assignTemplate(profileId: Long, templateUuid: String?) {
        viewModelScope.launch { profileRepository.assignTemplate(profileId, templateUuid) }
    }

    private fun refreshJellyfinState() {
        val url = prefs.getString(KEY_URL, "") ?: ""
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        _uiState.update {
            it.copy(
                jellyfinServerUrl = url,
                // Token+userId = přihlášeno; samotné heslo v balíku = „nastaveno" (login při vstupu).
                jellyfinConnected = url.isNotBlank() && ((token.isNotBlank() && userId.isNotBlank()) || activeJfHasPassword),
            )
        }
    }

    /**
     * Plan FUSE F5 — Trakt device-code přihlášení (TV větev, bez webového redirectu).
     * Vyžádá kód → uživatel ho zadá na trakt.tv/activate na telefonu → polluje token.
     * Token se ukládá přes stejný [com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider]
     * jako browser flow, takže `isLoggedIn()`/`logout()` fungují beze změny.
     */
    fun startTraktDeviceLogin() {
        if (_uiState.value.traktUserCode != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(traktStatus = "Získávám kód…") }
            val code = traktDeviceAuth.requestCode()
            if (code == null) {
                _uiState.update { it.copy(traktStatus = "Nepodařilo se získat kód, zkus to znovu") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    traktUserCode = code.userCode,
                    traktVerificationUrl = code.verificationUrl,
                    traktStatus = "Otevři ${code.verificationUrl} a zadej kód",
                )
            }
            when (val result = traktDeviceAuth.poll(code)) {
                is TraktDevicePollResult.Success -> {
                    _uiState.update {
                        it.copy(
                            traktLoggedIn = true,
                            traktUserCode = null,
                            traktVerificationUrl = null,
                            traktStatus = "Přihlášeno ✓",
                        )
                    }
                    captureTraktIntoActiveProfile() // Plan VAULT — Trakt per-profil
                }
                is TraktDevicePollResult.Expired -> _uiState.update {
                    it.copy(traktUserCode = null, traktVerificationUrl = null, traktStatus = "Kód vypršel, zkus to znovu")
                }
                is TraktDevicePollResult.Failed -> _uiState.update {
                    it.copy(traktUserCode = null, traktVerificationUrl = null, traktStatus = result.message)
                }
            }
        }
    }

    fun logout() {
        traktAuthManager.logout()
        _uiState.update {
            it.copy(traktLoggedIn = false, traktUserCode = null, traktVerificationUrl = null, traktStatus = null)
        }
        captureTraktIntoActiveProfile() // Plan VAULT — vyčistí Trakt i z balíku aktivního profilu
    }

    fun disconnectJellyfin() {
        prefs.edit()
            .remove(KEY_URL)
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .apply()
        parentalControlsRepository.clear()
        refreshJellyfinState()
    }
}
