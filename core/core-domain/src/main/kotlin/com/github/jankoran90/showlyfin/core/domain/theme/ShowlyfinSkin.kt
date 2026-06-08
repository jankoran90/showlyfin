package com.github.jankoran90.showlyfin.core.domain.theme

/**
 * Vzhledový engine Showlyfinu (Plan PRISM). Čistá doménová vrstva bez závislosti na Compose —
 * mapování na MaterialKolor / Material3 `ColorScheme` je až v core-ui (`rememberSkinColorScheme`).
 */

/** Režim světlý/tmavý/dle systému. */
enum class DarkMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): DarkMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Styl tonální palety. Reprezentace nezávislá na Compose — na `com.materialkolor.PaletteStyle`
 * se převádí v core-ui (`SkinPaletteStyle.toPaletteStyle()`), aby core-domain zůstala čistá.
 */
enum class SkinPaletteStyle {
    EXPRESSIVE,
    VIBRANT,
    NEUTRAL,
    TONAL_SPOT,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
}

/**
 * Předdefinovaný skin = seed barva (ARGB jako [Long]) + styl palety. Z toho MaterialKolor
 * vygeneruje kompletní Material 3 (Expressive) schéma pro světlý i tmavý režim.
 * Přidat preset = přidat jednu položku (žádné ruční dolaďování desítek barev).
 */
enum class SkinPreset(
    val id: String,
    val displayName: String,
    val seedColor: Long,
    val style: SkinPaletteStyle,
) {
    Showlyfin("showlyfin", "Showlyfin", 0xFFFF7A1A, SkinPaletteStyle.EXPRESSIVE),
    Ocean("ocean", "Oceán", 0xFF1C5D99, SkinPaletteStyle.EXPRESSIVE),
    Forest("forest", "Les", 0xFF2A9D8F, SkinPaletteStyle.EXPRESSIVE),
    Violet("violet", "Fialová", 0xFF6750A4, SkinPaletteStyle.EXPRESSIVE),
    Sunset("sunset", "Západ slunce", 0xFFC75C2E, SkinPaletteStyle.VIBRANT),
    Rose("rose", "Růžová", 0xFFB5366B, SkinPaletteStyle.EXPRESSIVE),
    Graphite("graphite", "Grafit", 0xFF55606E, SkinPaletteStyle.NEUTRAL);

    companion object {
        val DEFAULT = Showlyfin
        fun fromId(id: String?): SkinPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Kompletní stav vzhledu aplikace (Plan PRISM). Persistován v DataStore přes `ThemeRepository`,
 * vystaven jako `StateFlow` přes `SkinController`, konzumován theme wrappery (phone i TV).
 *
 * [presetId] = id zvoleného presetu, nebo `null` pokud uživatel zadal vlastní barvu v color-pickeru
 * ([seedColor] pak nese tu vlastní barbu a [style] je EXPRESSIVE). [contrast] a [fontScale] se plně
 * propojí ve Fázi 4/5 — model je drží už teď, aby je DataStore perzistoval.
 */
data class ShowlyfinSkin(
    val seedColor: Long = SkinPreset.DEFAULT.seedColor,
    val style: SkinPaletteStyle = SkinPreset.DEFAULT.style,
    val presetId: String? = SkinPreset.DEFAULT.id,
    val darkMode: DarkMode = DarkMode.DARK,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val contrast: Float = 0f,
    val fontScale: Float = 1f,
) {
    companion object {
        val DEFAULT = ShowlyfinSkin()
    }
}
