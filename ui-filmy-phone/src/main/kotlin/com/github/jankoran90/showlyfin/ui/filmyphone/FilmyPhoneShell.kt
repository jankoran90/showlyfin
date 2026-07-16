package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.ui.LocalCsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.LocalCzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.LocalDirectorProvider
import com.github.jankoran90.showlyfin.ui.phone.CardCsfdViewModel
import com.github.jankoran90.showlyfin.ui.phone.FontPrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.ui.phone.theme.ShowlyfinPhoneTheme
import kotlinx.coroutines.launch

/**
 * CELLULOID (SHW-98) Fáze 2 M2.1 — kořen telefonní vrstvy appky „Filmy".
 *
 * Nahrazuje dřívější placeholder `FilmyPhoneApp`. Obaluje sdílený motiv showlyfinu
 * ([ShowlyfinPhoneTheme] — activity-scoped VM sdílené s TV/Nastavením) a staví shell ve stylu
 * audiomanu: postranní menu ([FilmyDrawer]) + horní lišta ([FilmyTopBar]) + přepínání sekcí.
 *
 * M2.1 = navigační kostra; sekce jsou zatím placeholdery. Reálný obsah (řady, karta detailu,
 * Filmotéka grid) reuse ze sdílených feature-* přijde v M2.2–M2.5. TV shell se NESAHÁ (varianta A).
 */
@Composable
fun FilmyPhoneShell() {
    // Motiv sdílený se zbytkem showlyfinu (AMOLED + amber). Activity-scoped → sekce Vzhled ho mění živě.
    val fontPrefs: FontPrefsViewModel = hiltViewModel()
    val font by fontPrefs.state.collectAsStateWithLifecycle()
    val themePrefs: ThemePrefsViewModel = hiltViewModel()
    val theme by themePrefs.state.collectAsStateWithLifecycle()
    ShowlyfinPhoneTheme(
        themeState = theme,
        serifFont = font.serif,
        headingOnly = font.headingOnly,
        fontScale = font.scale,
    ) {
        FilmyShellContent()
    }
}

@Composable
private fun FilmyShellContent() {
    var current by remember { mutableStateOf(FilmySection.HOME) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Obohacení karet/řádků (ČSFD %, český popis, režisér) — líně z TMDB/ČSFD + cache. Filmy má vlastní
    // shell (ne ShowlyfinPhoneApp), proto providery zapojujeme tady, jinak by režie/ČSFD/popis byly null.
    val cardCsfd: CardCsfdViewModel = hiltViewModel()

    // Zavřený → back gesto zavře menu místo odchodu z appky.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    CompositionLocalProvider(
        LocalCsfdRatingProvider provides cardCsfd,
        LocalCzechOverviewProvider provides cardCsfd,
        LocalDirectorProvider provides cardCsfd,
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FilmyDrawer(current = current) { section ->
                current = section
                scope.launch { drawerState.close() }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                FilmyTopBar(title = current.label) { scope.launch { drawerState.open() } }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (current) {
                    // M2.2: domov = řady ve stylu TV (reuse TvHomeViewModel). Detail = M2.3 (zatím no-op).
                    FilmySection.HOME -> FilmyHomeScreen(
                        onOpenDetail = {},
                        onOpenJellyfinDetail = {},
                    )
                    else -> FilmySectionPlaceholder(current)
                }
            }
        }
    }
    }
}

/**
 * Dočasný obsah sekce (M2.1). Každá sekce dostane vlastní reuse-obrazovku v M2.2+; teď jen srozumitelně
 * ukáže, že navigace funguje a co se sem doplní.
 */
@Composable
private fun FilmySectionPlaceholder(section: FilmySection) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = section.label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Obsah této sekce se dotáhne v dalším milníku.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
