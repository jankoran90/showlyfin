package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * User (2026-08-16 18:23–18:30, „pořadí umožníme měnit v appce a co se tam zobrazí taky, pořadí si
 * nastavim sám") — pořadí + viditelnost sekcí Objevit/Zdroje v draweru (Domů je vždy první a nejde
 * skrýt). Self-contained (bez VM, [SlovoShellPrefs] má vlastní SharedPreferences vrstvu) — lokální
 * stav se po každé akci hned zapíše a znovu načte, ať UI vždy odpovídá tomu, co je persistováno.
 */
@Composable
fun SlovoDrawerOrderSection() {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf(SlovoShellPrefs.reorderableWithVisibility(ctx)) }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Domů je vždy první. Šipky mění pořadí, oko skryje/zobrazí sekci v menu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEachIndexed { i, (section, visible) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        SlovoShellPrefs.setSectionHidden(ctx, section, hidden = visible)
                        rows = SlovoShellPrefs.reorderableWithVisibility(ctx)
                    },
                ) {
                    Icon(
                        if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (visible) "Skrýt v menu" else "Zobrazit v menu",
                    )
                }
                IconButton(
                    onClick = {
                        val newOrder = rows.map { it.first }.toMutableList()
                        val t = newOrder[i]; newOrder[i] = newOrder[i - 1]; newOrder[i - 1] = t
                        SlovoShellPrefs.setDrawerOrder(ctx, newOrder)
                        rows = SlovoShellPrefs.reorderableWithVisibility(ctx)
                    },
                    enabled = i > 0,
                ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nahoru") }
                IconButton(
                    onClick = {
                        val newOrder = rows.map { it.first }.toMutableList()
                        val t = newOrder[i]; newOrder[i] = newOrder[i + 1]; newOrder[i + 1] = t
                        SlovoShellPrefs.setDrawerOrder(ctx, newOrder)
                        rows = SlovoShellPrefs.reorderableWithVisibility(ctx)
                    },
                    enabled = i < rows.lastIndex,
                ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dolů") }
            }
        }
    }
}
