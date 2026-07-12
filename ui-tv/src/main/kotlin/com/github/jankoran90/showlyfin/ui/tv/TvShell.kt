package com.github.jankoran90.showlyfin.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.home.SidebarItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.ui.tv.components.ImmersiveInfo
import com.github.jankoran90.showlyfin.ui.tv.components.TvImmersiveBackground
import com.github.jankoran90.showlyfin.ui.tv.discover.TvDiscoverScreen
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeScreen
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeSidebar
import com.github.jankoran90.showlyfin.ui.tv.library.TvLibraryScreen
import com.github.jankoran90.showlyfin.ui.tv.nav.TvSection
import com.github.jankoran90.showlyfin.ui.tv.settings.TvSettingsScreen
import com.github.jankoran90.showlyfin.ui.tv.watchlist.TvWatchlistScreen
import kotlinx.coroutines.delay

/**
 * TENFOOT (SHW-87) — TV shell: perzistentní levý [TvHomeSidebar] + přepínání hlavních [TvSection] +
 * Netflix immersive pozadí ([TvImmersiveBackground]) za sidebarem i obsahem. Sekce jsou peer přepínač
 * (drží [com.github.jankoran90.showlyfin.ui.tv.nav.TvNavViewModel.section]); drill (Detail/Player/…) jde
 * NAD shell na navigační stack. Back v ne-Home sekci → Home; v Home → propadne (ukončí appku).
 */
@Composable
fun TvShell(
    section: TvSection,
    onSelectSection: (TvSection) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    homeVm: TvHomeViewModel = hiltViewModel(),
) {
    val immersive by homeVm.immersiveBackground.collectAsStateWithLifecycle()
    val sidebarEntries by homeVm.sidebar.collectAsStateWithLifecycle()
    val sidebarItems = sidebarEntries.filter { it.enabled }.mapNotNull { SidebarItem.fromName(it.item) }

    // Back v ne-Home sekci = zpět na Domů (skládá se jen když je shell aktuální = žádný drill nahoře).
    BackHandler(enabled = section != TvSection.HOME) { onSelectSection(TvSection.HOME) }

    // Immersive info z fokusované karty (debounce proti thrashingu při rychlém D-padu).
    var rawInfo by remember { mutableStateOf<ImmersiveInfo?>(null) }
    var shownInfo by remember { mutableStateOf<ImmersiveInfo?>(null) }
    LaunchedEffect(rawInfo) { delay(120); shownInfo = rawInfo }
    LaunchedEffect(section) { rawInfo = null }

    Box(Modifier.fillMaxSize()) {
        if (immersive) TvImmersiveBackground(shownInfo)

        Row(Modifier.fillMaxSize()) {
            TvHomeSidebar(
                items = sidebarItems,
                active = section.toSidebarItem(),
                onSelect = { item ->
                    when (item) {
                        SidebarItem.DOMU -> onSelectSection(TvSection.HOME)
                        SidebarItem.OBJEVOVAT -> onSelectSection(TvSection.DISCOVER)
                        SidebarItem.KNIHOVNA -> onSelectSection(TvSection.LIBRARY)
                        SidebarItem.OBLIBENE -> onSelectSection(TvSection.WATCHLIST)
                        SidebarItem.NASTAVENI -> onSelectSection(TvSection.SETTINGS)
                        SidebarItem.HLEDAT -> onOpenSearch()
                    }
                },
            )

            Box(Modifier.weight(1f).fillMaxSize()) {
                when (section) {
                    TvSection.HOME -> TvHomeScreen(
                        onOpenDetail = onOpenDetail,
                        onOpenJellyfinDetail = onOpenJellyfinDetail,
                        immersive = immersive,
                        onFocusItem = { rawInfo = it },
                        homeVm = homeVm,
                    )
                    TvSection.DISCOVER -> TvDiscoverScreen(
                        onOpenDetail = onOpenDetail,
                        immersive = immersive,
                        onFocusItem = { rawInfo = it },
                    )
                    TvSection.LIBRARY -> TvLibraryScreen(onOpenLibrary = onOpenLibrary)
                    TvSection.WATCHLIST -> TvWatchlistScreen(
                        onOpenDetail = onOpenDetail,
                        onBack = { onSelectSection(TvSection.HOME) },
                    )
                    TvSection.SETTINGS -> TvSettingsScreen(onBack = { onSelectSection(TvSection.HOME) })
                }
            }
        }
    }
}

private fun TvSection.toSidebarItem(): SidebarItem = when (this) {
    TvSection.HOME -> SidebarItem.DOMU
    TvSection.DISCOVER -> SidebarItem.OBJEVOVAT
    TvSection.LIBRARY -> SidebarItem.KNIHOVNA
    TvSection.WATCHLIST -> SidebarItem.OBLIBENE
    TvSection.SETTINGS -> SidebarItem.NASTAVENI
}
