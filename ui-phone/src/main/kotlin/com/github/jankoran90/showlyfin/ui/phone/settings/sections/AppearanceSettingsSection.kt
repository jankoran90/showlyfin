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
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel

@Composable
internal fun AppearanceSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isAdmin: Boolean,
    expandedMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
) {
            // Plan WARDEN W2: ne-admin user vidí Vzhled jen pokud ho šablona nezamkla (lock-mapa).
            CollapsibleSettingsSection("Zobrazení detailu", expandedMap) {
                if (isAdmin || ProfileConfig.LockKeys.APPEARANCE !in uiState.lockedKeys) {
                    DetailModeSection()
                } else {
                    LockedByAdminNote()
                }
            }
    Spacer(Modifier.height(16.dp))
    // PANORAMA (SHW-78): počet sloupců mřížky (globální) napříč Objevit/Chci vidět/Historie/Na RD.
    CollapsibleSettingsSection("Zobrazení sekcí", expandedMap) {
        SectionGridColumnsSettings()
    }
    Spacer(Modifier.height(16.dp))
    // PANORAMA (SHW-78): rozvržení a styl karet Knihovny (řady/mřížka, plakát/na šířku/+popis).
    CollapsibleSettingsSection("Zobrazení knihovny", expandedMap) {
        LibraryDisplaySettings()
    }
    Spacer(Modifier.height(16.dp))
    // CHORUS Osa 3 (kánon motivu z hubme): skin/akcent + pozadí + dynamické posuvníky s náhledem.
    CollapsibleSettingsSection("Motiv a barvy", expandedMap) {
        ThemeSettingsSection()
    }
    Spacer(Modifier.height(16.dp))
    CollapsibleSettingsSection("Barvy a plochy — pokročilé", expandedMap) {
        AdvancedColorSection()
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    orderedKeys.forEachIndexed { i, keyItem ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(keyItem), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { onReorder(orderedKeys.moved(i, i - 1)) }, enabled = i > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nahoru", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { onReorder(orderedKeys.moved(i, i + 1)) }, enabled = i < orderedKeys.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dolů", tint = MaterialTheme.colorScheme.onSurface)
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
            Text("Detail z knihovny", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Jak otevřít detail filmu/seriálu z knihovny. Objevit a Watchlist mají vždy bohatý detail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bohatý detail", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (s.rich) "kolekce, režisér, studio, obsazení, ČSFD" else "jen obrázek, popis, kolekce a Přehrát",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.rich, onCheckedChange = { viewModel.setRich(it) })
            }
            if (s.rich) {
                Spacer(Modifier.height(8.dp))
                Text("Zobrazit sekce:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DetailSectionCheckRow("Tvůrci (herci, režie, scénář, kamera)", s.showCreators) { viewModel.setCreators(it) }
                DetailSectionCheckRow("Kolekce", s.showCollections) { viewModel.setCollections(it) }
                DetailSectionCheckRow("Od stejného režiséra", s.showDirector) { viewModel.setDirector(it) }
                DetailSectionCheckRow("Od stejného studia", s.showStudio) { viewModel.setStudio(it) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Popis – řádků ve sbaleném stavu:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Pořadí akčních tlačítek na detailu:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            val actionLabels = mapOf(
                "favorite" to "Oblíbené", "play" to "Přehrát zde", "tv" to "Na TV",
                "stremio" to "Stremio", "download" to "Stáhnout", "watchlist" to "Chci vidět",
            )
            s.actionOrder.forEachIndexed { i, key ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${actionLabels[key] ?: key}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { viewModel.setActionOrder(s.actionOrder.moved(i, i - 1)) }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Nahoru", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { viewModel.setActionOrder(s.actionOrder.moved(i, i + 1)) }, enabled = i < s.actionOrder.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "Dolů", tint = MaterialTheme.colorScheme.onSurface)
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.serif, onClick = { viewModel.setSerif(false) }, label = { Text("Systémové") })
            FilterChip(selected = state.serif, onClick = { viewModel.setSerif(true) }, label = { Text("Newsreader") })
        }
        if (state.serif) {
            Spacer(Modifier.height(12.dp))
            Text("Kde se použije", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.headingOnly, onClick = { viewModel.setHeadingOnly(false) }, label = { Text("Celá aplikace") })
                FilterChip(selected = state.headingOnly, onClick = { viewModel.setHeadingOnly(true) }, label = { Text("Jen nadpisy") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Velikost textu", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontPrefsViewModel.SCALE_OPTIONS.forEach { pct ->
                FilterChip(selected = state.scalePct == pct, onClick = { viewModel.setScalePct(pct) }, label = { Text("$pct %") })
            }
        }
    }
}


/** PANORAMA (SHW-78): globální počet sloupců mřížky (0 = Auto). Ukládá `grid_columns` do trakt_prefs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionGridColumnsSettings() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
    var cols by remember { mutableStateOf(prefs.getInt("grid_columns", 0)) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text("Počet sloupců v mřížce", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Platí pro mřížku v Objevit, Chci vidět, Historii a Na RD. Auto = podle šířky displeje.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Auto", 2 to "2", 3 to "3", 4 to "4", 5 to "5").forEach { (n, l) ->
                    FilterChip(
                        selected = cols == n,
                        onClick = { cols = n; prefs.edit().putInt("grid_columns", n).apply() },
                        label = { Text(l) },
                    )
                }
            }
        }
    }
}

/** PANORAMA (SHW-78): rozvržení + styl karet Knihovny. Ukládá `library_layout`/`library_style` do trakt_prefs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryDisplaySettings() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
    var layout by remember { mutableStateOf(prefs.getString("library_layout", "rows") ?: "rows") }
    var style by remember { mutableStateOf(prefs.getString("library_style", "grid") ?: "grid") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text("Rozvržení", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Řady = vodorovné kategorie (jako doteď). Mřížka = svislé dlaždice po kategoriích.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("rows" to "Řady", "grid" to "Mřížka").forEach { (v, l) ->
                    FilterChip(
                        selected = layout == v,
                        onClick = { layout = v; prefs.edit().putString("library_layout", v).apply() },
                        label = { Text(l) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Styl karet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("grid" to "Plakát", "landscape" to "Na šířku", "landscape_detail" to "Na šířku + popis").forEach { (v, l) ->
                    FilterChip(
                        selected = style == v,
                        onClick = { style = v; prefs.edit().putString("library_style", v).apply() },
                        label = { Text(l) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Jednotlivou řadu přepneš zvlášť podržením jejího názvu v Knihovně.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                style = "landscape"
                val e = prefs.edit()
                prefs.all.keys.filter { it.startsWith("libstyle_") }.forEach { e.remove(it) }
                e.putString("library_style", "landscape").apply()
            }) { Text("Všechny řady na šířku") }
        }
    }
}
