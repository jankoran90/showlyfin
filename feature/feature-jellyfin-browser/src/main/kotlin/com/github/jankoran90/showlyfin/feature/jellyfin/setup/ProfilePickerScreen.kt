package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.ui.tvFocusable

/** Velikost kruhového avataru v úvodní bráně. */
private val AVATAR_SIZE = 116.dp

/**
 * Plan WARDEN W1 — avatar úvodní brána (startup gate). Řada kruhových avatarů (foto + jméno),
 * vodorovně scrollovatelná (na telefon se vejdou ~3, víc se odscrolluje; na TV/landscape víc).
 * Profil s PINem ([ProfileEntity.loginPinHash]) má zámek a po kliku vyžádá PIN ([onSubmitPin]).
 * Perzistence „zůstaň přihlášen" řeší [com.github.jankoran90.showlyfin.core.data.ProfileRepository]
 * (`restoreActive` přes uložené `active_profile_id`) → brána se ukáže jen když není aktivní profil.
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<ProfileEntity>,
    onProfileClicked: (ProfileEntity) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
    pinPromptName: String? = null,
    pinError: Boolean = false,
    onSubmitPin: (String) -> Unit = {},
    onCancelPin: () -> Unit = {},
    /** Plan VAULT — chyba poslední aktivace (Jellyfin odmítl creds profilu); null = bez chyby. */
    errorMessage: String? = null,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Kdo se dívá?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            ) {
                profiles.forEach { profile ->
                    AvatarItem(profile = profile, onClick = { onProfileClicked(profile) })
                }
                AddAvatarItem(onClick = onAddProfile)
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )
            }
        }
    }

    if (pinPromptName != null) {
        PinPromptDialog(
            profileName = pinPromptName,
            isError = pinError,
            onSubmit = onSubmitPin,
            onDismiss = onCancelPin,
        )
    }
}

@Composable
private fun AvatarItem(profile: ProfileEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(AVATAR_SIZE).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .tvFocusable(shape = CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // Plan PROFILES 1D: vlastní lokální fotka má přednost, pak Jellyfin avatar, pak iniciála.
            val localAvatar = profile.avatarPath?.let { java.io.File(it) }?.takeIf { it.exists() }
            val avatarUrl = profile.avatarTag?.let { tag ->
                "${profile.serverUrl}/Users/${profile.jellyfinUserId}/Images/Primary?tag=$tag&quality=85"
            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            // Plan WARDEN W1: zámek u profilu chráněného PINem.
            if (!profile.loginPinHash.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Chráněno PINem",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = profile.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (profile.isAdmin || profile.isDefault) {
            Text(
                text = buildString {
                    if (profile.isAdmin) append("Admin")
                    if (profile.isAdmin && profile.isDefault) append(" · ")
                    if (profile.isDefault) append("Výchozí")
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun AddAvatarItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(AVATAR_SIZE).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .tvFocusable(shape = CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Přidat profil",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Přidat profil",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PinPromptDialog(
    profileName: String,
    isError: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN pro $profileName") },
        text = {
            Column {
                Text(
                    "Zadej PIN pro odemčení profilu.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = isError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (isError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Špatný PIN, zkus to znovu.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pin) },
                enabled = pin.isNotBlank(),
            ) { Text("Odemknout") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zrušit") }
        },
    )
}
