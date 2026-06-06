package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState

/** Jedna položka postranního menu — data-driven, řaditelná. */
data class TvDrawerEntry(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavDrawer(
    entries: List<TvDrawerEntry>,
    moveMode: Boolean = false,
    movingKey: String? = null,
    onToggleMove: (String) -> Unit = {},
    onMove: (key: String, up: Boolean) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            val expanded = drawerValue == DrawerValue.Open
            Column(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E))
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.forEach { entry ->
                    DrawerRow(
                        entry = entry,
                        expanded = expanded,
                        moveMode = moveMode,
                        isMoving = moveMode && movingKey == entry.key,
                        onToggleMove = { onToggleMove(entry.key) },
                        onMove = { up -> onMove(entry.key, up) },
                    )
                }
            }
        },
    ) {
        content()
    }
}

@Composable
private fun DrawerRow(
    entry: TvDrawerEntry,
    expanded: Boolean,
    moveMode: Boolean,
    isMoving: Boolean,
    onToggleMove: () -> Unit,
    onMove: (up: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Při přesunu drží fokus na pohybující se položce (i když změní pozici v seznamu)
    LaunchedEffect(isMoving) {
        if (isMoving) runCatching { focusRequester.requestFocus() }
    }

    val accent = MaterialTheme.colorScheme.primary
    val bg = when {
        isMoving -> accent
        entry.selected -> accent.copy(alpha = 0.28f)
        focused -> Color.White.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val tint = when {
        isMoving -> Color.Black
        entry.selected || focused -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ke ->
                if (!isMoving) return@onKeyEvent false
                if (ke.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ke.key) {
                    Key.DirectionUp -> { onMove(true); true }
                    Key.DirectionDown -> { onMove(false); true }
                    Key.Back -> { onToggleMove(); true }
                    else -> false
                }
            }
            .combinedClickable(
                onClick = { if (isMoving) onToggleMove() else if (!moveMode) entry.onClick() },
                onLongClick = { if (!moveMode) onToggleMove() },
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = tint,
        )
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (isMoving) "${entry.label}  ↕" else entry.label,
                style = MaterialTheme.typography.bodyMedium,
                color = tint,
            )
        }
    }
}
