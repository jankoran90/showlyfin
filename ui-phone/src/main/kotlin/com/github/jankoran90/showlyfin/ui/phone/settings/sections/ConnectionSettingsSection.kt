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
internal fun ConnectionSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isAdmin: Boolean,
    credLocked: Boolean,
    onOpenUploader: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
          if (credLocked) {
            LockedByAdminNote()
          } else {
            // Jellyfin sekce
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

            // Plan STRATA Fáze I — všechna přihlášení pohromadě: Audiobookshelf + Uploader sem.
            Spacer(Modifier.height(16.dp))
            AbsSection(
                configured = uiState.absConfigured,
                baseUrl = uiState.absBaseUrl,
                hideFinishedEpisodes = uiState.hideFinishedEpisodes,
                onToggleHideFinished = { viewModel.setHideFinishedEpisodes(it) },
                isAdmin = isAdmin,
                onOpenAdmin = onOpenAdmin,
            )
            Spacer(Modifier.height(16.dp))
            UploaderSection(isAdmin = isAdmin, onOpenAdmin = onOpenAdmin)
          }
}

@Composable
internal fun UploaderSection(
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    viewModel: UploaderViewModel = hiltViewModel(),
) {
    // Plan VAULT — jen stav. Přihlášení Uploaderu se spravuje v sekci Správa (jediný zdroj pravdy).
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
internal fun AbsSection(
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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


