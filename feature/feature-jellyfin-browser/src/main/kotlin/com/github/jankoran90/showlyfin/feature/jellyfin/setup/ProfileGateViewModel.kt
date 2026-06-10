package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.PinHasher
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import javax.inject.Inject

data class ProfileGateState(
    val isLoading: Boolean = true,
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfile: ProfileEntity? = null,
    val isAddingProfile: Boolean = false,
    /** Viditelné sekce aktivního profilu (Plan PROFILES 1E). Prázdné = vše (admin/legacy). */
    val visibleSections: Set<String> = emptySet(),
    /** „Hlavní" sekce aktivního profilu (Plan PROFILES Fáze 4). null = výchozí. */
    val defaultSection: String? = null,
    /** Plan WARDEN W1: profil čekající na zadání PINu (klik na profil s [ProfileEntity.loginPinHash]). */
    val pendingPinProfile: ProfileEntity? = null,
    /** Plan WARDEN W1: poslední zadaný PIN byl špatný. */
    val pinError: Boolean = false,
    /**
     * Plan GATEKEY G-A1: čistá instalace (žádná lokální data + backend nepřihlášen) → ukázat **hlavní
     * login obrazovku** před ServerSetup/pickerem. Po úspěšném loginu → false.
     */
    val needsMainLogin: Boolean = false,
    /** Plan GATEKEY G-A1: probíhá hlavní login (spinner v tlačítku). */
    val mainLoginLoading: Boolean = false,
    /** Plan GATEKEY G-A1: hlavní login selhal (špatné heslo / síť). */
    val mainLoginError: String? = null,
    /** Plan GATEKEY G-A3: stahuje se roster profilů z backendu (drží spinner místo ServerSetup). */
    val seeding: Boolean = false,
    /** Plan GATEKEY G-A4: tap profilu → hydratace creds + auto JF login (drží spinner do vstupu). */
    val activating: Boolean = false,
)

@HiltViewModel
class ProfileGateViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val configGateway: ProfileConfigGateway,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileGateState())
    val state: StateFlow<ProfileGateState> = _state.asStateFlow()

    /**
     * Plan GATEKEY G-A1 — dostupnost backendu (uploader URL+cookie). null = ještě nezjištěno (drží
     * spinner, ať neproblikne ServerSetup), false = nepřihlášen (fresh install → hlavní login),
     * true = přihlášen. Po úspěšném [submitMainLogin] se nastaví na true.
     */
    private val uploaderAvailable = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            val available = configGateway.isAvailable()
            uploaderAvailable.value = available
            // Backend dostupný (cookie přežila / re-install s cookie) ale lokál prázdný → nasaď roster.
            if (available && profileRepository.getAll().isEmpty()) seedRoster()
        }

        combine(
            profileRepository.observeAll(),
            profileRepository.activeProfile,
            uploaderAvailable,
        ) { profiles, active, available -> Triple(profiles, active, available) }
            .onEach { (profiles, active, available) ->
                _state.update {
                    it.copy(
                        profiles = profiles,
                        activeProfile = active,
                        // Čekáme na zjištění stavu backendu → žádné probliknutí ServerSetup.
                        isLoading = available == null,
                        // Fresh install = nepřihlášený backend + žádná lokální data → hlavní login.
                        needsMainLogin = available == false && profiles.isEmpty() && active == null,
                    )
                }
            }
            .launchIn(viewModelScope)

        profileRepository.activeConfig
            .onEach { cfg ->
                _state.update {
                    it.copy(visibleSections = cfg.visibleSections, defaultSection = cfg.defaultSection)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Plan GATEKEY G-A1 — hlavní login: přihlásí app k backendu (heslo, URL zapečená nebo
     * [urlOverride]). Úspěch → backend dostupný → fresh-install pokračuje na roster/picker (G-A3).
     */
    fun submitMainLogin(password: String, urlOverride: String?) {
        viewModelScope.launch {
            _state.update { it.copy(mainLoginLoading = true, mainLoginError = null) }
            val ok = configGateway.login(password, urlOverride)
            if (ok) {
                uploaderAvailable.value = true
                _state.update { it.copy(mainLoginLoading = false, needsMainLogin = false) }
                // Plan GATEKEY G-A3: stáhni roster profilů z backendu → profil picker.
                seedRoster()
            } else {
                _state.update {
                    it.copy(mainLoginLoading = false, mainLoginError = "Přihlášení selhalo. Zkontroluj heslo a připojení.")
                }
            }
        }
    }

    /**
     * Plan GATEKEY G-A4 — vstup do profilu: **hydratuje creds z backendu PŘED aktivací** a zařídí
     * auto Jellyfin login (jinak JF/Poslech obrazovky naběhnou s prázdným tokenem dřív, než async
     * sync dotáhne creds → „nepřihlášeno"). Drží `activating` spinner do vstupu.
     */
    fun selectProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            _state.update { it.copy(activating = true) }
            runCatching { hydrateAndActivate(profile) }
                .onFailure { Timber.w(it, "[GATEKEY] aktivace profilu selhala") }
            _state.update { it.copy(activating = false) }
        }
    }

    /** Doplní https:// když chybí scheme, odřízne koncové „/". Prázdné nechá prázdné. */
    private fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://$t"
    }

    private suspend fun hydrateAndActivate(profile: ProfileEntity) {
        // 1. Stáhni config balík (dešifrované creds); offline → fallback na lokální configJson.
        val json = profileRepository.fetchBackendConfig(profile) ?: profile.configJson
        val config = ProfileConfig.fromJson(json)
        val jf = config.credentials.jellyfin
        // Web admin ukládá host bez scheme (např. „video.jankoran.cz") → doplň https://, jinak
        // jellyfin.createApi() i applier spadnou/nepřihlásí.
        var serverUrl = normalizeUrl(jf?.url?.takeIf { it.isNotBlank() } ?: profile.serverUrl)
        var token = jf?.token?.takeIf { it.isNotBlank() } ?: profile.jellyfinToken
        var effectiveJson = json

        // 2. Bez tokenu, ale s heslem → vyrob token přes AuthenticateByName (jádro G-A4).
        if (token.isBlank() && jf != null && !jf.password.isNullOrBlank() && serverUrl.isNotBlank()) {
            runCatching {
                val api = jellyfin.createApi(baseUrl = serverUrl)
                val authResult by api.userApi.authenticateUserByName(
                    username = jf.username.ifBlank { profile.name },
                    password = jf.password!!,
                )
                val at = authResult.accessToken
                val uid = authResult.user?.id?.toString()
                if (!at.isNullOrBlank()) {
                    token = at
                    val merged = config.copy(
                        credentials = config.credentials.copy(
                            jellyfin = jf.copy(url = serverUrl, token = at, userId = (uid ?: jf.userId.ifBlank { profile.jellyfinUserId })),
                        ),
                    )
                    effectiveJson = ProfileConfig.toJson(merged)
                    Timber.i("[GATEKEY] AuthenticateByName OK pro '${profile.name}'")
                }
            }.onFailure { Timber.w(it, "[GATEKEY] AuthenticateByName selhal pro '${profile.name}'") }
        }

        // 3. Nastav sdílený ApiClient hned, ať JF obrazovky naběhnou už přihlášené (ne až po async syncu).
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            runCatching {
                apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
            }.onFailure { Timber.w(it, "[GATEKEY] apiClient.update selhal") }
        }

        // 4. Zapiš hydratované creds do entity PŘED aktivací → setActive zapíše správné kanonické prefs.
        profileRepository.applyHydratedJellyfin(profile.id, serverUrl, token, effectiveJson)
        profileRepository.setActive(profile.id)
    }

    /**
     * Plan WARDEN W1 — klik na avatar v úvodní bráně. Bez PINu → rovnou aktivuje; s PINem
     * ([ProfileEntity.loginPinHash]) → otevře PIN prompt (ověří se v [submitPin]).
     */
    fun onProfileClicked(profile: ProfileEntity) {
        if (profile.loginPinHash.isNullOrBlank()) {
            selectProfile(profile)
        } else {
            _state.value = _state.value.copy(pendingPinProfile = profile, pinError = false)
        }
    }

    /** Ověří zadaný PIN proti čekajícímu profilu; shoda → aktivuje a zavře prompt, jinak chyba. */
    fun submitPin(pin: String) {
        val pending = _state.value.pendingPinProfile ?: return
        if (PinHasher.verify(pin, pending.loginPinHash)) {
            _state.value = _state.value.copy(pendingPinProfile = null, pinError = false)
            selectProfile(pending)
        } else {
            _state.value = _state.value.copy(pinError = true)
        }
    }

    fun cancelPin() {
        _state.value = _state.value.copy(pendingPinProfile = null, pinError = false)
    }

    /** Plan GATEKEY G-A3 — stáhne backend roster a nasadí lokální stuby; observeAll pak ukáže picker. */
    private fun seedRoster() {
        viewModelScope.launch {
            _state.update { it.copy(seeding = true) }
            runCatching { profileRepository.seedFromBackendRoster() }
            _state.update { it.copy(seeding = false) }
        }
    }

    fun startAddProfile() {
        _state.value = _state.value.copy(isAddingProfile = true)
    }

    fun cancelAddProfile() {
        _state.value = _state.value.copy(isAddingProfile = false)
    }
}
