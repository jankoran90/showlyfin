package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.PinHasher
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel

/**
 * CELLULOID (SHW-98) M2.5 — telefonní sekce „Profil" appky Filmy.
 * 2 pevné profily (Dospělý/Děti) — reuse [SettingsViewModel] (`profiles`, `activeProfileId`, `switchProfile`)
 * nad sdíleným ProfileRepository. BEZ přidávat/mazat (appka má fixní roster). Přepnutí na profil s PINem
 * (typicky Dospělý) vyžaduje PIN ([PinHasher.verify]) → děti se nepřepnou na Dospělého bez hesla.
 */
@Composable
fun FilmyProfileScreen(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    // M2.6: dlouhý stisk na kartu → nastavit/změnit/zrušit PIN (typicky Dospělý, ať se děti nepřepnou).
    var pinSetFor by remember { mutableStateOf<ProfileEntity?>(null) }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(title = "Profil", onMenu = onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Kdo se dívá?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Podržením profilu nastavíš nebo zrušíš PIN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.profiles.forEach { p ->
                ProfileCard(
                    profile = p,
                    active = p.id == ui.activeProfileId,
                    onClick = {
                        when {
                            p.id == ui.activeProfileId -> Unit
                            !p.loginPinHash.isNullOrBlank() -> pinFor = p
                            else -> vm.switchProfile(p.id)
                        }
                    },
                    onLongClick = { pinSetFor = p },
                )
            }
        }
    }

    pinFor?.let { target ->
        PinDialog(
            profileName = target.name,
            onDismiss = { pinFor = null },
            onVerified = { vm.switchProfile(target.id); pinFor = null },
            verify = { pin -> PinHasher.verify(pin, target.loginPinHash) },
        )
    }

    pinSetFor?.let { target ->
        SetPinDialog(
            profileName = target.name,
            hasPin = !target.loginPinHash.isNullOrBlank(),
            onDismiss = { pinSetFor = null },
            onSave = { pin -> vm.setProfilePin(target.id, pin); pinSetFor = null },
            onClear = { vm.clearProfilePin(target.id); pinSetFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(profile: ProfileEntity, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (profile.isAdmin) Icons.Rounded.Person else Icons.Rounded.ChildCare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = if (profile.isAdmin) "Dospělý — plný přístup" else "Děti — jen vhodný obsah",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!profile.loginPinHash.isNullOrBlank() && !active) {
                Icon(Icons.Rounded.Lock, contentDescription = "Chráněno PINem", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Aktivní", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PinDialog(
    profileName: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    verify: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN pro $profileName") },
        text = {
            Column {
                Text("Zadej PIN pro přepnutí na tento profil.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit); error = false },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error) {
                    Text("Nesprávný PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (verify(pin)) onVerified() else error = true }) { Text("Přepnout") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}

/** M2.6 — nastavení/změna/zrušení PINu profilu (reuse [SettingsViewModel.setProfilePin]/`clearProfilePin`). */
@Composable
private fun SetPinDialog(
    profileName: String,
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Změnit PIN — $profileName" else "Nastavit PIN — $profileName") },
        text = {
            Column {
                Text(
                    "Zadej číselný PIN. Bez PINu se na profil může přepnout kdokoli.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pin) },
                enabled = pin.length >= 3,
            ) { Text("Uložit") }
        },
        dismissButton = {
            // Když PIN existuje, dej i možnost ho zrušit; jinak jen Zrušit dialog.
            if (hasPin) {
                TextButton(onClick = onClear) { Text("Zrušit PIN") }
            } else {
                TextButton(onClick = onDismiss) { Text("Zavřít") }
            }
        },
    )
}
