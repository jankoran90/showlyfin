package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

    Column(modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color(0xFF1A1A2E),
            contentColor = Color.White,
            edgePadding = 8.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (tabs[page]) {
                AdminTab.PROFILY -> AdminTabPlaceholder("Profily — plný editor (H3)")
                AdminTab.SABLONY -> AdminTabPlaceholder("Šablony — lock-mapa (H4)")
                AdminTab.ZALOHA -> AdminTabPlaceholder("Záloha — export/import (H5)")
            }
        }
    }
}

/** Plan HELM H2 — dočasný placeholder obsahu tabu (nahradí H3–H5). */
@Composable
private fun AdminTabPlaceholder(label: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label)
        }
    }
}
