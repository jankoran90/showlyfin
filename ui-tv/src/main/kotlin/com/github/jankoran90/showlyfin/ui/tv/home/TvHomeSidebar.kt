package com.github.jankoran90.showlyfin.ui.tv.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 224.dp else 68.dp, label = "sidebarWidth")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { expanded = it.hasFocus }
            .focusGroup()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(vertical = 28.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            SidebarRow(
                item = item,
                expanded = expanded,
                active = item == active,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun SidebarRow(
    item: SidebarItem,
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
            imageVector = item.icon(),
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun SidebarItem.icon(): ImageVector = when (this) {
    SidebarItem.DOMU -> Icons.Filled.Home
    SidebarItem.OBJEVOVAT -> Icons.Filled.Explore
    SidebarItem.KNIHOVNA -> Icons.Filled.VideoLibrary
    SidebarItem.OBLIBENE -> Icons.Filled.Favorite
    SidebarItem.HLEDAT -> Icons.Filled.Search
    SidebarItem.NASTAVENI -> Icons.Filled.Settings
}
