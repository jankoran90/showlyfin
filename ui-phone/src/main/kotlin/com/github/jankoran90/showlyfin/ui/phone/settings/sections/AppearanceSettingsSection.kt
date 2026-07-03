package com.github.jankoran90.showlyfin.ui.phone.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.feature.uploader.UploaderViewModel
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.data.uploader.model.StreamFilterPrefs
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.jankoran90.showlyfin.ui.phone.*
import com.github.jankoran90.showlyfin.ui.phone.settings.*

@Composable
internal fun AppearanceSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isAdmin: Boolean,
    expandedMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
) {
            // Plan WARDEN W2: ne-admin user vidí Vzhled jen pokud ho šablona nezamkla (lock-mapa).
            if (isAdmin || ProfileConfig.LockKeys.APPEARANCE !in uiState.lockedKeys) {
                DetailModeSection()
            } else {
                LockedByAdminNote()
            }
    Spacer(Modifier.height(16.dp))
    // CHORUS Osa 3 (kánon Písmo): patkové Newsreader vs systémové + rozsah + velikost. Plošné (motiv).
    CollapsibleSettingsSection("Písmo", expandedMap) {
        FontSettingsSection()
    }
    Spacer(Modifier.height(16.dp))
    CollapsibleSettingsSection("Pořadí sekcí", expandedMap) {
            if (ProfileConfig.LockKeys.ORDER in uiState.lockedKeys) {
                LockedByAdminNote()
            } else {
                Text(
                    "Podrž a táhni pro změnu pořadí záložek a podsekcí.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                OrderEditor(
                    title = "Záložky",
                    orderedKeys = uiState.orderedSections,
                    label = { NAV_ORDER_LABELS[it] ?: it },
                    onReorder = { viewModel.reorderSections(it) },
                )
                OrderEditor(
                    title = "Podsekce Sleduj",
                    orderedKeys = uiState.orderedSubsections,
                    label = { SUBSECTION_ORDER_LABELS[it] ?: it },
                    onReorder = { viewModel.reorderSubsections(it) },
                )
            }
    }
}

// Plan STRATA Fáze E — popisky pro editor pořadí (drag&drop).
internal val NAV_ORDER_LABELS = mapOf(
    ProfileConfig.Sections.SLEDUJ to "Sleduj",
    ProfileConfig.Sections.OVLADAC to "Ovladač",
    ProfileConfig.Sections.POSLECH to "Poslech",
)

internal val SUBSECTION_ORDER_LABELS = mapOf(
    ProfileConfig.Sections.KNIHOVNA to "Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Objevit",
    ProfileConfig.Sections.HISTORIE to "Historie",
    ProfileConfig.Sections.NA_RD to "Na RD",
)


/**
 * Plan STRATA Fáze E + CANVAS (SHW-47) E — editor pořadí seznamu klíčů s popiskem, přeskládávání
 * **šipkami ▲▼** (drag&drop se na telefonu pral se scrollem). [orderedKeys] = aktuální pořadí;
 * [onReorder] dostane NOVÉ pořadí klíčů. Skryje se pro <2 položky.
 */
@Composable
internal fun OrderEditor(
    title: String,
    orderedKeys: List<String>,
    label: (String) -> String,
    onReorder: (List<String>) -> Unit,
) {
    if (orderedKeys.size < 2) return
    Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    orderedKeys.forEachIndexed { i, keyItem ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(keyItem), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            IconButton(onClick = { onReorder(orderedKeys.moved(i, i - 1)) }, enabled = i > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nahoru", tint = Color.White)
            }
            IconButton(onClick = { onReorder(orderedKeys.moved(i, i + 1)) }, enabled = i < orderedKeys.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dolů", tint = Color.White)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailModeSection(
    viewModel: DetailPrefsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Detail z knihovny", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Jak otevřít detail filmu/seriálu z knihovny. Objevit a Watchlist mají vždy bohatý detail.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bohatý detail", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(
                        text = if (s.rich) "kolekce, režisér, studio, obsazení, ČSFD" else "jen obrázek, popis, kolekce a Přehrát",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Switch(checked = s.rich, onCheckedChange = { viewModel.setRich(it) })
            }
            if (s.rich) {
                Spacer(Modifier.height(8.dp))
                Text("Zobrazit sekce:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                DetailSectionCheckRow("Tvůrci (herci, režie, scénář, kamera)", s.showCreators) { viewModel.setCreators(it) }
                DetailSectionCheckRow("Kolekce", s.showCollections) { viewModel.setCollections(it) }
                DetailSectionCheckRow("Od stejného režiséra", s.showDirector) { viewModel.setDirector(it) }
                DetailSectionCheckRow("Od stejného studia", s.showStudio) { viewModel.setStudio(it) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Popis – řádků ve sbaleném stavu:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailPrefsViewModel.PLOT_LINE_OPTIONS.forEach { n ->
                    FilterChip(
                        selected = s.plotLines == n,
                        onClick = { viewModel.setPlotLines(n) },
                        label = { Text(if (n == 0) "Vše" else "$n") },
                    )
                }
            }
            // CANVAS A/E: pořadí akčních tlačítek v hero detailu (šipky ▲▼).
            Spacer(Modifier.height(12.dp))
            Text("Pořadí akčních tlačítek na detailu:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            val actionLabels = mapOf(
                "favorite" to "Oblíbené", "play" to "Přehrát zde", "tv" to "Na TV",
                "stremio" to "Stremio", "download" to "Stáhnout", "watchlist" to "Chci vidět",
            )
            s.actionOrder.forEachIndexed { i, key ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${actionLabels[key] ?: key}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    IconButton(onClick = { viewModel.setActionOrder(s.actionOrder.moved(i, i - 1)) }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Nahoru", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.setActionOrder(s.actionOrder.moved(i, i + 1)) }, enabled = i < s.actionOrder.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "Dolů", tint = Color.White)
                    }
                }
            }
        }
    }
}


@Composable
internal fun DetailSectionCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}



/** CHORUS Osa 3 (kánon Písmo): volba fontu (systémové/Newsreader) + rozsah + velikost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontSettingsSection(viewModel: FontPrefsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Systémové bezpatkové, nebo patkové Newsreader (jednotné s ostatními appkami).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.serif, onClick = { viewModel.setSerif(false) }, label = { Text("Systémové") })
            FilterChip(selected = state.serif, onClick = { viewModel.setSerif(true) }, label = { Text("Newsreader") })
        }
        if (state.serif) {
            Spacer(Modifier.height(12.dp))
            Text("Kde se použije", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.headingOnly, onClick = { viewModel.setHeadingOnly(false) }, label = { Text("Celá aplikace") })
                FilterChip(selected = state.headingOnly, onClick = { viewModel.setHeadingOnly(true) }, label = { Text("Jen nadpisy") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Velikost textu", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontPrefsViewModel.SCALE_OPTIONS.forEach { pct ->
                FilterChip(selected = state.scalePct == pct, onClick = { viewModel.setScalePct(pct) }, label = { Text("$pct %") })
            }
        }
    }
}
