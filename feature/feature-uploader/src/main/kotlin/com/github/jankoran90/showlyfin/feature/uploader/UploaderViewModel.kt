package com.github.jankoran90.showlyfin.feature.uploader

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.TmmFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class TmmFileQueueItem(val fid: String, val file: TmmFile)

data class UploaderUiState(
    val isLoading: Boolean = false,
    val isNotConfigured: Boolean = false,
    /** Přihlášen = máme platnou session cookie (Plan PROFILES #21 — indikátor stavu v Nastavení). */
    val isLoggedIn: Boolean = false,
    val sessionId: String? = null,
    val files: List<TmmFileQueueItem> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class UploaderViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private val TERMINAL_STATES = setOf("moved", "error")
        const val PREF_TMM_SESSION_ID = "uploader_tmm_session_id"
        private const val PREF_UPLOADER_URL = "uploader_base_url"
        private const val PREF_UPLOADER_COOKIE = "uploader_session_cookie"
        private const val PREF_UPLOADER_PASSWORD = "uploader_password"
    }

    private val _uiState = MutableStateFlow(UploaderUiState())
    val uiState: StateFlow<UploaderUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    val baseUrl get() = prefs.getString(PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(PREF_UPLOADER_COOKIE, "") ?: ""

    val isConfigured get() = baseUrl.isNotBlank()

    init { refreshLoginState() }

    /** Promítne aktuální stav přihlášení (cookie present) z prefs do uiState (pro indikátor v UI). */
    fun refreshLoginState() {
        _uiState.update {
            it.copy(isNotConfigured = !isConfigured, isLoggedIn = isConfigured && cookie.isNotBlank())
        }
    }

    /** Odhlášení z Uploaderu — smaže session cookie + heslo + TMM session (URL ponechá). */
    fun logout() {
        pollingJob?.cancel()
        prefs.edit()
            .remove(PREF_UPLOADER_COOKIE)
            .remove(PREF_UPLOADER_PASSWORD)
            .remove(PREF_TMM_SESSION_ID)
            .apply()
        _uiState.update { it.copy(isLoggedIn = false, sessionId = null, files = emptyList(), error = null) }
    }

    fun checkConfiguration() {
        if (!isConfigured) { _uiState.update { it.copy(isNotConfigured = true) }; return }
        val sid = prefs.getString(PREF_TMM_SESSION_ID, null)
        _uiState.update { it.copy(isNotConfigured = false, sessionId = sid) }
        if (sid != null) startPolling(sid)
    }

    fun login(password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { uploaderDs.login(baseUrl, password) }
                .onSuccess { sessionCookie ->
                    // Ulož cookie i heslo → interceptor umí auto-relogin po expiraci (401).
                    prefs.edit()
                        .putString(PREF_UPLOADER_COOKIE, sessionCookie)
                        .putString(PREF_UPLOADER_PASSWORD, password)
                        .apply()
                    _uiState.update { it.copy(isLoading = false, isNotConfigured = false, isLoggedIn = true) }
                    checkConfiguration()
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun saveBaseUrl(url: String) {
        // Doplň scheme, když chybí (uživatel zadal jen host) → OkHttp jinak spadne na localhost.
        val normalized = url.trim().trimEnd('/').let {
            if (it.isEmpty() || it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        prefs.edit().putString(PREF_UPLOADER_URL, normalized).apply()
        _uiState.update { it.copy(isNotConfigured = normalized.isBlank()) }
    }

    fun startPolling(sid: String) {
        prefs.edit().putString(PREF_TMM_SESSION_ID, sid).apply()
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                runCatching {
                    val session = uploaderDs.getTmmSession(baseUrl, cookie, sid)
                    val items = session.files.entries.map { (fid, file) -> TmmFileQueueItem(fid, file) }
                    _uiState.update { it.copy(files = items, error = null, sessionId = sid) }
                    session.files.values.all { it.status in TERMINAL_STATES } && session.files.isNotEmpty()
                }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                    .getOrDefault(false).let { allDone -> if (allDone) return@launch }
                delay(3_000)
            }
        }
    }

    fun processFiles() {
        val sid = prefs.getString(PREF_TMM_SESSION_ID, null) ?: return
        viewModelScope.launch {
            runCatching { uploaderDs.tmmProcess(baseUrl, cookie, sid) }
                .onSuccess { _uiState.update { it.copy(message = "Zpracovávám…") } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null, error = null) } }

    override fun onCleared() { super.onCleared(); pollingJob?.cancel() }
}
