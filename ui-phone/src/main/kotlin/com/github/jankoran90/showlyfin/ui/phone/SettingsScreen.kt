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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
        UploaderSection()
        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(16.dp))
        UpdateSection()
        Spacer(Modifier.height(16.dp))
        DebugSection()

        uiState.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
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
private fun DebugSection() {
    val launcher = LocalDebugCaptureLauncher.current
    var isSending by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Debug", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
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
