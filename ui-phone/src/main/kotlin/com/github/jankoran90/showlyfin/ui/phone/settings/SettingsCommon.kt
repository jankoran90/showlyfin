package com.github.jankoran90.showlyfin.ui.phone.settings

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
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.*

/**
 * Sbalovací kategorie Nastavení. Stav drží volající přes [expandedMap] (klíč = [title]),
 * default sbaleno. Recompozice z data state nesbalí (mapa žije mimo); odchod z tabu Nastavení
 * mapu zahodí → po návratu zase vše sbalené.
 */
@Composable
internal fun CollapsibleSettingsSection(
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


/** Plan WARDEN W2: hlavička Nastavení — avatar + jméno + stav vlevo, „Přepnout" (odhlásit→brána) vpravo. */
@Composable
internal fun ProfileHeader(profile: ProfileEntity?, onSwitch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile?.name ?: "Nepřihlášen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
internal fun LockedByAdminNote() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Zamčeno správcem profilu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun ManagedInAdminNote(isAdmin: Boolean, onOpenAdmin: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isAdmin) "Přihlašovací údaje spravuješ v sekci Správa."
            else "Přihlašovací údaje spravuje správce profilu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (isAdmin) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
            Text("Otevřít Správu")
        }
    }
}


@Composable
internal fun ListenGroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}


@Composable
internal fun ServerPodcastRow(title: String, checked: Boolean, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
        }
        Switch(checked = checked, enabled = !busy, onCheckedChange = onToggle)
    }
}


@Composable
internal fun ListenInfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}


@Composable
internal fun ListenSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ListenChipRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


