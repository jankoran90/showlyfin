package com.github.jankoran90.showlyfin.ui.slovophone

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
 * Slovo (user 2026-09-17): appka neměla ŽÁDNOU Jellyfin přihlašovací obrazovku, takže cast na TV
 * z YouTube kanálů nešel spustit vůbec — `feature-listen` (CastTargetViewModel a spol.) čte
 * "jellyfin_server_url"/"jellyfin_token" ze SharedPreferences, ale nic je nikdy nezapisovalo (jen
 * Filmy appka tenhle formulář měla). Zrcadlí `FilmyJellyfinLoginViewModel` (ui-filmy-phone), jen
 * BEZ výběru knihoven (Slovo JF knihovny nebrowsuje, jen potřebuje token, aby šlo poslat titulky
 * na Jellyfin session přes cast). [ProfileRepository.setActive] propaguje creds do STEJNÝCH flat
 * prefs klíčů, které `feature-listen` čte — žádná další úprava tam není potřeba.
 */
@HiltViewModel
class SlovoJellyfinLoginViewModel @Inject constructor(
    private val authService: JellyfinAuthService,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val error: String? = null,
        /** Non-null = přihlášeno (URL serveru), null = nepřihlášeno. */
        val connectedServer: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        profileRepository.activeConfig
            .onEach { cfg ->
                val url = cfg.credentials.jellyfin?.url?.takeIf { it.isNotBlank() }
                _state.update { it.copy(connectedServer = url) }
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

    fun disconnect() {
        viewModelScope.launch {
            val active = profileRepository.activeProfile.value ?: return@launch
            val base = ProfileConfig.fromJson(active.configJson)
            val merged = base.copy(credentials = base.credentials.copy(jellyfin = null))
            val updated = active.copy(serverUrl = "", jellyfinToken = "", configJson = ProfileConfig.toJson(merged))
            profileRepository.upsert(updated)
            profileRepository.setActive(active.id)
            _state.update { it.copy(connectedServer = null, error = null) }
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return "https://$trimmed"
        return trimmed
    }
}
