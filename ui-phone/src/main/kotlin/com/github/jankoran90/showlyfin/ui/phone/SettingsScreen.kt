package com.github.jankoran90.showlyfin.ui.phone

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.feature.uploader.UploaderViewModel
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.data.uploader.model.StreamFilterPrefs
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Nastavení", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // Jellyfin sekce
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Jellyfin", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                if (uiState.jellyfinConnected) {
                    Text(
                        text = "Server: ${uiState.jellyfinServerUrl}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    if (uiState.jellyfinUserName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Profil: ${uiState.jellyfinUserName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Připojeno",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.disconnectJellyfin() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Odhlásit Jellyfin")
                    }
                } else {
                    Text(
                        text = "Nenastaveno — otevři kartu Jellyfin a přihlas se",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Profil & parental controls
        if (uiState.jellyfinConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Profil", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Věkové omezení: ${uiState.parentalAgeRating.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    uiState.maxParentalRating?.let { rating ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Jellyfin MaxParentalRating: $rating",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    if (uiState.parentalLocked) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Filtr je uzamčen profilem Jellyfin — nelze přepnout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Trakt sekce
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Trakt", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(8.dp))
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.traktLoggedIn) {
                    Text(
                        "Přihlášen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Odhlásit z Trakt")
                    }
                } else {
                    Text(
                        "Nepřihlášen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Config.traktAuthorizeUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                    ) {
                        Text("Přihlásit přes Trakt")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsGroupHeader("Streamování")
        UploaderSection()
        Spacer(Modifier.height(16.dp))
        StremioFilterSection(
            sf = uiState.streamFilter,
            loading = uiState.streamFilterLoading,
            error = uiState.streamFilterError,
            onUpdate = { viewModel.updateStreamFilter(it) },
            onMoveFallback = { i, dir -> viewModel.moveFallback(i, dir) },
            onToggleFallback = { k, e -> viewModel.toggleFallback(k, e) },
            onReload = { viewModel.loadStreamFilter() },
        )
        Spacer(Modifier.height(24.dp))
        SettingsGroupHeader("Vzhled")
        DetailModeSection()
        Spacer(Modifier.height(24.dp))
        SettingsGroupHeader("Účet & profily")
        ProfilesSection(
            profiles = uiState.profiles,
            activeProfileId = uiState.activeProfileId,
            onSwitch = { viewModel.switchProfile(it) },
            onSetDefault = { viewModel.setDefaultProfile(it) },
            onSetTvDefault = { viewModel.setTvDefaultProfile(it) },
            onDelete = { viewModel.deleteProfile(it) },
        )
        val activeProfile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
        if (activeProfile?.isAdmin == true && uiState.profiles.size > 1) {
            Spacer(Modifier.height(16.dp))
            AdminRestrictionsSection(
                profiles = uiState.profiles.filter { it.id != activeProfile.id },
                onUpdateAgeRating = { profileId, rating ->
                    viewModel.updateProfileAgeRating(profileId, rating)
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        SettingsGroupHeader("Systém")
        UpdateSection()
        Spacer(Modifier.height(16.dp))
        DebugSection(
            liveLogging = uiState.liveLogging,
            onLiveLogging = { viewModel.setLiveLogging(it) },
        )

        uiState.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailModeSection(
    viewModel: DetailPrefsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
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
                DetailSectionCheckRow("Kolekce", s.showCollections) { viewModel.setCollections(it) }
                DetailSectionCheckRow("Od stejného režiséra", s.showDirector) { viewModel.setDirector(it) }
                DetailSectionCheckRow("Od stejného studia", s.showStudio) { viewModel.setStudio(it) }
            }
        }
    }
}

@Composable
private fun DetailSectionCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun UploaderSection(
    viewModel: UploaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(viewModel.baseUrl) }
    var password by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Uploader", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Přihlášení k upload serveru (Stremio streamy, Sdílej.cz, Smart Remux). Heslo se uloží pro automatické obnovení relace po vypršení.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL serveru") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Heslo") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Button(
                    onClick = { viewModel.saveBaseUrl(url); viewModel.login(password) },
                    enabled = url.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Přihlásit") }
            }
            uiState.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Chyba: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.error == null && password.isBlank() && viewModel.baseUrl.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nastaveno: ${viewModel.baseUrl}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val launcher = LocalUpdateLauncher.current
    var isChecking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val lastCheck = launcher.lastCheckAt()
    val lastText = if (lastCheck > 0L) {
        SimpleDateFormat("d.M.yyyy HH:mm", Locale("cs", "CZ")).format(Date(lastCheck))
    } else "—"
    val buildInfo = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            "${info.versionName} (build $code)"
        }.getOrDefault("—")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Aktualizace", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Aktuální verze: $buildInfo",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Poslední kontrola: $lastText",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    isChecking = true
                    statusText = "Kontroluji…"
                    launcher.checkNow { result ->
                        isChecking = false
                        statusText = when (result) {
                            is UpdateCheckResult.Available -> "Nová verze ${result.tagName} — dialog otevřen"
                            UpdateCheckResult.UpToDate -> "Máte nejnovější verzi"
                            UpdateCheckResult.Failed -> "Kontrola selhala (zkontrolujte připojení)"
                        }
                    }
                },
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isChecking) "Kontroluji…" else "Zkontrolovat aktualizace")
            }
            statusText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ProfilesSection(
    profiles: List<ProfileEntity>,
    activeProfileId: Long?,
    onSwitch: (Long) -> Unit,
    onSetDefault: (Long) -> Unit,
    onSetTvDefault: (Long) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Profily", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            if (profiles.isEmpty()) {
                Text(
                    text = "Žádné uložené profily. Přihlas se přes Jellyfin tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            } else {
                profiles.forEach { profile ->
                    val isActive = profile.id == activeProfileId
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            text = buildString {
                                append(profile.name)
                                if (profile.isAdmin) append(" · Admin")
                                if (profile.isDefault) append(" · Výchozí (phone)")
                                if (profile.tvDefault) append(" · Výchozí (TV)")
                                if (isActive) append(" · Aktivní")
                            },
                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = profile.serverUrl,
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            if (!isActive) {
                                OutlinedButton(onClick = { onSwitch(profile.id) }) {
                                    Text("Přepnout")
                                }
                                Spacer(Modifier.padding(end = 8.dp))
                            }
                            if (!profile.isDefault) {
                                OutlinedButton(onClick = { onSetDefault(profile.id) }) {
                                    Text("Výchozí pro telefon")
                                }
                                Spacer(Modifier.padding(end = 8.dp))
                            }
                            if (!profile.tvDefault) {
                                OutlinedButton(onClick = { onSetTvDefault(profile.id) }) {
                                    Text("Výchozí pro TV")
                                }
                                Spacer(Modifier.padding(end = 8.dp))
                            }
                            OutlinedButton(
                                onClick = { onDelete(profile) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Smazat")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRestrictionsSection(
    profiles: List<ProfileEntity>,
    onUpdateAgeRating: (Long, com.github.jankoran90.showlyfin.core.domain.AgeRating?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Restrikce profilů (Admin)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Nastav věkový limit pro každý profil. Hranice se ihned aplikuje napříč aplikací.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            profiles.forEach { profile ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = profile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    AgeRatingDropdown(
                        current = profile.maxAgeRating?.let {
                            runCatching { com.github.jankoran90.showlyfin.core.domain.AgeRating.valueOf(it) }.getOrNull()
                        },
                        onSelect = { onUpdateAgeRating(profile.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgeRatingDropdown(
    current: com.github.jankoran90.showlyfin.core.domain.AgeRating?,
    onSelect: (com.github.jankoran90.showlyfin.core.domain.AgeRating?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val ratings = com.github.jankoran90.showlyfin.core.domain.AgeRating.entries
    val label = current?.displayName ?: "Bez omezení (Jellyfin default)"
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Bez omezení (Jellyfin default)") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            ratings.forEach { rating ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(rating.displayName) },
                    onClick = {
                        onSelect(rating)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun DebugSection(liveLogging: Boolean, onLiveLogging: (Boolean) -> Unit) {
    val launcher = LocalDebugCaptureLauncher.current
    var isSending by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Debug / Logy", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            // Živé logování — periodicky posílá log buffer na server (Claude tailuje při ladění)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Živé logování", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(
                        "Periodicky posílá logy na server (víc řádků; zapni jen při ladění)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Switch(checked = liveLogging, onCheckedChange = onLiveLogging)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Manuální screenshot + log dump → upload na server",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    isSending = true
                    statusText = "Posílám…"
                    launcher.captureNow { ok ->
                        isSending = false
                        statusText = if (ok) "Odesláno ✓" else "Odeslání selhalo"
                    }
                },
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSending) "Posílám…" else "Poslat debug snapshot")
            }
            statusText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

private val FALLBACK_LABELS = linkedMapOf(
    "cached" to "RD cached", "czsk" to "CZ/SK", "res4k" to "4K", "res1080p" to "1080p",
    "sizeSweet" to "Velikost 2–8 GB", "hevc" to "HEVC", "av1" to "AV1", "noHdr" to "Bez HDR",
    "hevcSdr" to "HEVC bez HDR", "atmos" to "Atmos", "ch71" to "7.1", "ch51" to "5.1",
    "remux" to "REMUX", "highBitrate" to "Bitrate", "resolution" to "Rozlišení", "score" to "Skóre",
)

@Composable
private fun FilterLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun FilterSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FilterStepRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        OutlinedButton(onClick = onMinus, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPlus, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChipRow(label: String, options: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    FilterLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StremioFilterSection(
    sf: StreamFilterPrefs?,
    loading: Boolean,
    error: String?,
    onUpdate: ((StreamFilterPrefs) -> StreamFilterPrefs) -> Unit,
    onMoveFallback: (Int, Int) -> Unit,
    onToggleFallback: (String, Boolean) -> Unit,
    onReload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(Modifier.padding(16.dp)) {
            Text("Stremio / Comet výsledky", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            when {
                sf == null && loading -> CircularProgressIndicator()
                sf == null -> {
                    Text("Dostupné po přihlášení k Uploaderu.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
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

                    Text("Přesné filtry (jen v režimu Přesné)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
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
                            Text("${i + 1}. ${FALLBACK_LABELS[key] ?: key}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            IconButton(onClick = { onMoveFallback(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Nahoru", tint = Color.White) }
                            IconButton(onClick = { onMoveFallback(i, 1) }, enabled = i < sf.fallbackOrder.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Dolů", tint = Color.White) }
                            Text("✕", Modifier.padding(horizontal = 8.dp).clickable { onToggleFallback(key, false) }, color = Color.White.copy(alpha = 0.7f))
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
