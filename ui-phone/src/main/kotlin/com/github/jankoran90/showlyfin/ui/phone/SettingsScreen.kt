package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.ui.phone.settings.SettingsCategory
import com.github.jankoran90.showlyfin.ui.phone.settings.SettingsCategoryScreen
import com.github.jankoran90.showlyfin.ui.phone.settings.SettingsHome
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.AppearanceSettingsSection
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.ConnectionSettingsSection
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.HomeTheaterSettingsSection
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.ListenSettingsSection
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.ProfilesCategorySection
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.StreamingSettingsSection
import com.github.jankoran90.showlyfin.ui.phone.settings.sections.SystemSettingsSection

/**
 * Nastavení = rozcestník ([SettingsHome]) + podstránky ([SettingsCategoryScreen]) místo dřívějšího
 * 2418řádkového monolitu (CHORUS Osa 1, kánon hubme). Tenký orchestrátor: drží vybranou kategorii a
 * přepíná Home ⇄ podstránka; obsah podstránky = sekční composable z `settings/sections/`. Signatura
 * zachována → `ShowlyfinPhoneApp` i `SettingsViewModel` beze změny.
 */
@Composable
fun SettingsScreen(
    onOpenUploader: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = true,
    onOpenAdmin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<SettingsCategory?>(null) }
    // Plan WARDEN W4: ne-admin s uzamčeným přihlášením needituje účty (lock-mapa CREDENTIALS).
    val credLocked = !isAdmin && ProfileConfig.LockKeys.CREDENTIALS in uiState.lockedKeys
    // Stav sbalení pod-skupin na sloučených podstránkách (Streamování/Vzhled/Systém). Fresh při novém vstupu.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val sel = selected
    if (sel == null) {
        SettingsHome(
            uiState = uiState,
            isAdmin = isAdmin,
            onOpenCategory = { selected = it },
            onSwitchProfile = { viewModel.logoutProfile() },
            modifier = modifier,
        )
    } else {
        SettingsCategoryScreen(category = sel, onBack = { selected = null }, modifier = modifier) {
            when (sel) {
                SettingsCategory.CONNECTION -> ConnectionSettingsSection(
                    uiState, viewModel, isAdmin, credLocked, expanded, onOpenUploader, onOpenAdmin,
                )
                SettingsCategory.STREAMING -> StreamingSettingsSection(
                    uiState, viewModel, credLocked, expanded, onOpenUploader,
                )
                SettingsCategory.LISTEN -> ListenSettingsSection(uiState, viewModel)
                SettingsCategory.APPEARANCE -> AppearanceSettingsSection(uiState, viewModel, isAdmin, expanded)
                SettingsCategory.PROFILES -> ProfilesCategorySection(uiState, viewModel, isAdmin)
                SettingsCategory.HOME_THEATER -> HomeTheaterSettingsSection(uiState, viewModel, expanded)
                SettingsCategory.SYSTEM -> SystemSettingsSection(uiState, viewModel, expanded)
            }
            uiState.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
