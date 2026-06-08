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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ShowlyfinDarkColors = darkColorScheme(
    primary = Color(0xFFFF7A1A),
    onPrimary = Color(0xFF1A0900),
    primaryContainer = Color(0xFF8A3C00),
    onPrimaryContainer = Color(0xFFFFE4D2),
    secondary = Color(0xFFED1C24),
    onSecondary = Color.White,
    tertiary = Color(0xFFFFB088),
    background = Color(0xFF07071A),
    onBackground = Color(0xFFF2F2F8),
    surface = Color(0xFF13132B),
    onSurface = Color(0xFFF2F2F8),
    surfaceVariant = Color(0xFF1E1E3A),
    onSurfaceVariant = Color(0xFFBFBFD6),
    outline = Color(0xFF4A4A66),
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

@Composable
fun ShowlyfinPhoneTheme(
    useDarkTheme: Boolean = true,
    useDynamicColor: Boolean = false,
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShowlyfinTypography,
        content = content,
    )
}
