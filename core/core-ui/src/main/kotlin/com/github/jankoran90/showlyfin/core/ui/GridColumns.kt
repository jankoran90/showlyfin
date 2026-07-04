package com.github.jankoran90.showlyfin.core.ui

import android.content.Context
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * PANORAMA (SHW-78): globální počet sloupců mřížky. Ukládá se v `trakt_prefs` (klíč `grid_columns`,
 * 0 = automaticky dle šířky, 2–5 = pevný počet). Čte se přímo (projeví se při návratu na obrazovku).
 */
@Composable
fun rememberGridColumnPref(): Int {
    val ctx = LocalContext.current
    return ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE).getInt("grid_columns", 0)
}

/**
 * [GridCells] pro daný [mode] a uživatelský počet sloupců [colPref] (0 = auto). Landscape karty jsou
 * širší → počet sloupců se ořízne (max 3), s auto-hodnotou default 2.
 */
fun gridCellsFor(mode: ViewMode, colPref: Int): GridCells {
    val cols = if (colPref in 2..5) colPref else 0
    return when (mode) {
        ViewMode.LANDSCAPE -> GridCells.Fixed((if (cols > 0) cols else 2).coerceAtMost(3))
        else -> if (cols > 0) GridCells.Fixed(cols) else GridCells.Adaptive(110.dp)
    }
}
