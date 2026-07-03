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
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.*

/** Zamykatelné domény šablony (LockKeys) s popiskem pro UI. */
internal val TEMPLATE_LOCKS = listOf(
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
internal fun TemplateAssignDropdown(
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
internal fun TemplateEditorBlock(
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

                // GLIDE: pořadí řeší profil (Nastavení), ne šablona/admin — editor pořadí tu odebrán,
                // ať se pořadí z víc míst nepere.

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


