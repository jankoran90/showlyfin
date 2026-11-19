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
import com.github.jankoran90.showlyfin.feature.listen.kids.KidsAutoDownloadPrefs

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
            subtitle = "V zamčeném dětském telefonu Slovo neukazuje vlastní aktualizace (instaluje je " +
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
        KidsAutoDownloadSettings()
    }
}

/**
 * Rodičovská správa R3 (2026-11-19) — samostahování obsahu Dětí přes Wi-Fi ([KidsAutoDownloadPrefs]).
 * Běží jen v dětském profilu: při spuštění appky, návratu do ní a každé 3 h na Wi-Fi.
 */
@Composable
private fun KidsAutoDownloadSettings() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(KidsAutoDownloadPrefs.mode(context)) }
    var limit by remember { mutableStateOf(KidsAutoDownloadPrefs.showLimit(context)) }
    var deleteRemoved by remember { mutableStateOf(KidsAutoDownloadPrefs.deleteRemoved(context)) }
    SettingChips(
        label = "Samo stahovat obsah Dětí přes Wi-Fi",
        subtitle = "Co rodiče Dětem zviditelní (knihy, pořady, série, díly), se v dětském profilu samo stáhne " +
            "do telefonu, jakmile je Wi-Fi, a pak jde poslouchat i bez sítě. „V dětském režimu“ = jen na zamčeném " +
            "dětském telefonu.",
        options = KidsAutoDownloadPrefs.Mode.entries.toList(),
        selected = mode,
        labelOf = { it.label },
        onSelect = { mode = it; KidsAutoDownloadPrefs.setMode(context, it) },
    )
    if (mode != KidsAutoDownloadPrefs.Mode.OFF) {
        SettingChips(
            label = "U celého pořadu stáhnout nejnovějších dílů",
            subtitle = "Série a jednotlivě přidané díly se stahují vždy celé.",
            options = KidsAutoDownloadPrefs.SHOW_LIMITS,
            selected = limit,
            labelOf = { it.toString() },
            onSelect = { limit = it; KidsAutoDownloadPrefs.setShowLimit(context, it) },
        )
        SettingSwitchRow(
            title = "Odebrané smazat i z telefonu",
            subtitle = "Co rodiče Dětem odeberou, zmizí i ze staženého. Maže se jen podle seznamu čerstvě " +
                "staženého ze serveru a jen obsah Dětí, ne stažené dospělých.",
            checked = deleteRemoved,
            onCheckedChange = { deleteRemoved = it; KidsAutoDownloadPrefs.setDeleteRemoved(context, it) },
        )
        val last = remember { KidsAutoDownloadPrefs.lastSummary(context) }
        val lastAt = remember { KidsAutoDownloadPrefs.lastAt(context) }
        Text(
            text = if (last == null) "Zatím neproběhlo." else "Naposled " +
                java.text.SimpleDateFormat("d. M. HH:mm", java.util.Locale("cs")).format(java.util.Date(lastAt)) + ": $last",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
