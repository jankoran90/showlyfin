package com.github.jankoran90.showlyfin.core.data.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.jankoran90.showlyfin.core.domain.theme.DarkMode
import com.github.jankoran90.showlyfin.core.domain.theme.ShowlyfinSkin
import com.github.jankoran90.showlyfin.core.domain.theme.SkinPaletteStyle
import com.github.jankoran90.showlyfin.core.domain.theme.SkinPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Perzistence vzhledu (Plan PRISM Fáze 1). Reuse sdíleného DataStore `showlyfin_prefs`
 * (poskytovaného v `CoreDataModule`), klíče prefixované `theme_` — žádná nová infra.
 * Změna se okamžitě propíše do [skin] flow → `SkinController` → recompose theme wrapperu.
 */
@Singleton
class ThemeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val SEED = longPreferencesKey("theme_seed_color")
        val STYLE = stringPreferencesKey("theme_style")
        val PRESET = stringPreferencesKey("theme_preset_id")
        val DARK_MODE = stringPreferencesKey("theme_dark_mode")
        val DYNAMIC = booleanPreferencesKey("theme_dynamic_color")
        val AMOLED = booleanPreferencesKey("theme_amoled")
        val CONTRAST = floatPreferencesKey("theme_contrast")
        val FONT_SCALE = floatPreferencesKey("theme_font_scale")
    }

    val skin: Flow<ShowlyfinSkin> = dataStore.data.map { p ->
        // presetId: klíč chybí = čistá instalace → default; klíč přítomný a prázdný = vlastní barva (null)
        val presetId = if (p.contains(Keys.PRESET)) p[Keys.PRESET]?.ifEmpty { null } else SkinPreset.DEFAULT.id
        ShowlyfinSkin(
            seedColor = p[Keys.SEED] ?: SkinPreset.DEFAULT.seedColor,
            style = p[Keys.STYLE]?.let { runCatching { SkinPaletteStyle.valueOf(it) }.getOrNull() }
                ?: SkinPreset.DEFAULT.style,
            presetId = presetId,
            darkMode = DarkMode.fromId(p[Keys.DARK_MODE]),
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            amoled = p[Keys.AMOLED] ?: false,
            contrast = p[Keys.CONTRAST] ?: 0f,
            fontScale = p[Keys.FONT_SCALE] ?: 1f,
        )
    }

    suspend fun setPreset(preset: SkinPreset) = dataStore.edit {
        it[Keys.SEED] = preset.seedColor
        it[Keys.STYLE] = preset.style.name
        it[Keys.PRESET] = preset.id
    }

    /** Vlastní seed barva z color-pickeru (Fáze 5) — preset = vlastní (prázdný id), styl Expressive. */
    suspend fun setCustomSeed(seedColor: Long) = dataStore.edit {
        it[Keys.SEED] = seedColor
        it[Keys.STYLE] = SkinPaletteStyle.EXPRESSIVE.name
        it[Keys.PRESET] = ""
    }

    suspend fun setDarkMode(mode: DarkMode) = dataStore.edit { it[Keys.DARK_MODE] = mode.id }
    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setAmoled(enabled: Boolean) = dataStore.edit { it[Keys.AMOLED] = enabled }
    suspend fun setContrast(value: Float) = dataStore.edit { it[Keys.CONTRAST] = value }
    suspend fun setFontScale(value: Float) = dataStore.edit { it[Keys.FONT_SCALE] = value }
}
