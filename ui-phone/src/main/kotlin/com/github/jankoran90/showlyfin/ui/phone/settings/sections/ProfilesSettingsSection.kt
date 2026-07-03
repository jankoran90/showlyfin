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
internal fun ProfilesCategorySection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isAdmin: Boolean,
) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
internal fun ProfileAvatar(profile: ProfileEntity) {
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


