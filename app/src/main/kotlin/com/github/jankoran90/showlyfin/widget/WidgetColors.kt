package com.github.jankoran90.showlyfin.widget

import androidx.compose.ui.graphics.Color

/**
 * RELAY — jediný zdroj barev pro domácí widgety.
 * Hodnoty zrcadlí kanonický Showlyfin dark theme (`PhoneShowlyfinTheme`): akcent oranžová
 * #FF7A1A, AMOLED-blízké pozadí. Glance nepoužívá MaterialTheme.colorScheme, takže tokeny
 * žijí tady (1 soubor) — feature widget kód jen ČTE odsud, nikde jinde barvu nedeklaruje.
 */
object WidgetColors {
    val Background = Color(0xFF07071A)
    val Surface = Color(0xFF13132B)
    val OnBackground = Color(0xFFF2F2F8)
    val OnBackgroundMuted = Color(0xFFBFBFD6)
    val Accent = Color(0xFFFF7A1A)
    val OnAccent = Color(0xFF1A0900)
    val ControlBg = Color(0xFF1E1E3A)
}
