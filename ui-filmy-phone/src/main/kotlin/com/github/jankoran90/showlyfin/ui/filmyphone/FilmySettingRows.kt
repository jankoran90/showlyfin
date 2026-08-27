package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * CELLULOID (SHW-98) M2.7 — sdílené touch řádky Nastavení Filmy (Switch/Chips/Slider/titulek).
 * Náhrada TV D-pad stepperů touch ovladači; používají je všechny Filmy settings sekce (Vzhled/Kurátor/…),
 * ať parita s TV nevzniká copy-paste. Logika vždy ze sdílených VM, tady jen prezentace.
 */

@Composable
internal fun SettingSectionTitle(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onBackground,
)

@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Popisek + vodorovná řada výběrových chipů (touch varianta TV `TvOptionStepperRow`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SettingChips(
    label: String,
    subtitle: String? = null,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                    label = { Text(labelOf(opt)) },
                )
            }
        }
    }
}

/** Vícevýběrové toggle chipy (množina zapnutých) — touch varianta TV toggle seznamu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SettingMultiChips(
    label: String,
    subtitle: String? = null,
    options: List<T>,
    enabled: Set<T>,
    labelOf: (T) -> String,
    onToggle: (T, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                val on = opt in enabled
                FilterChip(
                    selected = on,
                    onClick = { onToggle(opt, !on) },
                    label = { Text(labelOf(opt)) },
                )
            }
        }
    }
}

/** Slider 0–100 % (hodnota 0f–1f) s popiskem a procentem. */
@Composable
internal fun SettingPercentSlider(label: String, subtitle: String? = null, value: Float, onValue: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("${(value * 100).roundToInt()} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValue, valueRange = 0f..1f)
    }
}

/**
 * SIMILAR (user 2026-08-27: „i ostatni jestli visible nebo non visible a klidne poradi") — seznam
 * pruhů se šipkami na přeskládání. Vypnutý pruh v seznamu ZŮSTÁVÁ (zešedlý, popsaný jako skrytý):
 * kdyby zmizel, uživatel by po zapnutí netušil, kam se zařadí.
 */
@Composable
internal fun SettingRowOrder(
    label: String,
    order: List<String>,
    hidden: Set<String>,
    onMove: (String, Int) -> Unit,
) {
    val popisky = mapOf(
        "creators" to "Tvůrci",
        "collection" to "Kolekce",
        "similar" to "Podobné",
        "director" to "Od stejného režiséra",
        "studio" to "Od stejného studia",
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        order.forEachIndexed { index, klic ->
            val skryty = klic in hidden
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (popisky[klic] ?: klic) + if (skryty) " (skryto)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (skryty) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMove(klic, -1) }, enabled = index > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Posunout nahoru")
                }
                IconButton(onClick = { onMove(klic, 1) }, enabled = index < order.lastIndex) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Posunout dolů")
                }
            }
        }
    }
}
