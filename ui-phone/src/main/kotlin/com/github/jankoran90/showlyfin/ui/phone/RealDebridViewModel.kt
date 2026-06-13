package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.WorkingSource
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderRdSavedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * Plan LEDGER (SHW-43) — správa RealDebridu z Nastavení.
 *
 * Vlastní malé VM kategorického bloku „RealDebrid" (CLAUDE.md „Nastavení = kategorické bloky"),
 * aby `SettingsViewModel`/`SettingsScreen` (oba už velké) nerostly. Drží dva seznamy:
 *  - **na RD účtu** (`_rd_fetch_saved` přes backend) — vše, co reálně leží na RealDebridu,
 *  - **zapamatované zdroje** (`WorkingSourceStore`) — co si appka pinuje jako „naposledy fungovalo".
 * Umí ruční smazání jednotlivé položky, hromadné smazání všeho z RD a zapomenutí pinu
 * (zapomenutí navíc smaže jeho torrent z RD účtu, aby se účet nehromadil).
 */
data class RealDebridUiState(
    val loading: Boolean = false,
    val configured: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val rdItems: List<UploaderRdSavedItem> = emptyList(),
    val remembered: List<WorkingSource> = emptyList(),
    /** hashe/identity položek, na kterých právě běží mazání (pro spinner/disable). */
    val busy: Set<String> = emptySet(),
)

@HiltViewModel
class RealDebridViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    private val workingSourceStore: WorkingSourceStore,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val baseUrl get() = prefs.getString("uploader_base_url", "") ?: ""
    private val cookie get() = prefs.getString("uploader_session_cookie", "") ?: ""

    private val _uiState = MutableStateFlow(RealDebridUiState())
    val uiState: StateFlow<RealDebridUiState> = _uiState.asStateFlow()

    /** Načti oba seznamy. [force] obejde 60s cache backendu (po smazání). */
    fun load(force: Boolean = false) {
        val remembered = workingSourceStore.getAll()
        if (baseUrl.isBlank()) {
            _uiState.update { it.copy(configured = false, loading = false, remembered = remembered, rdItems = emptyList()) }
            return
        }
        _uiState.update { it.copy(loading = true, error = null, configured = true, remembered = remembered) }
        viewModelScope.launch {
            runCatching { uploaderDs.rdList(baseUrl, cookie, force) }
                .onSuccess { items -> _uiState.update { it.copy(loading = false, rdItems = items, error = null) } }
                .onFailure { e ->
                    Timber.w(e, "[LEDGER] rdList selhal")
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Nepodařilo se načíst RealDebrid") }
                }
        }
    }

    /** Smaž jednu položku z RD účtu (podle hashe). */
    fun deleteRd(hash: String) {
        val h = hash.trim().lowercase()
        if (h.isBlank() || baseUrl.isBlank()) return
        _uiState.update { it.copy(busy = it.busy + h, message = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.rdDelete(baseUrl, cookie, listOf(h)) }
                .onSuccess { n ->
                    _uiState.update {
                        it.copy(
                            busy = it.busy - h,
                            rdItems = it.rdItems.filterNot { item -> item.hash.equals(h, ignoreCase = true) },
                            message = if (n > 0) "Smazáno z RealDebridu" else "Nic ke smazání",
                        )
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "[LEDGER] deleteRd selhal hash=$h")
                    _uiState.update { it.copy(busy = it.busy - h, error = e.message ?: "Mazání selhalo") }
                }
        }
    }

    /** Hromadně smaž VŠE, co je na RD účtu. */
    fun deleteAllRd() {
        val hashes = _uiState.value.rdItems.map { it.hash.lowercase() }.filter { it.isNotBlank() }.distinct()
        if (hashes.isEmpty() || baseUrl.isBlank()) return
        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            runCatching { uploaderDs.rdDelete(baseUrl, cookie, hashes) }
                .onSuccess { n ->
                    _uiState.update { it.copy(loading = false, message = "Smazáno z RealDebridu: $n") }
                    load(force = true)
                }
                .onFailure { e ->
                    Timber.w(e, "[LEDGER] deleteAllRd selhal")
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Hromadné mazání selhalo") }
                }
        }
    }

    /**
     * Zapomeň zapamatovaný zdroj a smaž jeho torrent z RD účtu.
     * [alsoDeleteFromRd] = false → jen zruší pin (torrent na RD zůstane).
     */
    fun forgetRemembered(ws: WorkingSource, alsoDeleteFromRd: Boolean = true) {
        val imdb = ws.imdb.takeIf { it.isNotBlank() }
        val tmdb = ws.tmdb.takeIf { it > 0L }
        workingSourceStore.clear(imdb, tmdb)
        val hash = workingSourceStore.rdHashOf(ws.stream)
        viewModelScope.launch {
            if (alsoDeleteFromRd && hash != null && baseUrl.isNotBlank()) {
                runCatching { uploaderDs.rdDelete(baseUrl, cookie, listOf(hash)) }
                    .onFailure { e -> Timber.w(e, "[LEDGER] forget: rdDelete selhal hash=$hash") }
            }
            _uiState.update {
                it.copy(
                    remembered = workingSourceStore.getAll(),
                    rdItems = if (hash != null) it.rdItems.filterNot { item -> item.hash.equals(hash, ignoreCase = true) } else it.rdItems,
                    message = "Zapomenuto",
                )
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, error = null) }
}
