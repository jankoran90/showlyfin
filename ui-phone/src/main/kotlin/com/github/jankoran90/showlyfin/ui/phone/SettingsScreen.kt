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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
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
    onOpenUploader: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Stav sbalení kategorií — mimo data state, default sbaleno (false). Odchod z tabu Nastavení
    // composable disposne (render přes when(dest)) → fresh mapa → vše zase sbalené (dle požadavku).
    val expanded = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

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

        // Plan PROFILES Fáze 4E: ne-admin (dětský) profil MÁ přístup do Nastavení (vzhled, poslech…),
        // jen NE do správy profilů a admin sekce omezení/práv (ty jsou gated `isAdmin` níže).
        if (!isAdmin) {
            Text(
                "Dětský profil — omezení a práva spravuje správce z admin profilu.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
        }
        CollapsibleSettingsSection("Účty", expanded) {
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

            // Profil & parental controls
            if (uiState.jellyfinConnected) {
                Spacer(Modifier.height(16.dp))
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
            }

            Spacer(Modifier.height(16.dp))
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
        }

        CollapsibleSettingsSection("Poslech", expanded) {
            AbsSection(
                configured = uiState.absConfigured,
                baseUrl = uiState.absBaseUrl,
                loading = uiState.absLoading,
                error = uiState.absError,
                hideFinishedEpisodes = uiState.hideFinishedEpisodes,
                onLogin = { url, user, pass -> viewModel.absLogin(url, user, pass) },
                onLogout = { viewModel.absLogout() },
                onToggleHideFinished = { viewModel.setHideFinishedEpisodes(it) },
            )
            if (uiState.absConfigured) {
                Spacer(Modifier.height(12.dp))
                ListenSettingsCard(uiState, viewModel)
            }
        }

        CollapsibleSettingsSection("Streamování", expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Uploader", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nahrávání filmů, fronta downloadů, Sdílej.cz, Smart Remux.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenUploader, modifier = Modifier.fillMaxWidth()) {
                        Text("Otevřít Uploader")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
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
        }

        CollapsibleSettingsSection("Vzhled", expanded) {
            DetailModeSection()
        }

        CollapsibleSettingsSection("Účet & profily", expanded) {
            ProfilesSection(
                profiles = uiState.profiles,
                activeProfileId = uiState.activeProfileId,
                canManage = isAdmin,
                onSwitch = { viewModel.switchProfile(it) },
                onSetDefault = { viewModel.setDefaultProfile(it) },
                onSetTvDefault = { viewModel.setTvDefaultProfile(it) },
                onDelete = { viewModel.deleteProfile(it) },
                onLogout = { viewModel.logoutProfile() },
                onRename = { id, name -> viewModel.renameProfile(id, name) },
                onSetAvatar = { id, uri -> viewModel.setProfileAvatar(id, uri) },
                onAddProfile = { viewModel.addProfile() },
            )
            val activeProfile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
            if (activeProfile?.isAdmin == true && uiState.uploaderBaseUrl.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.uploaderBaseUrl)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Spravovat profily na webu") }
                Text(
                    "Centrální administrace profilů: sekce, knihovny, žánry, věk, přihlášení.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (activeProfile?.isAdmin == true && uiState.profiles.size > 1) {
                Spacer(Modifier.height(16.dp))
                AdminRestrictionsSection(
                    profiles = uiState.profiles.filter { it.id != activeProfile.id },
                    absLibraries = uiState.absLibraries,
                    onUpdateAgeRating = { profileId, rating ->
                        viewModel.updateProfileAgeRating(profileId, rating)
                    },
                    onUpdateConfig = { profileId, transform ->
                        viewModel.updateProfileConfig(profileId, transform)
                    },
                )
            }
        }

        CollapsibleSettingsSection("Systém", expanded) {
            UpdateSection()
            Spacer(Modifier.height(16.dp))
            DebugSection(
                liveLogging = uiState.liveLogging,
                onLiveLogging = { viewModel.setLiveLogging(it) },
            )
        }

        uiState.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            if (uiState.isLoggedIn) {
                // Plan PROFILES #21: jasný indikátor stavu přihlášení (jako Jellyfin/ABS) + Odhlásit.
                Text(
                    text = "Přihlášen: ${viewModel.baseUrl}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Stremio streamy, Sdílej.cz, Smart Remux.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Odhlásit Uploader") }
            } else {
                Text(
                    text = "Přihlášení k upload serveru (Stremio streamy, Sdílej.cz, Smart Remux). " +
                        "Zadej URL i s https://. Heslo se uloží pro automatické obnovení relace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL serveru") },
                    placeholder = { Text("https://upload.jankoran.cz") },
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
            }
        }
    }
}

@Composable
private fun AbsSection(
    configured: Boolean,
    baseUrl: String,
    loading: Boolean,
    error: String?,
    hideFinishedEpisodes: Boolean,
    onLogin: (url: String, user: String, pass: String) -> Unit,
    onLogout: () -> Unit,
    onToggleHideFinished: (Boolean) -> Unit,
) {
    var url by remember(baseUrl) { mutableStateOf(baseUrl.ifBlank { "https://poslech.jankoran.cz" }) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Audiobookshelf", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Přihlášení k serveru audioknih pro sekci Poslech. Heslo se uloží pro automatické obnovení relace.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            if (configured) {
                Text(
                    text = "Připojeno: $baseUrl",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Odhlásit Audiobookshelf") }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skrývat přehrané epizody", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Text(
                            "Dokončené podcast epizody se v detailu nezobrazí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Switch(checked = hideFinishedEpisodes, onCheckedChange = onToggleHideFinished)
                }
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL serveru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Uživatel") },
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
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Button(
                        onClick = { onLogin(url, user, password) },
                        enabled = url.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Přihlásit") }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Chyba: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Nastavení poslechové sekce: přehrávač, fronta, stahování, zobrazení, sync. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListenSettingsCard(uiState: SettingsUiState, vm: SettingsViewModel) {
    val s = uiState.listen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            ListenGroupTitle("Přehrávání")
            ListenChipRow(
                title = "Velikost přeskoku ◀▶",
                options = listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30, "45 s" to 45, "60 s" to 60),
                selected = s.skipSeconds,
                onSelect = { vm.setSkipSeconds(it) },
            )
            ListenSwitchRow("Zapamatovat rychlost", "Zvlášť pro audioknihy a podcasty.", s.rememberSpeed) { vm.setRememberSpeed(it) }
            ListenChipRow(
                title = "Výchozí rychlost",
                subtitle = if (s.rememberSpeed) "Použije se, dokud rychlost nezměníš v přehrávači." else null,
                options = listOf("0,8×" to 0.8f, "1×" to 1f, "1,25×" to 1.25f, "1,5×" to 1.5f, "2×" to 2f, "3×" to 3f),
                selected = s.defaultSpeed,
                onSelect = { vm.setDefaultSpeed(it) },
            )
            ListenSwitchRow("Auto-přehrát další z fronty", "Po dokončení epizody přejít na další ve frontě.", s.autoAdvanceQueue) { vm.setAutoAdvanceQueue(it) }
            ListenSwitchRow("Označit dokončené na konci", "Na konci epizody ji na serveru označit jako přehranou.", s.autoMarkFinished) { vm.setAutoMarkFinished(it) }

            ListenGroupTitle("Fronta")
            ListenSwitchRow("Pokračovat v podcastu", "Po vyprázdnění fronty přehrát další nepřehranou epizodu téhož podcastu.", s.continuePodcastAfterQueue) { vm.setContinuePodcastAfterQueue(it) }
            ListenSwitchRow("Pamatovat frontu", "Fronta přežije restart aplikace.", s.persistQueue) { vm.setPersistQueue(it) }

            ListenGroupTitle("Stahování do zařízení (offline)")
            ListenInfoText("Stahování přímo do telefonu pro offline poslech (ze serveru). Auto-download na ABS server se nastavuje u konkrétního podcastu (chip „Auto na server“).")
            ListenSwitchRow("Stahovat jen přes Wi-Fi", "Bez Wi-Fi se stažení nespustí.", s.downloadWifiOnly) { vm.setDownloadWifiOnly(it) }
            ListenSwitchRow("Smazat po přehrání", "Stažení se po dokončení epizody automaticky smaže.", s.deleteDownloadAfterFinish) { vm.setDeleteDownloadAfterFinish(it) }
            ListenChipRow(
                title = "Souběžná stahování",
                options = listOf("1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5),
                selected = s.maxConcurrentDownloads,
                onSelect = { vm.setMaxConcurrentDownloads(it) },
            )
            ListenChipRow(
                title = "Auto-stáhnout nejnovější do telefonu",
                subtitle = "Při otevření podcastu stáhnout N nejnovějších nepřehraných epizod.",
                options = listOf("Vyp" to 0, "1" to 1, "3" to 3, "5" to 5, "10" to 10),
                selected = s.autoDownloadNewest,
                onSelect = { vm.setAutoDownloadNewest(it) },
            )
            if (s.autoDownloadNewest > 0) {
                ListenChipRow(
                    title = "Pro které podcasty",
                    subtitle = "Vybrané = jen podcasty s chipem „Auto do telefonu“ v detailu.",
                    options = listOf("Všechny" to 0, "Jen vybrané" to 1),
                    selected = s.autoDownloadScope,
                    onSelect = { vm.setAutoDownloadScope(it) },
                )
            }

            ListenGroupTitle("Stahování na ABS server")
            ListenInfoText("Auto-download nových epizod z RSS na ABS server (ABS-nativní). Zapni per-podcast — plánovač a pravidla řeší ABS server. Konkrétní epizody dotáhneš přes „Prohledat epizody“ v detailu podcastu.")
            LaunchedEffect(Unit) { vm.loadServerPodcasts() }
            when {
                uiState.serverPodcastsLoading && uiState.serverPodcasts.isEmpty() ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                uiState.serverPodcasts.isEmpty() ->
                    ListenInfoText("Žádné podcasty na serveru.")
                else -> uiState.serverPodcasts.forEach { p ->
                    ServerPodcastRow(
                        title = p.title,
                        checked = p.autoDownload,
                        busy = p.itemId in uiState.serverPodcastsBusyIds,
                        onToggle = { vm.toggleServerPodcast(p.itemId, it) },
                    )
                }
            }

            ListenGroupTitle("Epizody")
            ListenChipRow(
                title = "Počet zobrazených epizod",
                subtitle = "Kolik epizod ukázat v detailu podcastu.",
                options = listOf("10" to 10, "20" to 20, "50" to 50, "100" to 100, "Vše" to 0),
                selected = s.episodeListLimit,
                onSelect = { vm.setEpisodeListLimit(it) },
            )
            ListenInfoText("Počet řádků názvu a popisku platí v detailu, frontě i v sekci stahování z RSS.")
            ListenChipRow(
                title = "Počet řádků názvu epizody",
                options = listOf("1" to 1, "2" to 2, "3" to 3, "Vše" to 99),
                selected = s.episodeTitleLines,
                onSelect = { vm.setEpisodeTitleLines(it) },
            )
            ListenChipRow(
                title = "Počet řádků popisku epizody",
                options = listOf("Skrýt" to 0, "1" to 1, "2" to 2, "3" to 3, "5" to 5, "Vše" to 99),
                selected = s.episodeDescriptionLines,
                onSelect = { vm.setEpisodeDescriptionLines(it) },
            )
            ListenSwitchRow(
                "Zvýrazňovat hosta",
                "Vyparsované jméno hosta (+profese) tučně jako poutač nad titulkem. Vyp = jen titulek a popis.",
                s.highlightGuest,
            ) { vm.setHighlightGuest(it) }
            ListenChipRow(
                title = "Velikost písma v seznamu",
                options = listOf("Kompakt" to 0.9f, "Normál" to 1.0f, "Velký" to 1.15f),
                selected = s.episodeFontScale,
                onSelect = { vm.setEpisodeFontScale(it) },
            )
            ListenSwitchRow(
                "Skrýt už stažené (Prohledat epizody)",
                "V „Prohledat epizody“ nezobrazovat epizody, které ABS server už má.",
                s.rssHideDownloaded,
            ) { vm.setRssHideDownloaded(it) }
            ListenChipRow(
                title = "Tlačítko u epizody",
                subtitle = "Akce trailing tlačítka v seznamu epizod.",
                options = listOf("Fronta (konec)" to 0, "Fronta (další)" to 1, "Stáhnout" to 2),
                selected = s.episodeQuickAction,
                onSelect = { vm.setEpisodeQuickAction(it) },
            )

            ListenGroupTitle("Zobrazení")
            ListenSwitchRow("Nejnovější epizody první", "Vyp = od nejstarších.", s.episodeSortNewestFirst) { vm.setEpisodeSortNewestFirst(it) }
            ListenSwitchRow("Zbývající čas", "V přehrávači zobrazit zbývající čas místo celkové délky.", s.showRemainingTime) { vm.setShowRemainingTime(it) }
            ListenSwitchRow("Tlačítko rychlosti", "Zobrazit ovládání rychlosti v přehrávači.", s.showSpeedButton) { vm.setShowSpeedButton(it) }
            ListenSwitchRow("Tlačítko časovače", "Zobrazit časovač spánku v přehrávači.", s.showSleepButton) { vm.setShowSleepButton(it) }
            ListenChipRow(
                title = "Swipe doprava ve frontě",
                subtitle = "Gesto doleva vždy odebere. Doprava:",
                options = listOf("Stáhnout" to 0, "Přehrát" to 1, "Na začátek" to 2),
                selected = s.queueSwipeAction,
                onSelect = { vm.setQueueSwipeAction(it) },
            )

            ListenGroupTitle("Synchronizace")
            ListenChipRow(
                title = "Interval syncu pozice",
                subtitle = "Jak často se ukládá pozice na ABS server.",
                options = listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30, "60 s" to 60),
                selected = s.syncIntervalSeconds,
                onSelect = { vm.setSyncIntervalSeconds(it) },
            )
        }
    }
}

@Composable
private fun ListenGroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ServerPodcastRow(title: String, checked: Boolean, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp, color = Color.White)
        }
        Switch(checked = checked, enabled = !busy, onCheckedChange = onToggle)
    }
}

@Composable
private fun ListenInfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ListenSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ListenChipRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfilesSection(
    profiles: List<ProfileEntity>,
    activeProfileId: Long?,
    canManage: Boolean,
    onSwitch: (Long) -> Unit,
    onSetDefault: (Long) -> Unit,
    onSetTvDefault: (Long) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
    onLogout: () -> Unit,
    onRename: (Long, String) -> Unit,
    onSetAvatar: (Long, android.net.Uri) -> Unit,
    onAddProfile: () -> Unit,
) {
    // Cíl výběru fotky (Plan PROFILES 1D) — PhotoPicker je single, drží se ID právě editovaného profilu.
    var avatarTargetId by remember { mutableStateOf<Long?>(null) }
    var renameTarget by remember { mutableStateOf<ProfileEntity?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = avatarTargetId
        if (uri != null && target != null) onSetAvatar(target, uri)
        avatarTargetId = null
    }

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
                // Ne-admin (děti) vidí jen svůj aktivní profil (read-only), bez správy ostatních.
                val shown = if (canManage) profiles else profiles.filter { it.id == activeProfileId }
                shown.forEach { profile ->
                    val isActive = profile.id == activeProfileId
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        ProfileAvatar(profile)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
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
                            if (canManage) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!isActive) {
                                        OutlinedButton(onClick = { onSwitch(profile.id) }) { Text("Přepnout") }
                                    }
                                    OutlinedButton(onClick = {
                                        avatarTargetId = profile.id
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }) { Text("Foto") }
                                    OutlinedButton(onClick = { renameTarget = profile }) { Text("Přejmenovat") }
                                    if (!profile.isDefault) {
                                        OutlinedButton(onClick = { onSetDefault(profile.id) }) { Text("Výchozí pro telefon") }
                                    }
                                    if (!profile.tvDefault) {
                                        OutlinedButton(onClick = { onSetTvDefault(profile.id) }) { Text("Výchozí pro TV") }
                                    }
                                    OutlinedButton(
                                        onClick = { onDelete(profile) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) { Text("Smazat") }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (canManage) {
                OutlinedButton(onClick = onAddProfile, modifier = Modifier.fillMaxWidth()) {
                    Text("Přidat profil")
                }
            }
            if (activeProfileId != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Odhlásit / přepnout profil")
                }
            }
        }
    }

    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Přejmenovat profil") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Jméno profilu") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(target.id, newName)
                    renameTarget = null
                }, enabled = newName.isNotBlank()) { Text("Uložit") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Zrušit") }
            },
        )
    }
}

/** Kolečko avataru profilu (Plan PROFILES 1D): lokální foto → Jellyfin avatar → iniciála. */
@Composable
private fun ProfileAvatar(profile: ProfileEntity) {
    val localAvatar = profile.avatarPath?.let { java.io.File(it) }?.takeIf { it.exists() }
    val avatarUrl = profile.avatarTag?.let { tag ->
        "${profile.serverUrl}/Users/${profile.jellyfinUserId}/Images/Primary?tag=$tag&quality=85"
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            localAvatar != null -> AsyncImage(
                model = localAvatar,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            avatarUrl != null -> AsyncImage(
                model = avatarUrl,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                text = profile.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Sekce přepínatelné adminem (Plan PROFILES 1E). SLEDUJ + NASTAVENI jsou vždy viditelné. */
private val SECTION_TOGGLES = listOf(
    ProfileConfig.Sections.POSLECH to "Poslech",
    ProfileConfig.Sections.KNIHOVNA to "Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Objevit",
    ProfileConfig.Sections.NA_RD to "Na RD",
)

/**
 * Přepne viditelnost sekce [key]. Když jsou viditelné všechny přepínatelné → prázdná množina
 * (= vše, legacy). Jinak explicitní allow-list (SLEDUJ + NASTAVENI vždy + zapnuté přepínatelné).
 */
private fun toggledSections(cfg: ProfileConfig, key: String, enabled: Boolean): Set<String> {
    val vis = SECTION_TOGGLES.associate { (k, _) -> k to cfg.isSectionVisible(k) }.toMutableMap()
    vis[key] = enabled
    return if (vis.values.all { it }) emptySet()
    else buildSet {
        add(ProfileConfig.Sections.SLEDUJ)
        add(ProfileConfig.Sections.NASTAVENI)
        vis.forEach { (k, v) -> if (v) add(k) }
    }
}

/** Možnosti „hlavní" (výchozí otevřené) sekce per profil (Plan PROFILES Fáze 4). */
private val LANDING_OPTIONS = listOf(
    ProfileConfig.Sections.KNIHOVNA to "Sleduj → Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Sleduj → Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Sleduj → Objevit",
    ProfileConfig.Sections.NA_RD to "Sleduj → Na RD",
    ProfileConfig.Sections.POSLECH to "Poslech",
)

/**
 * Admin authoring profilů (Plan PROFILES Fáze 4) — KAŽDÝ profil je vlastní sbalovací kategorický blok
 * (šablona Plan TIDY / CLAUDE.md „## Nastavení"). Uvnitř logicky seskupené: Hlavní sekce · Viditelné
 * sekce + podsekce „Sleduj" · Žánry (allow/block) · Věkový limit. Write-through `onUpdateConfig`
 * (push na backend pod stabilním `profileUuid` → bez prolévání mezi profily).
 */
@Composable
private fun AdminRestrictionsSection(
    profiles: List<ProfileEntity>,
    absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    onUpdateAgeRating: (Long, com.github.jankoran90.showlyfin.core.domain.AgeRating?) -> Unit,
    onUpdateConfig: (Long, (ProfileConfig) -> ProfileConfig) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Profily (Admin) — nastavení per profil",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pro každý profil nastav hlavní sekci, viditelné sekce/podsekce, žánry a věk. " +
                "Aplikuje se při přepnutí profilu. Každý profil má vlastní izolované nastavení.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        profiles.forEach { profile ->
            ProfileAuthoringBlock(profile, absLibraries, onUpdateAgeRating, onUpdateConfig)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Jeden profil = sbalovací blok (default sbaleno, reset při odchodu z tabu). */
@Composable
private fun ProfileAuthoringBlock(
    profile: ProfileEntity,
    absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    onUpdateAgeRating: (Long, com.github.jankoran90.showlyfin.core.domain.AgeRating?) -> Unit,
    onUpdateConfig: (Long, (ProfileConfig) -> ProfileConfig) -> Unit,
) {
    val cfg = ProfileConfig.fromJson(profile.configJson)
    var open by remember(profile.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(profile.name)
                        if (profile.isAdmin) append(" 👑")
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Icon(
                    imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Sbalit" else "Rozbalit",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (open) {
                Spacer(Modifier.height(10.dp))

                // — Hlavní sekce —
                Text("Hlavní sekce (otevře se po vstupu)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                val landingChoices = LANDING_OPTIONS.filter { (key, _) -> cfg.isSectionVisible(key) }
                LandingDropdown(
                    current = cfg.defaultSection,
                    options = landingChoices,
                    onSelect = { key -> onUpdateConfig(profile.id) { c -> c.copy(defaultSection = key) } },
                )
                Spacer(Modifier.height(12.dp))

                // — Viditelné sekce + podsekce —
                Text("Viditelné sekce a podsekce", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                SECTION_TOGGLES.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Switch(
                            checked = cfg.isSectionVisible(key),
                            onCheckedChange = { enabled ->
                                onUpdateConfig(profile.id) { c ->
                                    val sections = toggledSections(c, key, enabled)
                                    // Skrytá hlavní sekce → zruš defaultSection (jinak by se otevřela skrytá).
                                    val newDefault = c.defaultSection?.takeIf { d ->
                                        sections.isEmpty() || d in sections
                                    }
                                    c.copy(visibleSections = sections, defaultSection = newDefault)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // — Poslech: whitelist ABS knihoven (Plan PROFILES Fáze 4E) —
                if (cfg.isSectionVisible(ProfileConfig.Sections.POSLECH) && absLibraries.isNotEmpty()) {
                    Text("Poslech — knihovny (nic = všechny)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    val wl = cfg.absLibraryWhitelist
                    absLibraries.forEach { lib ->
                        val checked = wl == null || lib.id in wl
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(lib.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    onUpdateConfig(profile.id) { c ->
                                        val current = c.absLibraryWhitelist?.toMutableSet()
                                            ?: absLibraries.map { it.id }.toMutableSet()
                                        if (enabled) current.add(lib.id) else current.remove(lib.id)
                                        val newWl = when {
                                            current.size == absLibraries.size -> null // vše = bez omezení
                                            else -> current.toList()
                                        }
                                        c.copy(absLibraryWhitelist = newWl)
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // — Žánry (allow + block) —
                Text("Žánry", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                var blockText by remember(profile.id, profile.configJson) {
                    mutableStateOf(cfg.blockedGenres.joinToString(", "))
                }
                var allowText by remember(profile.id, profile.configJson) {
                    mutableStateOf(cfg.allowedGenres.joinToString(", "))
                }
                OutlinedTextField(
                    value = blockText,
                    onValueChange = { blockText = it },
                    label = { Text("Zakázané žánry (čárkou)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = allowText,
                    onValueChange = { allowText = it },
                    label = { Text("Povolené žánry (prázdné = vše kromě zakázaných)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    val block = blockText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                    val allow = allowText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                    onUpdateConfig(profile.id) { c -> c.copy(blockedGenres = block, allowedGenres = allow) }
                }) { Text("Uložit žánry") }
                Spacer(Modifier.height(12.dp))

                // — Věk —
                Text("Věkový limit", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
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

@Composable
private fun LandingDropdown(
    current: String?,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == current }?.second ?: "Výchozí (první viditelná)"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Výchozí (první viditelná)") },
                onClick = { onSelect(null); expanded = false },
            )
            options.forEach { (key, lbl) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = { onSelect(key); expanded = false },
                )
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

/**
 * Sbalovací kategorie Nastavení. Stav drží volající přes [expandedMap] (klíč = [title]),
 * default sbaleno. Recompozice z data state nesbalí (mapa žije mimo); odchod z tabu Nastavení
 * mapu zahodí → po návratu zase vše sbalené.
 */
@Composable
private fun CollapsibleSettingsSection(
    title: String,
    expandedMap: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    content: @Composable () -> Unit,
) {
    val isOpen = expandedMap[title] ?: false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedMap[title] = !isOpen }
            .padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isOpen) "Sbalit" else "Rozbalit",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (isOpen) {
        content()
    }
    Spacer(Modifier.height(16.dp))
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
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f),
                    )
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
