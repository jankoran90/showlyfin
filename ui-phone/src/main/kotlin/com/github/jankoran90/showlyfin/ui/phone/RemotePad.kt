package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Plan PILOT — virtuální D-pad „dálkáč" v dolní části Ovladače pro navigaci nativním UI na TV.
 *
 * Hybrid (volba usera): uprostřed gesto-plocha (swipe = směr, tap = OK) + viditelné šipky kolem.
 * Haptika na každou akci (přes [LocalHapticFeedback] — bez VIBRATE permission). Gesto-plocha má
 * [systemGestureExclusion], takže swipy uvnitř NEkradou systémová gesta telefonu (jinde fungují dál).
 *
 * Akce jdou přes [OvladacViewModel] → Jellyfin `GeneralCommand` (`MoveUp`…/`Select`/`Back`/`GoHome`);
 * Yellyfin na boxu je přeloží na injektnuté D-pad klávesy. Power tlačítko = MAESTRO zapnout/vypnout
 * sestavu (červené když TV vypnutá, zelené když běží ovladatelná session).
 */
@Composable
fun RemotePad(
    tvOn: Boolean,
    hasSession: Boolean,
    isPlaying: Boolean,
    vm: OvladacViewModel,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    fun act(enabled: Boolean, block: () -> Unit) { if (enabled) { tick(); block() } }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Horní řada: Power (červená/zelená) | Zpět | Domů.
            Row(verticalAlignment = Alignment.CenterVertically) {
                PowerButton(tvOn) { tick(); vm.togglePower() }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { act(hasSession) { vm.navBack() } }, enabled = hasSession) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                }
                IconButton(onClick = { act(hasSession) { vm.navHome() } }, enabled = hasSession) {
                    Icon(Icons.Filled.Home, contentDescription = "Domů")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Směrový kříž: gesto-plocha (swipe = směr, tap = OK) + viditelné šipky.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .systemGestureExclusion()
                    .pointerInput(hasSession) {
                        if (!hasSession) return@pointerInput
                        detectTapGestures(onTap = { tick(); vm.navSelect() })
                    }
                    .pointerInput(hasSession) {
                        if (!hasSession) return@pointerInput
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDrag = { _, drag -> dx += drag.x; dy += drag.y },
                            onDragEnd = {
                                val t = SWIPE_THRESHOLD_PX
                                if (abs(dx) > abs(dy)) {
                                    if (dx > t) { tick(); vm.navRight() }
                                    else if (dx < -t) { tick(); vm.navLeft() }
                                } else {
                                    if (dy > t) { tick(); vm.navDown() }
                                    else if (dy < -t) { tick(); vm.navUp() }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                ArrowButton(Icons.Filled.KeyboardArrowUp, "Nahoru", Modifier.align(Alignment.TopCenter)) { act(hasSession) { vm.navUp() } }
                ArrowButton(Icons.Filled.KeyboardArrowLeft, "Vlevo", Modifier.align(Alignment.CenterStart)) { act(hasSession) { vm.navLeft() } }
                ArrowButton(Icons.Filled.KeyboardArrowRight, "Vpravo", Modifier.align(Alignment.CenterEnd)) { act(hasSession) { vm.navRight() } }
                ArrowButton(Icons.Filled.KeyboardArrowDown, "Dolů", Modifier.align(Alignment.BottomCenter)) { act(hasSession) { vm.navDown() } }

                // Střed = OK (i tap kdekoliv v ploše).
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .pointerInput(hasSession) {
                            if (!hasSession) return@pointerInput
                            detectTapGestures(onTap = { tick(); vm.navSelect() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "OK",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Play/Pauza (rychlá akce i tady; jinak v bohatém ovládání výš).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { act(hasSession) { vm.playPause() } }, enabled = hasSession) {
                    Icon(
                        if (isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                        contentDescription = if (isPlaying) "Pauza" else "Přehrát",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.size(64.dp)) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit) {
    // Stavová barva (semantická výjimka z token palety): červená = TV vypnutá, zelená = běží.
    val color = if (on) Color(0xFF43A047) else Color(0xFFE53935)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PowerSettingsNew,
            contentDescription = if (on) "Vypnout sestavu" else "Zapnout sestavu",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

private const val SWIPE_THRESHOLD_PX = 60f
