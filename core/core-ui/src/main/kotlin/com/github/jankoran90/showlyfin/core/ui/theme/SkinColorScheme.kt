package com.github.jankoran90.showlyfin.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.domain.theme.DarkMode
import com.github.jankoran90.showlyfin.core.domain.theme.ShowlyfinSkin
import com.github.jankoran90.showlyfin.core.domain.theme.SkinPaletteStyle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/** Převod doménového stylu na MaterialKolor `PaletteStyle` (core-domain je bez Compose). */
fun SkinPaletteStyle.toPaletteStyle(): PaletteStyle = when (this) {
    SkinPaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
    SkinPaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    SkinPaletteStyle.NEUTRAL -> PaletteStyle.Neutral
    SkinPaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    SkinPaletteStyle.RAINBOW -> PaletteStyle.Rainbow
    SkinPaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
    SkinPaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
}

/** Vyhodnotí, zda se má použít tmavý režim, dle [ShowlyfinSkin.darkMode] + systému. */
@Composable
fun shouldUseDarkTheme(skin: ShowlyfinSkin): Boolean = when (skin.darkMode) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

/**
 * Sdílený engine schématu (Plan PRISM Fáze 1) — krmí phone i TV theme wrappery.
 * Material You (dynamicColor, API 31+) přebije seed systémovou paletou; jinak MaterialKolor
 * vygeneruje schéma ze seed barvy + stylu palety (Expressive tonalita), `isAmoled` → čistě
 * černé pozadí, `contrastLevel` z [ShowlyfinSkin.contrast].
 */
@Composable
fun rememberSkinColorScheme(skin: ShowlyfinSkin): ColorScheme {
    val dark = shouldUseDarkTheme(skin)
    val context = LocalContext.current
    return when {
        skin.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> dynamicColorScheme(
            seedColor = Color(skin.seedColor),
            isDark = dark,
            isAmoled = skin.amoled,
            style = skin.style.toPaletteStyle(),
            contrastLevel = skin.contrast.toDouble(),
        )
    }
}
