package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Téma poslechové sekce — izolovaný wrapper, jediné místo, kde se řeší vzhled „Poslechu".
 *
 * CÍL: `MaterialExpressiveTheme` (Material 3 Expressive). V aktuálním BOM (compose-bom
 * 2026.05.01 → material3 **1.4.0**) je ale `MaterialExpressiveTheme`/`ExperimentalMaterial3ExpressiveApi`
 * ještě **internal** (public až v material3 1.5.0-alpha). Dokud se nerozhodne o bumpu material3
 * na alphu, běží sekce na standardním `MaterialTheme` se stejným AMOLED-dark schématem.
 * Přepnutí na expressive = záměna `MaterialTheme(...)` za `MaterialExpressiveTheme(...)` zde.
 */
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
    MaterialTheme(colorScheme = colors, content = content)
}
