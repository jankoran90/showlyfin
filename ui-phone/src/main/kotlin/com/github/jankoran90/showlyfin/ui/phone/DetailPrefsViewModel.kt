package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named

data class DetailPrefsState(
    val rich: Boolean = true,
    val showCollections: Boolean = true,
    val showDirector: Boolean = true,
    val showStudio: Boolean = true,
)

/** Nastavení detailu obsahu z knihovny (jednoduchý vs bohatý + volitelné sekce). */
@HiltViewModel
class DetailPrefsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<DetailPrefsState> = _state.asStateFlow()

    private fun read() = DetailPrefsState(
        rich = prefs.getBoolean(KEY_RICH, true),
        showCollections = prefs.getBoolean(KEY_COLLECTIONS, true),
        showDirector = prefs.getBoolean(KEY_DIRECTOR, true),
        showStudio = prefs.getBoolean(KEY_STUDIO, true),
    )

    fun setRich(value: Boolean) = put(KEY_RICH) { _state.update { s -> s.copy(rich = value) }; value }
    fun setCollections(value: Boolean) = put(KEY_COLLECTIONS) { _state.update { s -> s.copy(showCollections = value) }; value }
    fun setDirector(value: Boolean) = put(KEY_DIRECTOR) { _state.update { s -> s.copy(showDirector = value) }; value }
    fun setStudio(value: Boolean) = put(KEY_STUDIO) { _state.update { s -> s.copy(showStudio = value) }; value }

    private inline fun put(key: String, block: () -> Boolean) {
        prefs.edit().putBoolean(key, block()).apply()
    }

    companion object {
        private const val KEY_RICH = "detail_mode_rich"
        private const val KEY_COLLECTIONS = "detail_show_collections"
        private const val KEY_DIRECTOR = "detail_show_director"
        private const val KEY_STUDIO = "detail_show_studio"
    }
}
