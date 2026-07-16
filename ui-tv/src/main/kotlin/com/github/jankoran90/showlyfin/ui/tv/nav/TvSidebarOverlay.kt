package com.github.jankoran90.showlyfin.ui.tv.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.home.SidebarItem
import com.github.jankoran90.showlyfin.feature.discover.home.TvHomeViewModel
import com.github.jankoran90.showlyfin.ui.tv.home.TvHomeSidebar

/**
 * CONVERGE (SHW-97) — sidebar jako PŘEKRYV nad detailem. Detail zůstane složený vzadu; D-pad doleva z akcí
 * detailu tento overlay zobrazí (sidebar vlevo + ztmavovací scrim přes zbytek). Výběr sekce → skok do shellu
 * na tu sekci ([onSelectSection] + `goHome`), Hledat → [onOpenSearch]. D-pad DOPRAVA / Back / klik na scrim →
 * [onDismiss] (schová overlay, fokus zpět do detailu). Vlevo je jediné, co je vidět z detailu překryté;
 * uživatel se odsud dostane kamkoli, včetně Nastavení, aniž by ztratil kontext karty.
 */
@Composable
fun TvSidebarOverlay(
    activeSection: TvSection,
    onSelectSection: (TvSection) -> Unit,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
    homeVm: TvHomeViewModel = hiltViewModel(),
) {
    val sidebarEntries by homeVm.sidebar.collectAsStateWithLifecycle()
    val traktAllowed by homeVm.traktAllowed.collectAsStateWithLifecycle()
    // Stejná filtrace jako v TvShell (dětský/zamčený profil nevidí Trakt ani „Chci vidět").
    val sidebarItems = sidebarEntries.filter { it.enabled }.mapNotNull { SidebarItem.fromName(it.item) }
        .filter { (it != SidebarItem.TRAKT && it != SidebarItem.CHCI_VIDET) || traktAllowed }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // D-pad doprava (ze sidebaru už není kam) nebo Back → schovej overlay, fokus zpět do detailu.
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionRight || e.key == Key.Back)) {
                    onDismiss(); true
                } else {
                    false
                }
            },
    ) {
        // Ztmavovací scrim přes celý detail; klik (fallback) = zavřít.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Row(Modifier.fillMaxSize()) {
            TvHomeSidebar(
                items = sidebarItems,
                active = activeSection.toOverlaySidebarItem(),
                onMove = { item, up -> homeVm.moveSidebar(item.name, up) },
                firstItemFocus = focus,
                onSelect = { item ->
                    when (item) {
                        SidebarItem.DOMU -> onSelectSection(TvSection.HOME)
                        SidebarItem.OBJEVOVAT -> onSelectSection(TvSection.FOR_YOU)
                        SidebarItem.FILMOTEKA -> onSelectSection(TvSection.FILMOTEKA)
                        SidebarItem.KLENOTY -> onSelectSection(TvSection.LAPIDARY)
                        SidebarItem.TRAKT -> onSelectSection(TvSection.TRAKT)
                        SidebarItem.CHCI_VIDET -> onSelectSection(TvSection.WANT_TO_SEE)
                        SidebarItem.KNIHOVNA -> onSelectSection(TvSection.LIBRARY)
                        SidebarItem.OBLIBENE -> onSelectSection(TvSection.WATCHLIST)
                        SidebarItem.NASTAVENI -> onSelectSection(TvSection.SETTINGS)
                        SidebarItem.HLEDAT -> onOpenSearch()
                    }
                },
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Mapování aktivní sekce na sidebar položku (jen pro zvýraznění v overlayi). */
private fun TvSection.toOverlaySidebarItem(): SidebarItem = when (this) {
    TvSection.HOME -> SidebarItem.DOMU
    TvSection.FOR_YOU -> SidebarItem.OBJEVOVAT
    TvSection.FILMOTEKA -> SidebarItem.FILMOTEKA
    TvSection.LAPIDARY -> SidebarItem.KLENOTY
    TvSection.TRAKT -> SidebarItem.TRAKT
    TvSection.LIBRARY -> SidebarItem.KNIHOVNA
    TvSection.WATCHLIST -> SidebarItem.OBLIBENE
    TvSection.WANT_TO_SEE -> SidebarItem.CHCI_VIDET
    TvSection.SETTINGS -> SidebarItem.NASTAVENI
}
