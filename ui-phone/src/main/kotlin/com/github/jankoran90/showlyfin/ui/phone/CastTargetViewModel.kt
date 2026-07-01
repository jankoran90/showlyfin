package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.jellyfin.CastTargetPrefs
import com.github.jankoran90.showlyfin.data.jellyfin.NaTvService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * DOCK (SHW-77): VM pro blok Nastavení „Výchozí zařízení pro Na TV".
 * Načte dostupná ovladatelná zařízení (Jellyfin session) a nechá zvolit výchozí cíl castu
 * (uloží stabilní deviceId přes [CastTargetPrefs]). Tutéž preferenci píše i přepínač v Ovladači.
 */
@HiltViewModel
class CastTargetViewModel @Inject constructor(
    private val naTv: NaTvService,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class Device(val deviceId: String, val name: String, val online: Boolean)

    data class UiState(
        val devices: List<Device> = emptyList(),
        val selectedDeviceId: String? = null,   // null = automatika (televize)
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(selectedDeviceId = CastTargetPrefs.defaultDeviceId(prefs)),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val url = prefs.getString("jellyfin_server_url", "").orEmpty()
        val token = prefs.getString("jellyfin_token", "").orEmpty()
        if (url.isBlank() || token.isBlank()) return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val online = naTv.getSessions(url, token)
                .mapNotNull { s -> s.deviceId?.let { Device(it, s.deviceName, true) } }
                .distinctBy { it.deviceId }
            val sel = CastTargetPrefs.defaultDeviceId(prefs)
            val list = online.toMutableList()
            // zvolený cíl, který zrovna není online, ukaž taky (ať z výběru nezmizí)
            if (sel != null && online.none { it.deviceId == sel }) {
                list += Device(sel, CastTargetPrefs.defaultDeviceName(prefs) ?: "Zařízení", false)
            }
            _state.value = UiState(devices = list, selectedDeviceId = sel, loading = false)
        }
    }

    /** null = automatika (televize). */
    fun select(device: Device?) {
        CastTargetPrefs.setDefault(prefs, device?.deviceId, device?.name)
        _state.value = _state.value.copy(selectedDeviceId = device?.deviceId)
    }
}
