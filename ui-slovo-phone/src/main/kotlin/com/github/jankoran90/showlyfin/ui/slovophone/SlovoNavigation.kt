package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing

/**
 * AMPHORA (SHW-124) F1 / KO1 — spodní lišta. Dospělý: Domů · Knihovna · Pořady · Hledat · Fronta
 * (pořadí a viditelnost v Nastavení › Spodní lišta a nabídka Víc), Fronta s odznakem počtu dílů.
 * Dítě: Domů · Poslech · Stažené (bez avataru; rodič se dostane dál podržením levého horního rohu).
 * [selected] = null, když je otevřená položka z Víc (Objevit, Zdroje…) — žádné místo nesvítí.
 */
@Composable
fun SlovoBottomBar(
    items: List<SlovoSection>,
    selected: SlovoSection?,
    queueCount: Int,
    onSelect: (SlovoSection) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        items.forEach { section ->
            NavigationBarItem(
                selected = section == selected,
                onClick = { onSelect(section) },
                icon = {
                    if (section == SlovoSection.FRONTA && queueCount > 0) {
                        BadgedBox(badge = { Badge { Text(if (queueCount > 99) "99+" else queueCount.toString()) } }) {
                            Icon(section.icon, contentDescription = section.label)
                        }
                    } else {
                        Icon(section.icon, contentDescription = section.label)
                    }
                },
                label = { Text(barLabel(section), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** Dítě vidí „Stažené“ (kratší a srozumitelnější než Stahování). */
private fun barLabel(section: SlovoSection): String =
    if (section == SlovoSection.STAHOVANI) "Stažené" else section.label

/** Místa dětské lišty (AMPHORA F1; velké dlaždice přijdou ve F7). */
val KidsBarSections = listOf(SlovoSection.DOMU, SlovoSection.POSLECH, SlovoSection.STAHOVANI)

/**
 * AMPHORA F1 / KO1 — nabídka Víc pod avatarem (nahradila postranní menu): nahoře profil (ťuk = Profil,
 * přepnutí), pak Objevit · Zdroje · Stahování · Katalog (pořadí a skrytí v Nastavení), dole Nastavení.
 * Dítě (otevře jen rodič podržením rohu): Profil + Nastavení.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlovoMoreSheet(
    profileName: String?,
    isKids: Boolean,
    onSelect: (SlovoSection) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.l)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(SlovoSection.PROFIL) }
                    .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SlovoAvatar(profileName, size = Spacing.xxl + Spacing.s)
                Column(Modifier.weight(1f).padding(start = Spacing.l)) {
                    Text(profileName ?: "Profil", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Přepnout profil, PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs))
            val items = if (isKids) emptyList() else SlovoShellPrefs.moreOrder(ctx)
            items.forEach { MoreRow(it, onSelect) }
            if (items.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs))
            MoreRow(SlovoSection.NASTAVENI, onSelect)
        }
    }
}

@Composable
private fun MoreRow(section: SlovoSection, onSelect: (SlovoSection) -> Unit) {
    ListItem(
        headlineContent = { Text(section.label) },
        leadingContent = {
            Icon(section.icon, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable { onSelect(section) }.padding(horizontal = Spacing.s),
    )
}
