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
import com.github.jankoran90.showlyfin.data.uploader.StreamPresetStore
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
internal fun StreamingSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    credLocked: Boolean,
    expandedMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    onOpenUploader: () -> Unit,
) {
          if (credLocked) {
            LockedByAdminNote()
          } else {
            CollapsibleSettingsSection("Uploader", expandedMap) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Nahrávání filmů, fronta downloadů, Sdílej.cz, Smart Remux.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenUploader, modifier = Modifier.fillMaxWidth()) {
                        Text("Otevřít Uploader")
                    }
                }
            }
            }
            // Plan EVEN — DRC/normalizér filmu (opt-in, default Vyp). Jen telefon (na TV passthrough).
            CollapsibleSettingsSection("Zvuk (DRC)", expandedMap) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    ListenGroupTitle("Hlasitost a vyrovnání (film)")
                    ListenInfoText("Krotí hlasité přestřelky (např. na noc) a jemně zvedá dialogy. Výchozí vypnuto. Platí jen pro přehrávání v telefonu — přehrávání na TV (přes box) se nedotkne, passthrough do AVR zůstane. Projeví se při příštím spuštění filmu.")
                    ListenChipRow(
                        title = "Úroveň",
                        options = listOf("Vyp" to 0, "Mírná" to 1, "Střední" to 2, "Noční" to 3),
                        selected = uiState.movieDrcLevel,
                        onSelect = { viewModel.setMovieDrcLevel(it) },
                    )
                }
            }
            }
            CollapsibleSettingsSection("Stremio / Comet výsledky", expandedMap) {
            StremioFilterSection(
                sf = uiState.streamFilter,
                loading = uiState.streamFilterLoading,
                error = uiState.streamFilterError,
                onUpdate = { viewModel.updateStreamFilter(it) },
                onMoveFallback = { i, dir -> viewModel.moveFallback(i, dir) },
                onToggleFallback = { k, e -> viewModel.toggleFallback(k, e) },
                onReload = { viewModel.loadStreamFilter() },
            )
            }
            // QUARRY (SHW-79): chování hledání zdroje na Sdílej.cz.
            CollapsibleSettingsSection("Sdílej.cz", expandedMap) {
                val ctx = LocalContext.current
                val sdilejPrefs = remember { ctx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
                var yearPm1 by remember { mutableStateOf(sdilejPrefs.getBoolean("sdilej_year_pm1", true)) }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Když se film na Sdílej.cz nenajde (rok v metadatech z TMDB/IMDB bývá o rok mimo), appka automaticky zkusí i sousední roky. Hledaný text (název + rok) jde vždy ručně upravit přímo v nabídce Sdílej.cz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilterSwitchRow("Při nule zkusit rok ±1", yearPm1) { v ->
                            yearPm1 = v
                            sdilejPrefs.edit().putBoolean("sdilej_year_pm1", v).apply()
                        }
                    }
                }
            }
            // DINGO — per-zařízení preset přehrávání (slabý HEVC dekodér v autě). Jen TOTO zařízení.
            CollapsibleSettingsSection("Přehrávání (toto zařízení)", expandedMap) {
                val ppCtx = LocalContext.current
                val pp = remember { ppCtx.getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE) }
                var codec by remember { mutableStateOf(pp.getString(StreamPresetStore.KEY_CODEC, StreamPresetStore.CODEC_ANY) ?: StreamPresetStore.CODEC_ANY) }
                var maxRes by remember { mutableStateOf(pp.getString(StreamPresetStore.KEY_MAXRES, StreamPresetStore.RES_ANY) ?: StreamPresetStore.RES_ANY) }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        ListenInfoText("Nastav jednou pro tohle zařízení a nesahej. V autě (slabý head unit, který seká na HEVC) zvol H.264 — appka pak při výběru zdroje preferuje H.264 verzi filmu, když existuje. Doma na TV/AVR nech Auto. Volba je jen na tomhle zařízení, ostatní se nezmění.")
                        Spacer(Modifier.height(8.dp))
                        PresetChipRow(
                            title = "Preferovaný video kodek",
                            options = listOf(
                                "Auto" to StreamPresetStore.CODEC_ANY,
                                "H.264" to StreamPresetStore.CODEC_AVC,
                                "HEVC" to StreamPresetStore.CODEC_HEVC,
                            ),
                            selected = codec,
                            onSelect = { codec = it; pp.edit().putString(StreamPresetStore.KEY_CODEC, it).apply() },
                        )
                        Spacer(Modifier.height(8.dp))
                        PresetChipRow(
                            title = "Strop rozlišení",
                            options = listOf(
                                "Bez limitu" to StreamPresetStore.RES_ANY,
                                "1080p" to "1080p",
                                "720p" to "720p",
                            ),
                            selected = maxRes,
                            onSelect = { maxRes = it; pp.edit().putString(StreamPresetStore.KEY_MAXRES, it).apply() },
                        )
                    }
                }
            }
          }
    if (!credLocked) {
        Spacer(Modifier.height(16.dp))
        CollapsibleSettingsSection("RealDebrid", expandedMap) {
            RealDebridSection()
        }
    }
}

internal val FALLBACK_LABELS = linkedMapOf(
    "cached" to "RD cached", "czsk" to "CZ/SK", "res4k" to "4K", "res1080p" to "1080p",
    "sizeSweet" to "Velikost 2–8 GB", "hevc" to "HEVC", "av1" to "AV1", "noHdr" to "Bez HDR",
    "hevcSdr" to "HEVC bez HDR", "atmos" to "Atmos", "ch71" to "7.1", "ch51" to "5.1",
    "remux" to "REMUX", "highBitrate" to "Bitrate", "resolution" to "Rozlišení", "score" to "Skóre",
)


@Composable
internal fun FilterLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}


@Composable
internal fun FilterSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


@Composable
internal fun FilterStepRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedButton(onClick = onMinus, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPlus, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("+") }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MultiChipRow(label: String, options: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    FilterLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) })
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioFilterSection(
    sf: StreamFilterPrefs?,
    loading: Boolean,
    error: String?,
    onUpdate: ((StreamFilterPrefs) -> StreamFilterPrefs) -> Unit,
    onMoveFallback: (Int, Int) -> Unit,
    onToggleFallback: (String, Boolean) -> Unit,
    onReload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            when {
                sf == null && loading -> CircularProgressIndicator()
                sf == null -> {
                    Text("Dostupné po přihlášení k Uploaderu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReload) { Text("Načíst") }
                }
                else -> {
                    fun toggleIn(list: List<String>, v: String) = if (v in list) list - v else list + v

                    MultiChipRow("Rozlišení", listOf("4K", "1080p", "720p"), sf.allowedResolutions) { r ->
                        onUpdate { it.copy(allowedResolutions = toggleIn(it.allowedResolutions, r)) }
                    }
                    Spacer(Modifier.height(12.dp))

                    FilterLabel("Počet výsledků: ${sf.maxResults}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 50).forEach { n ->
                            FilterChip(selected = sf.maxResults == n, onClick = { onUpdate { it.copy(maxResults = n) } }, label = { Text("$n") })
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    FilterStepRow("Min. velikost: ${"%.1f".format(sf.minSizeGB)} GB",
                        onMinus = { onUpdate { it.copy(minSizeGB = (it.minSizeGB - 0.5).coerceAtLeast(0.0)) } },
                        onPlus = { onUpdate { it.copy(minSizeGB = it.minSizeGB + 0.5) } })
                    FilterStepRow("Max. velikost: ${"%.0f".format(sf.maxSizeGB)} GB",
                        onMinus = { onUpdate { it.copy(maxSizeGB = (it.maxSizeGB - 1).coerceAtLeast(1.0)) } },
                        onPlus = { onUpdate { it.copy(maxSizeGB = it.maxSizeGB + 1) } })
                    FilterStepRow("Sweet-spot: ${"%.0f".format(sf.sizeSweetSpot.min)}–${"%.0f".format(sf.sizeSweetSpot.max)} GB",
                        onMinus = { onUpdate { it.copy(sizeSweetSpot = it.sizeSweetSpot.copy(max = (it.sizeSweetSpot.max - 1).coerceAtLeast(it.sizeSweetSpot.min))) } },
                        onPlus = { onUpdate { it.copy(sizeSweetSpot = it.sizeSweetSpot.copy(max = it.sizeSweetSpot.max + 1)) } })
                    Spacer(Modifier.height(8.dp))

                    FilterSwitchRow("Přesné hledání (výchozí)", sf.strict) { v -> onUpdate { it.copy(strict = v) } }
                    FilterSwitchRow("Vždy zahrnout CZ/SK", sf.guaranteeCzSk) { v -> onUpdate { it.copy(guaranteeCzSk = v) } }
                    Spacer(Modifier.height(12.dp))

                    FilterLabel("RealDebrid — už uložené (DebridSearch)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "off" to "Vypnuto",
                            "hash" to "Označit v nabídce",
                            "search" to "Hledat na RD",
                            "both" to "Obojí",
                        ).forEach { (key, lbl) ->
                            FilterChip(selected = sf.rdFirstMode == key, onClick = { onUpdate { it.copy(rdFirstMode = key) } }, label = { Text(lbl) })
                        }
                    }
                    Text(
                        "Co už máš na RealDebrid → ukáže se nahoře jako 💾 a přehraje hned (i při opakovaném sledování).",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Přesné filtry (jen v režimu Přesné)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    MultiChipRow("Kodek", listOf("HEVC", "AV1", "AVC"), sf.videoCodecs) { v -> onUpdate { it.copy(videoCodecs = toggleIn(it.videoCodecs, v)) } }
                    Spacer(Modifier.height(8.dp))
                    MultiChipRow("Audio", listOf("Atmos", "TrueHD", "DTS-HD", "DDP"), sf.audioFormats) { v -> onUpdate { it.copy(audioFormats = toggleIn(it.audioFormats, v)) } }
                    Spacer(Modifier.height(8.dp))
                    MultiChipRow("Kanály", listOf("7.1", "5.1"), sf.channels) { v -> onUpdate { it.copy(channels = toggleIn(it.channels, v)) } }
                    Spacer(Modifier.height(8.dp))
                    MultiChipRow("Jazyk audia", listOf("CZ", "SK", "EN"), sf.audioLanguages) { v -> onUpdate { it.copy(audioLanguages = toggleIn(it.audioLanguages, v)) } }
                    Spacer(Modifier.height(16.dp))

                    FilterLabel("Pořadí fallbacků (priorita řazení)")
                    sf.fallbackOrder.forEachIndexed { i, key ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${FALLBACK_LABELS[key] ?: key}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { onMoveFallback(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Nahoru", tint = MaterialTheme.colorScheme.onSurface) }
                            IconButton(onClick = { onMoveFallback(i, 1) }, enabled = i < sf.fallbackOrder.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Dolů", tint = MaterialTheme.colorScheme.onSurface) }
                            Text("✕", Modifier.padding(horizontal = 8.dp).clickable { onToggleFallback(key, false) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val available = FALLBACK_LABELS.keys.filter { it !in sf.fallbackOrder }
                    if (available.isNotEmpty()) {
                        FilterLabel("Přidat kritérium")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            available.forEach { k ->
                                FilterChip(selected = false, onClick = { onToggleFallback(k, true) }, label = { Text(FALLBACK_LABELS[k] ?: k) })
                            }
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Chyba: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// DINGO — jednoduchý řádek volby (label + chipy) pro per-zařízení preset přehrávání.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChipRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

