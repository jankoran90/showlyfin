package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Téma poslechové sekce — izolovaný wrapper, jediné místo, kde se řeší vzhled „Poslechu".
 *
 * Material 3 **Expressive** (`MaterialExpressiveTheme` + `MotionScheme.expressive()`).
 * Odemčeno pinem `material3 1.5.0-alpha18` (Plan PRISM Fáze 0) — v BOM 2026.05.01 (material3 1.4.0)
 * byla Expressive API ještě `internal`. AMOLED-dark amber schéma zatím zachováno; sjednocení se
 * sdíleným skin enginem přijde v Plan PRISM Fázi 2.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListenExpressiveTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFFFC25C),
        onPrimary = Color(0xFF3A2600),
        primaryContainer = Color(0xFF5A4000),
        onPrimaryContainer = Color(0xFFFFDFA8),
        secondary = Color(0xFFD9C2A0),
        onSecondary = Color(0xFF3A2D14),
        tertiary = Color(0xFF9CC7FF),
        background = Color(0xFF0D0D1A),
        onBackground = Color(0xFFEDEDF2),
        surface = Color(0xFF14141F),
        onSurface = Color(0xFFEDEDF2),
        surfaceVariant = Color(0xFF26263A),
        onSurfaceVariant = Color(0xFFB9B9C7),
        surfaceContainer = Color(0xFF1A1A2E),
        surfaceContainerHigh = Color(0xFF22223A),
        outline = Color(0xFF49495C),
    )
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
