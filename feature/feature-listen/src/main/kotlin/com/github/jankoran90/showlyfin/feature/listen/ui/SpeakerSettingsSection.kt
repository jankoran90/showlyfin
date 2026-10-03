package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.SpeakerSettingsViewModel
import com.github.jankoran90.showlyfin.feature.listen.tv.SpeakerPrefs

/**
 * Nastavení → Poslech → „Přehrávání na repro" (2026-10-03). Akce „Na repro (receiver)" v menu dílu
 * pošle zvuk na Chromecast u receiveru; server receiver zapne a přepne vstup. Parita Nastavení.
 */
@Composable
fun SpeakerSettingsSection(viewModel: SpeakerSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val p = state.prefs
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Přehrávání na repro",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Akce „Na repro (receiver)\" v menu dílu pustí zvuk na Chromecastu připojeném k receiveru. " +
                "Server receiver sám zapne a přepne na zvolený vstup. Funguje i mimo domácí Wi-Fi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HostField("Adresa Chromecastu", p.castHost, SpeakerPrefs.DEF_CAST_HOST, viewModel::setCastHost)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Zapnout a přepnout receiver", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Vypni, pokud Chromecast nevisí na receiveru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = p.avrEnabled, onCheckedChange = viewModel::setAvrEnabled)
        }
        if (p.avrEnabled) {
            HostField("Adresa receiveru", p.avrHost, SpeakerPrefs.DEF_AVR_HOST, viewModel::setAvrHost)
            Text("Vstup receiveru s Chromecastem", style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpeakerPrefs.INPUTS.forEach { (code, label) ->
                    val selected = p.avrInput == code
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setAvrInput(code) },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
        state.nowPlaying?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { viewModel.refreshStatus() }, enabled = !state.busy) {
            Text(if (state.busy) "Zjišťuju…" else "Co hraje na repru?")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { viewModel.control("pause") }) { Text("Pauza") }
            TextButton(onClick = { viewModel.control("play") }) { Text("Pokračovat") }
            TextButton(onClick = { viewModel.control("stop") }) { Text("Zastavit") }
        }
    }
}

@Composable
private fun HostField(label: String, value: String, default: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onSave(it.ifBlank { default }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}
