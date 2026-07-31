package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * CELLULOID (SHW-98) Fáze 2 M2.1 — postranní menu appky „Filmy" (vzor audioman [AppDrawer]).
 * Hlavní navigace mezi sekcemi + oddělené Nastavení/Profil dole. Vzhled čte z motivu (AMOLED + amber).
 * Scrollovatelné — na škálovaných displejích se poslední položky nesmí uříznout (poučení z audiomanu).
 */
@Composable
fun FilmyDrawer(
    current: FilmySection,
    onSelect: (FilmySection) -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "Filmy",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 16.dp),
            )
            // PŮDORYS (SHW-112, user 2026-07-31): pořadí i viditelnost sekcí si skládá uživatel
            // (Nastavení → Domov a menu). Bez vlastní volby platí kanonické pořadí, kde „Filmotéka
            // jako výchozí" vynese Filmotéku nahoru (user 2026-07-18). Kategorické nadpisy zmizely —
            // seznam je teď jeden uživatelský, ne dvě pevné skupiny.
            val filmotekaFirst = FilmyShellPrefs.defaultFilmoteka(LocalContext.current)
            val layoutVm: FilmyHomeLayoutViewModel = hiltViewModel()
            val storedMenu by layoutVm.menu.collectAsStateWithLifecycle()
            FilmyMenuConfig.visibleSections(storedMenu, filmotekaFirst)
                .forEach { DrawerRow(it, current, onSelect) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
            DrawerRow(FilmySection.SETTINGS, current, onSelect)
            // PROFIL (user 2026-07-28 „místo Profil zobrazovat aktivní vybraný profil") — v menu má být
            // vidět, KDO je přihlášený, ne obecné slovo. Bez aktivního profilu zůstává původní popisek.
            val profileVm: com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel = hiltViewModel()
            val profileUi by profileVm.uiState.collectAsStateWithLifecycle()
            val activeName = profileUi.profiles.firstOrNull { it.id == profileUi.activeProfileId }?.name
            DrawerRow(FilmySection.PROFILE, current, onSelect, label = activeName?.takeIf { it.isNotBlank() })
        }
    }
}

@Composable
private fun DrawerRow(
    section: FilmySection,
    current: FilmySection,
    onSelect: (FilmySection) -> Unit,
    /** Vlastní popisek místo výchozího názvu sekce (Profil → jméno aktivního profilu). */
    label: String? = null,
) {
    NavigationDrawerItem(
        icon = { Icon(section.icon, contentDescription = null) },
        label = { Text(label ?: section.label) },
        selected = section == current,
        onClick = { onSelect(section) },
        modifier = Modifier.padding(horizontal = 12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
