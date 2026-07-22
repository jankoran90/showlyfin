package com.github.jankoran90.showlyfin.core.theme

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named

/** CHORUS Osa 3 (kánon Písmo): volba fontu appky — čte se v kořeni [ShowlyfinApp] a předává do motivu. */
data class FontPrefsState(
    val serif: Boolean = false,        // false = systémové bezpatkové, true = Newsreader (patkové)
    val headingOnly: Boolean = false,  // font jen na nadpisy (jen když serif); jinak celá app
    val scalePct: Int = 100,           // měřítko velikosti textu v %
    val uiScalePct: Int = 100,         // měřítko hustoty celého UI (density) — jen TV shell
    val gridWidthPct: Int = 100,       // COUCH DA4: globální šířka karet mřížky (všechny řady) — jen TV shell
    val gridSpacingPct: Int = 100,     // COUCH DA4: globální rozestupy karet — jen TV shell
) {
    val scale: Float get() = scalePct / 100f
    val uiScale: Float get() = uiScalePct / 100f
    val gridWidth: Float get() = gridWidthPct / 100f
    val gridSpacing: Float get() = gridSpacingPct / 100f
}

@HiltViewModel
class FontPrefsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    // COUCH per-profil — vzhled (font/grid/uiScale) je per AKTIVNÍ profil. Klíč nese prefix `p<id>_`;
    // read padá na GLOBÁLNÍ klíč (bezešvá migrace — dospělý zdědí stávající, deti stejný default).
    private val activeId: Long? get() = profileRepository.activeProfile.value?.id
    private fun keyFor(base: String): String = activeId?.let { "p${it}_$base" } ?: base

    private val _state = MutableStateFlow(read())
    val state: StateFlow<FontPrefsState> = _state.asStateFlow()

    init {
        // Přepnutí profilu → načti jeho vzhled.
        profileRepository.activeProfile
            .map { it?.id }
            .distinctUntilChanged()
            .onEach { _state.value = read() }
            .launchIn(viewModelScope)
    }

    private fun read() = FontPrefsState(
        serif = prefs.getBoolean(keyFor(KEY_SERIF), prefs.getBoolean(KEY_SERIF, false)),
        headingOnly = prefs.getBoolean(keyFor(KEY_HEADING_ONLY), prefs.getBoolean(KEY_HEADING_ONLY, false)),
        scalePct = prefs.getInt(keyFor(KEY_SCALE), prefs.getInt(KEY_SCALE, 100)),
        uiScalePct = prefs.getInt(keyFor(KEY_UI_SCALE), prefs.getInt(KEY_UI_SCALE, 100)),
        gridWidthPct = prefs.getInt(keyFor(KEY_GRID_WIDTH), prefs.getInt(KEY_GRID_WIDTH, 100)),
        gridSpacingPct = prefs.getInt(keyFor(KEY_GRID_SPACING), prefs.getInt(KEY_GRID_SPACING, 100)),
    )

    fun setSerif(value: Boolean) {
        _state.update { it.copy(serif = value) }
        prefs.edit().putBoolean(keyFor(KEY_SERIF), value).apply()
    }

    fun setHeadingOnly(value: Boolean) {
        _state.update { it.copy(headingOnly = value) }
        prefs.edit().putBoolean(keyFor(KEY_HEADING_ONLY), value).apply()
    }

    fun setScalePct(value: Int) {
        _state.update { it.copy(scalePct = value) }
        prefs.edit().putInt(keyFor(KEY_SCALE), value).apply()
    }

    fun setUiScalePct(value: Int) {
        _state.update { it.copy(uiScalePct = value) }
        prefs.edit().putInt(keyFor(KEY_UI_SCALE), value).apply()
    }

    fun setGridWidthPct(value: Int) {
        _state.update { it.copy(gridWidthPct = value) }
        prefs.edit().putInt(keyFor(KEY_GRID_WIDTH), value).apply()
    }

    fun setGridSpacingPct(value: Int) {
        _state.update { it.copy(gridSpacingPct = value) }
        prefs.edit().putInt(keyFor(KEY_GRID_SPACING), value).apply()
    }

    companion object {
        private const val KEY_SERIF = "font_serif"
        private const val KEY_HEADING_ONLY = "font_heading_only"
        private const val KEY_SCALE = "font_scale_pct"
        private const val KEY_UI_SCALE = "ui_scale_pct"
        private const val KEY_GRID_WIDTH = "grid_width_pct"
        private const val KEY_GRID_SPACING = "grid_spacing_pct"
        // Supersety starých seznamů (žádná uložená hodnota nespadne na idx 0 ve stepperu) + jemnější kroky
        // a UI scale i do menších/hustších hodnot (user 2026-07-13).
        val SCALE_OPTIONS = listOf(70, 80, 85, 90, 95, 100, 110, 115, 120, 130, 145, 160)
        val UI_SCALE_OPTIONS = listOf(50, 60, 70, 80, 85, 90, 95, 100, 110, 125, 150)
        // COUCH DA4 (user 2026-07-13 „víc voleb"): globální šířka karet mřížky + rozestupy. Širší karta = víc textu,
        // méně sloupců/položek na řádek. Rozestup nezávisle. 100 = dnešní default.
        val GRID_WIDTH_OPTIONS = listOf(70, 80, 90, 100, 110, 120, 130, 145, 160, 180)
        val GRID_SPACING_OPTIONS = listOf(50, 65, 80, 100, 125, 150, 200)
    }
}
