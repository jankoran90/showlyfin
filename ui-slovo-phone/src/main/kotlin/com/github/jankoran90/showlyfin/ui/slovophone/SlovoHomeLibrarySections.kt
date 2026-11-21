package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.ui.BookFilter
import com.github.jankoran90.showlyfin.feature.listen.ui.BookSort
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeRow
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeRowsPrefs
import com.github.jankoran90.showlyfin.feature.listen.ui.LibraryPrefs

/**
 * AMPHORA (SHW-124) F2 — Nastavení › Řady na Domů (DO1: které řady, pořadí, délka krátkého dílu)
 * a Nastavení › Knihovna (KN1: výchozí záložka, řazení, filtr, mřížka/seznam). Bez VM, jako
 * [SlovoNavOrderSection]; projeví se při příštím otevření Domů / Knihovny.
 */
@Composable
fun SlovoHomeRowsSection() {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf(HomeRowsPrefs.order(ctx)) }
    var hidden by remember { mutableStateOf(HomeRowsPrefs.hidden(ctx)) }
    var shortMin by remember { mutableStateOf(HomeRowsPrefs.shortMinutes(ctx)) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            "Řady pod sebou na Domů. Prázdná řada se sama skryje; Pokračovat zůstává vždy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        order.forEachIndexed { i, row ->
            val visible = row == HomeRow.CONTINUE || row !in hidden
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (row == HomeRow.SHORT) "Krátké do $shortMin min" else row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { HomeRowsPrefs.setHidden(ctx, row, hidden = visible); hidden = HomeRowsPrefs.hidden(ctx) },
                    enabled = row != HomeRow.CONTINUE,
                ) {
                    Icon(if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = if (visible) "Skrýt" else "Zobrazit")
                }
                IconButton(onClick = { order = order.swapped(i, i - 1).also { HomeRowsPrefs.setOrder(ctx, it) } }, enabled = i > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Nahoru")
                }
                IconButton(onClick = { order = order.swapped(i, i + 1).also { HomeRowsPrefs.setOrder(ctx, it) } }, enabled = i < order.lastIndex) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dolů")
                }
            }
        }
        SettingChips(
            label = "Krátký díl je do",
            options = HomeRowsPrefs.shortChoices,
            selected = shortMin,
            labelOf = { "$it min" },
            onSelect = { HomeRowsPrefs.setShortMinutes(ctx, it); shortMin = it },
        )
    }
}

@Composable
fun SlovoLibrarySection() {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(LibraryPrefs.defaultTab(ctx)) }
    var sort by remember { mutableStateOf(LibraryPrefs.sort(ctx)) }
    var filter by remember { mutableStateOf(LibraryPrefs.filter(ctx)) }
    var list by remember { mutableStateOf(LibraryPrefs.listMode(ctx)) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingChips(
            label = "Otevřít na záložce",
            options = LibraryPrefs.tabs.indices.toList(),
            selected = tab,
            labelOf = { LibraryPrefs.tabs[it] },
            onSelect = { LibraryPrefs.setDefaultTab(ctx, it); tab = it },
        )
        SettingChips(
            label = "Řazení knih",
            options = BookSort.entries.toList(),
            selected = sort,
            labelOf = { it.label },
            onSelect = { LibraryPrefs.setSort(ctx, it); sort = it },
        )
        SettingChips(
            label = "Ukázat knihy",
            options = BookFilter.entries.toList(),
            selected = filter,
            labelOf = { it.label },
            onSelect = { LibraryPrefs.setFilter(ctx, it); filter = it },
        )
        SettingSwitchRow(
            title = "Knihy jako seznam",
            subtitle = "Vypnuto = mřížka obalů",
            checked = list,
            onCheckedChange = { LibraryPrefs.setListMode(ctx, it); list = it },
        )
    }
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    val t = it[a]; it[a] = it[b]; it[b] = t
}
