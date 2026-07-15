package com.github.jankoran90.showlyfin.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.github.jankoran90.showlyfin.ui.tv.components.LocalSavedSourceKeys
import com.github.jankoran90.showlyfin.ui.tv.components.TvImmersiveBackground
import com.github.jankoran90.showlyfin.ui.tv.discover.TvDiscoverScreen
import com.github.jankoran90.showlyfin.ui.tv.filmoteka.TvFilmotekaScreen
import com.github.jankoran90.showlyfin.ui.tv.lapidary.TvLapidaryScreen
import com.github.jankoran90.showlyfin.ui.tv.trakt.TvTraktScreen
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeScreen
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeSidebar
import com.github.jankoran90.showlyfin.ui.tv.profile.TvProfileSwitcher
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
    // LAPIDARY S4b: klik na kartu řady „Uloženo k přehrání" → detail v režimu one-click (default = jako onOpenDetail).
    onOpenDetailPlay: (MediaItem) -> Unit = onOpenDetail,
    onOpenJellyfinDetail: (itemId: String) -> Unit,
    onOpenLibrary: (libraryId: String, libraryName: String, collectionType: String?) -> Unit,
    homeVm: TvHomeViewModel = hiltViewModel(),
) {
    val immersive by homeVm.immersiveBackground.collectAsStateWithLifecycle()
    val immersiveHeader by homeVm.immersiveHeader.collectAsStateWithLifecycle()
    val sidebarEntries by homeVm.sidebar.collectAsStateWithLifecycle()
    // COUCH R2: zamčený/dětský profil nevidí sekci Trakt (ani obsah mimo dětský).
    val traktAllowed by homeVm.traktAllowed.collectAsStateWithLifecycle()
    val sidebarItems = sidebarEntries.filter { it.enabled }.mapNotNull { SidebarItem.fromName(it.item) }
        .filter { it != SidebarItem.TRAKT || traktAllowed }
    // LAPIDARY (SHW-96) — klíče titulů s uloženým zdrojem → odznak „hraje hned" na kartách všech sekcí.
    val savedSourceKeys by homeVm.savedSourceKeys.collectAsStateWithLifecycle()

    // Back v ne-Home sekci = zpět na Domů (skládá se jen když je shell aktuální = žádný drill nahoře).
    BackHandler(enabled = section != TvSection.HOME) { onSelectSection(TvSection.HOME) }

    // COUCH R2: kdyby se přepnul na dětský profil zatímco je otevřená sekce Trakt → hoď zpět na Domů.
    LaunchedEffect(traktAllowed, section) {
        if (!traktAllowed && section == TvSection.TRAKT) onSelectSection(TvSection.HOME)
    }

    // COUCH T5: overlay přepínače profilu (spouští se z profilového tlačítka dole ve sidebaru).
    var showProfiles by remember { mutableStateOf(false) }

    // Immersive info z fokusované karty (debounce proti thrashingu při rychlém D-padu).
    var rawInfo by remember { mutableStateOf<ImmersiveInfo?>(null) }
    var shownInfo by remember { mutableStateOf<ImmersiveInfo?>(null) }
    LaunchedEffect(rawInfo) { delay(120); shownInfo = rawInfo }
    LaunchedEffect(section) { rawInfo = null }

    CompositionLocalProvider(LocalSavedSourceKeys provides savedSourceKeys) {
    Box(Modifier.fillMaxSize()) {
        if (immersive) TvImmersiveBackground(shownInfo)

        Row(Modifier.fillMaxSize()) {
            TvHomeSidebar(
                items = sidebarItems,
                active = section.toSidebarItem(),
                onMove = { item, up -> homeVm.moveSidebar(item.name, up) },
                onOpenProfiles = { showProfiles = true },
                onSelect = { item ->
                    when (item) {
                        SidebarItem.DOMU -> onSelectSection(TvSection.HOME)
                        SidebarItem.OBJEVOVAT -> onSelectSection(TvSection.DISCOVER)
                        SidebarItem.FILMOTEKA -> onSelectSection(TvSection.FILMOTEKA)
                        SidebarItem.KLENOTY -> onSelectSection(TvSection.LAPIDARY)
                        SidebarItem.TRAKT -> onSelectSection(TvSection.TRAKT)
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
                        onOpenDetailPlay = onOpenDetailPlay,
                        onOpenJellyfinDetail = onOpenJellyfinDetail,
                        immersive = immersive,
                        immersiveHeader = immersiveHeader,
                        onFocusItem = { rawInfo = it },
                        homeVm = homeVm,
                    )
                    TvSection.DISCOVER -> TvDiscoverScreen(
                        onOpenDetail = onOpenDetail,
                        immersive = immersive,
                        onFocusItem = { rawInfo = it },
                    )
                    TvSection.FILMOTEKA -> TvFilmotekaScreen(
                        onOpenDetail = onOpenDetail,
                        onOpenJellyfinDetail = onOpenJellyfinDetail,
                        immersive = immersive,
                        immersiveHeader = immersiveHeader,
                        onFocusItem = { rawInfo = it },
                    )
                    TvSection.LAPIDARY -> TvLapidaryScreen(
                        onOpenDetail = onOpenDetail,
                        onOpenJellyfinDetail = onOpenJellyfinDetail,
                        immersive = immersive,
                        immersiveHeader = immersiveHeader,
                        onFocusItem = { rawInfo = it },
                    )
                    TvSection.TRAKT -> TvTraktScreen(
                        onOpenDetail = onOpenDetail,
                        immersive = immersive,
                        onFocusItem = { rawInfo = it },
                    )
                    TvSection.LIBRARY -> TvLibraryScreen(
                        onOpenLibrary = onOpenLibrary,
                        onOpenDetail = onOpenDetail,
                        onOpenJellyfinDetail = onOpenJellyfinDetail,
                        immersive = immersive,
                        immersiveHeader = immersiveHeader,
                        onFocusItem = { rawInfo = it },
                    )
                    TvSection.WATCHLIST -> TvWatchlistScreen(
                        onOpenDetail = onOpenDetail,
                        onBack = { onSelectSection(TvSection.HOME) },
                    )
                    TvSection.SETTINGS -> TvSettingsScreen(onBack = { onSelectSection(TvSection.HOME) })
                }
            }
        }

        if (showProfiles) TvProfileSwitcher(onDismiss = { showProfiles = false })
    }
    }
}

private fun TvSection.toSidebarItem(): SidebarItem = when (this) {
    TvSection.HOME -> SidebarItem.DOMU
    TvSection.DISCOVER -> SidebarItem.OBJEVOVAT
    TvSection.FILMOTEKA -> SidebarItem.FILMOTEKA
    TvSection.LAPIDARY -> SidebarItem.KLENOTY
    TvSection.TRAKT -> SidebarItem.TRAKT
    TvSection.LIBRARY -> SidebarItem.KNIHOVNA
    TvSection.WATCHLIST -> SidebarItem.OBLIBENE
    TvSection.SETTINGS -> SidebarItem.NASTAVENI
}
