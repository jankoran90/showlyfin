package com.github.jankoran90.showlyfin.feature.playback.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder
import kotlinx.coroutines.delay

/**
 * TENFOOT (SHW-87) F2c — 10-foot transport lišta přehrávače pro TV (vzor Kodi Arctic Fuse).
 * Vše ovladatelné čistě D-padem: časová osa se scrubbingem (Left/Right o [seekStepMs]) + fokusovatelná
 * tlačítka ⏪ ⏯ ⏩ (+ CC / audio). Auto-fokus přistane na ⏯ ([playPauseFocusRequester]). Barvy/tvary
 * z motivu (design guard), fokus přes kanonický [tvFocusBorder] (záře + lift). Telefon používá dosavadní
 * `Slider` lištu — tahle větev se aktivuje jen na TV.
 *
 * Scrubbing NEseekuje na každý stisk: posouvá lokální náhled a po ~0,5 s klidu commitne jediným [onSeekTo]
 * (žádný rebuffer při držení šipky). [onInteraction] resetuje auto-hide, [onDismiss] (Back) lištu schová.
 */
@Composable
internal fun TvTransportBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    seekStepMs: Long,
    hasAudioChoice: Boolean,
    audioProblem: Boolean,
    subtitleActive: Boolean,
    subtitleColor: Color,
    playPauseFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }

    val shownMs = if (scrubbing) scrubMs else positionMs
    val remainingMs = (durationMs - shownMs).coerceAtLeast(0L)
    val progress = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Držení šipky posouvá náhled; po klidu se pozice commitne jediným seekem (bez rebufferu na každý krok).
    LaunchedEffect(scrubMs, scrubbing) {
        if (scrubbing) {
            delay(500)
            onSeekTo(scrubMs)
            scrubbing = false
        }
    }

    val scrubBy: (Long) -> Unit = { delta ->
        val base = if (scrubbing) scrubMs else positionMs
        scrubMs = (base + delta).coerceIn(0L, durationMs.coerceAtLeast(0L))
        scrubbing = true
        onInteraction()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Back) { onDismiss(); true } else false
            }
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f)),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Časy: uplynulo vlevo, zbývá vpravo.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = fmtTime(shownMs),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "−${fmtTime(remainingMs)}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Časová osa — fokusovatelná, Left/Right scrubuje o krok.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .tvFocusBorder(shape = RoundedCornerShape(14.dp))
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft -> { scrubBy(-seekStepMs); true }
                        Key.DirectionRight -> { scrubBy(seekStepMs); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val trackWidth = maxWidth
                val knob = 14.dp
                // Neaktivní dráha
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                )
                // Uplynulá část = akcent motivu
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                // Ukazatel pozice
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = trackWidth * progress - knob / 2)
                        .size(knob)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        // Tlačítka: ⏪ ⏯ ⏩ ...... CC 🔊
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(glyph = "⏪", onClick = { onInteraction(); onSeekTo((positionMs - seekStepMs).coerceAtLeast(0L)) })
                TransportButton(
                    glyph = if (isPlaying) "⏸" else "▶",
                    focusRequester = playPauseFocusRequester,
                    emphasized = true,
                    onClick = { onInteraction(); onPlayPause() },
                )
                TransportButton(glyph = "⏩", onClick = {
                    onInteraction()
                    val target = if (durationMs > 0) (positionMs + seekStepMs).coerceAtMost(durationMs) else positionMs + seekStepMs
                    onSeekTo(target)
                })
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasAudioChoice) {
                    TransportButton(
                        glyph = "🔊",
                        tint = if (audioProblem) MaterialTheme.colorScheme.error else Color.White,
                        onClick = { onInteraction(); onAudio() },
                    )
                }
                TransportButton(
                    glyph = "CC",
                    tint = if (subtitleActive) subtitleColor else Color.White,
                    onClick = { onInteraction(); onSubtitles() },
                )
            }
        }
    }
}

/** Jedno tlačítko transport lišty — glyf, fokusovatelné D-padem, highlight přes [tvFocusBorder]. */
@Composable
private fun TransportButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    tint: Color = Color.White,
    emphasized: Boolean = false,
) {
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusBorder(shape = CircleShape)
            .clip(CircleShape)
            .background(
                if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.08f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (glyph == "CC") 14.dp else 16.dp, vertical = 10.dp)
            .width(if (glyph == "CC") 40.dp else 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = tint,
            style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}
