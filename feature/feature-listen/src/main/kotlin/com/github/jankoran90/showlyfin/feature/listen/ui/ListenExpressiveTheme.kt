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
        // UNISON kanon: AMOLED čistě černá base + neutrální šedé plochy (sjednoceno s claude-voice /
        // PhoneShowlyfinTheme). Warm amber akcent Poslechu zůstává jako identita sekce, navy pryč.
        background = Color(0xFF000000),
        onBackground = Color(0xFFEDEDED),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFF9E9E9E),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF1E1E1E),
        outline = Color(0xFF2E2E2E),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
