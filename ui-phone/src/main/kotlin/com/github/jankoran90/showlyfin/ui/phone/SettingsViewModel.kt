package com.github.jankoran90.showlyfin.ui.phone

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
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
    // Stremio / Comet filtr výsledků
    val streamFilter: StreamFilterPrefs? = null,
    val streamFilterLoading: Boolean = false,
    val streamFilterError: String? = null,
    // Živé logování (Debug)
    val liveLogging: Boolean = false,
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
    val listen: ListenSettings = ListenSettings(),
    // Stahování na ABS server — per-podcast auto-download (přesunuto z detailu)
    val serverPodcasts: List<com.github.jankoran90.showlyfin.data.abs.model.PodcastServerAutoDownload> = emptyList(),
    val serverPodcastsLoading: Boolean = false,
    val serverPodcastsBusyIds: Set<String> = emptySet(),
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
    private val parentalControlsRepository: ParentalControlsRepository,
    private val profileRepository: ProfileRepository,
    private val uploaderDs: UploaderRemoteDataSource,
    private val absRepo: AbsRepository,
    private val absPrefs: AbsPreferences,
    @ApplicationContext private val appContext: Context,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val KEY_URL = "jellyfin_server_url"
        private const val KEY_TOKEN = "jellyfin_token"
        private const val KEY_USER_ID = "jellyfin_user_id"
        const val KEY_LIVE_LOGGING = "live_logging_enabled"
    }

    private val uploaderBase get() = prefs.getString("uploader_base_url", "") ?: ""
    private val uploaderCookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshJellyfinState()
        _uiState.update { it.copy(traktLoggedIn = traktAuthManager.isLoggedIn()) }
        viewModelScope.launch {
            traktAuthManager.authCodeFlow.collect { code ->
                _uiState.update { it.copy(isLoading = true, error = null) }
                try {
                    traktAuthManager.authorize(code)
                    _uiState.update { it.copy(traktLoggedIn = true, isLoading = false) }
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
        _uiState.update { it.copy(liveLogging = prefs.getBoolean(KEY_LIVE_LOGGING, false), uploaderBaseUrl = uploaderBase) }
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

    private fun refreshJellyfinState() {
        val url = prefs.getString(KEY_URL, "") ?: ""
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        _uiState.update {
            it.copy(
                jellyfinServerUrl = url,
                jellyfinConnected = url.isNotBlank() && token.isNotBlank() && userId.isNotBlank(),
            )
        }
    }

    fun logout() {
        traktAuthManager.logout()
        _uiState.update { it.copy(traktLoggedIn = false) }
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
