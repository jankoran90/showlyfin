package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * MERIDIAN (SHW-119, user 2026-08-24: „tenký edge posuvník s náhledy počátečních písmenek … bez doteku
 * bude super tenký a vždy signalizovat kde se nachází, bublina jen když dám drag") — rychlý posuvník
 * u pravé hrany seznamu.
 *
 * Chování přesně podle zadání:
 *  - **v klidu vlásková lišta** s jezdcem, který pořád ukazuje polohu ve výpisu,
 *  - **při tažení** jezdec zesílí a vedle prstu vyskočí bublina s náhledem,
 *  - **co je v bublině, určuje aktuální řazení** ([label]) — u abecedy písmeno, u data rok, u stopáže
 *    čas. Posuvník nikdy neukazuje písmeno u seznamu, který podle abecedy seřazený není.
 *
 * Záměrně pracuje s POMĚREM (0..1), ne s indexem: volající si sám přeloží pozici na svůj seznam
 * (mřížka počítá řádky, seznam položky), takže komponenta nemusí znát ani rozvržení, ani typ obsahu.
 */
@Composable
internal fun FilmyFastScroller(
    /** Kolik položek výpis má. 0 = posuvník se nekreslí (není co posouvat). */
    itemCount: Int,
    /** Aktuální pozice ve výpisu jako podíl 0f..1f (z prvního viditelného indexu). */
    progress: Float,
    /** Náhled do bubliny pro danou pozici; null = pro tenhle výpis nemá co ukázat → bublina se nekreslí. */
    label: (index: Int) -> String?,
    /** Uživatel táhne na daný index — volající tam odscrolluje. */
    onScrollTo: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (itemCount <= MIN_ITEMS_FOR_SCROLLER) return
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val trackWidth: Dp by animateDpAsState(
        targetValue = if (dragging) ActiveTrackWidth else IdleTrackWidth,
        label = "fastScrollerWidth",
    )

    BoxWithConstraints(modifier.fillMaxHeight().width(TouchWidth)) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx = with(density) { ThumbHeight.toPx() }
        val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        fun indexFor(fraction: Float): Int =
            (fraction.coerceIn(0f, 1f) * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)

        val shownFraction = if (dragging) dragFraction else progress.coerceIn(0f, 1f)

        // Dotyková plocha je širší než kresba — na vláskovou lištu by se prstem trefoval jen kouzelník.
        Box(
            Modifier
                .fillMaxHeight()
                .width(TouchWidth)
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            onScrollTo(indexFor(dragFraction))
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            dragFraction = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            onScrollTo(indexFor(dragFraction))
                        },
                    )
                },
            contentAlignment = Alignment.TopEnd,
        ) {
            // Lišta na pozadí — v klidu sotva viditelná, ať neruší obsah.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(trackWidth)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            )
            // Jezdec — VŽDY viditelný, i bez doteku (uživatel chce pořád vidět, kde je).
            Box(
                Modifier
                    .offset(y = with(density) { (shownFraction * travelPx).toDp() })
                    .height(ThumbHeight)
                    .width(trackWidth)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
            )
        }

        // Bublina jen při tažení (user: „ať ukazuje jen když dám drag").
        if (dragging) {
            label(indexFor(dragFraction))?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            x = (-TouchWidth - 8.dp),
                            y = with(density) { (dragFraction * travelPx).toDp() },
                        ),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Pod tímhle počtem je posouvání prstem stejně rychlé — lišta by jen překážela. */
private const val MIN_ITEMS_FOR_SCROLLER = 20

private val IdleTrackWidth = 2.dp
private val ActiveTrackWidth = 6.dp
private val ThumbHeight = 48.dp
private val TouchWidth = 24.dp
