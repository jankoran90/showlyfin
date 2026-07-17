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
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.LocalCsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.LocalCzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.LocalDirectorProvider
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
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
    // M2.3: lehký back-stack detailů (klik na řádek/CollectionPart → push; back → pop). Prázdný = shell sekcí.
    var detailStack by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Obohacení karet/řádků (ČSFD %, český popis, režisér) — líně z TMDB/ČSFD + cache. Filmy má vlastní
    // shell (ne ShowlyfinPhoneApp), proto providery zapojujeme tady, jinak by režie/ČSFD/popis byly null.
    val cardCsfd: CardCsfdViewModel = hiltViewModel()

    CompositionLocalProvider(
        LocalCsfdRatingProvider provides cardCsfd,
        LocalCzechOverviewProvider provides cardCsfd,
        LocalDirectorProvider provides cardCsfd,
    ) {
        val detailItem = detailStack.lastOrNull()
        if (detailItem != null) {
            // M2.3: karta detailu = sdílený DetailScreen (telefonní větev). Přehrávání/cast = pozdější milník
            // (callbacky zatím null). Klik na film v řadách detailu (CollectionPart s tmdbId) → další detail.
            BackHandler { detailStack = detailStack.dropLast(1) }
            DetailScreen(
                item = detailItem,
                onBack = { detailStack = detailStack.dropLast(1) },
                onCollectionPartClick = { part ->
                    part.tmdbId?.let { tmdb ->
                        detailStack = detailStack + MediaItem(
                            traktId = 0L, tmdbId = tmdb, imdbId = null, title = part.title,
                            year = part.year?.toIntOrNull(), overview = null, rating = null,
                            genres = null, type = MediaType.MOVIE,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Zavřený → back gesto zavře menu místo odchodu z appky.
            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
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
                            // M2.2 domov = řady (reuse TvHomeViewModel); M2.3 klik → detail (push na stack).
                            FilmySection.HOME -> FilmyHomeScreen(
                                onOpenDetail = { detailStack = detailStack + it },
                                onOpenJellyfinDetail = {}, // JF-only detail = pozdější milník (jiná obrazovka)
                            )
                            // M2.4: Filmotéka = mřížka plakátů se sekcemi (reuse TvFilmotekaViewModel).
                            FilmySection.FILMOTEKA -> FilmyFilmotekaScreen(
                                onOpenDetail = { detailStack = detailStack + it },
                            )
                            // M2.3b: Nastavení = zatím uploader login (české ČSFD popisky). Plné Nastavení = M2.5.
                            FilmySection.SETTINGS -> FilmySettingsScreen()
                            else -> FilmySectionPlaceholder(current)
                        }
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
