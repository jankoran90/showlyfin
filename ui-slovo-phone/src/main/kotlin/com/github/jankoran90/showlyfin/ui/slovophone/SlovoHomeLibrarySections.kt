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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.search.SearchPrefs
import com.github.jankoran90.showlyfin.feature.listen.ui.BookFilter
import com.github.jankoran90.showlyfin.feature.listen.ui.BookSort
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeRow
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeRowsPrefs
import com.github.jankoran90.showlyfin.feature.listen.ui.LibraryPrefs
import com.github.jankoran90.showlyfin.feature.listen.ui.RowSize
import com.github.jankoran90.showlyfin.feature.listen.ui.ShowFilter
import com.github.jankoran90.showlyfin.feature.listen.ui.ShowSort

/**
 * AMPHORA (SHW-124) F2 — Nastavení › Řady na Domů (DO1: které řady, pořadí, délka krátkého dílu)
 * a Nastavení › Knihovna (záložky Domů per profil, řazení, filtr, mřížka/seznam a velikost řádku — knihy i pořady). Bez VM, jako
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

/** Nastavení › Domů a vzhled › Záložky na Domů (user 2026-11-23 09:52 „skrytí přehledu nevidím v nastavení“). */
@Composable
fun SlovoHomeTabsSection() {
    val ctx = LocalContext.current
    var tabs by remember { mutableStateOf(LibraryPrefs.tabsWithVisibility(ctx)) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        // User 2026-11-23: „pořadí si každý změní a výchozí je první“ — záložky Domů per profil.
        Text(
            "Záložky na Domů (pro tento profil). Appka se otevře na první viditelné; oko záložku skryje.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            val firstVisible = tabs.indexOfFirst { it.second }
            tabs.forEachIndexed { i, (tab, visible) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            !visible -> "${tab.label} · skrytá"
                            i == firstVisible -> "${tab.label} · otevře se první"
                            else -> tab.label
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { LibraryPrefs.setTabHidden(ctx, tab, hidden = visible); tabs = LibraryPrefs.tabsWithVisibility(ctx) },
                        // Poslední viditelnou záložku skrýt nejde.
                        enabled = !visible || tabs.count { it.second } > 1,
                    ) {
                        Icon(if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = if (visible) "Skrýt" else "Zobrazit")
                    }
                    IconButton(onClick = { LibraryPrefs.setTabOrder(ctx, tabs.map { it.first }.swapped(i, i - 1)); tabs = LibraryPrefs.tabsWithVisibility(ctx) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Nahoru")
                    }
                    IconButton(onClick = { LibraryPrefs.setTabOrder(ctx, tabs.map { it.first }.swapped(i, i + 1)); tabs = LibraryPrefs.tabsWithVisibility(ctx) }, enabled = i < tabs.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dolů")
                    }
                }
            }
        }
    }
}

@Composable
fun SlovoLibrarySection() {
    val ctx = LocalContext.current
    var sort by remember { mutableStateOf(LibraryPrefs.sort(ctx)) }
    var filter by remember { mutableStateOf(LibraryPrefs.filter(ctx)) }
    var list by remember { mutableStateOf(LibraryPrefs.listMode(ctx)) }
    var rowSize by remember { mutableStateOf(LibraryPrefs.rowSize(ctx)) }
    var showSort by remember { mutableStateOf(LibraryPrefs.showSort(ctx)) }
    var showFilter by remember { mutableStateOf(LibraryPrefs.showFilter(ctx)) }
    var showList by remember { mutableStateOf(LibraryPrefs.showListMode(ctx)) }
    var showRow by remember { mutableStateOf(LibraryPrefs.showRowSize(ctx)) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
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
        if (list) {
            SettingChips(
                label = "Velikost řádku knih",
                options = RowSize.entries.toList(),
                selected = rowSize,
                labelOf = { it.label },
                onSelect = { LibraryPrefs.setRowSize(ctx, it); rowSize = it },
            )
        }
        SettingChips(
            label = "Řazení pořadů",
            options = ShowSort.entries.toList(),
            selected = showSort,
            labelOf = { it.label },
            onSelect = { LibraryPrefs.setShowSort(ctx, it); showSort = it },
        )
        SettingChips(
            label = "Ukázat pořady",
            options = ShowFilter.entries.toList(),
            selected = showFilter,
            labelOf = { it.label },
            onSelect = { LibraryPrefs.setShowFilter(ctx, it); showFilter = it },
        )
        SettingSwitchRow(
            title = "Pořady jako seznam",
            subtitle = "Vypnuto = mřížka obalů",
            checked = showList,
            onCheckedChange = { LibraryPrefs.setShowListMode(ctx, it); showList = it },
        )
        if (showList) {
            SettingChips(
                label = "Velikost řádku pořadů",
                options = RowSize.entries.toList(),
                selected = showRow,
                labelOf = { it.label },
                onSelect = { LibraryPrefs.setShowRowSize(ctx, it); showRow = it },
            )
        }
    }
}

/** AMPHORA F3 — Nastavení › Hledání: skupiny výsledků, počet dílů hned, nedávná hledání. */
@Composable
fun SlovoSearchSettingsSection() {
    val ctx = LocalContext.current
    var audioteka by remember { mutableStateOf(SearchPrefs.showAudioteka(ctx)) }
    var newShows by remember { mutableStateOf(SearchPrefs.showNewShows(ctx)) }
    var episodes by remember { mutableStateOf(SearchPrefs.episodesShown(ctx)) }
    var rememberRecent by remember { mutableStateOf(SearchPrefs.rememberRecent(ctx)) }
    var recentCount by remember { mutableStateOf(SearchPrefs.recent(ctx).size) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingSwitchRow(
            title = "Hledat i v Audiotéce",
            subtitle = "Knihy a série z katalogu Audioteky pod vlastními výsledky",
            checked = audioteka,
            onCheckedChange = { SearchPrefs.setShowAudioteka(ctx, it); audioteka = it },
        )
        SettingSwitchRow(
            title = "Nabízet nové pořady",
            subtitle = "Podcasty a kanály, které ještě nesleduješ, s tlačítkem Sledovat",
            checked = newShows,
            onCheckedChange = { SearchPrefs.setShowNewShows(ctx, it); newShows = it },
        )
        SettingChips(
            label = "Dílů ukázat hned (zbytek pod „Všechny díly“)",
            options = SearchPrefs.episodeChoices,
            selected = episodes,
            labelOf = { "$it" },
            onSelect = { SearchPrefs.setEpisodesShown(ctx, it); episodes = it },
        )
        SettingSwitchRow(
            title = "Pamatovat nedávná hledání",
            subtitle = "Vypnutím se uložená hledání smažou",
            checked = rememberRecent,
            onCheckedChange = {
                SearchPrefs.setRememberRecent(ctx, it); rememberRecent = it
                recentCount = SearchPrefs.recent(ctx).size
            },
        )
        if (recentCount > 0) {
            TextButton(onClick = { SearchPrefs.clearRecent(ctx); recentCount = 0 }) {
                Text("Vymazat nedávná hledání ($recentCount)")
            }
        }
    }
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    val t = it[a]; it[a] = it[b]; it[b] = t
}
