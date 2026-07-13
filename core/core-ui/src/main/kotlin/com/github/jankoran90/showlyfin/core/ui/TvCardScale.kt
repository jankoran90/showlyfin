package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/**
 * COUCH DA4 (SHW-88) — globálně konfigurovatelné metriky mřížky pro TV shell (user krédo „víc voleb").
 * Jeden zdroj pro VŠECHNY řady/mřížky: [widthScale] škáluje šířku karet (širší = víc textu, méně sloupců),
 * [spacingScale] rozestupy mezi kartami. Poskytuje se v `ShowlyfinTvApp` z `FontPrefs` (gridWidth/gridSpacing);
 * default 1f = dnešní vzhled. Telefon tímto neprochází (jen TV shell).
 */
data class TvCardScale(
    val widthScale: Float = 1f,
    val spacingScale: Float = 1f,
) {
    /** Škálovaná šířka karty z bazální [base] dp. */
    operator fun times(base: Dp): Dp = base * widthScale

    /** Škálovaný rozestup z bazálního [base] dp. */
    fun spacing(base: Dp): Dp = base * spacingScale
}

val LocalTvCardScale = staticCompositionLocalOf { TvCardScale() }
