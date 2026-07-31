package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.HomeCardStyle
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowParams
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSort
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowSourceType

/**
 * PŮDORYS (SHW-112, user 2026-07-31: „v telefonu ani nemam moznost zobrazovat … co ma byt na home") —
 * editor ŘAD DOMOVA na telefonu. Do vc126 uměla řady přeskládat jen TV (`TvHomeRowEditor`); telefon je
 * jen četl. Sahá na TÝŽ `HomeLayoutStore` jako TV a přes `HomeLayoutSync` se změna propíše i na TV.
 *
 * Touch varianta TV overlaye: seznam všech řad (i skrytých), u každé přesun/viditelnost, po rozkliknutí
 * „Upravit" plné volby (styl, řazení, počty, popisky, kategorie u Objevovat, přejmenování, smazání).
 */
@Composable
internal fun FilmyHomeRowsSection(vm: FilmyHomeLayoutViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) }
    var showAddRow by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Text(
        text = "Co je vidět na domovské obrazovce a v jakém pořadí. Nastavení platí pro tenhle profil " +
            "a přenese se i na televizi.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val enabledIds = rows.filter { it.enabled }.map { it.id }
    rows.forEach { row ->
        val posInVisible = enabledIds.indexOf(row.id)
        FilmyRowCard(
            row = row,
            canMoveUp = posInVisible > 0,
            canMoveDown = posInVisible >= 0 && posInVisible < enabledIds.lastIndex,
            expanded = editing == row.id,
            onToggleExpand = { editing = if (editing == row.id) null else row.id },
            onMove = { up -> vm.moveRow(row.id, up) },
            onEnabled = { vm.setRowEnabled(row.id, it) },
            onUpdate = { vm.updateRow(it) },
            onDelete = { vm.removeRow(row.id); editing = null },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { showAddRow = true }) { Text("Přidat řadu") }
        TextButton(onClick = { confirmReset = true }) { Text("Obnovit výchozí") }
    }

    if (showAddRow) {
        FilmyAddRowDialog(
            onPick = { vm.addRow(it); showAddRow = false },
            onDismiss = { showAddRow = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Obnovit výchozí řady?") },
            text = { Text("Vlastní řady i přeskládané pořadí se zahodí a domov se vrátí do výrobního stavu.") },
            confirmButton = { TextButton(onClick = { vm.resetRows(); confirmReset = false }) { Text("Obnovit") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Zrušit") } },
        )
    }
}

/** Jedna řada v editoru: hlavička (název + přesun + vypínač) a po rozkliknutí plné volby. */
@Composable
private fun FilmyRowCard(
    row: HomeRowConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onUpdate: (HomeRowConfig) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.resolvedTitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = row.source.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Posunout nahoru")
                }
                IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Posunout dolů")
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Upravit řadu")
                }
                Switch(checked = row.enabled, onCheckedChange = onEnabled)
            }
            if (expanded) FilmyRowOptions(row = row, onUpdate = onUpdate, onDelete = onDelete)
        }
    }
}

/** Plné volby jedné řady — parita s TV `TvHomeRowEditor` (progressive disclosure: jen co dává smysl). */
@Composable
private fun FilmyRowOptions(
    row: HomeRowConfig,
    onUpdate: (HomeRowConfig) -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = row.title,
            onValueChange = { onUpdate(row.copy(title = it)) },
            label = { Text("Název řady") },
            placeholder = { Text(row.source.label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingChips(
            label = "Styl karet",
            options = HomeCardStyle.entries.toList(),
            selected = row.cardStyle,
            labelOf = { it.label },
            onSelect = { onUpdate(row.copy(cardStyle = it)) },
        )

        SettingChips(
            label = "Řazení",
            options = HomeRowSort.entries.toList(),
            selected = row.sort,
            labelOf = { it.label },
            onSelect = { onUpdate(row.copy(sort = it)) },
        )

        // Kategorie/typ jen pro „Objevovat" (Trakt) — jinde nemají co ovlivnit.
        if (row.source == HomeRowSourceType.DISCOVER) {
            SettingChips(
                label = "Typ",
                options = listOf("movies", "shows"),
                selected = row.params[HomeRowParams.TAB] ?: "movies",
                labelOf = { if (it == "shows") "Seriály" else "Filmy" },
                onSelect = { onUpdate(row.withParam(HomeRowParams.TAB, it)) },
            )
            SettingChips(
                label = "Kategorie",
                options = listOf("trending", "popular", "anticipated", "recommended"),
                selected = row.params[HomeRowParams.FILTER] ?: "trending",
                labelOf = { filterLabel(it) },
                onSelect = { onUpdate(row.withParam(HomeRowParams.FILTER, it)) },
            )
        }

        if (row.source == HomeRowSourceType.DISCOVER ||
            row.source == HomeRowSourceType.JELLYFIN_LIBRARY ||
            row.source == HomeRowSourceType.RECENTLY_ADDED
        ) {
            SettingSwitchRow(
                title = "Skrýt zhlédnuté",
                checked = row.params[HomeRowParams.HIDE_WATCHED] == "true",
                onCheckedChange = { onUpdate(row.withParam(HomeRowParams.HIDE_WATCHED, it.toString())) },
            )
        }

        SettingSwitchRow(
            title = "Popisky na kartách",
            checked = row.showTitles,
            onCheckedChange = { onUpdate(row.copy(showTitles = it)) },
        )

        SettingChips(
            label = "Karet v řadě",
            subtitle = "Kolik karet je vidět; zbytek otevře „Zobrazit vše\".",
            options = listOf(3, 4, 5, 6, 8, 10, 12, 15, 20),
            selected = row.displayLimit.coerceIn(1, 60),
            labelOf = { "$it" },
            onSelect = { onUpdate(row.copy(displayLimit = it)) },
        )

        SettingSwitchRow(
            title = "Dlaždice „Zobrazit vše\"",
            checked = row.showAll,
            onCheckedChange = { onUpdate(row.copy(showAll = it)) },
        )

        SettingChips(
            label = "Načíst položek",
            subtitle = "Strop stahování — kolik titulů je za „Zobrazit vše\".",
            options = listOf(10, 20, 30, 40, 60),
            selected = row.limit.coerceIn(1, 60),
            labelOf = { "$it" },
            onSelect = { onUpdate(row.copy(limit = it)) },
        )

        // Smazat jde jen VLASTNÍ řada — výrobní se skrývá vypínačem (jinak by se vrátila při dalším merge).
        if (row.id.startsWith("custom_")) {
            TextButton(onClick = onDelete) { Text("Smazat řadu") }
        }
    }
}

private fun HomeRowConfig.withParam(key: String, value: String): HomeRowConfig =
    copy(params = params + (key to value))

private fun filterLabel(filter: String): String = when (filter) {
    "popular" -> "Populární"
    "anticipated" -> "Očekávané"
    "recommended" -> "Doporučené"
    else -> "Trendy"
}
