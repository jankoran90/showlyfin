package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.home.SidebarItem
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder

/**
 * TENFOOT — TV DOMOV REDESIGN. Levý sidebar (vzor yellyfin NavDrawer, ale bez androidx.tv):
 * kompaktní ikonky (68 dp) → na fokus rozbalí na ikonu + název (~220 dp). Aktivní sekce (Domů) = akcent.
 * Konfigurovatelný obsah (default minimal: Domů/Hledat/Nastavení) přes [SidebarItem].
 */
@Composable
fun TvHomeSidebar(
    items: List<SidebarItem>,
    active: SidebarItem,
    onSelect: (SidebarItem) -> Unit,
    onMove: (SidebarItem, up: Boolean) -> Unit = { _, _ -> },
    onOpenProfiles: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // COUCH T6: podržení položky → režim přesunu (D-pad nahoru/dolů posouvá, OK/Zpět potvrdí).
    var reordering by remember { mutableStateOf<SidebarItem?>(null) }
    val reorderFocus = remember { FocusRequester() }
    val width by animateDpAsState(if (expanded) 224.dp else 68.dp, label = "sidebarWidth")
    // Po každém přesunu (změna indexu) i vstupu do režimu udrž fokus na přesouvané položce.
    LaunchedEffect(reordering, reordering?.let { items.indexOf(it) }) {
        if (reordering != null) runCatching { reorderFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { expanded = it.hasFocus; if (!it.hasFocus) reordering = null }
            .focusGroup()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(vertical = 28.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            key(item) {
                SidebarRow(
                    item = item,
                    expanded = expanded,
                    active = item == active,
                    reordering = reordering == item,
                    focusRequester = if (reordering == item) reorderFocus else null,
                    onClick = { if (reordering != null) reordering = null else onSelect(item) },
                    onLongClick = { reordering = item },
                    onMove = { up -> onMove(item, up) },
                    onExitReorder = { reordering = null },
                )
            }
        }
        // COUCH T5: přepínač JF profilu dole ve sidebaru.
        Spacer(Modifier.weight(1f))
        ProfileButton(expanded = expanded, onClick = onOpenProfiles)
    }
}

@Composable
private fun ProfileButton(expanded: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .tvFocusBorder(shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = "Přepnout profil",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Profil",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarRow(
    item: SidebarItem,
    expanded: Boolean,
    active: Boolean,
    reordering: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onExitReorder: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .tvFocusBorder(shape = shape)
            // V režimu přesunu odchyť D-pad nahoru/dolů (posun) a OK/Zpět (potvrzení) DŘÍV než fokus.
            .onPreviewKeyEvent { e ->
                if (reordering && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionUp -> { onMove(true); true }
                        Key.DirectionDown -> { onMove(false); true }
                        Key.DirectionCenter, Key.Enter, Key.Back -> { onExitReorder(); true }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (reordering) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rowTint = if (reordering) MaterialTheme.colorScheme.primary else tint
        Icon(
            imageVector = if (reordering) Icons.Filled.UnfoldMore else item.icon(),
            contentDescription = item.label,
            tint = rowTint,
            modifier = Modifier.size(28.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = if (reordering) "↑↓ přesouvej · OK hotovo" else item.label,
                style = MaterialTheme.typography.titleMedium,
                color = rowTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun SidebarItem.icon(): ImageVector = when (this) {
    SidebarItem.DOMU -> Icons.Filled.Home
    SidebarItem.OBJEVOVAT -> Icons.Filled.Explore
    SidebarItem.TRAKT -> Icons.Filled.Recommend
    SidebarItem.KNIHOVNA -> Icons.Filled.VideoLibrary
    SidebarItem.OBLIBENE -> Icons.Filled.Favorite
    SidebarItem.HLEDAT -> Icons.Filled.Search
    SidebarItem.NASTAVENI -> Icons.Filled.Settings
}
