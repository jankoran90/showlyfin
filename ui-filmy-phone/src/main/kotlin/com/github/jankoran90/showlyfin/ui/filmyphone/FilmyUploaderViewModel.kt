package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * CELLULOID (SHW-98) M2.3b — přihlášení appky Filmy k uploader serveru (jen heslo).
 *
 * Filmy má vlastní sandbox → nedědí uploader session ze showlyfinu, proto ČSFD český popis/galerie/
 * komentáře u titulů bez českého TMDB překladu (Kikudžiró, Tokyo Sonata…) nedorazí. [ProfileConfigGateway.login]
 * pošle heslo na `DEFAULT_BASE_URL` (`upload.jankoran.cz`) → uloží base_url + session cookie + heslo do
 * prefs (interceptor pak umí auto-relogin po 401). Tím ČSFD backend naskočí i v appce Filmy.
 */
@HiltViewModel
class FilmyUploaderViewModel @Inject constructor(
    private val gateway: ProfileConfigGateway,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val configured: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State(configured = readConfigured()))
    val state: StateFlow<State> = _state.asStateFlow()

    /** Přihlášeno = máme session cookie NEBO heslo (interceptor umí relogin heslem po 401). */
    private fun readConfigured(): Boolean =
        prefs.getString("uploader_session_cookie", "").orEmpty().isNotBlank() ||
            prefs.getString("uploader_password", "").orEmpty().isNotBlank()

    fun login(password: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val ok = gateway.login(password)
            _state.update {
                it.copy(
                    loading = false,
                    configured = ok || readConfigured(),
                    error = if (ok) null else "Přihlášení selhalo — zkontroluj heslo",
                )
            }
        }
    }
}
