package com.github.jankoran90.showlyfin.feature.listen.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Test F8 (P2): v přehrávači byly ikony stavové lišty tmavé na tmavém pozadí (barva z obalu).
 * Podle světlosti pozadí přepne ikony (tmavé pozadí = světlé ikony); po zavření vrátí původní stav.
 */
@Composable
internal fun PlayerStatusBarIcons(background: Color) {
    val view = LocalView.current
    val darkBackground = background.luminance() < HALF
    DisposableEffect(darkBackground) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !darkBackground
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

private const val HALF = 0.5f
