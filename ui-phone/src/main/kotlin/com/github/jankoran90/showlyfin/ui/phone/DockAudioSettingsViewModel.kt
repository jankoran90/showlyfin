package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named

/**
 * REVERB (SHW-82): VM pro blok Nastavení „Zvukový výstup přehrávače" (Domácí sestava).
 * Čte/píše stejné preference jako přepínač v ovladači ([OvladacViewModel] `PK_DOCK_*`):
 *  - výchozí zvukový výstup po startu castu na Zenbook (Zenbook / AV receiver),
 *  - výchozí lip-sync posun pro AV receiver (ms).
 * Přepnout a doladit posun jde vždy i za běhu v ovladači; tady se nastavuje jen výchozí chování.
 */
@HiltViewModel
class DockAudioSettingsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(
        /** id výchozího cíle: "local" = Zenbook (bez auto-přepnutí), "avr" = AV receiver. */
        val defaultTarget: String = TARGET_LOCAL,
        /** výchozí lip-sync posun pro AVR v ms (− = zvuk dřív / video zdrženo). */
        val avrDelayMs: Int = 0,
    )

    private val _state = MutableStateFlow(
        UiState(
            defaultTarget = prefs.getString(KEY_TARGET, TARGET_LOCAL).orEmpty().ifBlank { TARGET_LOCAL },
            avrDelayMs = prefs.getInt(KEY_DELAY, 0),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setDefaultTarget(id: String) {
        prefs.edit().putString(KEY_TARGET, id).apply()
        _state.value = _state.value.copy(defaultTarget = id)
    }

    fun nudgeAvrDelay(deltaMs: Int) {
        val v = (_state.value.avrDelayMs + deltaMs).coerceIn(-10_000, 10_000)
        prefs.edit().putInt(KEY_DELAY, v).apply()
        _state.value = _state.value.copy(avrDelayMs = v)
    }

    fun resetAvrDelay() {
        prefs.edit().putInt(KEY_DELAY, 0).apply()
        _state.value = _state.value.copy(avrDelayMs = 0)
    }

    companion object {
        const val TARGET_LOCAL = "local"
        const val TARGET_AVR = "avr"
        // Sdíleno s OvladacViewModel.PK_DOCK_* — musí sedět (jedna preference, dvě místa zápisu).
        private const val KEY_TARGET = "dock_audio_default_target"
        private const val KEY_DELAY = "dock_audio_avr_delay_ms"
    }
}
