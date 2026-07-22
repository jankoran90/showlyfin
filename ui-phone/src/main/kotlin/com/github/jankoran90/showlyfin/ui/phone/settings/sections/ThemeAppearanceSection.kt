package com.github.jankoran90.showlyfin.ui.phone.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.Background
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinSkin
import kotlin.math.roundToInt

/**
 * CHORUS Osa 3 — blok „Motiv a barvy" (kánon z hubme): skin/akcent + pozadí + dynamické posuvníky
 * (Tónování ploch / Světlost ploch / Síla akcentu) s živým náhledem. Čte/píše [ThemePrefsViewModel]
 * (activity-scoped, tatáž instance jako v kořeni) → změny se projeví okamžitě napříč celou appkou.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSettingsSection(viewModel: ThemePrefsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Barevný motiv sjednocený s ostatními appkami. Pozadí u tmavých variant zůstává čistě černé, charakter ploch a barvy se plynule ladí posuvníky.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        SectionLabel("Barva (akcent)")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShowlyfinSkin.entries.forEach { skin ->
                SkinSwatch(
                    skin = skin,
                    selected = !state.useCustomAccent && state.skin == skin,
                    onClick = { viewModel.setSkin(skin) },
                )
            }
            CustomAccentSwatch(
                color = Color(state.customSeed),
                selected = state.useCustomAccent,
                onClick = { showPicker = true },
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Pozadí")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Background.entries.forEach { bg ->
                FilterChip(
                    selected = state.background == bg,
                    onClick = { viewModel.setBackground(bg) },
                    label = { Text(bg.displayName) },
                )
            }
        }
        Text(
            state.background.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(16.dp))
        SectionLabel("Plochy a barvy")
        ThemePreviewCard()
        Spacer(Modifier.height(6.dp))
        SliderRow(
            title = "Tónování ploch",
            value = (state.surfaceTint * 100).roundToInt(),
            onValueChange = { viewModel.setSurfaceTint(it / 100f) },
            valueLabel = { if (it == 0) "vypnuto" else "$it %" },
        )
        SliderHint("Kolik barvy prostupuje do karet a bloků (0 = neutrální šedé, výš = výraznější).")
        SliderRow(
            title = "Světlost ploch",
            value = (state.surfaceLightness * 100).roundToInt(),
            onValueChange = { viewModel.setSurfaceLightness(it / 100f) },
            valueLabel = { "$it %" },
        )
        SliderHint("Jak zvednuté jsou karty nad pozadím (0 = splývají s černou, výš = tmavě šedé). Pozadí zůstává černé.")
        SliderRow(
            title = "Síla akcentu",
            value = (state.accentStrength * 100).roundToInt(),
            onValueChange = { viewModel.setAccentStrength(it / 100f) },
            valueLabel = { if (it == 0) "jemný" else "$it %" },
        )
        SliderHint("Výraznost akcentní barvy (nadpisy, tlačítka, aktivní stavy).")
    }

    if (showPicker) {
        AccentColorPickerDialog(
            initial = state.customSeed,
            onDismiss = { showPicker = false },
            onPick = { argb ->
                viewModel.setCustomSeed(argb)
                showPicker = false
            },
        )
    }
}

/**
 * CHORUS Osa 3 — pokročilé doladění barev (tónování kontejnerů / kontrast textu / sytost) + náhled.
 * Doplňuje základní posuvníky výše; oddělené kvůli přehlednosti (progressive disclosure).
 */
@Composable
internal fun AdvancedColorSection(viewModel: ThemePrefsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Jemné doladění napříč appkou. Změny vidíš hned v náhledu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ThemePreviewCard()
        Spacer(Modifier.height(6.dp))
        SliderRow(
            title = "Tónování prvků",
            value = (state.containerTint * 100).roundToInt(),
            onValueChange = { viewModel.setContainerTint(it / 100f) },
            valueLabel = { if (it == 0) "neutrální" else "$it %" },
        )
        SliderHint("Kolik barvy dostanou chipy, štítky a odznaky (0 = neutrální šedé).")
        SliderRow(
            title = "Kontrast textu",
            value = (state.textContrast * 100).roundToInt(),
            onValueChange = { viewModel.setTextContrast(it / 100f) },
            valueLabel = { "$it %" },
        )
        SliderHint("Čitelnost textu na plochách (výš = ostřejší).")
        SliderRow(
            title = "Sytost barev",
            value = (state.accentChroma * 100).roundToInt(),
            onValueChange = { viewModel.setAccentChroma(it / 100f) },
            valueLabel = { "$it %" },
        )
        SliderHint("Sytost akcentní barvy (0 = do šeda, 100 % = plná barva).")
    }
}

// ————— Živý náhled —————

/** Živý náhled motivu — čte VÝHRADNĚ z aktivního [MaterialTheme], reaguje okamžitě na posuvníky. */
@Composable
private fun ThemePreviewCard(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Náhled motivu", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        // Řádek „média": cover + název + podtitul + hodnocení.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceContainer)
                .padding(10.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("S", style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer) }
            Column(Modifier.weight(1f)) {
                Text("Název pořadu", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                Text("Podtitul epizody", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Box(
                Modifier.clip(CircleShape).background(cs.tertiaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("82%", style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer) }
        }
        // Tlačítko + štítek.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 16.dp, vertical = 7.dp),
            ) { Text("Přehrát", style = MaterialTheme.typography.labelLarge, color = cs.onPrimary) }
            Box(
                Modifier.clip(CircleShape).background(cs.secondaryContainer).padding(horizontal = 12.dp, vertical = 7.dp),
            ) { Text("štítek", style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer) }
        }
        // Průběh přehrávání.
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(CircleShape).background(cs.primary))
        }
    }
}

// ————— Helper prvky (kánon CHORUS) —————

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun SkinSwatch(skin: ShowlyfinSkin, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = skin.seed,
            modifier = Modifier.size(52.dp),
            onClick = onClick,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = "Vybráno", tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(skin.displayName, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CustomAccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(52.dp),
            onClick = onClick,
            border = BorderStroke(
                3.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (selected) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = "Vlastní barva",
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Vlastní", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueLabel: (Int) -> String = { it.toString() },
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel(value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..100f,
            steps = 19,
        )
    }
}

@Composable
private fun SliderHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ————— Vlastní akcent (HSV picker, bez knihovny) —————

@Composable
private fun AccentColorPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val startHsv = remember(initial) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(Color(initial).toArgb(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var sat by remember { mutableFloatStateOf(startHsv[1].coerceAtLeast(0.15f)) }
    var value by remember { mutableFloatStateOf(startHsv[2].coerceAtLeast(0.35f)) }

    val current = Color.hsv(hue, sat, value)
    val currentArgb = current.toArgb().toLong() and 0xFFFFFFFFL

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(currentArgb) }) { Text("Použít") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
        title = { Text("Vlastní akcent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(current))
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Barva pro tlačítka, nadpisy a zvýraznění.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SpectrumLabel("Odstín")
                GradientTrack(Brush.horizontalGradient((0..12).map { Color.hsv(it * 30f % 360f, sat.coerceAtLeast(0.5f), value.coerceAtLeast(0.6f)) }))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                SpectrumLabel("Sytost")
                GradientTrack(Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))))
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                SpectrumLabel("Jas")
                GradientTrack(Brush.horizontalGradient(listOf(Color.hsv(hue, sat, 0f), Color.hsv(hue, sat, 1f))))
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
            }
        },
    )
}

@Composable
private fun SpectrumLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GradientTrack(brush: Brush) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
}
