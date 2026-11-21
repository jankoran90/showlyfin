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

/**
 * AMPHORA (SHW-124) F1 — Nastavení › Spodní lišta a nabídka Víc (dřív „Pořadí menu“ draweru).
 * Lišta: Domů vždy první; Knihovna, Pořady, Hledat, Fronta jde přeskládat i skrýt. Víc: Objevit,
 * Zdroje, Stahování, Katalog (pořadí + skrytí; Nastavení je v nabídce vždy). Opětovný ťuk na otevřené
 * místo = návrat na začátek (vypínatelné). Bez VM — [SlovoShellPrefs] má vlastní SharedPreferences;
 * změna se projeví při příštím přepnutí sekce.
 */
@Composable
fun SlovoNavOrderSection() {
    val ctx = LocalContext.current
    var bar by remember { mutableStateOf(SlovoShellPrefs.barWithVisibility(ctx)) }
    var more by remember { mutableStateOf(SlovoShellPrefs.reorderableWithVisibility(ctx)) }
    var reselect by remember { mutableStateOf(SlovoShellPrefs.reselectToTop(ctx)) }

    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Hint("Spodní lišta — Domů je vždy první. Šipky mění pořadí, oko místo skryje.")
        OrderList(
            rows = bar,
            onToggle = { section, visible ->
                SlovoShellPrefs.setBarHidden(ctx, section, hidden = visible)
                bar = SlovoShellPrefs.barWithVisibility(ctx)
            },
            onReorder = { order ->
                SlovoShellPrefs.setBarOrder(ctx, order)
                bar = SlovoShellPrefs.barWithVisibility(ctx)
            },
        )
        SettingSwitchRow(
            title = "Ťuk na otevřené místo vrátí na začátek",
            subtitle = "Např. v Knihovně nebo Pořadech odroluje zpět nahoru",
            checked = reselect,
            onCheckedChange = { SlovoShellPrefs.setReselectToTop(ctx, it); reselect = it },
        )
        Hint("Nabídka Víc pod avatarem vlevo nahoře — Nastavení je v ní vždy.")
        OrderList(
            rows = more,
            onToggle = { section, visible ->
                SlovoShellPrefs.setSectionHidden(ctx, section, hidden = visible)
                more = SlovoShellPrefs.reorderableWithVisibility(ctx)
            },
            onReorder = { order ->
                SlovoShellPrefs.setDrawerOrder(ctx, order)
                more = SlovoShellPrefs.reorderableWithVisibility(ctx)
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OrderList(
    rows: List<Pair<SlovoSection, Boolean>>,
    onToggle: (SlovoSection, Boolean) -> Unit,
    onReorder: (List<SlovoSection>) -> Unit,
) {
    rows.forEachIndexed { i, (section, visible) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.m),
            )
            Text(
                section.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onToggle(section, visible) }) {
                Icon(
                    if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (visible) "Skrýt" else "Zobrazit",
                )
            }
            IconButton(onClick = { onReorder(rows.map { it.first }.swapped(i, i - 1)) }, enabled = i > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Nahoru")
            }
            IconButton(onClick = { onReorder(rows.map { it.first }.swapped(i, i + 1)) }, enabled = i < rows.lastIndex) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Dolů")
            }
        }
    }
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also {
    val t = it[a]; it[a] = it[b]; it[b] = t
}
