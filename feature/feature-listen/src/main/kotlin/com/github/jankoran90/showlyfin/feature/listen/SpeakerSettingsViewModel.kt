package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.SpeakerCastRemote
import com.github.jankoran90.showlyfin.feature.listen.tv.SpeakerPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/** Nastavení „Na repro" (Chromecast u receiveru) + rychlé ovládání toho, co na repru hraje. */
@HiltViewModel
class SpeakerSettingsViewModel @Inject constructor(
    private val remote: SpeakerCastRemote,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(val prefs: SpeakerPrefs, val nowPlaying: String? = null, val busy: Boolean = false)

    private val _state = MutableStateFlow(UiState(SpeakerPrefs.from(prefs)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val base get() = prefs.getString("uploader_base_url", "").orEmpty()
    private val cookie get() = prefs.getString("uploader_session_cookie", "").orEmpty()

    fun setCastHost(v: String) = save { putString(SpeakerPrefs.KEY_CAST_HOST, v.trim()) }
    fun setAvrHost(v: String) = save { putString(SpeakerPrefs.KEY_AVR_HOST, v.trim()) }
    fun setAvrEnabled(v: Boolean) = save { putBoolean(SpeakerPrefs.KEY_AVR_ENABLED, v) }
    fun setAvrInput(code: String) = save { putString(SpeakerPrefs.KEY_AVR_INPUT, code) }

    /** Zjisti, co na repru hraje (ověří i spojení server → Chromecast). */
    fun refreshStatus() = viewModelScope.launch {
        _state.value = _state.value.copy(busy = true)
        val st = remote.status(base, cookie, _state.value.prefs.castHost)
        val text = when {
            st == null -> "Chromecast nedostupný (server ho nevidí)."
            st.optString("state") in setOf("PLAYING", "BUFFERING", "PAUSED") ->
                "${if (st.optString("state") == "PAUSED") "Pozastaveno" else "Hraje"}: ${st.optString("title").ifBlank { "?" }}"
            else -> "Chromecast je připojený, nic nehraje."
        }
        _state.value = _state.value.copy(nowPlaying = text, busy = false)
    }

    fun control(action: String) = viewModelScope.launch {
        remote.control(base, cookie, action, castHost = _state.value.prefs.castHost)
        refreshStatus()
    }

    private fun save(edit: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(edit).apply()
        _state.value = _state.value.copy(prefs = SpeakerPrefs.from(prefs))
    }
}
