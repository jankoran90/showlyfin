package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.feature.listen.ui.HiddenPodcastsSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenOfflineSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenOrderSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoverySettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastLinksSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastVideoQualitySettingsSection

/**
 * Slovo (EXCISE/SHW-103 Krok 2) — Nastavení poslechové appky (zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmySettingsScreen], ale bez filmu/profilu/PIN).
 * Kategorické sbalovací karty: Vzhled (sdílený motiv) + poslechové sekce reuse z :feature:feature-listen.
 * Známý gap (Krok 3 app-slovo): „O aplikaci" (verze/OTA UpdateChecker) + cast cíl „Na TV" — potřebují app kontext.
 */
@Composable
fun SlovoSettingsScreen(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SlovoSectionBar(title = "Nastavení", onMenu = onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SlovoCollapsibleSection("Účet / Audioknihy", Icons.Rounded.AccountCircle) {
                SlovoAccountSection()
            }
            SlovoCollapsibleSection("Vzhled", Icons.Rounded.Palette, initiallyExpanded = true) {
                SlovoAppearanceSection()
            }
            SlovoCollapsibleSection("Řazení zdrojů", Icons.Rounded.Sort) {
                ListenOrderSettingsSection()
            }
            SlovoCollapsibleSection("Stažené / offline", Icons.Rounded.Download) {
                ListenOfflineSettingsSection()
            }
            SlovoCollapsibleSection("Objevování podcastů", Icons.Rounded.Explore) {
                PodcastDiscoverySettingsSection()
            }
            SlovoCollapsibleSection("Skryté pořady", Icons.Rounded.VisibilityOff) {
                HiddenPodcastsSettingsSection()
            }
            SlovoCollapsibleSection("Propojené pořady", Icons.Rounded.Link) {
                PodcastLinksSettingsSection()
            }
            SlovoCollapsibleSection("Kvalita videa", Icons.Rounded.HighQuality) {
                PodcastVideoQualitySettingsSection()
            }
            SlovoCollapsibleSection("O aplikaci", Icons.Rounded.Info) {
                SlovoAboutSection()
            }
        }
    }
}
