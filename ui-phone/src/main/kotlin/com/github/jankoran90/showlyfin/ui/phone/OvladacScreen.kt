package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.github.jankoran90.showlyfin.data.jellyfin.StreamTrack
import com.github.jankoran90.showlyfin.data.maestro.AvrController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TICKS_PER_MS = 10_000L

/** Krok hlasitosti JF session na klik (AVR krok je konfigurovatelný v Nastavení, default 3). */
private const val JF_VOLUME_STEP = 5

/** RELAY — sekce „Ovladač": real-time sledování + dálkové ovládání běžící Jellyfin TV session. */
@Composable
fun OvladacScreen(
    onOpenDetail: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: OvladacViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    // Polling jen dokud je sekce na obrazovce.
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Ovladač",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        // Přepínač zařízení (když je víc remote-control TV session).
        if (state.sessions.size > 1) {
            DeviceSwitcher(state.sessions, state.selectedId, vm::selectDevice)
            Spacer(Modifier.height(12.dp))
        }

        // Napájení domácí sestavy (zapnout/vypnout receiver + box) — vždy, když je sestava povolená.
        if (state.avrEnabled) {
            SystemPowerCard(state.sceneStatus, vm)
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.loading && state.current == null -> Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.noCreds -> Hint("Jellyfin není přihlášen", "Přihlas se v Nastavení.")

            state.current == null -> Hint(
                "Nic nehraje na TV",
                if (state.sessions.isEmpty()) "Žádná aktivní TV session."
                else "Dostupné: " + state.sessions.joinToString { it.deviceName },
            )

            else -> NowPlaying(state.current!!, state.coverUrl, state.externalTitle, state.externalPosterUrl, onOpenDetail, vm)
        }

        // CONSOLE: nastavení obrazu/titulků TV streamu — jen pro externí (RD/Stremio) přehrávání,
        // kde to box (yellyfin ExternalPlaybackPage) umí aplikovat na PlayerView.
        if (state.isExternal && state.current != null) {
            Spacer(Modifier.height(12.dp))
            DisplaySettingsCard(state, vm)
        }

        // PILOT: virtuální D-pad „dálkáč" v dolní části — navigace nativního UI na TV.
        // Viditelný i bez session (power tlačítko umí sestavu zapnout).
        if (!state.noCreds) {
            Spacer(Modifier.height(16.dp))
            RemotePad(
                tvOn = state.sessions.isNotEmpty(),
                hasSession = state.current != null,
                isPlaying = state.current?.isPlaying == true,
                vm = vm,
            )
        }
    }
}

@Composable
private fun SystemPowerCard(sceneStatus: String?, vm: OvladacViewModel) {
    val busy = sceneStatus != null
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Domácí sestava", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { vm.powerOnSystem() },
                    enabled = !busy,
                    label = { Text("Zapnout") },
                    leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { vm.powerOffSystem() },
                    enabled = !busy,
                    label = { Text("Vypnout") },
                    leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(18.dp)) },
                )
            }
            if (sceneStatus != null) {
                Spacer(Modifier.height(6.dp))
                Text(sceneStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * CONSOLE (SHW-39): nastavení obrazu a titulků běžícího externího streamu na TV (z telefonu).
 * Posílá se na box přes FERRYCFG kanál; box aplikuje na PlayerView/ExoPlayer. Sbalitelné.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DisplaySettingsCard(state: OvladacViewModel.UiState, vm: OvladacViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Subtitles, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Obraz a titulky", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(if (expanded) "Skrýt" else "Upravit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                // Poměr obrazu.
                Text("Obraz", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                val modes = listOf("fit" to "Přizpůsobit", "zoom" to "Vyplnit (zoom)", "fill" to "Roztáhnout")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (key, label) ->
                        SegmentedButton(
                            selected = state.displayResizeMode == key,
                            onClick = { vm.setResizeMode(key) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Velikost titulků.
                StepperRow("Velikost titulků", "${state.subFontSizeSp}", { vm.nudgeSubFontSize(-2) }, { vm.nudgeSubFontSize(2) })
                Spacer(Modifier.height(8.dp))
                // Pozice (svislý posun).
                StepperRow("Pozice titulků", "${state.subBottomMarginPct} %", { vm.nudgeSubMargin(-2) }, { vm.nudgeSubMargin(2) })
                Spacer(Modifier.height(12.dp))
                // Barva titulků — 4 uložitelné pozice (tap = použít, dlouhý stisk = přepsat z palety).
                var pickerForSlot by remember { mutableStateOf(-1) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Barva titulků", style = MaterialTheme.typography.labelLarge)
                    Text("Dlouhý stisk = upravit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.subColorSlots.forEachIndexed { i, argb ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Color(argb))
                                .border(
                                    width = if (state.subColorArgb == argb) 3.dp else 1.dp,
                                    color = if (state.subColorArgb == argb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .combinedClickable(
                                    onClick = { vm.setSubColor(argb) },
                                    onLongClick = { pickerForSlot = i },
                                ),
                        )
                    }
                }
                if (pickerForSlot >= 0) {
                    ColorPickerDialog(
                        initial = state.subColorSlots.getOrElse(pickerForSlot) { 0xFFFFFFFF.toInt() },
                        onDismiss = { pickerForSlot = -1 },
                        onPick = { argb -> vm.saveColorToSlot(pickerForSlot, argb); pickerForSlot = -1 },
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Časový posun titulků (sync) — re-timestamp na boxu, krok ±0,1 s.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Posun titulků", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (state.subOffsetMs == 0) "synchronní" else "%+.1f s".format(state.subOffsetMs / 1000f),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { vm.nudgeSubOffset(-100) }) { Icon(Icons.Filled.Remove, "Titulky dřív") }
                        TextButton(onClick = { vm.resetSubOffset() }) { Text("0") }
                        FilledTonalIconButton(onClick = { vm.nudgeSubOffset(100) }) { Icon(Icons.Filled.Add, "Titulky později") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // TEMPO: snímkování titulků — když titulky postupně utíkají (jsou v jiném FPS než video,
                // typicky PAL 25 vs film 23,976), konstantní posun nestačí → přeškálovat časy poměrem FPS.
                Text("Snímkování titulků", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (state.subFpsScale == 1.0) "Když titulky postupně utíkají (jiné FPS než video)"
                    else "přeškálováno ×%.4f".format(state.subFpsScale),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val fpsPresets = listOf(
                    "Synchronní" to 1.0,
                    "25→23,976" to 25.0 / 23.976,
                    "23,976→25" to 23.976 / 25.0,
                    "24→25" to 24.0 / 25.0,
                    "25→24" to 25.0 / 24.0,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fpsPresets.forEach { (label, scale) ->
                        FilterChip(
                            selected = kotlin.math.abs(state.subFpsScale - scale) < 0.0005,
                            onClick = { vm.setSubFpsScale(scale) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}

/** CONSOLE: plný výběr barvy (HSV spektrum) → uloží na zvolenou pozici. */
@Composable
private fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(argb) }) { Text("Uložit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
        title = { Text("Barva titulků") },
        text = {
            Column {
                // Náhled.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color(argb)),
                )
                Spacer(Modifier.height(12.dp))
                Text("Odstín", style = MaterialTheme.typography.labelMedium)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Sytost", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text("Jas", style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
            }
        },
    )
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "Snížit") }
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "Zvýšit") }
        }
    }
}

@Composable
private fun DeviceSwitcher(
    sessions: List<JellyfinSessionSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sessions.forEach { s ->
            FilterChip(
                selected = s.sessionId == selectedId,
                onClick = { onSelect(s.sessionId) },
                label = { Text(s.deviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Tv, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun NowPlaying(
    s: JellyfinSessionSummary,
    coverUrl: String?,
    externalTitle: String?,
    externalPosterUrl: String?,
    onOpenDetail: (String) -> Unit,
    vm: OvladacViewModel,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            val jfCover = if (coverUrl != null && s.itemId != null) coverUrl else null
            // JF položka → cover z knihovny (klik = detail v knihovně). Externí stream → poster z TMDB
            // (klik = vrať na kartu filmu / RD sekci). U knihovny i streamu stejný vzhled.
            val effectiveCover = jfCover ?: externalPosterUrl
            Row {
                if (effectiveCover != null) {
                    AsyncImage(
                        model = effectiveCover,
                        contentDescription = s.nowPlayingTitle ?: externalTitle,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (jfCover != null && s.itemId != null) onOpenDetail(s.itemId!!) else vm.openCastDetail()
                            },
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            s.deviceName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.nowPlayingTitle ?: externalTitle ?: "Nic nehraje",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (s.nowPlayingTitle == null && externalTitle != null) {
                        Text(
                            "Externí stream na TV",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val sub = s.nowPlayingSubtitle
                    if (!sub.isNullOrBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (s.runtimeTicks > 0L) {
                        Text(
                            buildString {
                                append(formatDuration(s.runtimeTicks))
                                endClock(s)?.let { append(" · skončí ve $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val overview = s.overview
            if (!overview.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Progress + seek slider.
            Spacer(Modifier.height(12.dp))
            ProgressSeek(s, vm)

            // Hlavní ovládání.
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.seekBy(-10_000L) }) {
                    Icon(Icons.Filled.Replay10, "Zpět 10 s", Modifier.size(34.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.playPause() }) {
                    Icon(
                        if (s.isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                        "Přehrát/Pauza",
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.seekBy(30_000L) }) {
                    Icon(Icons.Filled.Forward30, "Vpřed 30 s", Modifier.size(34.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.stopPlayback() }) {
                    Icon(Icons.Filled.Stop, "Stop", Modifier.size(30.dp))
                }
            }

            // Hlasitost.
            Spacer(Modifier.height(4.dp))
            VolumeRow(s, vm)

            // Titulky / audio / info.
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubtitlePicker(s, vm, Modifier.weight(1f))
                AudioPicker(s, vm, Modifier.weight(1f))
                if (s.mediaInfoLines.isNotEmpty()) InfoButton(s.mediaInfoLines)
            }
        }
    }
}

@Composable
private fun ProgressSeek(s: JellyfinSessionSummary, vm: OvladacViewModel) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val liveFraction =
        if (s.runtimeTicks > 0L) (s.positionTicks.toFloat() / s.runtimeTicks).coerceIn(0f, 1f) else 0f
    val fraction = if (scrubbing) scrubFraction else liveFraction

    if (s.runtimeTicks > 0L && s.canSeek) {
        Slider(
            value = fraction,
            onValueChange = { scrubbing = true; scrubFraction = it },
            onValueChangeFinished = { vm.seekToFraction(scrubFraction); scrubbing = false },
        )
    } else if (s.runtimeTicks > 0L) {
        LinearProgressIndicator(progress = { liveFraction }, modifier = Modifier.fillMaxWidth())
    }
    if (s.runtimeTicks > 0L) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((fraction * s.runtimeTicks).toLong()), style = MaterialTheme.typography.labelSmall)
            Text("-" + formatDuration(((1f - fraction) * s.runtimeTicks).toLong()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VolumeRow(s: JellyfinSessionSummary, vm: OvladacViewModel) {
    val st by vm.state.collectAsStateWithLifecycle()
    // Když je v Nastavení zapnutý AVR, hlasitost cílí na něj (pravý master obýváku — box jen
    // digitálně zeslabuje); jinak fallback na hlasitost JF session.
    val avr = st.avrEnabled
    val max = if (avr) AvrController.MAX_VOLUME else 100
    val stepSize = if (avr) st.avrVolumeStep else JF_VOLUME_STEP
    val effMuted = if (avr) st.avrMuted else s.isMuted
    // Známá hlasitost: u AVR může být null (zatím nepřečteno) → zobrazíme „—", krok ale funguje
    // RELATIVNĚ (MVLUP/MVLDOWN) → nikdy neskočí na 0/ticho. JF session jede absolutně z lokálu.
    val knownVol: Int? = if (avr) st.avrVolume else (s.volumeLevel ?: 50)

    var jfLocal by remember(knownVol) { mutableIntStateOf(knownVol ?: 50) }
    fun step(delta: Int) {
        if (avr) {
            vm.avrVolumeStep(delta)
        } else {
            jfLocal = (jfLocal + delta).coerceIn(0, max)
            vm.applyVolume(jfLocal)
        }
    }
    val displayVol = if (avr) knownVol else jfLocal
    val frac by animateFloatAsState(
        targetValue = if (effMuted || displayVol == null) 0f else (displayVol.toFloat() / max).coerceIn(0f, 1f),
        label = "volumeBar",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.toggleVolumeMute() }) {
            Icon(if (effMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Ztlumit")
        }
        // AVR: +/- povolené vždy (relativní krok od reálné úrovně AVR). JF: dle lokálu.
        FilledTonalIconButton(onClick = { step(-stepSize) }, enabled = !effMuted && (avr || jfLocal > 0)) {
            Icon(Icons.Filled.Remove, "Snížit hlasitost")
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = when {
                    effMuted -> "ztlumeno"
                    displayVol == null -> "—"
                    else -> "$displayVol"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FilledTonalIconButton(onClick = { step(stepSize) }, enabled = !effMuted && (avr || jfLocal < max)) {
            Icon(Icons.Filled.Add, "Zvýšit hlasitost")
        }
    }
    if (avr) {
        Text(
            text = if (st.avrReachable) "Hlasitost ovládá AVR" else "AVR nedostupný — zkontroluj síť/IP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        )
    }
}

@Composable
private fun SubtitlePicker(s: JellyfinSessionSummary, vm: OvladacViewModel, modifier: Modifier) {
    if (s.subtitleTracks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val current = s.subtitleTracks.firstOrNull { it.index == s.currentSubtitleIndex }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(current?.label ?: "Titulky vyp.", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Subtitles, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Vypnuto") }, onClick = { vm.setSubtitle(-1); open = false })
            s.subtitleTracks.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.setSubtitle(t.index); open = false })
            }
        }
    }
}

@Composable
private fun AudioPicker(s: JellyfinSessionSummary, vm: OvladacViewModel, modifier: Modifier) {
    if (s.audioTracks.size <= 1) return
    var open by remember { mutableStateOf(false) }
    val current = s.audioTracks.firstOrNull { it.index == s.currentAudioIndex }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(current?.label ?: "Audio", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.VolumeUp, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            s.audioTracks.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.setAudio(t.index); open = false })
            }
        }
    }
}

@Composable
private fun InfoButton(lines: List<String>) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.Info, "Informace o stopách") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Zavřít") } },
            title = { Text("Audio / Video") },
            text = { Column { lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } },
        )
    }
}

@Composable
private fun Hint(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Délka v ticks → „1 h 24 min" / „14 min". */
private fun formatDuration(ticks: Long): String {
    val totalMin = (ticks / TICKS_PER_MS / 1000 / 60).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** Hodina, ve kterou přehrávání skončí (teď + zbývající runtime), formát HH:mm. */
private fun endClock(s: JellyfinSessionSummary): String? {
    if (s.runtimeTicks <= 0L) return null
    val remainingMs = ((s.runtimeTicks - s.positionTicks).coerceAtLeast(0L)) / TICKS_PER_MS
    val end = Date(System.currentTimeMillis() + remainingMs)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(end)
}
