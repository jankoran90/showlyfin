package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * Plan STRATA Fáze E / GLIDE — drag&drop reorder pro KRÁTKÉ seznamy (sekce/podsekce/knihovny) v
 * obyčejném [Column] (ne Lazy). [handle] aplikuj **jen na úchyt ☰** (ne na celý řádek): tah za úchyt
 * začne okamžitě a gesto se zkonzumuje, takže se NEPERE s rodičovským scrollem stránky (dřív byl
 * handle na celém řádku + long-press → drag spouštěl zároveň scroll). Po překročení poloviny výšky
 * sousedního řádku se zavolá [onMove] (živý swap). Bez externí knihovny.
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, isDragging: Boolean, handle: Modifier) -> Unit,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights: SnapshotStateMap<Int, Int> = remember { mutableStateMapOf() }
    val haptics = LocalHapticFeedback.current

    Column(modifier) {
        items.forEachIndexed { index, item ->
            val dragging = draggedIndex == index
            Box(
                Modifier
                    .onGloballyPositioned { heights[index] = it.size.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
            ) {
                val handle = Modifier.pointerInput(items.size, key(item)) {
                    detectDragGestures(
                        onDragStart = {
                            draggedIndex = index
                            dragOffset = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = { draggedIndex = -1; dragOffset = 0f },
                        onDragCancel = { draggedIndex = -1; dragOffset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val cur = draggedIndex
                            if (cur < 0) return@detectDragGestures
                            val h = heights[cur] ?: return@detectDragGestures
                            when {
                                dragOffset > h / 2f && cur < items.lastIndex -> {
                                    onMove(cur, cur + 1); draggedIndex = cur + 1; dragOffset -= h
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragOffset < -h / 2f && cur > 0 -> {
                                    onMove(cur, cur - 1); draggedIndex = cur - 1; dragOffset += h
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                    )
                }
                row(item, dragging, handle)
            }
        }
    }
}

/** Posune prvek z [from] na [to] (immutable). */
fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }
