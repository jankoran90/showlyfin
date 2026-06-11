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
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService
import org.jellyfin.sdk.api.client.ApiClient
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
    /** Viditelné sekce na TV (Plan VAULT V10). null = zrcadlí [visibleSections]. */
    val visibleSectionsTv: Set<String>? = null,
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
    /**
     * Plan VAULT — Jellyfin server ODMÍTL přihlášení profilu (špatné jméno/heslo v balíku) →
     * aktivace se NEPROVEDLA a brána ukáže tuhle chybu. Dřív tichý Timber.w → user nevěděl proč
     * „není nastaven". null = bez chyby.
     */
    val activationError: String? = null,
)

@HiltViewModel
class ProfileGateViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val configGateway: ProfileConfigGateway,
    private val jellyfinAuth: JellyfinAuthService,
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
            var available = configGateway.isAvailable()
            // Auto-login z hesla zapečeného v build env (ProfileConfigGateway.autoLoginPassword) po čisté
            // instalaci — ať se při vývoji nemusí pořád znovu vyplňovat přihlášení. Prázdné heslo (env
            // nenastaven) = přeskočí se (běžný fresh-install tok → hlavní login obrazovka). Release-only.
            if (!available && profileRepository.getAll().isEmpty() &&
                ProfileConfigGateway.autoLoginPassword.isNotBlank()
            ) {
                if (configGateway.login(ProfileConfigGateway.autoLoginPassword)) available = true
            }
            uploaderAvailable.value = available
            // Backend dostupný (cookie přežila / re-install s cookie / auto-login) ale lokál prázdný → roster.
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
                    it.copy(
                        visibleSections = cfg.visibleSections,
                        visibleSectionsTv = cfg.visibleSectionsTv,
                        defaultSection = cfg.defaultSection,
                    )
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
            _state.update { it.copy(activating = true, activationError = null) }
            val error = runCatching { hydrateAndActivate(profile) }
                .onFailure { Timber.w(it, "[GATEKEY] aktivace profilu selhala") }
                .getOrNull()
            _state.update { it.copy(activating = false, activationError = error) }
        }
    }

    /** Doplní https:// když chybí scheme, odřízne koncové i úvodní „/". Prázdné nechá prázdné. */
    private fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://${t.trimStart('/')}"
    }

    /**
     * Vrací `null` = aktivováno; jinak text chyby pro bránu (a profil se NEaktivuje) — Jellyfin
     * server creds odmítl (Plan VAULT: viditelná chyba místo tichého warningu).
     */
    private suspend fun hydrateAndActivate(profile: ProfileEntity): String? {
        // 1. Stáhni config balík (dešifrované creds); offline → fallback na lokální configJson.
        //    VAULT V7: chybějící creds domény v backend balíku doplň z LOKÁLNÍHO configu TÉHOŽ
        //    profilu — anomální/prázdný backend záznam jinak smaže funkční přihlášení (incident
        //    b129: prázdný override → Děti zdědily adminovy účty přes tehdejší „NEMAZAT" applier).
        val localConfig = ProfileConfig.fromJson(profile.configJson)
        val remoteJson = profileRepository.fetchBackendConfig(profile)
        val config = if (remoteJson != null) {
            val remote = ProfileConfig.fromJson(remoteJson)
            remote.copy(credentials = remote.credentials.mergeMissingFrom(localConfig.credentials))
        } else {
            localConfig
        }
        val json = ProfileConfig.toJson(config)
        val jf = config.credentials.jellyfin
        // Web admin ukládá host bez scheme (např. „video.jankoran.cz") → doplň https://, jinak
        // jellyfin.createApi() i applier spadnou/nepřihlásí.
        var serverUrl = normalizeUrl(jf?.url?.takeIf { it.isNotBlank() } ?: profile.serverUrl)
        var token = jf?.token?.takeIf { it.isNotBlank() } ?: profile.jellyfinToken
        var effectiveJson = json

        // 2. Vyrob/obnov token (jádro G-A4 + VAULT): bez tokenu, NEBO server uložený token odmítá
        //    (admin změnil heslo v Správě → starý token patří starým creds) → AuthenticateByName.
        //    Offline (validace/login nejde posoudit) → pokračuj s tím co je, ať profil jde otevřít.
        if (serverUrl.isNotBlank()) {
            val tokenValid = if (token.isBlank()) false else (jellyfinAuth.validateToken(serverUrl, token) ?: true)
            val password = jf?.password
            if (!tokenValid && jf != null && !password.isNullOrBlank()) {
                when (val outcome = jellyfinAuth.authenticate(serverUrl, jf.username.ifBlank { profile.name }, password)) {
                    is JellyfinAuthService.AuthOutcome.Success -> {
                        token = outcome.login.token
                        val merged = config.copy(
                            credentials = config.credentials.copy(
                                jellyfin = jf.copy(
                                    url = serverUrl,
                                    token = token,
                                    userId = outcome.login.userId.ifBlank { jf.userId.ifBlank { profile.jellyfinUserId } },
                                ),
                            ),
                        )
                        effectiveJson = ProfileConfig.toJson(merged)
                        Timber.i("[GATEKEY] AuthenticateByName OK pro '${profile.name}'")
                    }
                    is JellyfinAuthService.AuthOutcome.Rejected -> {
                        Timber.w("[GATEKEY] AuthenticateByName ODMÍTNUT pro '${profile.name}' (HTTP ${outcome.status})")
                        return "Jellyfin odmítl přihlášení profilu ${profile.name} (HTTP ${outcome.status}). " +
                            "Zkontroluj jméno a heslo v admin Správě profilů."
                    }
                    is JellyfinAuthService.AuthOutcome.Unavailable ->
                        Timber.w("[GATEKEY] AuthenticateByName nedostupný pro '${profile.name}': ${outcome.message}")
                }
            } else if (!tokenValid && token.isBlank()) {
                Timber.w("[GATEKEY] profil '${profile.name}' nemá token ani heslo — JF zůstane nepřihlášen")
            }
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
        return null
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
