package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRESET (SHW-65): konfigurace POŘADÍ v Poslechu (společné pro zařízení) — pro sekci v Nastavení.
 * (1) Audioknihy vs Podcasty (co první), (2) ruční pořadí knihoven pod oběma (Děti/Dospělí…).
 * Ukládá se do [AbsPreferences]; ListenScreen ho převezme při dalším vstupu (reloadOrderPrefs).
 */
@HiltViewModel
class ListenOrderViewModel @Inject constructor(
    private val prefs: AbsPreferences,
    private val repo: AbsRepository,
) : ViewModel() {

    data class LibRow(val id: String, val name: String)

    data class UiState(
        val isLoading: Boolean = true,
        val notConfigured: Boolean = false,
        val booksFirst: Boolean = true,
        val audiobookLibraries: List<LibRow> = emptyList(),
        val podcastLibraries: List<LibRow> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(booksFirst = prefs.listenBooksFirst, notConfigured = !repo.isConfigured) }
        if (!repo.isConfigured) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val books = runCatching { repo.getAudiobookLibraries() }.getOrDefault(emptyList())
            val pods = runCatching { repo.getPodcastLibraries() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    isLoading = false,
                    audiobookLibraries = orderLibs(books, prefs.audiobookLibraryOrder),
                    podcastLibraries = orderLibs(pods, prefs.podcastLibraryOrder),
                )
            }
        }
    }

    private fun orderLibs(libs: List<AbsLibrary>, order: List<String>): List<LibRow> {
        val rows = libs.map { LibRow(it.id, it.name) }
        if (order.isEmpty()) return rows
        val idx = order.withIndex().associate { (i, id) -> id to i }
        return rows.sortedBy { idx[it.id] ?: Int.MAX_VALUE }
    }

    fun setBooksFirst(value: Boolean) {
        prefs.listenBooksFirst = value
        _state.update { it.copy(booksFirst = value) }
    }

    fun moveAudiobook(id: String, up: Boolean) =
        move(_state.value.audiobookLibraries, id, up)?.let { newList ->
            prefs.audiobookLibraryOrder = newList.map { it.id }
            _state.update { it.copy(audiobookLibraries = newList) }
        }

    fun movePodcast(id: String, up: Boolean) =
        move(_state.value.podcastLibraries, id, up)?.let { newList ->
            prefs.podcastLibraryOrder = newList.map { it.id }
            _state.update { it.copy(podcastLibraries = newList) }
        }

    private fun move(list: List<LibRow>, id: String, up: Boolean): List<LibRow>? {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= list.size) return null
        return list.toMutableList().apply { val t = this[i]; this[i] = this[j]; this[j] = t }
    }
}
