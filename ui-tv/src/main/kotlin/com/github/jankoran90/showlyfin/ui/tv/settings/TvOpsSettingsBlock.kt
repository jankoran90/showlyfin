package com.github.jankoran90.showlyfin.ui.tv.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.data.uploader.OpsPrefs

/**
 * PROVOZ (SHW-114) — blok „Provoz" v TV nastavení. **Parita s telefonem** (`FilmyOpsSettingsSection`).
 *
 * Sekce Provoz jako obrazovka je jen na telefonu (tak si to user vyžádal), ale televize je to hlavní
 * zařízení, které se do ní hlásí — a bez pojmenování by se v přehledu ukázala jako model boxu.
 * Uživatel chtěl vidět *„když poběží na TV v appce něco, ať to vidím v telefonu"*, takže tady musí
 * jít nastavit obojí: jméno i to, jestli se televize hlásí.
 *
 * Volby, které se týkají profilu (jak automat hledá zdroje), zůstávají v sekci Provoz na telefonu —
 * nejsou vlastností zařízení.
 */
@Composable
fun TvOpsSettingsBlock() {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE) }

    var report by remember { mutableStateOf(OpsPrefs.reportPlayback(prefs)) }
    var name by remember { mutableStateOf(OpsPrefs.deviceName(prefs)) }
    var refresh by remember { mutableIntStateOf(OpsPrefs.refreshSec(prefs)) }

    TvSettingsBlock(title = "Provoz") {
        TvToggleRow(
            label = "Hlásit, co tu hraje",
            subtitle = "Televize pošle serveru titul, zdroj a stopáž → uvidíš to v telefonu v sekci Provoz",
            checked = report,
            onCheckedChange = { report = it; OpsPrefs.setReportPlayback(prefs, it) },
        )
        // Na TV se text nezadává pohodlně → volba z připravených jmen (D-pad), ne klávesnice.
        TvOptionStepperRow(
            label = "Název tohoto zařízení",
            subtitle = "Pod tímhle jménem se televize ukáže v přehledu",
            options = DEVICE_NAMES,
            selected = DEVICE_NAMES.firstOrNull { it == name } ?: "",
            labelOf = { it.ifBlank { "podle modelu" } },
            onSelect = { name = it; OpsPrefs.setDeviceName(prefs, it) },
        )
        TvOptionStepperRow(
            label = "Obnovovat přehled po",
            subtitle = "Platí pro sekci Provoz na tomhle zařízení",
            options = OpsPrefs.REFRESH_OPTIONS,
            selected = refresh,
            labelOf = { if (it == 0) "jen při otevření" else "$it s" },
            onSelect = { refresh = it; OpsPrefs.setRefreshSec(prefs, it) },
        )
    }
}

/** Prázdná hodnota = použij model zařízení. Jména pokrývají běžné umístění televize v bytě. */
private val DEVICE_NAMES = listOf(
    "", "TV v obýváku", "TV v ložnici", "TV v dětském pokoji", "TV v kuchyni", "Projektor",
)
