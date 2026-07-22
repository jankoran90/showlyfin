package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Slovo (EXCISE/SHW-103) — postranní menu poslechové appky (zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyDrawer]). Poslech/Objevit/Zdroje + oddělené
 * Nastavení dole. Scrollovatelné (na škálovaných displejích se položky nesmí uříznout). Vzhled z motivu.
 */
@Composable
fun SlovoDrawer(
    current: SlovoSection,
    onSelect: (SlovoSection) -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "Slovo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 16.dp),
            )
            DrawerSectionLabel("Poslech")
            SlovoShellPrefs.drawerOrder.forEach { DrawerRow(it, current, onSelect) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
            DrawerRow(SlovoSection.NASTAVENI, current, onSelect)
        }
    }
}

/** Kategorický nadpis v menu (oranžový, drobný). */
@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerRow(
    section: SlovoSection,
    current: SlovoSection,
    onSelect: (SlovoSection) -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(section.icon, contentDescription = null) },
        label = { Text(section.label) },
        selected = section == current,
        onClick = { onSelect(section) },
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
