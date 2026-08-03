package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.uploader.OpsPrefs

/**
 * PROVOZ (SHW-114) — blok „Provoz" v Nastavení. Parita s TV (`TvOpsSettingsSection`).
 *
 * 🔒 Featura s nastavitelným chováním patří i do Nastavení, ne jen do vlastní obrazovky. Tady sedí to,
 * co se týká **tohohle zařízení**: jestli se má hlásit, pod jakým jménem a jak často sekce obnovuje.
 * Přepínač „jak automat hledá" zůstává v samotné sekci Provoz — týká se profilu, ne zařízení.
 *
 * Jméno zařízení je nejdůležitější volba bloku: bez něj svítí v přehledu „Xiaomi MiBox4" místo
 * „TV v obýváku" a přehled neodpoví na otázku, KDE se hraje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilmyOpsSettingsSection() {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ctx.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE) }

    var report by remember { mutableStateOf(OpsPrefs.reportPlayback(prefs)) }
    var name by remember { mutableStateOf(OpsPrefs.deviceName(prefs)) }
    var refresh by remember { mutableIntStateOf(OpsPrefs.refreshSec(prefs)) }
    var days by remember { mutableIntStateOf(OpsPrefs.historyDays(prefs)) }

    SettingSwitchRow(
        title = "Hlásit, co tu hraje",
        subtitle = "Zařízení pošle serveru název titulu, odkud stream jde a kde je stopáž — díky tomu " +
            "je vidět v sekci Provoz na telefonu. Vypnuté = tohle zařízení se v přehledu neobjeví.",
        checked = report,
        onCheckedChange = { report = it; OpsPrefs.setReportPlayback(prefs, it) },
    )

    OutlinedTextField(
        value = name,
        onValueChange = { name = it; OpsPrefs.setDeviceName(prefs, it) },
        label = { Text("Název tohoto zařízení") },
        placeholder = { Text("např. TV v obýváku") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Text(
        text = "Prázdné = použije se model zařízení.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SettingSectionTitle("Jak často se sekce obnovuje")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OpsPrefs.REFRESH_OPTIONS.forEach { sec ->
            FilterChip(
                selected = refresh == sec,
                onClick = { refresh = sec; OpsPrefs.setRefreshSec(prefs, sec) },
                label = { Text(if (sec == 0) "Jen při otevření" else "$sec s") },
            )
        }
    }

    SettingSectionTitle("Za jaké období počítat historii")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OpsPrefs.HISTORY_DAYS_OPTIONS.forEach { d ->
            FilterChip(
                selected = days == d,
                onClick = { days = d; OpsPrefs.setHistoryDays(prefs, d) },
                label = { Text(if (d >= 365) "rok" else "$d dní") },
            )
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = "Historie se ukládá na serveru a drží posledních 300 přehrání. Rychlost a plynulost " +
                "se měří jen u streamů, které tečou přes náš server — Jellyfin a Česká televize jdou " +
                "do zařízení přímo.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
