package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/**
 * Sdílené chování „skryj hlavičku (filtry / hledání) při scrollu dolů, ukaž při scrollu nahoru".
 * Vstup = aktuální index první viditelné položky + scroll offset (z LazyListState / LazyGridState).
 * Stejná logika jako v sekci Objevit → konzistentní napříč všemi sekcemi s vyhledáváním.
 */
@Composable
fun rememberScrollHeaderVisibility(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
): State<Boolean> {
    val visible = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow { firstVisibleItemIndex() to firstVisibleItemScrollOffset() }
            .collect { (index, offset) ->
                val direction = when {
                    index < prevIndex -> 1
                    index > prevIndex -> -1
                    offset < prevOffset - 4 -> 1
                    offset > prevOffset + 4 -> -1
                    else -> 0
                }
                if (index == 0 && offset < 60) visible.value = true
                else if (direction == 1) visible.value = true
                else if (direction == -1) visible.value = false
                prevIndex = index
                prevOffset = offset
            }
    }
    return visible
}
