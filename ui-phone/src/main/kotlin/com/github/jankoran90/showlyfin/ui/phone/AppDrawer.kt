package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * COMPASS C1 — levý vysouvací drawer = JEDINÁ navigace (rozhodnutí usera 2026-06-14, spodní lišta
 * zrušena). Vykreslí stejnou sadu cílů jako dřív spodní lišta (`navItems`, vč. ⭐ Oblíbení), v pořadí
 * a viditelnosti dle profilu (STRATA). Barvy z theme (UNISON).
 */
@Composable
internal fun AppDrawer(
    items: List<ShellNavItem>,
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Showlyfin",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            items.forEach { item ->
                NavigationDrawerItem(
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                    selected = selected == item.dest,
                    onClick = { onSelect(item.dest) },
                    colors = NavigationDrawerItemDefaults.colors(),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
