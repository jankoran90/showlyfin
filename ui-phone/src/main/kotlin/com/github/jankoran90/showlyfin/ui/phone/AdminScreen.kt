package com.github.jankoran90.showlyfin.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.tvOverscan
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Plan HELM — samostatná admin destinace (jen pro admin profil; gating řeší [ShowlyfinPhoneApp]
 * navItems). Administrace profilů přesunutá z webu CELÁ do appky: horní `ScrollableTabRow` +
 * `HorizontalPager` se třemi taby (jako MainScreen/ListenScreen).
 *
 * Taby: **Profily** (plný editor — sekce, knihovny, žánry, věk, přihlášení, PIN, hlavní sekce),
 * **Šablony** (lock-mapa authoring + přiřazení), **Záloha** (export/import balíku z backendu).
 *
 * Reuse [SettingsViewModel] — drží admin CRUD (profily/šablony) + observuje `ProfileRepository`.
 */
private enum class AdminTab(val title: String) {
    PROFILY("Profily"),
    SABLONY("Šablony"),
    ZALOHA("Záloha"),
}

@Composable
fun AdminScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = AdminTab.entries
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()

    // Plan HELM — načti zdroje editoru (knihovny) při otevření admin obrazovky.
    LaunchedEffect(Unit) {
        viewModel.loadAdminJellyfinLibraries()
        viewModel.loadAbsLibraries()
        viewModel.loadAdminPodcasts()
    }

    // FUSE/HELM: tvOverscan = bezpečné okraje na TV (no-op na telefonu), ať se taby/obsah neořežou.
    Column(modifier.fillMaxSize().tvOverscan()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = Color.White,
            edgePadding = 8.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title) },
                    modifier = Modifier.tvFocusable(), // D-pad fokus highlight tabu na TV
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (tabs[page]) {
                AdminTab.PROFILY -> AdminTabScroll {
                    // Správa profilů (přidat/přejmenovat/foto/výchozí/smazat).
                    ProfilesSection(
                        profiles = uiState.profiles,
                        activeProfileId = uiState.activeProfileId,
                        canManage = true,
                        onSwitch = { viewModel.switchProfile(it) },
                        onSetDefault = { viewModel.setDefaultProfile(it) },
                        onSetTvDefault = { viewModel.setTvDefaultProfile(it) },
                        onDelete = { viewModel.deleteProfile(it) },
                        onLogout = { viewModel.logoutProfile() },
                        onRename = { id, name -> viewModel.renameProfile(id, name) },
                        onSetAvatar = { id, uri -> viewModel.setProfileAvatar(id, uri) },
                        onAddProfile = { viewModel.addProfile() },
                    )
                    // Per-profil editor (sekce/knihovny/žánry/věk/PIN/přihlášení) — všechny profily.
                    if (uiState.profiles.isNotEmpty()) {
                        AdminRestrictionsSection(
                            profiles = uiState.profiles,
                            absLibraries = uiState.absLibraries,
                            adminPodcasts = uiState.adminPodcasts,
                            jellyfinLibraries = uiState.adminJellyfinLibraries,
                            templates = uiState.templates,
                            onUpdateAgeRating = { id, rating -> viewModel.updateProfileAgeRating(id, rating) },
                            onUpdateConfig = { id, transform -> viewModel.updateProfileConfig(id, transform) },
                            onAssignTemplate = { id, uuid -> viewModel.assignTemplate(id, uuid) },
                            onSetPin = { id, pin -> viewModel.setProfilePin(id, pin) },
                            onClearPin = { id -> viewModel.clearProfilePin(id) },
                            onSaveCredentials = { id, bundle -> viewModel.saveProfileCredentials(id, bundle) },
                            credsStatus = uiState.adminCredsStatus,
                        )
                        Spacer(Modifier.height(16.dp))
                        // Plan VAULT — Trakt (OAuth) pro AKTIVNÍ profil; tokeny se uloží do jeho balíku.
                        AdminTraktCard(uiState = uiState, viewModel = viewModel)
                    }
                }
                AdminTab.SABLONY -> AdminTabScroll {
                    TemplateAuthoringSection(
                        templates = uiState.templates,
                        absLibraries = uiState.absLibraries,
                        onCreate = { viewModel.createTemplate(it) },
                        onSave = { tpl, name, age, cfg -> viewModel.saveTemplate(tpl, name, age, cfg) },
                        onDelete = { viewModel.deleteTemplate(it) },
                    )
                }
                AdminTab.ZALOHA -> AdminBackupTab(viewModel)
            }
        }
    }
}

/** Plan HELM — scrollovatelný kontejner obsahu tabu. */
@Composable
private fun AdminTabScroll(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

/**
 * Plan HELM H5 — tab Záloha: export balíku profilů+šablon z backendu do souboru (SAF) a import zpět.
 * Backend zůstává zdrojem pravdy; tohle je jen ruční záloha/obnova celého balíku.
 */
@Composable
private fun AdminBackupTab(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportProfiles { json ->
            if (json == null) { status = "Export selhal (backend nedostupný?)"; return@exportProfiles }
            scope.launch {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
                status = if (ok) "Záloha uložena." else "Zápis do souboru selhal."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (json.isNullOrBlank()) { status = "Soubor nelze přečíst."; return@launch }
            viewModel.importProfiles(json) { ok ->
                status = if (ok) "Import dokončen. Balík obnoven na backendu." else "Import selhal."
            }
        }
    }

    AdminTabScroll {
        Text("Záloha profilů a šablon", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(
            "Export stáhne celý balík profilů + šablon (vč. přihlašovacích údajů) z backendu do souboru. " +
                "Import ho nahraje zpět. Backend je zdroj pravdy.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        OutlinedButton(
            onClick = { exportLauncher.launch("showlyfin-profily-zaloha.json") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exportovat do souboru") }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Importovat ze souboru") }
        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Plan VAULT — přihlášení Trakt (OAuth, vč. Google sign-in) pro **aktivní profil**. Tokeny se po
 * úspěchu uloží do balíku aktivního profilu ([SettingsViewModel.captureTraktIntoActiveProfile]) a
 * pushnou na backend. Telefon = browser redirect; TV = device-code (kód na trakt.tv/activate).
 * Pro jiný profil je třeba na něj nejdřív přepnout (Trakt vyžaduje interaktivní přihlášení vlastníka).
 */
@Composable
private fun AdminTraktCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val activeName = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }?.name ?: "—"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Trakt — profil: $activeName", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "Přihlášení přes Trakt (i účtem Google). Tokeny se uloží k aktivnímu profilu. " +
                    "Pro jiný profil na něj nejdřív přepni.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            when {
                uiState.traktLoggedIn -> {
                    Text("Přihlášen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Odhlásit z Trakt") }
                }
                isTvFormFactor() -> {
                    Text("Nepřihlášen", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f))
                    uiState.traktUserCode?.let { code ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Otevři ${uiState.traktVerificationUrl ?: "trakt.tv/activate"} a zadej kód:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(code, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.startTraktDeviceLogin() },
                        enabled = uiState.traktUserCode == null,
                        modifier = Modifier.fillMaxWidth().tvFocusable(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                    ) { Text(if (uiState.traktUserCode == null) "Přihlásit přes Trakt" else "Čekám na potvrzení…") }
                    uiState.traktStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                else -> {
                    Text("Nepřihlášen", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.traktAuthorizeUrl))) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                    ) { Text("Přihlásit přes Trakt") }
                }
            }
        }
    }
}
