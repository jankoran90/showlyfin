package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Plan FUSE — F0 (salvage z `ui-tv`): scrolluje tak, aby fokusovaná položka byla u horního okraje
 * (s offsetem pro titulek řady). Bez tohoto specu LazyColumn při horizontální D-pad navigaci v řadě
 * vertikálně „poskakuje".
 *
 * Použití: `LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec()` na vertikální LazyColumn,
 * uvnitř horizontálních řad obnovit default (aby LazyRow scrolloval normálně).
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
