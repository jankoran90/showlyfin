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
internal fun HomeTheaterSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    expandedMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
) {
            CollapsibleSettingsSection("Domácí sestava (AVR)", expandedMap) {
            AvrSection(
                enabled = uiState.avrEnabled,
                host = uiState.avrHost,
                boxHost = uiState.avrBoxHost,
                boxMac = uiState.avrBoxMac,
                tvHost = uiState.avrTvHost,
                defaultVolume = uiState.avrDefaultVolume,
                volumeStep = uiState.avrVolumeStep,
                boxPairing = uiState.avrBoxPairing,
                boxPairStatus = uiState.avrBoxPairStatus,
                onEnabled = { viewModel.setAvrEnabled(it) },
                onHost = { viewModel.setAvrHost(it) },
                onBoxHost = { viewModel.setAvrBoxHost(it) },
                onBoxMac = { viewModel.setAvrBoxMac(it) },
                onTvHost = { viewModel.setAvrTvHost(it) },
                onDefaultVolume = { viewModel.setAvrDefaultVolume(it) },
                onVolumeStep = { viewModel.setAvrVolumeStep(it) },
                onPairBox = { viewModel.pairBox() },
            )
            }
            // DOCK (SHW-77): výchozí cíl castu „Na TV" (televize / Zenbook / jiné zařízení)
            CollapsibleSettingsSection("Cíl pro Na TV", expandedMap) {
            CastTargetSettingsSection()
            }
            // REVERB (SHW-82): zvukový výstup přehrávače na Zenbooku (Zenbook / AV receiver) + lip-sync
            CollapsibleSettingsSection("Zvukový výstup přehrávače", expandedMap) {
            DockAudioSettingsSection()
            }
}

/**
 * Plan MAESTRO — ovládání domácí sestavy. Když je zapnuté: (1) hlasitost v Ovladači cílí na AV
 * receiver (pravý master obýváku, box jen digitálně zeslabuje); (2) „Přehrát na TV" umí probrat
 * celou sestavu z vypnutého stavu (receiver + box + spuštění Yellyfinu) přes IP boxu a jeho MAC.
 */
// D-c: public (ne internal) — reuse ve Filmy Nastavení (ui-filmy-phone) pro blok „Domácí sestava".
@Composable
fun AvrSection(
    enabled: Boolean,
    host: String,
    boxHost: String,
    boxMac: String,
    tvHost: String,
    defaultVolume: String,
    volumeStep: String,
    boxPairing: Boolean,
    boxPairStatus: String?,
    onEnabled: (Boolean) -> Unit,
    onHost: (String) -> Unit,
    onBoxHost: (String) -> Unit,
    onBoxMac: (String) -> Unit,
    onTvHost: (String) -> Unit,
    onDefaultVolume: (String) -> Unit,
    onVolumeStep: (String) -> Unit,
    onPairBox: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ovládat domácí sestavu", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Hlasitost přes receiver + „Přehrát na TV” probudí celý obývák",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            AvrTextField(
                value = host,
                onCommit = onHost,
                label = "IP receiveru",
                placeholder = "např. 192.168.1.233",
                numeric = true,
            )
            Spacer(Modifier.height(8.dp))
            AvrTextField(
                value = boxHost,
                onCommit = onBoxHost,
                label = "IP TV boxu",
                placeholder = "např. 192.168.1.184",
                numeric = true,
            )
            // Plan MAESTRO — jednorázové spárování boxu (ADB) s diagnostikou. Box musí běžet, mít
            // zapnuté „Ladění po síti" a na TV potvrď „Vždy povolit". Jedno spojení → žádný loop dialogů.
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPairBox,
                enabled = !boxPairing && boxHost.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (boxPairing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (boxPairing) "Páruji box…" else "Spárovat box (ADB)")
            }
            boxPairStatus?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            AvrTextField(
                value = boxMac,
                onCommit = onBoxMac,
                label = "MAC TV boxu (pro probuzení)",
                placeholder = "např. 80:9d:65:fd:68:04",
                numeric = false,
            )
            Spacer(Modifier.height(8.dp))
            AvrTextField(
                value = tvHost,
                onCommit = onTvHost,
                label = "IP televize (zapnout/vypnout napřímo)",
                placeholder = "např. 192.168.1.102",
                numeric = true,
            )
            Spacer(Modifier.height(8.dp))
            AvrTextField(
                value = volumeStep,
                onCommit = onVolumeStep,
                label = "Krok hlasitosti +/- (jednotky AVR)",
                placeholder = "např. 3",
                numeric = true,
            )
            Spacer(Modifier.height(8.dp))
            AvrTextField(
                value = defaultVolume,
                onCommit = onDefaultVolume,
                label = "Výchozí hlasitost po zapnutí (prázdné = nechat na receiveru)",
                placeholder = "např. 45",
                numeric = true,
            )
            Text(
                "Vše na stejné Wi‑Fi. Box i televize potřebují zapnuté ladění po síti (port 5555) " +
                    "a jednou povolit telefon (dialog na obrazovce). Ukládá se hned při psaní.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}


@Composable
internal fun AvrTextField(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    placeholder: String,
    numeric: Boolean,
) {
    var local by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = local,
        // Ukládáme HNED při psaní (ne až při opuštění pole — to se nemuselo spustit a hodnota se
        // pak neuložila; device test #50).
        onValueChange = { local = it; onCommit(it.trim()) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}


