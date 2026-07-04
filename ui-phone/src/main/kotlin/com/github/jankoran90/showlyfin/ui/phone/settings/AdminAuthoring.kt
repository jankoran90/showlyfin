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

/** Sekce přepínatelné adminem (Plan PROFILES 1E). SLEDUJ + NASTAVENI jsou vždy viditelné. */
internal val SECTION_TOGGLES = listOf(
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
internal fun toggledHidden(cfg: ProfileConfig, key: String, visible: Boolean): Set<String> =
    if (visible) cfg.hiddenSections - key else cfg.hiddenSections + key


/** Viditelnost sekce na TV (Plan STRATA): TV sada skrytých null = zrcadlí telefon. */
internal fun tvSectionVisible(cfg: ProfileConfig, key: String): Boolean {
    val tvHidden = cfg.hiddenSectionsTv ?: return cfg.isSectionVisible(key)
    return key !in tvHidden
}


/** Přepne viditelnost sekce [key] na TV — od prvního dotyku je TV sada skrytých nezávislá (forkne z telefonu). */
internal fun toggledHiddenTv(cfg: ProfileConfig, key: String, visible: Boolean): Set<String> {
    val base = cfg.hiddenSectionsTv ?: cfg.hiddenSections
    return if (visible) base - key else base + key
}


/** Možnosti „hlavní" (výchozí otevřené) sekce per profil (Plan PROFILES Fáze 4). */
internal val LANDING_OPTIONS = listOf(
    ProfileConfig.Sections.KNIHOVNA to "Sleduj → Knihovna",
    ProfileConfig.Sections.CHCI_VIDET to "Sleduj → Chci vidět",
    ProfileConfig.Sections.OBJEVIT to "Sleduj → Objevit",
    ProfileConfig.Sections.HISTORIE to "Sleduj → Historie",
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pro každý profil nastav hlavní sekci, viditelné sekce/podsekce, knihovny, žánry, věk, " +
                "PIN a přihlašovací údaje. Aplikuje se při přepnutí profilu. Každý profil má vlastní izolované nastavení.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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


/** Plan HELM — nastavení/zrušení app-login PINu profilu (krátký rodinný kód). */
@Composable
internal fun ProfilePinEditor(
    hasPin: Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Text(
        if (hasPin) "PIN: nastaven" else "PIN: bez PINu",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun ProfileCredentialsEditor(
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
        Text("Přihlašovací údaje", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun CredField(label: String, value: String, isPassword: Boolean = false, onChange: (String) -> Unit) {
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
internal fun LandingDropdown(
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
internal fun AgeRatingDropdown(
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


