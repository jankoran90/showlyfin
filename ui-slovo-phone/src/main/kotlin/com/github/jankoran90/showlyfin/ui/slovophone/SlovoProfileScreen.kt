package com.github.jankoran90.showlyfin.ui.slovophone

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.PinHasher

/**
 * Profily (2026-08-15, user „profily jak jsme je používali v showlyfin") — sekce „Profil" appky
 * Slovo. Vzor [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyProfileScreen]: 2 pevné profily
 * (Dospělý/Děti), přepnutí s PINem (chrání zpětné přepnutí na Dospělého), podržením nastav/zruš PIN.
 * Navíc jen u Dospělého: seznam ABS podcastů s zaškrtávátkem „vidí Děti" (admin curation).
 */
@Composable
fun SlovoProfileScreen(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SlovoProfileViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinSetFor by remember { mutableStateOf<ProfileEntity?>(null) }
    val activeIsAdmin = ui.profiles.firstOrNull { it.id == ui.activeProfileId }?.isAdmin == true

    LaunchedEffect(activeIsAdmin) { if (activeIsAdmin) vm.loadPodcastCuration() }

    Column(modifier.fillMaxSize()) {
        SlovoSectionBar(title = "Profil", onMenu = onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Kdo poslouchá?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Podržením profilu nastavíš nebo zrušíš PIN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.profiles.forEach { p ->
                SlovoProfileCard(
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

            if (activeIsAdmin) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Podcasty pro Děti",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Které pořady se dětskému profilu zobrazí v sekci Poslech (jen jejich stažené epizody).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.podcastsLoading && ui.podcasts.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (ui.podcasts.isEmpty()) {
                    Text(
                        "Žádné podcasty v ABS knihovně.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ui.podcasts.forEach { pod ->
                        val visible = pod.id !in ui.hiddenForKids
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { vm.setPodcastVisibleForKids(pod.id, !visible) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = visible, onCheckedChange = { vm.setPodcastVisibleForKids(pod.id, it) })
                            Text(
                                pod.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    pinFor?.let { target ->
        SlovoPinDialog(
            profileName = target.name,
            onDismiss = { pinFor = null },
            onVerified = { vm.switchProfile(target.id); pinFor = null },
            verify = { pin -> PinHasher.verify(pin, target.loginPinHash) },
        )
    }

    pinSetFor?.let { target ->
        SlovoSetPinDialog(
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
private fun SlovoProfileCard(profile: ProfileEntity, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
                    text = if (profile.isAdmin) "Dospělý — plný přístup" else "Děti — dětská knihovna + schválené podcasty",
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
private fun SlovoPinDialog(
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

@Composable
private fun SlovoSetPinDialog(
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
            TextButton(onClick = { onSave(pin) }, enabled = pin.length >= 3) { Text("Uložit") }
        },
        dismissButton = {
            if (hasPin) {
                TextButton(onClick = onClear) { Text("Zrušit PIN") }
            } else {
                TextButton(onClick = onDismiss) { Text("Zavřít") }
            }
        },
    )
}
