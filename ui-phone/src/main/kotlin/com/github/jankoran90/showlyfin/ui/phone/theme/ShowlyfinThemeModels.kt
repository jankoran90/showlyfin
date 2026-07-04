package com.github.jankoran90.showlyfin.ui.phone.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle

/**
 * CHORUS Osa 3 (kánon motivu z hubme, user 2026-07-03) — model vzhledu Showlyfinu.
 *
 * Dvě NEZÁVISLÉ osy jako v hubme:
 *  - **Podklad** ([Background]) = pozadí + charakter ploch. AMOLED = pozadí VŽDY čistě černé,
 *    charakter ploch (tmavost/tónování) se plynule ladí posuvníky ([SurfaceTuning]).
 *  - **Skin** ([ShowlyfinSkin]) = akcent (seed barva → celé Material 3 schéma přes MaterialKolor),
 *    NEBO vlastní akcent ([ThemePrefsState.customSeed]). Skin NIKDY nebarví pozadí.
 */
enum class Background(val id: String, val displayName: String, val subtitle: String) {
    System("system", "Podle systému", "Světlý/tmavý dle nastavení telefonu"),
    Light("light", "Světlá", "Světlé pozadí (opak AMOLED)"),
    Amoled("amoled", "AMOLED černá", "Plně černé pozadí (úspora na OLED)");

    /** Tmavá varianta (černé pozadí). [System] se sem neřadí — vyhodnocuje se dřív. */
    val isDark: Boolean get() = this == Amoled

    companion object {
        fun fromId(id: String?): Background = when (id) {
            // Legacy tmavé odstíny → sjednoceno na AMOLED černou (charakter ploch teď řeší posuvníky).
            "softdark", "dark", "amoled_dark", "amoled_colorful" -> Amoled
            "light_colorful" -> Light
            else -> entries.firstOrNull { it.id == id } ?: Amoled
        }
    }
}

/**
 * Předdefinované skiny (akcenty). Seed + styl palety (+ kontrast) → MaterialKolor vygeneruje kompletní
 * Material 3 schéma. Default = oranžová (Sunset) = kánon fleetu (Plan UNISON, preference usera).
 */
enum class ShowlyfinSkin(
    val id: String,
    val displayName: String,
    val seed: Color,
    val style: PaletteStyle,
    val contrastLevel: Double = 0.0,
) {
    // — Teplé (default rodina) —
    Sunset("sunset", "Západ slunce", Color(0xFFC75C2E), PaletteStyle.Vibrant),
    Amber("amber", "Jantar", Color(0xFFE0922F), PaletteStyle.Expressive),
    Crimson("crimson", "Karmín", Color(0xFFC1294A), PaletteStyle.Vibrant),

    // — Barevné —
    Ocean("ocean", "Oceán", Color(0xFF1C5D99), PaletteStyle.Expressive),
    Forest("forest", "Les", Color(0xFF2A9D8F), PaletteStyle.Expressive),
    Teal("teal", "Tyrkys", Color(0xFF008C8C), PaletteStyle.Expressive),
    Violet("violet", "Fialová", Color(0xFF6750A4), PaletteStyle.Expressive),
    Indigo("indigo", "Indigo", Color(0xFF4B4BCE), PaletteStyle.Expressive),
    Rose("rose", "Růžová", Color(0xFFB5366B), PaletteStyle.Expressive),
    Graphite("graphite", "Grafit", Color(0xFF55606E), PaletteStyle.Neutral),

    // — Kombinované —
    Contrast("contrast", "Kontrastní", Color(0xFFFF6A00), PaletteStyle.Vibrant, contrastLevel = 1.0),
    Mono("mono", "Černobílá", Color(0xFF7C7C7C), PaletteStyle.Monochrome);

    companion object {
        val DEFAULT = Sunset
        fun fromId(id: String?): ShowlyfinSkin = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Dynamické ladění ploch (karty/bloky) nad pozadím — plynule laditelné posuvníky ve Vzhledu (0f..1f).
 * Pozadí ([Background]) tím NENÍ dotčené (u AMOLED zůstává vždy čistě černé).
 *  - **tint** = kolik barvy skinu prostupuje do ploch (0 = neutrální šedé, 1 = plně tónované).
 *  - **lightness** = jak zvednuté jsou plochy nad pozadím (0 = splývají s černou, 1 = tmavě šedé).
 *  - **accent** = síla akcentu (0 = jemný, 1 = výrazný — přičítá se ke contrastLevel palety).
 */
object SurfaceTuning {
    const val DEFAULT_TINT = 0f
    const val DEFAULT_LIGHTNESS = 0.7f
    const val DEFAULT_ACCENT = 0f
    fun clamp(v: Float): Float = v.coerceIn(0f, 1f)
}

/**
 * Pokročilé ladění kontejnerů/akcentů a čitelnosti (0f..1f).
 *  - **containerTint** = tónování neutrálních kontejnerů (secondary/tertiary — chipy, avatary) barvou.
 *  - **textContrast** = kontrast textu na plochách (0 = jemný, 1 = ostrý; 0.5 = výchozí).
 *  - **accentChroma** = sytost akcentu (1 = plná barva skinu, 0 = odbarveno do šedé).
 */
object AccentTuning {
    const val DEFAULT_CONTAINER_TINT = 0f
    const val DEFAULT_TEXT_CONTRAST = 0.5f
    const val DEFAULT_CHROMA = 1.0f
    fun clamp(v: Float): Float = v.coerceIn(0f, 1f)
}

/** Kompletní stav motivu Showlyfinu (bez písma — to drží [com.github.jankoran90.showlyfin.ui.phone.FontPrefsViewModel]). */
data class ThemePrefsState(
    val background: Background = Background.Amoled,
    val skin: ShowlyfinSkin = ShowlyfinSkin.DEFAULT,
    val useCustomAccent: Boolean = false,
    val customSeed: Long = 0xFFC75C2E,
    // Dynamické ladění ploch (posuvníky Vzhled).
    val surfaceTint: Float = SurfaceTuning.DEFAULT_TINT,
    val surfaceLightness: Float = SurfaceTuning.DEFAULT_LIGHTNESS,
    val accentStrength: Float = SurfaceTuning.DEFAULT_ACCENT,
    // Pokročilé ladění (kontejnery/kontrast/sytost).
    val containerTint: Float = AccentTuning.DEFAULT_CONTAINER_TINT,
    val textContrast: Float = AccentTuning.DEFAULT_TEXT_CONTRAST,
    val accentChroma: Float = AccentTuning.DEFAULT_CHROMA,
)
