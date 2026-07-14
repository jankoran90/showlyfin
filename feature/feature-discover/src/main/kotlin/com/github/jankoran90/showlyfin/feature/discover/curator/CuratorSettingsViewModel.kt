package com.github.jankoran90.showlyfin.feature.discover.curator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.CuratorKind
import com.github.jankoran90.showlyfin.core.domain.CuratorPrefs
import com.github.jankoran90.showlyfin.core.domain.CuratorSurprise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AUTEUR (SHW-91) Fáze C1 — VM bloku „Kurátor" v Nastavení (SDÍLENÝ TV i telefon → parita a jeden
 * zdroj pravdy). Čte/zapisuje [CuratorPrefs] per profil přes [ProfileRepository] (activeConfig ⊕
 * updateConfig) → volby se synchronizují TV↔telefon (vzor SUBWEAVE `subtitleStyle`).
 *
 * Slider „míra objevování" jde přes debounce (tažení generuje mnoho změn a každý updateConfig pushuje
 * na backend) + optimistický lokální update, ať UI reaguje okamžitě.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CuratorSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _prefs = MutableStateFlow(CuratorPrefs.DEFAULT)
    val prefs: StateFlow<CuratorPrefs> = _prefs.asStateFlow()

    private val discoveryWrites = MutableSharedFlow<Float>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        profileRepository.activeConfig
            .map { it.curator ?: CuratorPrefs.DEFAULT }
            .distinctUntilChanged()
            .onEach { _prefs.value = it }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            discoveryWrites.debounce(DEBOUNCE_MS).collect { d -> persist { it.copy(discovery = d) } }
        }
    }

    private fun persist(transform: (CuratorPrefs) -> CuratorPrefs) {
        val id = profileRepository.activeProfile.value?.id ?: return
        viewModelScope.launch {
            profileRepository.updateConfig(id) { cfg ->
                cfg.copy(curator = transform(cfg.curator ?: CuratorPrefs.DEFAULT))
            }
        }
    }

    fun setEnabled(v: Boolean) = persist { it.copy(enabled = v) }
    fun setKind(v: CuratorKind) = persist { it.copy(kind = v) }
    fun setMood(v: String) = persist { it.copy(mood = v) }
    fun setGenres(v: Set<String>) = persist { it.copy(genres = v) }
    fun setModel(v: String?) = persist { it.copy(model = v?.trim()?.takeIf { s -> s.isNotEmpty() }) }

    /** Slider osy jistoty↔překvapení — optimistický lokální update + debounced write-through. */
    fun setDiscovery(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _prefs.value = _prefs.value.copy(discovery = clamped)
        discoveryWrites.tryEmit(clamped)
    }

    fun setSurprise(mode: CuratorSurprise, enabled: Boolean) = persist {
        it.copy(surprise = if (enabled) it.surprise + mode else it.surprise - mode)
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
