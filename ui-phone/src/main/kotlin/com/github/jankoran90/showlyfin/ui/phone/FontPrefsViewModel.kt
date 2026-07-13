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

/** CHORUS Osa 3 (kánon Písmo): volba fontu appky — čte se v kořeni [ShowlyfinApp] a předává do motivu. */
data class FontPrefsState(
    val serif: Boolean = false,        // false = systémové bezpatkové, true = Newsreader (patkové)
    val headingOnly: Boolean = false,  // font jen na nadpisy (jen když serif); jinak celá app
    val scalePct: Int = 100,           // měřítko velikosti textu v %
    val uiScalePct: Int = 100,         // měřítko hustoty celého UI (density) — jen TV shell
) {
    val scale: Float get() = scalePct / 100f
    val uiScale: Float get() = uiScalePct / 100f
}

@HiltViewModel
class FontPrefsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<FontPrefsState> = _state.asStateFlow()

    private fun read() = FontPrefsState(
        serif = prefs.getBoolean(KEY_SERIF, false),
        headingOnly = prefs.getBoolean(KEY_HEADING_ONLY, false),
        scalePct = prefs.getInt(KEY_SCALE, 100),
        uiScalePct = prefs.getInt(KEY_UI_SCALE, 100),
    )

    fun setSerif(value: Boolean) {
        _state.update { it.copy(serif = value) }
        prefs.edit().putBoolean(KEY_SERIF, value).apply()
    }

    fun setHeadingOnly(value: Boolean) {
        _state.update { it.copy(headingOnly = value) }
        prefs.edit().putBoolean(KEY_HEADING_ONLY, value).apply()
    }

    fun setScalePct(value: Int) {
        _state.update { it.copy(scalePct = value) }
        prefs.edit().putInt(KEY_SCALE, value).apply()
    }

    fun setUiScalePct(value: Int) {
        _state.update { it.copy(uiScalePct = value) }
        prefs.edit().putInt(KEY_UI_SCALE, value).apply()
    }

    companion object {
        private const val KEY_SERIF = "font_serif"
        private const val KEY_HEADING_ONLY = "font_heading_only"
        private const val KEY_SCALE = "font_scale_pct"
        private const val KEY_UI_SCALE = "ui_scale_pct"
        val SCALE_OPTIONS = listOf(85, 100, 115, 130, 145, 160)
        val UI_SCALE_OPTIONS = listOf(80, 90, 100, 110, 125, 150)
    }
}
