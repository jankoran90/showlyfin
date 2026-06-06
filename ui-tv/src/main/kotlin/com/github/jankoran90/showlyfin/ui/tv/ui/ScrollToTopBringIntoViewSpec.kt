package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolluje tak, aby fokusovaná položka byla u horního okraje (s offsetem pro titulek řady).
 * Bez tohoto specu LazyColumn při horizontální navigaci v řadě vertikálně „poskakuje".
 * Použití: LocalBringIntoViewSpec provides tento spec na LazyColumn, uvnitř řad obnovit default
 * (aby horizontální LazyRow scrolloval normálně).
 */
@OptIn(ExperimentalFoundationApi::class)
class ScrollToTopBringIntoViewSpec(
    private val spaceAbovePx: Float = 120f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = offset - spaceAbovePx
}
