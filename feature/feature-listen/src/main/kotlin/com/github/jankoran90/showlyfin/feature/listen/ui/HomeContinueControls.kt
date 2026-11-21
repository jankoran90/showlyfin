package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing

/**
 * AMPHORA (SHW-124) F8 — ovládání přes obal karty Pokračovat na Domů ([SlovoMediaCard] overlay):
 * odznak „hraje“, Ukončit poslech vlevo dole, ▶/⏸ vpravo dole (stejně jako dřív na dlaždici).
 */
@Composable
internal fun BoxScope.ContinueCoverControls(
    loaded: Boolean,
    playing: Boolean,
    finished: Boolean,
    onEndListening: (() -> Unit)?,
    onPlayPause: (() -> Unit)?,
) {
    if (loaded && !finished) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = "Hraje",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.s)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(Spacing.xs)
                .size(Spacing.l),
        )
    }
    if (onEndListening != null) {
        EndListeningButton(
            compact = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.s),
            onConfirm = onEndListening,
        )
    }
    if (onPlayPause != null) {
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s).size(Spacing.xxl + Spacing.xs),
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pauza" else "Poslouchat",
            )
        }
    }
}
