package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.KioskMode

/**
 * Dětský telefon / kiosek (TREEHOUSE INF-20, 2026-11-19) — Nastavení › Dětský telefon. V kiosku Slovo
 * nenabízí vlastní aktualizace (instaluje je Dětský launcher) ani sdílení do jiných appek.
 */
@Composable
internal fun SlovoKioskSection() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(KioskMode.mode(context)) }
    val lockTask = remember(mode) { KioskMode.isLockTaskActive(context) }
    val active = when (mode) {
        KioskMode.Mode.ON -> true
        KioskMode.Mode.OFF -> false
        KioskMode.Mode.AUTO -> lockTask
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingChips(
            label = "Dětský režim telefonu",
            subtitle = "Ve zamčeném dětském telefonu Slovo neukazuje vlastní aktualizace (instaluje je " +
                "Dětský launcher) ani sdílení do jiných aplikací. „Poznat samo“ ho zapne, když je telefon zamčený.",
            options = KioskMode.Mode.entries.toList(),
            selected = mode,
            labelOf = { it.label },
            onSelect = { mode = it; KioskMode.setMode(context, it) },
        )
        Text(
            text = if (active) "Teď: dětský režim zapnutý" else "Teď: běžný telefon",
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
