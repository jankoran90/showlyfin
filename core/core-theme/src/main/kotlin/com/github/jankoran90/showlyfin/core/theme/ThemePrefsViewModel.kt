package com.github.jankoran90.showlyfin.core.theme

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named

/**
 * CHORUS Osa 3 — perzistence motivu (pozadí + skin + dynamické posuvníky). Čte se v kořeni
 * [ShowlyfinApp] a předává do [com.github.jankoran90.showlyfin.core.theme.ShowlyfinPhoneTheme];
 * settery volají posuvníky/přepínače v Nastavení → Vzhled. Activity-scoped (jako FontPrefs) → stejná
 * instance v kořeni i v sekci → změna se projeví ŽIVĚ (i v náhledu).
 */
@HiltViewModel
class ThemePrefsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<ThemePrefsState> = _state.asStateFlow()

    private fun read() = ThemePrefsState(
        background = Background.fromId(prefs.getString(KEY_BACKGROUND, null)),
        skin = ShowlyfinSkin.fromId(prefs.getString(KEY_SKIN, null)),
        useCustomAccent = prefs.getBoolean(KEY_USE_CUSTOM, false),
        customSeed = prefs.getLong(KEY_CUSTOM_SEED, 0xFFC75C2E),
        surfaceTint = SurfaceTuning.clamp(prefs.getFloat(KEY_SURFACE_TINT, SurfaceTuning.DEFAULT_TINT)),
        surfaceLightness = SurfaceTuning.clamp(prefs.getFloat(KEY_SURFACE_LIGHT, SurfaceTuning.DEFAULT_LIGHTNESS)),
        accentStrength = SurfaceTuning.clamp(prefs.getFloat(KEY_ACCENT_STRENGTH, SurfaceTuning.DEFAULT_ACCENT)),
        containerTint = AccentTuning.clamp(prefs.getFloat(KEY_CONTAINER_TINT, AccentTuning.DEFAULT_CONTAINER_TINT)),
        textContrast = AccentTuning.clamp(prefs.getFloat(KEY_TEXT_CONTRAST, AccentTuning.DEFAULT_TEXT_CONTRAST)),
        accentChroma = AccentTuning.clamp(prefs.getFloat(KEY_ACCENT_CHROMA, AccentTuning.DEFAULT_CHROMA)),
    )

    fun setBackground(v: Background) = put { it.copy(background = v) }.also { prefs.edit().putString(KEY_BACKGROUND, v.id).apply() }
    fun setSkin(v: ShowlyfinSkin) = put { it.copy(skin = v, useCustomAccent = false) }.also {
        prefs.edit().putString(KEY_SKIN, v.id).putBoolean(KEY_USE_CUSTOM, false).apply()
    }
    fun setCustomSeed(argb: Long) = put { it.copy(customSeed = argb, useCustomAccent = true) }.also {
        prefs.edit().putLong(KEY_CUSTOM_SEED, argb).putBoolean(KEY_USE_CUSTOM, true).apply()
    }
    fun setSurfaceTint(v: Float) = put { it.copy(surfaceTint = SurfaceTuning.clamp(v)) }.also { prefs.edit().putFloat(KEY_SURFACE_TINT, SurfaceTuning.clamp(v)).apply() }
    fun setSurfaceLightness(v: Float) = put { it.copy(surfaceLightness = SurfaceTuning.clamp(v)) }.also { prefs.edit().putFloat(KEY_SURFACE_LIGHT, SurfaceTuning.clamp(v)).apply() }
    fun setAccentStrength(v: Float) = put { it.copy(accentStrength = SurfaceTuning.clamp(v)) }.also { prefs.edit().putFloat(KEY_ACCENT_STRENGTH, SurfaceTuning.clamp(v)).apply() }
    fun setContainerTint(v: Float) = put { it.copy(containerTint = AccentTuning.clamp(v)) }.also { prefs.edit().putFloat(KEY_CONTAINER_TINT, AccentTuning.clamp(v)).apply() }
    fun setTextContrast(v: Float) = put { it.copy(textContrast = AccentTuning.clamp(v)) }.also { prefs.edit().putFloat(KEY_TEXT_CONTRAST, AccentTuning.clamp(v)).apply() }
    fun setAccentChroma(v: Float) = put { it.copy(accentChroma = AccentTuning.clamp(v)) }.also { prefs.edit().putFloat(KEY_ACCENT_CHROMA, AccentTuning.clamp(v)).apply() }

    private inline fun put(transform: (ThemePrefsState) -> ThemePrefsState) = _state.update(transform)

    companion object {
        private const val KEY_BACKGROUND = "theme_background"
        private const val KEY_SKIN = "theme_skin"
        private const val KEY_USE_CUSTOM = "theme_use_custom_accent"
        private const val KEY_CUSTOM_SEED = "theme_custom_seed"
        private const val KEY_SURFACE_TINT = "theme_surface_tint"
        private const val KEY_SURFACE_LIGHT = "theme_surface_lightness"
        private const val KEY_ACCENT_STRENGTH = "theme_accent_strength"
        private const val KEY_CONTAINER_TINT = "theme_container_tint"
        private const val KEY_TEXT_CONTRAST = "theme_text_contrast"
        private const val KEY_ACCENT_CHROMA = "theme_accent_chroma"
    }
}
