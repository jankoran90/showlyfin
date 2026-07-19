package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.JellyfinCreds
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ORCHARD — přihlášení appky Filmy k Jellyfin serveru (device-local) + výběr knihoven.
 *
 * Proč vlastní login: JF creds jsou v celém ekosystému DEVICE-LOKÁLNÍ, do cross-device backend bundlu se
 * nepushují ([ProfileRepository.syncConfigFromBackend] je jen adoptuje, když tam jsou — u nás nejsou). Filmy
 * profily ([FilmyProfileManager]) mají správné JF `jellyfinUserId` (sdílené se showlyfin účty), ale prázdné
 * `serverUrl`/`jellyfinToken` → JF API vrací null (Knihovna prázdná, next-up mrtvý). Tento login je naplní.
 *
 * Výběr knihoven (whitelist): [com.github.jankoran90.showlyfin.feature.jellyfin.LibraryRowsViewModel] zobrazuje
 * JF knihovny jako řady i na DOMOVĚ. Bez omezení (whitelist=null) by se domov ZASPAMOVAL všemi knihovnami — user
 * to explicitně nechce („nejdřív vybrat které se mají zobrazit"). Proto po přihlášení defaultně whitelist =
 * PRÁZDNÝ (opt-in) a uživatel si knihovny naklikne ([toggleLibrary]) → `jellyfinLibraryWhitelist` aktivního profilu.
 *
 * Reuse: sdílený [JellyfinAuthService] (auth + `listLibraries` přes jednorázové api, nesahá na aktivní session —
 * proto NEinjektujeme raw JF SDK `ApiClient` do tohoto UI modulu). Po úspěchu [ProfileRepository.setActive]
 * propaguje token do kanonických prefs → Knihovna/Filmotéka JF zdroj/next-up naskočí bez restartu. Backend sync
 * creds NEpřepíše (`jellyfin = remote ?: local`).
 */
@HiltViewModel
class FilmyJellyfinLoginViewModel @Inject constructor(
    private val authService: JellyfinAuthService,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val error: String? = null,
        /** Non-null = přihlášeno (URL serveru), null = nepřihlášeno. */
        val connectedServer: String? = null,
        val librariesLoading: Boolean = false,
        val libraries: List<JellyfinAuthService.JfLibrary> = emptyList(),
        /** Normalizovaná id knihoven zapnutých ve whitelistu (prázdné = žádná se nezobrazí). */
        val selectedLibraryIds: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        // Stav JF přihlášení + whitelist = z aktivního profilu (přežije přepnutí profilu i restart).
        profileRepository.activeConfig
            .onEach { cfg ->
                val url = cfg.credentials.jellyfin?.url?.takeIf { it.isNotBlank() }
                _state.update {
                    it.copy(
                        connectedServer = url,
                        selectedLibraryIds = cfg.jellyfinLibraryWhitelist.orEmpty().map(::normId).toSet(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun connect(rawUrl: String, username: String, password: String, rememberPassword: Boolean = true) {
        val url = normalizeUrl(rawUrl)
        if (url.isBlank()) {
            _state.update { it.copy(error = "Zadej URL serveru") }
            return
        }
        if (username.isBlank()) {
            _state.update { it.copy(error = "Zadej uživatelské jméno") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val outcome = authService.authenticate(url, username, password)) {
                is JellyfinAuthService.AuthOutcome.Success -> {
                    val login = outcome.login
                    val active = profileRepository.activeProfile.value
                    if (active == null) {
                        _state.update { it.copy(loading = false, error = "Není aktivní profil") }
                        return@launch
                    }
                    val userId = login.userId.ifBlank { active.jellyfinUserId }
                    val base = ProfileConfig.fromJson(active.configJson)
                    val merged = base.copy(
                        // Default opt-in: dokud si user knihovny nevybere, žádná se nezobrazí (domov se nezaspamuje).
                        jellyfinLibraryWhitelist = base.jellyfinLibraryWhitelist ?: emptyList(),
                        credentials = base.credentials.copy(
                            jellyfin = JellyfinCreds(
                                url = url,
                                userId = userId,
                                token = login.token,
                                username = login.userName,
                                password = if (rememberPassword) password else null,
                            ),
                        ),
                    )
                    val updated = active.copy(
                        serverUrl = url,
                        jellyfinUserId = userId,
                        jellyfinToken = login.token,
                        configJson = ProfileConfig.toJson(merged),
                    )
                    profileRepository.upsert(updated)
                    profileRepository.setActive(active.id) // propaguj creds do prefs + re-emit aktivního profilu
                    _state.update { it.copy(loading = false, error = null, connectedServer = url) }
                    loadLibraries()
                }
                is JellyfinAuthService.AuthOutcome.Rejected ->
                    _state.update { it.copy(loading = false, error = "Špatné jméno nebo heslo") }
                is JellyfinAuthService.AuthOutcome.Unavailable ->
                    _state.update {
                        it.copy(loading = false, error = "Server nedostupný: ${outcome.message.orEmpty()}")
                    }
            }
        }
    }

    /** Načte seznam JF knihoven (pro výběr, které zobrazit). Creds z aktivního profilu. */
    fun loadLibraries() {
        val jf = profileRepository.activeConfig.value.credentials.jellyfin ?: return
        if (jf.url.isBlank() || jf.token.isBlank() || jf.userId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(librariesLoading = true) }
            val libs = authService.listLibraries(jf.url, jf.token, jf.userId)
            _state.update { it.copy(librariesLoading = false, libraries = libs) }
        }
    }

    /** Zapni/vypni knihovnu ve whitelistu (které se zobrazí na domově i v sekci Knihovna). Persist per profil. */
    fun toggleLibrary(libraryId: String) {
        val active = profileRepository.activeProfile.value ?: return
        viewModelScope.launch {
            val norm = normId(libraryId)
            profileRepository.updateConfig(active.id) { cfg ->
                val current = cfg.jellyfinLibraryWhitelist.orEmpty()
                val next = if (current.any { normId(it) == norm }) {
                    current.filter { normId(it) != norm }
                } else {
                    current + libraryId
                }
                cfg.copy(jellyfinLibraryWhitelist = next)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val active = profileRepository.activeProfile.value ?: return@launch
            val base = ProfileConfig.fromJson(active.configJson)
            val merged = base.copy(credentials = base.credentials.copy(jellyfin = null))
            val updated = active.copy(serverUrl = "", jellyfinToken = "", configJson = ProfileConfig.toJson(merged))
            profileRepository.upsert(updated)
            profileRepository.setActive(active.id)
            _state.update { it.copy(connectedServer = null, error = null, libraries = emptyList()) }
        }
    }

    private fun normId(id: String): String = id.replace("-", "").lowercase()

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return "https://$trimmed"
        return trimmed
    }
}
