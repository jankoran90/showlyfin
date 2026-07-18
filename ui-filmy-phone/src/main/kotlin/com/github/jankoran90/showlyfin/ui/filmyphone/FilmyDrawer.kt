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
            DrawerSectionLabel("Objevování")
            // Pořadí dle prefu „Filmotéka jako výchozí" — když je zapnuto, Filmotéka je nahoře (user 2026-07-18).
            val filmotekaFirst = FilmyShellPrefs.defaultFilmoteka(LocalContext.current)
            FilmyShellPrefs.discoverOrder(filmotekaFirst).forEach { DrawerRow(it, current, onSelect) }

            DrawerSectionLabel("Knihovna")
            DrawerRow(FilmySection.LIBRARY, current, onSelect)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
            DrawerRow(FilmySection.SETTINGS, current, onSelect)
            DrawerRow(FilmySection.PROFILE, current, onSelect)
        }
    }
}

/** Kategorický nadpis v menu (oranžový, drobný) — seskupuje položky. */
@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerRow(
    section: FilmySection,
    current: FilmySection,
    onSelect: (FilmySection) -> Unit,
) {
    NavigationDrawerItem(
        icon = { Icon(section.icon, contentDescription = null) },
        label = { Text(section.label) },
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
