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

@Composable
fun SettingsScreen(
    onOpenUploader: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = true,
    onOpenAdmin: () -> Unit = {},
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
        Spacer(Modifier.height(16.dp))

        // Plan WARDEN W2: hlavička aktivního profilu (avatar + jméno + stav) vlevo, přepnout/odhlásit vpravo.
        val activeHeaderProfile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
        ProfileHeader(profile = activeHeaderProfile, onSwitch = { viewModel.logoutProfile() })
        Spacer(Modifier.height(16.dp))

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
        // Plan WARDEN W4: ne-admin user s uzamčeným přihlášením (lock-mapa CREDENTIALS) needituje účty.
        val credLocked = !isAdmin && ProfileConfig.LockKeys.CREDENTIALS in uiState.lockedKeys
        CollapsibleSettingsSection("Účty", expanded) {
          if (credLocked) {
            LockedByAdminNote()
          } else {
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
                    } else {
                        Text(
                            text = "Nenastaveno",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                    ManagedInAdminNote(isAdmin = isAdmin, onOpenAdmin = onOpenAdmin)
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
                    // Plan VAULT — Trakt je teď per-profil pod adminem (OAuth, vč. Google sign-in);
                    // přihlášení/odhlášení se dělá v sekci Správa, tady jen stav.
                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(
                            if (uiState.traktLoggedIn) "Přihlášen" else "Nepřihlášen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.traktLoggedIn) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.65f),
                        )
                    }
                    ManagedInAdminNote(isAdmin = isAdmin, onOpenAdmin = onOpenAdmin)
                }
            }
          }
        }

        CollapsibleSettingsSection("Poslech", expanded) {
          if (credLocked) {
            LockedByAdminNote()
          } else {
            AbsSection(
                configured = uiState.absConfigured,
                baseUrl = uiState.absBaseUrl,
                hideFinishedEpisodes = uiState.hideFinishedEpisodes,
                onToggleHideFinished = { viewModel.setHideFinishedEpisodes(it) },
                isAdmin = isAdmin,
                onOpenAdmin = onOpenAdmin,
            )
          }
            // Playback preference (ne creds) — dostupné i při uzamčeném přihlášení, pokud je ABS nastaven.
            if (uiState.absConfigured) {
                Spacer(Modifier.height(12.dp))
                ListenSettingsCard(uiState, viewModel)
            }
        }

        CollapsibleSettingsSection("Streamování", expanded) {
          if (credLocked) {
            LockedByAdminNote()
          } else {
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
            UploaderSection(isAdmin = isAdmin, onOpenAdmin = onOpenAdmin)
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
        }

        CollapsibleSettingsSection("Vzhled", expanded) {
            // Plan WARDEN W2: ne-admin user vidí Vzhled jen pokud ho šablona nezamkla (lock-mapa).
            if (isAdmin || ProfileConfig.LockKeys.APPEARANCE !in uiState.lockedKeys) {
                DetailModeSection()
            } else {
                LockedByAdminNote()
            }
        }

        // Plan STRATA Fáze E — uživatelské přehazování pořadí (jen když admin nezamkl ORDER).
        CollapsibleSettingsSection("Pořadí sekcí", expanded) {
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
            // Plan HELM H6: administrace profilů/šablon přesunuta do samostatné admin destinace
            // („Správa" ve spodní liště, jen pro admin) — už NENÍ zamíchaná v Nastavení. Web admin zrušen.
        }

        CollapsibleSettingsSection("Domácí sestava", expanded) {
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

/** Plan WARDEN W2: hlavička Nastavení — avatar + jméno + stav vlevo, „Přepnout" (odhlásit→brána) vpravo. */
@Composable
private fun ProfileHeader(profile: ProfileEntity?, onSwitch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val localAvatar = profile?.avatarPath?.let { java.io.File(it) }?.takeIf { it.exists() }
                val avatarUrl = profile?.avatarTag?.let { tag ->
                    "${profile.serverUrl}/Users/${profile.jellyfinUserId}/Images/Primary?tag=$tag&quality=85"
                }
                when {
                    localAvatar != null -> AsyncImage(localAvatar, profile?.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    avatarUrl != null -> AsyncImage(avatarUrl, profile?.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> Text(
                        (profile?.name ?: "?").take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile?.name ?: "Nepřihlášen",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    if (profile?.isAdmin == true) "Admin · přihlášen" else "Přihlášen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onSwitch) { Text("Přepnout") }
        }
    }
}

/** Plan WARDEN W2: blok zamčený správcem profilu (lock-mapa) — ne-admin user ho needituje. */
@Composable
private fun LockedByAdminNote() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Zamčeno správcem profilu.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Plan VAULT — přihlašovací údaje (Jellyfin/Poslech/Streamování/Trakt) jsou JEDINÝM zdrojem pravdy
 * spravovány v admin sekci „Správa" (per-profil editor → push na backend). Staré sekce v Nastavení
 * proto jen ZOBRAZUJÍ stav a odkazují do Správy; samostatné přihlašování zde už není (zabraňuje
 * driftu mezi kanonickými prefs a profilem na backendu). Admin dostane tlačítko rovnou do Správy.
 */
@Composable
private fun ManagedInAdminNote(isAdmin: Boolean, onOpenAdmin: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isAdmin) "Přihlašovací údaje spravuješ v sekci Správa."
            else "Přihlašovací údaje spravuje správce profilu.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
    if (isAdmin) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
            Text("Otevřít Správu")
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
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    viewModel: UploaderViewModel = hiltViewModel(),
) {
    // Plan VAULT — jen stav. Přihlášení Uploaderu se spravuje v sekci Správa (jediný zdroj pravdy).
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Uploader", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (uiState.isLoggedIn) "Přihlášen: ${viewModel.baseUrl}" else "Nenastaveno",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isLoggedIn) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Stremio streamy, Sdílej.cz, Smart Remux.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            ManagedInAdminNote(isAdmin = isAdmin, onOpenAdmin = onOpenAdmin)
        }
    }
}

@Composable
private fun AbsSection(
    configured: Boolean,
    baseUrl: String,
    hideFinishedEpisodes: Boolean,
    onToggleHideFinished: (Boolean) -> Unit,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
) {
    // Plan VAULT — jen stav + playback toggle. Přihlášení ABS se spravuje v sekci Správa.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Audiobookshelf", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (configured) "Připojeno: $baseUrl" else "Nenastaveno",
                style = MaterialTheme.typography.bodyMedium,
                color = if (configured) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.65f),
            )
            if (configured) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zobrazovat přehrané epizody", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Text(
                            "Dokončené podcast epizody zůstanou v detailu (vyp = skryjí se).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    // Polarita: zapnuto = viditelné (model je „hide", proto invertujeme).
                    Switch(checked = !hideFinishedEpisodes, onCheckedChange = { onToggleHideFinished(!it) })
                }
            }
            ManagedInAdminNote(isAdmin = isAdmin, onOpenAdmin = onOpenAdmin)
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

            // Plan STRATA B2 — hromadné stažení celých audioknih (scoped na profil).
            ListenGroupTitle("Celé audioknihy")
            ListenInfoText("Stáhne do telefonu všechny audioknihy, které vidí tento profil (dle knihoven profilu). Stahuje na pozadí, počet souběžných řídí nastavení výše.")
            val bulk by vm.audiobookBulk.collectAsStateWithLifecycle()
            when {
                bulk.resolving -> ListenInfoText("Zjišťuji seznam knih…")
                bulk.total > 0 -> ListenInfoText(
                    buildString {
                        append("Staženo ${bulk.done} z ${bulk.total}")
                        if (bulk.downloading > 0) append(" · stahuje se ${bulk.downloading}")
                        if (bulk.failed > 0) append(" · selhalo ${bulk.failed}")
                    },
                )
                bulk.storedTotal > 0 -> ListenInfoText("V telefonu: ${bulk.storedTotal} stažených audioknih.")
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { vm.downloadAllAudiobooks() },
                    enabled = !bulk.active,
                    modifier = Modifier.weight(1f),
                ) { Text(if (bulk.active) "Stahuji…" else "Stáhnout vše") }
                OutlinedButton(
                    onClick = { vm.deleteAllAudiobookDownloads() },
                    enabled = bulk.storedTotal > 0 && !bulk.active,
                    modifier = Modifier.weight(1f),
                ) { Text("Smazat stažené") }
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
            // Polarita: zapnuto = viditelné (model je „hide", proto invertujeme).
            ListenSwitchRow(
                "Zobrazit už stažené (Prohledat epizody)",
                "V „Prohledat epizody“ ukazovat i epizody, které ABS server už má (vyp = skryje je).",
                !s.rssHideDownloaded,
            ) { vm.setRssHideDownloaded(!it) }
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
internal fun ProfilesSection(
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
    // V10: Sleduj (Jellyfin/Trakt video sekce) je nově skrývatelná — typicky skrýt na telefonu,
    // nechat na TV. Nastavení zůstává jediná vždy viditelná sekce.
    ProfileConfig.Sections.SLEDUJ to "Sleduj (Jellyfin)",
    ProfileConfig.Sections.OVLADAC to "Ovladač (TV)",
    ProfileConfig.Sections.POSLECH to "Poslech",
    ProfileConfig.Sections.KNIHOVNA to "— Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "— Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "— Objevit",
    ProfileConfig.Sections.HISTORIE to "— Historie",
    ProfileConfig.Sections.NA_RD to "— Na RD",
)

/**
 * Přepne viditelnost sekce [key]. Když jsou viditelné všechny přepínatelné → prázdná množina
 * (= vše, legacy). Jinak explicitní allow-list (SLEDUJ + NASTAVENI vždy + zapnuté přepínatelné).
 */
private fun toggledHidden(cfg: ProfileConfig, key: String, visible: Boolean): Set<String> =
    if (visible) cfg.hiddenSections - key else cfg.hiddenSections + key

/** Viditelnost sekce na TV (Plan STRATA): TV sada skrytých null = zrcadlí telefon. */
private fun tvSectionVisible(cfg: ProfileConfig, key: String): Boolean {
    val tvHidden = cfg.hiddenSectionsTv ?: return cfg.isSectionVisible(key)
    return key !in tvHidden
}

/** Přepne viditelnost sekce [key] na TV — od prvního dotyku je TV sada skrytých nezávislá (forkne z telefonu). */
private fun toggledHiddenTv(cfg: ProfileConfig, key: String, visible: Boolean): Set<String> {
    val base = cfg.hiddenSectionsTv ?: cfg.hiddenSections
    return if (visible) base - key else base + key
}

/** Možnosti „hlavní" (výchozí otevřené) sekce per profil (Plan PROFILES Fáze 4). */
private val LANDING_OPTIONS = listOf(
    ProfileConfig.Sections.KNIHOVNA to "Sleduj → Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Sleduj → Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Sleduj → Objevit",
    ProfileConfig.Sections.HISTORIE to "Sleduj → Historie",
    ProfileConfig.Sections.NA_RD to "Sleduj → Na RD",
    ProfileConfig.Sections.POSLECH to "Poslech",
)

// Plan STRATA Fáze E — popisky pro editor pořadí (drag&drop).
private val NAV_ORDER_LABELS = mapOf(
    ProfileConfig.Sections.SLEDUJ to "Sleduj",
    ProfileConfig.Sections.OVLADAC to "Ovladač",
    ProfileConfig.Sections.POSLECH to "Poslech",
)
private val SUBSECTION_ORDER_LABELS = mapOf(
    ProfileConfig.Sections.KNIHOVNA to "Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Objevit",
    ProfileConfig.Sections.HISTORIE to "Historie",
    ProfileConfig.Sections.NA_RD to "Na RD",
)

/**
 * Plan STRATA Fáze E — znovupoužitelný editor pořadí (drag&drop) pro seznam klíčů s popiskem.
 * [orderedKeys] = aktuální pořadí; [onReorder] dostane NOVÉ pořadí klíčů. Skryje se pro <2 položky.
 */
@Composable
private fun OrderEditor(
    title: String,
    orderedKeys: List<String>,
    label: (String) -> String,
    onReorder: (List<String>) -> Unit,
) {
    if (orderedKeys.size < 2) return
    Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    ReorderColumn(
        items = orderedKeys,
        key = { it },
        onMove = { from, to -> onReorder(orderedKeys.moved(from, to)) },
    ) { keyItem, dragging, handle ->
        Row(
            Modifier
                .fillMaxWidth()
                .then(handle)
                .background(
                    if (dragging) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Přesunout", tint = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.width(12.dp))
            Text(label(keyItem), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Admin authoring profilů (Plan PROFILES Fáze 4) — KAŽDÝ profil je vlastní sbalovací kategorický blok
 * (šablona Plan TIDY / CLAUDE.md „## Nastavení"). Uvnitř logicky seskupené: Hlavní sekce · Viditelné
 * sekce + podsekce „Sleduj" · Žánry (allow/block) · Věkový limit. Write-through `onUpdateConfig`
 * (push na backend pod stabilním `profileUuid` → bez prolévání mezi profily).
 */
@Composable
internal fun AdminRestrictionsSection(
    profiles: List<ProfileEntity>,
    absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    adminPodcasts: List<com.github.jankoran90.showlyfin.data.abs.model.Podcast>,
    jellyfinLibraries: List<com.github.jankoran90.showlyfin.core.domain.JellyfinLibraryRef>,
    templates: List<TemplateEntity>,
    onUpdateAgeRating: (Long, com.github.jankoran90.showlyfin.core.domain.AgeRating?) -> Unit,
    onUpdateConfig: (Long, (ProfileConfig) -> ProfileConfig) -> Unit,
    onAssignTemplate: (Long, String?) -> Unit,
    onSetPin: (Long, String) -> Unit,
    onClearPin: (Long) -> Unit,
    // Plan VAULT — uložení creds s ověřením JF loginu + viditelný výsledek (profileId → zpráva).
    onSaveCredentials: (Long, com.github.jankoran90.showlyfin.core.domain.CredentialBundle) -> Unit,
    credsStatus: Pair<Long, String>? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Profily (Admin) — nastavení per profil",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pro každý profil nastav hlavní sekci, viditelné sekce/podsekce, knihovny, žánry, věk, " +
                "PIN a přihlašovací údaje. Aplikuje se při přepnutí profilu. Každý profil má vlastní izolované nastavení.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        profiles.forEach { profile ->
            ProfileAuthoringBlock(
                profile, absLibraries, adminPodcasts, jellyfinLibraries, templates,
                onUpdateAgeRating, onUpdateConfig, onAssignTemplate, onSetPin, onClearPin,
                onSaveCredentials = onSaveCredentials,
                credsStatus = credsStatus?.takeIf { it.first == profile.id }?.second,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Jeden profil = sbalovací blok (default sbaleno, reset při odchodu z tabu). */
@Composable
private fun ProfileAuthoringBlock(
    profile: ProfileEntity,
    absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    adminPodcasts: List<com.github.jankoran90.showlyfin.data.abs.model.Podcast>,
    jellyfinLibraries: List<com.github.jankoran90.showlyfin.core.domain.JellyfinLibraryRef>,
    templates: List<TemplateEntity>,
    onUpdateAgeRating: (Long, com.github.jankoran90.showlyfin.core.domain.AgeRating?) -> Unit,
    onUpdateConfig: (Long, (ProfileConfig) -> ProfileConfig) -> Unit,
    onAssignTemplate: (Long, String?) -> Unit,
    onSetPin: (Long, String) -> Unit,
    onClearPin: (Long) -> Unit,
    onSaveCredentials: (Long, com.github.jankoran90.showlyfin.core.domain.CredentialBundle) -> Unit,
    credsStatus: String? = null,
) {
    val cfg = ProfileConfig.fromJson(profile.configJson)
    var open by remember(profile.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.tvFocusable(),
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

                // — Šablona (Plan WARDEN W3c) — zamčené domény diktuje šablona, odemčené si user mění sám —
                Text("Šablona", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                TemplateAssignDropdown(
                    templates = templates,
                    current = profile.templateUuid,
                    onSelect = { uuid -> onAssignTemplate(profile.id, uuid) },
                )
                Spacer(Modifier.height(12.dp))

                // — Hlavní sekce —
                Text("Hlavní sekce (otevře se po vstupu)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                val landingChoices = LANDING_OPTIONS.filter { (key, _) -> cfg.isSectionVisible(key) }
                LandingDropdown(
                    current = cfg.defaultSection,
                    options = landingChoices,
                    onSelect = { key -> onUpdateConfig(profile.id) { c -> c.copy(defaultSection = key) } },
                )
                Spacer(Modifier.height(12.dp))

                // — Viditelné sekce + podsekce (V10: zvlášť telefon a TV; TV zrcadlí telefon do
                //   prvního vlastního přepnutí) —
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Viditelné sekce a podsekce", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Text("📱", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(34.dp))
                    Text("📺", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(22.dp))
                }
                SECTION_TOGGLES.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Switch(
                            checked = cfg.isSectionVisible(key),
                            onCheckedChange = { visible ->
                                onUpdateConfig(profile.id) { c ->
                                    val hidden = toggledHidden(c, key, visible)
                                    // Skrytá hlavní sekce → zruš defaultSection (jinak by se otevřela skrytá).
                                    val newDefault = c.defaultSection?.takeIf { it !in hidden }
                                    c.copy(hiddenSections = hidden, defaultSection = newDefault)
                                }
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = tvSectionVisible(cfg, key),
                            onCheckedChange = { visible ->
                                onUpdateConfig(profile.id) { c ->
                                    c.copy(hiddenSectionsTv = toggledHiddenTv(c, key, visible))
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // — Pořadí sekcí a podsekcí (Plan STRATA Fáze E, drag&drop — podrž a táhni) —
                OrderEditor(
                    title = "Pořadí sekcí (podrž a táhni)",
                    orderedKeys = cfg.orderedSections(),
                    label = { NAV_ORDER_LABELS[it] ?: it },
                    onReorder = { newOrder -> onUpdateConfig(profile.id) { c -> c.copy(sectionOrder = newOrder) } },
                )
                OrderEditor(
                    title = "Pořadí podsekcí Sleduj",
                    orderedKeys = cfg.orderedSubsections(),
                    label = { SUBSECTION_ORDER_LABELS[it] ?: it },
                    onReorder = { newOrder -> onUpdateConfig(profile.id) { c -> c.copy(subsectionOrder = newOrder) } },
                )

                // — Knihovny Jellyfin: whitelist (Plan HELM; nic = všechny) —
                if (jellyfinLibraries.isNotEmpty()) {
                    Text("Knihovny (Jellyfin) — nic zaškrtnuté = všechny", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    val jwl = cfg.jellyfinLibraryWhitelist
                    jellyfinLibraries.forEach { lib ->
                        val checked = jwl == null || lib.id in jwl
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(lib.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    onUpdateConfig(profile.id) { c ->
                                        val current = c.jellyfinLibraryWhitelist?.toMutableSet()
                                            ?: jellyfinLibraries.map { it.id }.toMutableSet()
                                        if (enabled) current.add(lib.id) else current.remove(lib.id)
                                        val newWl = if (current.size == jellyfinLibraries.size) null else current.toList()
                                        c.copy(jellyfinLibraryWhitelist = newWl)
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Pořadí knihovních řádků (Plan STRATA Fáze E) — drag&drop.
                    val libIds = jellyfinLibraries.map { it.id }
                    OrderEditor(
                        title = "Pořadí knihoven (podrž a táhni)",
                        orderedKeys = cfg.orderedLibraryIds(libIds),
                        label = { id -> jellyfinLibraries.firstOrNull { it.id == id }?.name ?: id },
                        onReorder = { newOrder -> onUpdateConfig(profile.id) { c -> c.copy(libraryOrder = newOrder) } },
                    )
                }

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

                // — Poslech: viditelnost jednotlivých podcastů pro tento profil (jemnější než whitelist police) —
                if (cfg.isSectionVisible(ProfileConfig.Sections.POSLECH) && adminPodcasts.isNotEmpty()) {
                    Text("Podcasty — zapnuto = zobrazený pro tento profil", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    adminPodcasts.forEach { pod ->
                        val visible = pod.id !in cfg.hiddenPodcastIds
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pod.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            // Polarita: zapnuto = viditelné (model drží skryté id, proto invertujeme).
                            Switch(
                                checked = visible,
                                onCheckedChange = { show ->
                                    onUpdateConfig(profile.id) { c ->
                                        val current = c.hiddenPodcastIds.toMutableSet()
                                        if (show) current.remove(pod.id) else current.add(pod.id)
                                        c.copy(hiddenPodcastIds = current)
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
                Spacer(Modifier.height(12.dp))

                // — PIN (Plan HELM) —
                ProfilePinEditor(
                    hasPin = !profile.loginPinHash.isNullOrBlank(),
                    onSetPin = { onSetPin(profile.id, it) },
                    onClearPin = { onClearPin(profile.id) },
                )
                Spacer(Modifier.height(12.dp))

                // — Přihlašovací údaje sub-appek (Plan HELM; uložení + ověření JF = Plan VAULT) —
                ProfileCredentialsEditor(
                    profileKey = profile.id,
                    configJson = profile.configJson,
                    current = cfg.credentials,
                    onSave = { bundle -> onSaveCredentials(profile.id, bundle) },
                    status = credsStatus,
                )
            }
        }
    }
}

/** Plan HELM — nastavení/zrušení app-login PINu profilu (krátký rodinný kód). */
@Composable
private fun ProfilePinEditor(
    hasPin: Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Text(
        if (hasPin) "PIN: nastaven" else "PIN: bez PINu",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.7f),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { ch -> ch.isDigit() } },
            label = { Text("Nový PIN") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { if (pin.isNotBlank()) { onSetPin(pin); pin = "" } }) { Text("Nastavit") }
    }
    if (hasPin) {
        OutlinedButton(
            onClick = onClearPin,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Zrušit PIN") }
    }
}

/**
 * Plan HELM — editor předvyplněných přihlašovacích údajů profilu (Jellyfin/ABS/Uploader). Ukládá do
 * [ProfileConfig.credentials]; při fresh-installu/přepnutí se z balíku auto-přihlásí (GATEKEY).
 */
@Composable
private fun ProfileCredentialsEditor(
    profileKey: Long,
    configJson: String?,
    current: com.github.jankoran90.showlyfin.core.domain.CredentialBundle,
    onSave: (com.github.jankoran90.showlyfin.core.domain.CredentialBundle) -> Unit,
    /** Plan VAULT — výsledek posledního uložení (Ukládám… / ověřeno / odmítnuto). */
    status: String? = null,
) {
    var open by remember(profileKey) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.tvFocusable(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Přihlašovací údaje", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Icon(
            imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) {
        // Lokální editovatelný stav inicializovaný z balíku; reset při změně profilu/configu.
        var jfUrl by remember(profileKey, configJson) { mutableStateOf(current.jellyfin?.url ?: "") }
        var jfUser by remember(profileKey, configJson) { mutableStateOf(current.jellyfin?.username ?: "") }
        var jfPass by remember(profileKey, configJson) { mutableStateOf(current.jellyfin?.password ?: "") }
        var absUrl by remember(profileKey, configJson) { mutableStateOf(current.abs?.url ?: "") }
        var absUser by remember(profileKey, configJson) { mutableStateOf(current.abs?.username ?: "") }
        var absPass by remember(profileKey, configJson) { mutableStateOf(current.abs?.password ?: "") }
        var upUrl by remember(profileKey, configJson) { mutableStateOf(current.uploader?.url ?: "") }
        var upPass by remember(profileKey, configJson) { mutableStateOf(current.uploader?.password ?: "") }

        Spacer(Modifier.height(6.dp))
        Text("Jellyfin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        CredField("Jellyfin URL", jfUrl) { jfUrl = it }
        CredField("Jellyfin jméno", jfUser) { jfUser = it }
        CredField("Jellyfin heslo", jfPass, isPassword = true) { jfPass = it }
        Spacer(Modifier.height(6.dp))
        Text("Audiobookshelf", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        CredField("ABS URL", absUrl) { absUrl = it }
        CredField("ABS jméno", absUser) { absUser = it }
        CredField("ABS heslo", absPass, isPassword = true) { absPass = it }
        Spacer(Modifier.height(6.dp))
        Text("Uploader", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        CredField("Uploader URL", upUrl) { upUrl = it }
        CredField("Uploader heslo", upPass, isPassword = true) { upPass = it }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            // Token zachovat ze stávajícího balíku (vyrobí se reálným přihlášením / GATEKEY hydratací).
            val jf = com.github.jankoran90.showlyfin.core.domain.JellyfinCreds(
                url = jfUrl.trim(),
                userId = current.jellyfin?.userId ?: "",
                token = current.jellyfin?.token ?: "",
                username = jfUser.trim(),
                password = jfPass.ifBlank { null },
            )
            val abs = com.github.jankoran90.showlyfin.core.domain.AbsCreds(
                url = absUrl.trim(), username = absUser.trim(), password = absPass, token = current.abs?.token,
            )
            val up = com.github.jankoran90.showlyfin.core.domain.UploaderCreds(url = upUrl.trim(), password = upPass)
            onSave(
                current.copy(
                    jellyfin = jf.takeIf { jfUrl.isNotBlank() || jfUser.isNotBlank() },
                    abs = abs.takeIf { absUrl.isNotBlank() || absUser.isNotBlank() },
                    uploader = up.takeIf { upUrl.isNotBlank() },
                ),
            )
        }) { Text("Uložit a ověřit přihlášení") }
        if (status != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if ("ODMÍTNUTO" in status) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Plan HELM — řádek přihlašovacího pole (kompaktní). */
@Composable
private fun CredField(label: String, value: String, isPassword: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
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

// ── Šablony — in-app admin authoring (Plan WARDEN W3c část 2) ────────────────────

/** Zamykatelné domény šablony (LockKeys) s popiskem pro UI. */
private val TEMPLATE_LOCKS = listOf(
    ProfileConfig.LockKeys.VISIBLE_SECTIONS to "Viditelné sekce",
    ProfileConfig.LockKeys.JELLYFIN_LIBRARIES to "Knihovny (Jellyfin)",
    ProfileConfig.LockKeys.ABS_LIBRARIES to "Knihovny (Poslech)",
    ProfileConfig.LockKeys.GENRES to "Žánry",
    ProfileConfig.LockKeys.AGE_RATING to "Věk",
    ProfileConfig.LockKeys.DEFAULT_SECTION to "Hlavní sekce",
    ProfileConfig.LockKeys.ORDER to "Pořadí sekcí/podsekcí",
    ProfileConfig.LockKeys.APPEARANCE to "Vzhled",
    ProfileConfig.LockKeys.CREDENTIALS to "Přihlášení",
)

/** Dropdown přiřazení šablony profilu (v ProfileAuthoringBlock). */
@Composable
private fun TemplateAssignDropdown(
    templates: List<TemplateEntity>,
    current: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = templates.firstOrNull { it.templateUuid == current }?.name ?: "Bez šablony (plná volnost)"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Bez šablony (plná volnost)") },
                onClick = { onSelect(null); expanded = false },
            )
            templates.forEach { t ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(t.name.ifBlank { "(bez názvu)" }) },
                    onClick = { onSelect(t.templateUuid); expanded = false },
                )
            }
        }
    }
}

/** Admin sekce authoringu šablon — seznam editorů + vytvoření nové. */
@Composable
internal fun TemplateAuthoringSection(
    templates: List<TemplateEntity>,
    absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    onCreate: (String) -> Unit,
    onSave: (TemplateEntity, String, AgeRating?, ProfileConfig) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Šablony (Admin)", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pojmenovaná sada nastavení + zámky („co smí uživatel měnit“). Přiřaď ji profilu níže. " +
                "Zamčené domény diktuje šablona, odemčené si uživatel mění sám.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(10.dp))
        templates.forEach { t ->
            TemplateEditorBlock(t, absLibraries, onSave, onDelete)
            Spacer(Modifier.height(8.dp))
        }
        var newName by remember { mutableStateOf("") }
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Název nové šablony") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            if (newName.isNotBlank()) { onCreate(newName); newName = "" }
        }) { Text("+ Vytvořit šablonu") }
    }
}

/** Editor jedné šablony (sbalovací) — název, hlavní/viditelné sekce, žánry, věk, zamčené domény. */
@Composable
private fun TemplateEditorBlock(
    template: TemplateEntity,
    absLibraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    onSave: (TemplateEntity, String, AgeRating?, ProfileConfig) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
) {
    var open by remember(template.id) { mutableStateOf(false) }
    val initial = remember(template.id, template.configJson) { ProfileConfig.fromJson(template.configJson) }
    var name by remember(template.id, template.name) { mutableStateOf(template.name) }
    var cfg by remember(template.id, template.configJson) { mutableStateOf(initial) }
    var age by remember(template.id, template.maxAgeRating) {
        mutableStateOf(template.maxAgeRating?.let { runCatching { AgeRating.valueOf(it) }.getOrNull() })
    }
    var blockText by remember(template.id, template.configJson) { mutableStateOf(initial.blockedGenres.joinToString(", ")) }
    var allowText by remember(template.id, template.configJson) { mutableStateOf(initial.allowedGenres.joinToString(", ")) }

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
                    "🧩 " + name.ifBlank { "(bez názvu)" },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Text(
                    "${cfg.lockedKeys.size} 🔒",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Sbalit" else "Rozbalit",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Název šablony") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                Text("Hlavní sekce (otevře se po vstupu)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                LandingDropdown(
                    current = cfg.defaultSection,
                    options = LANDING_OPTIONS.filter { (key, _) -> cfg.isSectionVisible(key) },
                    onSelect = { key -> cfg = cfg.copy(defaultSection = key) },
                )
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Viditelné sekce a podsekce", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Text("📱", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(34.dp))
                    Text("📺", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(22.dp))
                }
                SECTION_TOGGLES.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Switch(
                            checked = cfg.isSectionVisible(key),
                            onCheckedChange = { visible ->
                                val hidden = toggledHidden(cfg, key, visible)
                                val newDefault = cfg.defaultSection?.takeIf { it !in hidden }
                                cfg = cfg.copy(hiddenSections = hidden, defaultSection = newDefault)
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = tvSectionVisible(cfg, key),
                            onCheckedChange = { visible ->
                                cfg = cfg.copy(hiddenSectionsTv = toggledHiddenTv(cfg, key, visible))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Pořadí sekcí/podsekcí (Plan STRATA Fáze E) — šablona předvyplní pořadí profilu při přiřazení.
                OrderEditor(
                    title = "Pořadí sekcí (podrž a táhni)",
                    orderedKeys = cfg.orderedSections(),
                    label = { NAV_ORDER_LABELS[it] ?: it },
                    onReorder = { newOrder -> cfg = cfg.copy(sectionOrder = newOrder) },
                )
                OrderEditor(
                    title = "Pořadí podsekcí Sleduj",
                    orderedKeys = cfg.orderedSubsections(),
                    label = { SUBSECTION_ORDER_LABELS[it] ?: it },
                    onReorder = { newOrder -> cfg = cfg.copy(subsectionOrder = newOrder) },
                )

                Text("Žánry", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
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
                Spacer(Modifier.height(12.dp))

                Text("Věkový limit", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                AgeRatingDropdown(current = age, onSelect = { age = it })
                Spacer(Modifier.height(12.dp))

                Text("🔒 Zamčené domény (uživatel needituje; bere se ze šablony)", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                TEMPLATE_LOCKS.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒 $label", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Switch(
                            checked = cfg.lockedKeys.contains(key),
                            onCheckedChange = { enabled ->
                                cfg = cfg.copy(lockedKeys = if (enabled) cfg.lockedKeys + key else cfg.lockedKeys - key)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val block = blockText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                        val allow = allowText.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                        onSave(template, name, age, cfg.copy(blockedGenres = block, allowedGenres = allow))
                    }) { Text("💾 Uložit šablonu") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onDelete(template) }) { Text("🗑 Smazat") }
                }
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
            // Plan FUSE F5 — D-pad highlight hlavičky sekce na TV (no-op telefon).
            .tvFocusable()
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

/**
 * Plan MAESTRO — ovládání domácí sestavy. Když je zapnuté: (1) hlasitost v Ovladači cílí na AV
 * receiver (pravý master obýváku, box jen digitálně zeslabuje); (2) „Přehrát na TV" umí probrat
 * celou sestavu z vypnutého stavu (receiver + box + spuštění Yellyfinu) přes IP boxu a jeho MAC.
 */
@Composable
private fun AvrSection(
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
private fun AvrTextField(
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
