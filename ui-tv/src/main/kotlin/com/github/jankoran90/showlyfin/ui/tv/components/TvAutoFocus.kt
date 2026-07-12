package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.first

/**
 * TENFOOT — robustní autofokus na PRVNÍ kartu obsahu.
 *
 * Bug (do OTA 292): `requestFocus()` se volal dřív, než byl cílový uzel umístěný (placed) → výjimka
 * „not placed" spolknutá `runCatching`, Boolean klíč `LaunchedEffect` = žádný retry → fokus zůstal na
 * liště/nadpisu. Tady počkáme přes `snapshotFlow`, až je cíl reálně v layoutu, a teprve pak fokusneme.
 *
 * [isTargetPlaced] čte snapshot-backed layout (např. `gridState.layoutInfo.visibleItemsInfo.any { it.index == 0 }`
 * nebo `listState…`). [enabled] = teprve až sekce má obsah (ne prázdná/loading), jinak korutina čeká věčně.
 */
@Composable
fun AutoFocusFirst(
    focusRequester: FocusRequester,
    enabled: Boolean,
    isTargetPlaced: () -> Boolean,
) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { isTargetPlaced() }.first { it }
        runCatching { focusRequester.requestFocus() }
    }
}
