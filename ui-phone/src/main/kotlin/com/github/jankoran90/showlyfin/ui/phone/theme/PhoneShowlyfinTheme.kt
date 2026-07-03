package com.github.jankoran90.showlyfin.ui.phone.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.github.jankoran90.showlyfin.ui.phone.R

// UNISON kanon (sjednoceno s claude-voice 2026-06-14, „velmi čistý") — AMOLED čistě černá base +
// oranžový akcent (default), neutrální šedé plochy, bílá/šedá text. ŽÁDNÁ navy/modrý nádech.
// Tohle je JEDINÝ zdroj barev appky; feature kód NIKDY nedeklaruje Color(0x…), jen čte z colorScheme.
private val Black = Color(0xFF000000)        // base pozadí (AMOLED)
private val Surface = Color(0xFF121212)      // bloky / karty
private val SurfaceHigh = Color(0xFF1E1E1E)  // zvýšené plochy (karty, top bary, nav, dialogy)
private val Orange = Color(0xFFFF7A1A)       // primární akcent
private val OrangeDim = Color(0xFFC85E12)    // ztlumený akcent / sekundární
private val OnDark = Color(0xFFEDEDED)       // primární text
private val OnDarkDim = Color(0xFF9E9E9E)    // sekundární text
private val OutlineGrey = Color(0xFF2E2E2E)  // jemné okraje / linky
private val ErrorRed = Color(0xFFFF6B5E)     // chyba

private val ShowlyfinDarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Black,
    primaryContainer = OrangeDim,
    onPrimaryContainer = OnDark,
    secondary = OrangeDim,
    onSecondary = Black,
    tertiary = Orange,
    background = Black,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnDarkDim,
    surfaceContainer = SurfaceHigh,
    surfaceContainerHigh = SurfaceHigh,
    outline = OutlineGrey,
    outlineVariant = OutlineGrey,
    error = ErrorRed,
    onError = Black,
)

private val ShowlyfinLightColors = lightColorScheme(
    primary = Color(0xFFD25A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4D2),
    secondary = Color(0xFFC41E25),
    background = Color(0xFFFAF5F0),
    surface = Color.White,
)

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
// Zabaleno jako standardní Android font resource (ui-phone `res/font/`) → spolehlivě v APK.
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
 * [headingOnly] = font jen na nadpisy (title/headline/display), tělo systémové; [scale] = měřítko
 * velikosti (0.85–1.30). Aplikuje se na VŠECHNY role (ne jen na explicitně nastavené v základu).
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

@Composable
fun ShowlyfinPhoneTheme(
    useDarkTheme: Boolean = true,
    useDynamicColor: Boolean = false,
    serifFont: Boolean = false,
    headingOnly: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (useDarkTheme || isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        useDarkTheme || isSystemInDarkTheme() -> ShowlyfinDarkColors
        else -> ShowlyfinLightColors
    }
    val typography = remember(serifFont, headingOnly, fontScale) {
        buildShowlyfinTypography(serifFont, headingOnly, fontScale)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
