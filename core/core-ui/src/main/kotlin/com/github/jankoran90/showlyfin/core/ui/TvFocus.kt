package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Plan FUSE — F0: fokusový toolkit pro sjednocené UI.
 *
 * Telefonní obrazovky používají `clickable`, který na TV automaticky dělá prvek fokusovatelným
 * D-padem; chybí jen vizuální highlight. Tyhle modifikátory přidávají highlight **jen na TV**
 * a na telefonu jsou no-op (nula vizuální změny, nula overheadu navíc kromě čtení formfactoru).
 *
 * Rukopis (bílý 2dp prstenec) je převzatý z přehrávače (`tvFocusBorder`, TV-PARITY TV-C v1.39.0),
 * ať je fokus napříč appkou jednotný.
 */

/** Bílý prstenec při fokusu — bez ohledu na formfactor (přímý ekvivalent toho z přehrávače). */
@Composable
fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    width: Dp = 2.dp,
    color: Color = Color.White,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = if (focused) width else 0.dp,
            color = if (focused) color else Color.Transparent,
            shape = shape,
        )
}

/**
 * Fokus highlight aktivní **jen na TV** (na telefonu vrací `this` beze změny).
 * Prvek musí být `clickable`/`focusable`, aby fokus reálně dostal — tohle jen kreslí prstenec.
 */
@Composable
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(8.dp),
    width: Dp = 2.dp,
    color: Color = Color.White,
): Modifier = if (isTvFormFactor()) this.tvFocusBorder(shape, width, color) else this

/**
 * 10-foot overscan: TV ořezává okraje obrazovky → obsah u kraje může být neviditelný.
 * Na TV přidá ~5 % bezpečné odsazení; na telefonu no-op.
 */
@Composable
fun Modifier.tvOverscan(
    horizontal: Dp = 48.dp,
    vertical: Dp = 27.dp,
): Modifier = if (isTvFormFactor()) this.padding(PaddingValues(horizontal = horizontal, vertical = vertical)) else this
