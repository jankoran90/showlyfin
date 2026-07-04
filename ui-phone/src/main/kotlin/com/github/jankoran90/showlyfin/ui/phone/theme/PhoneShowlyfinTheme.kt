package com.github.jankoran90.showlyfin.ui.phone.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.github.jankoran90.showlyfin.ui.phone.R

// UNISON/CHORUS kánon — AMOLED čistě černé pozadí + oranžový akcent (default). Barvy se generují
// dynamicky přes MaterialKolor ze skin seedu; charakter ploch se ladí posuvníky ([withTunedScheme]).
// Feature kód NIKDY nedeklaruje Color(0x…), jen čte z colorScheme.

val ShowlyfinTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

// CHORUS Osa 3 (kánon Písmo): Newsreader (Production Type, OFL) = patková volba fleetu, jako v hubme.
private val NewsreaderFamily: FontFamily = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.newsreader_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.newsreader_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.newsreader_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

private fun TextStyle.tuned(family: FontFamily?, scale: Float): TextStyle = copy(
    fontFamily = family ?: fontFamily,
    fontSize = if (fontSize != TextUnit.Unspecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
)

/**
 * Postaví typografii dle voleb Písma (kánon): [serif] = Newsreader vs systémové bezpatkové;
 * [headingOnly] = font jen na nadpisy; [scale] = měřítko velikosti (0.85–1.30).
 */
private fun buildShowlyfinTypography(serif: Boolean, headingOnly: Boolean, scale: Float): Typography {
    val b = ShowlyfinTypography
    val family = if (serif) NewsreaderFamily else null
    val heading = family
    val body = if (headingOnly) null else family
    return b.copy(
        displayLarge = b.displayLarge.tuned(heading, scale),
        displayMedium = b.displayMedium.tuned(heading, scale),
        displaySmall = b.displaySmall.tuned(heading, scale),
        headlineLarge = b.headlineLarge.tuned(heading, scale),
        headlineMedium = b.headlineMedium.tuned(heading, scale),
        headlineSmall = b.headlineSmall.tuned(heading, scale),
        titleLarge = b.titleLarge.tuned(heading, scale),
        titleMedium = b.titleMedium.tuned(body, scale),
        titleSmall = b.titleSmall.tuned(body, scale),
        bodyLarge = b.bodyLarge.tuned(body, scale),
        bodyMedium = b.bodyMedium.tuned(body, scale),
        bodySmall = b.bodySmall.tuned(body, scale),
        labelLarge = b.labelLarge.tuned(body, scale),
        labelMedium = b.labelMedium.tuned(body, scale),
        labelSmall = b.labelSmall.tuned(body, scale),
    )
}

/** Odbarví barvu k šedé o dané [chroma] (1 = plná barva, 0 = šedá dle jasu). Pro posuvník „Sytost". */
private fun Color.desaturate(chroma: Float): Color {
    val c = chroma.coerceIn(0f, 1f)
    if (c >= 1f) return this
    val g = luminance().coerceIn(0f, 1f)
    return lerp(Color(g, g, g), this, c)
}

/**
 * Sestaví plochy + kontejnery dle [background] + dynamických posuvníků (KÁNON CHORUS, port z hubme).
 * Pozadí AMOLED = vždy čistě černé (posuvníky ho nemění). Věrně sjednoceno s hubme `withTunedScheme`.
 */
private fun ColorScheme.withTunedScheme(
    background: Background,
    surfaceTint: Float,
    surfaceLightness: Float,
    containerTint: Float,
    textContrast: Float,
    accentChroma: Float,
): ColorScheme {
    val t = SurfaceTuning.clamp(surfaceTint)
    val l = SurfaceTuning.clamp(surfaceLightness)
    val bt = AccentTuning.clamp(containerTint)
    val tc = AccentTuning.clamp(textContrast)
    val ch = AccentTuning.clamp(accentChroma)
    return if (background == Background.Light) {
        val root = Color(0xFFFDFDFD)
        val neutralCont = lerp(Color(0xFFFFFFFF), Color(0xFFDFDFDF), l)
        val onHi = lerp(Color(0xFF3A3A3A), Color(0xFF000000), tc)
        val onVar = lerp(Color(0xFF6E6E6E), Color(0xFF2C2C2C), tc)
        val onCont = lerp(Color(0xFF505050), Color(0xFF000000), tc)
        copy(
            background = root,
            surface = root,
            surfaceDim = lerp(Color(0xFFFFFFFF), Color(0xFFE6E6E6), l),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = lerp(lerp(Color(0xFFFFFFFF), Color(0xFFF0F0F0), l), surfaceContainerLow, t),
            surfaceContainer = lerp(lerp(Color(0xFFFFFFFF), Color(0xFFE9E9E9), l), surfaceContainer, t),
            surfaceContainerHigh = lerp(lerp(Color(0xFFFFFFFF), Color(0xFFE1E1E1), l), surfaceContainerHigh, t),
            surfaceContainerHighest = lerp(lerp(Color(0xFFFFFFFF), Color(0xFFD9D9D9), l), surfaceContainerHighest, t),
            surfaceVariant = lerp(lerp(Color(0xFFFFFFFF), Color(0xFFE4E4E4), l), surfaceVariant, t),
            secondaryContainer = lerp(neutralCont, secondaryContainer.desaturate(ch), bt),
            tertiaryContainer = lerp(neutralCont, tertiaryContainer.desaturate(ch), bt),
            primaryContainer = primaryContainer.desaturate(ch),
            primary = primary.desaturate(ch),
            secondary = secondary.desaturate(ch),
            tertiary = tertiary.desaturate(ch),
            onBackground = onHi,
            onSurface = onHi,
            onSurfaceVariant = onVar,
            onSecondaryContainer = onCont,
            onTertiaryContainer = onCont,
            onPrimaryContainer = onCont,
            outline = Color(0xFF767676),
            outlineVariant = Color(0xFFCCCCCC),
        )
    } else {
        // AMOLED: pozadí VŽDY čistě černé. Default (t=0,l=0.7,bt=0,ch=1,tc=0.5) = tmavě šedé plochy
        // na černé (kánon „AMOLED tmavá"), neutrální kontejnery, akcentní primary.
        val neutralCont = lerp(Color(0xFF000000), Color(0xFF2E2E2E), l)
        val onHi = lerp(Color(0xFFCFCFCF), Color(0xFFFFFFFF), tc)
        val onVar = lerp(Color(0xFF9E9E9E), Color(0xFFD8D8D8), tc)
        val onCont = lerp(Color(0xFFC8C8C8), Color(0xFFFFFFFF), tc)
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = lerp(lerp(Color(0xFF000000), Color(0xFF383838), l), surfaceBright, t),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = lerp(lerp(Color(0xFF000000), Color(0xFF1A1A1A), l), surfaceContainerLow, t),
            surfaceContainer = lerp(lerp(Color(0xFF000000), Color(0xFF1E1E1E), l), surfaceContainer, t),
            surfaceContainerHigh = lerp(lerp(Color(0xFF000000), Color(0xFF282828), l), surfaceContainerHigh, t),
            surfaceContainerHighest = lerp(lerp(Color(0xFF000000), Color(0xFF333333), l), surfaceContainerHighest, t),
            surfaceVariant = lerp(lerp(Color(0xFF000000), Color(0xFF2A2A2A), l), surfaceVariant, t),
            secondaryContainer = lerp(neutralCont, secondaryContainer.desaturate(ch), bt),
            tertiaryContainer = lerp(neutralCont, tertiaryContainer.desaturate(ch), bt),
            primaryContainer = primaryContainer.desaturate(ch),
            primary = primary.desaturate(ch),
            secondary = secondary.desaturate(ch),
            tertiary = tertiary.desaturate(ch),
            onBackground = onHi,
            onSurface = onHi,
            onSurfaceVariant = onVar,
            onSecondaryContainer = onCont,
            onTertiaryContainer = onCont,
            onPrimaryContainer = onCont,
            outline = Color(0xFF8A8A8A),
            outlineVariant = lerp(Color(0xFF000000), Color(0xFF3A3A3A), l),
        )
    }
}

@Composable
fun ShowlyfinPhoneTheme(
    themeState: ThemePrefsState = ThemePrefsState(),
    serifFont: Boolean = false,
    headingOnly: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    // „Podle systému": tmavý → AMOLED černá, světlý → Světlá.
    val resolved = when (themeState.background) {
        Background.System -> if (systemDark) Background.Amoled else Background.Light
        else -> themeState.background
    }
    val isDark = resolved.isDark

    // Akcent: vlastní barva má přednost před presetem skinu. isAmoled=false → MaterialKolor generuje
    // TÓNOVANÉ plochy, mezi nimi a neutrálními pak mícháme dle surfaceTint. Síla akcentu = +contrast.
    val contrast = ((if (themeState.useCustomAccent) 0.0 else themeState.skin.contrastLevel) +
        themeState.accentStrength.toDouble()).coerceIn(-1.0, 1.0)
    val accentScheme = dynamicColorScheme(
        seedColor = if (themeState.useCustomAccent) Color(themeState.customSeed) else themeState.skin.seed,
        isDark = isDark,
        isAmoled = false,
        style = if (themeState.useCustomAccent) PaletteStyle.Expressive else themeState.skin.style,
        contrastLevel = contrast,
    )

    val colorScheme = accentScheme.withTunedScheme(
        background = resolved,
        surfaceTint = themeState.surfaceTint,
        surfaceLightness = themeState.surfaceLightness,
        containerTint = themeState.containerTint,
        textContrast = themeState.textContrast,
        accentChroma = themeState.accentChroma,
    )

    val typography = remember(serifFont, headingOnly, fontScale) {
        buildShowlyfinTypography(serifFont, headingOnly, fontScale)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
