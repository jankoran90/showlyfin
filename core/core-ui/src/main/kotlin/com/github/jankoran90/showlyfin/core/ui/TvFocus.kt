package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * Prstenec při fokusu. Default = **akcent z motivu** (`primary`) v šířce 3 dp — na TV výrazně
 * viditelnější než dřívější tenký bílý prstenec (user feedback 2026-07-12: bílé zesvětlení bylo
 * na světlých plochách sotva vidět). Kde by akcentní prstenec splynul s akcentním pozadím (primární
 * tlačítko), předej kontrastní `color` (např. `onPrimary`).
 */
@Composable
fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    width: Dp = 3.dp,          // základní tloušťka záře (decentní, ne obří)
    color: Color = Color.Unspecified,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    // Výchozí barva = akcent aktuálního motivu (primary). Fokus = JEN akcentní záře, ŽÁDNÉ zvětšení prvku
    // (user 2026-07-13: zvětšování vybraného objektu pryč, stačí akcent). Bez scale se navíc nemění vizuální
    // bounds karty → jistota, že fokus nikdy nezavdá příčinu ke svislému posunu řady (bump).
    val glowColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        label = "tvFocusGlow",
    )
    return this
        .onFocusChanged { focused = it.isFocused }
        .drawBehind {
            if (glow <= 0.01f) return@drawBehind
            val outline = shape.createOutline(size, layoutDirection, this)
            // Měkká záře BEZ tvrdé linky: vrstvené obrysové tahy, u objektu nejsilnější → graduálně do ztracena.
            // Kreslíme od nejširšího/nejslabšího po nejužší/nejsilnější (nejsilnější navrch, u hrany objektu).
            val layers = 6
            val base = width.toPx()
            for (j in layers - 1 downTo 0) {
                val frac = j / (layers - 1f)            // 0 = u objektu, 1 = nejdál ven
                val strokeW = base * (1f + frac * 3f)   // 1×..4× → decentní dosah
                val a = glow * 0.5f * (1f - frac)       // u objektu neprůhledné, ven do ztracena
                drawOutline(
                    outline = outline,
                    color = glowColor,
                    alpha = a,
                    style = Stroke(width = strokeW),
                )
            }
        }
}

/**
 * Fokus highlight aktivní **jen na TV** (na telefonu vrací `this` beze změny).
 * Prvek musí být `clickable`/`focusable`, aby fokus reálně dostal — tohle jen kreslí prstenec.
 */
@Composable
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(8.dp),
    width: Dp = 3.dp,
    color: Color = Color.Unspecified,
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
