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

/** Jeden profil = sbalovací blok (default sbaleno, reset při odchodu z tabu). */
@Composable
internal fun ProfileAuthoringBlock(
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                Text("Šablona", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TemplateAssignDropdown(
                    templates = templates,
                    current = profile.templateUuid,
                    onSelect = { uuid -> onAssignTemplate(profile.id, uuid) },
                )
                Spacer(Modifier.height(12.dp))

                // — Hlavní sekce —
                Text("Hlavní sekce (otevře se po vstupu)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Viditelné sekce a podsekce", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("📱", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(34.dp))
                    Text("📺", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(22.dp))
                }
                SECTION_TOGGLES.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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

                // GLIDE: pořadí sekcí/podsekcí se řeší VÝHRADNĚ v profilu (Nastavení → „Pořadí sekcí"),
                // ne v adminu — ať se profilové a admin pořadí nepere. Admin smí pořadí jen ZAMKNOUT
                // (LockKeys.ORDER), ne ho tu sám editovat.

                // — Knihovny Jellyfin: whitelist (Plan HELM; nic = všechny) —
                if (jellyfinLibraries.isNotEmpty()) {
                    Text("Knihovny (Jellyfin) — nic zaškrtnuté = všechny", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val jwl = cfg.jellyfinLibraryWhitelist
                    jellyfinLibraries.forEach { lib ->
                        val checked = jwl == null || lib.id in jwl
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(lib.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
                    Text("Poslech — knihovny (nic = všechny)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val wl = cfg.absLibraryWhitelist
                    absLibraries.forEach { lib ->
                        val checked = wl == null || lib.id in wl
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(lib.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
                    Text("Podcasty — zapnuto = zobrazený pro tento profil", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    adminPodcasts.forEach { pod ->
                        val visible = pod.id !in cfg.hiddenPodcastIds
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pod.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
                Text("Žánry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Věkový limit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


