package com.github.jankoran90.showlyfin.ui.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val Background = Color(0xFF07071A)
private val Surface = Color(0xFF13132B)
private val SurfaceVariant = Color(0xFF1E1E3A)
private val Primary = Color(0xFFFF7A1A)
private val PrimaryContainer = Color(0xFF8A3C00)
private val Secondary = Color(0xFFED1C24)
private val OnPrimary = Color(0xFF1A0900)
private val OnSurface = Color(0xFFF5F5FA)
private val OnSurfaceVariant = Color(0xFFBFBFD6)

@OptIn(ExperimentalTvMaterial3Api::class)
private val ShowlyfinTvColors: ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = Secondary,
    onSecondary = Color.White,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    border = Color.White.copy(alpha = 0.12f),
    borderVariant = Color.White.copy(alpha = 0.06f),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShowlyfinTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShowlyfinTvColors,
        typography = ShowlyfinTvTypography,
        content = content,
    )
}
