package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * PŮDORYS (SHW-112, user 2026-07-31: „v telefonu ani nemam moznost zobrazovat co ma byt v sidebaru") —
 * editor MENU TELEFONU. Do vc126 bylo pořadí sekcí natvrdo v `FilmyShellPrefs.discoverOrder`; teď si ho
 * uživatel skládá sám a volba se přes `HomeLayoutSync` přenese i na druhé zařízení.
 *
 * Nastavení a Profil se needitují — jsou připnuté dole v menu, aby nešlo schovat cestu zpátky sem.
 */
@Composable
internal fun FilmyMenuSection(vm: FilmyHomeLayoutViewModel = hiltViewModel()) {
    val stored by vm.menu.collectAsStateWithLifecycle()
    val filmotekaFirst = FilmyShellPrefs.defaultFilmoteka(LocalContext.current)
    val merged = FilmyMenuConfig.merge(stored, filmotekaFirst)

    Text(
        text = "Které sekce jsou v postranním menu a v jakém pořadí. Nastavení a Profil zůstávají vždycky dole.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    merged.forEachIndexed { index, entry ->
        val section = FilmyMenuConfig.sectionOf(entry.item) ?: return@forEachIndexed
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "  ${section.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { vm.moveMenu(merged, entry.item, up = true) },
                    enabled = index > 0,
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Posunout nahoru")
                }
                IconButton(
                    onClick = { vm.moveMenu(merged, entry.item, up = false) },
                    enabled = index < merged.lastIndex,
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Posunout dolů")
                }
                // Poslední zapnutou sekci nejde vypnout — prázdné menu by uživatele zavřelo v jedné obrazovce.
                val lastEnabled = entry.enabled && merged.count { it.enabled } == 1
                Switch(
                    checked = entry.enabled,
                    enabled = !lastEnabled,
                    onCheckedChange = { vm.setMenuEnabled(merged, entry.item, it) },
                )
            }
        }
    }

    TextButton(onClick = { vm.resetMenu() }) { Text("Obnovit výchozí pořadí") }
}
