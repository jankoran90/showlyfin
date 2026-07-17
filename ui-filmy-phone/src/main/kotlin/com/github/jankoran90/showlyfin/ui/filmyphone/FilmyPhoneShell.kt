package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
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
 * audiomanu: postranní menu ([FilmyDrawer]) + horní lišta sekce ([FilmySectionBar]) + přepínání sekcí.
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
            // ☰ otevře menu — každá sekce si kreslí vlastní horní pruh (FilmySectionBar) s tímto callbackem,
            // takže ovladače sekce splynou s lištou (žádný samostatný TopAppBar s názvem = min chrome, M2.5).
            val onMenu: () -> Unit = { scope.launch { drawerState.open() } }
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
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        val openDetail: (MediaItem) -> Unit = { detailStack = detailStack + it }
                        when (current) {
                            // M2.2 domov = řady (reuse TvHomeViewModel); M2.3 klik → detail (push na stack).
                            FilmySection.HOME -> FilmyHomeScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenJellyfinDetail = {}, // JF-only detail = pozdější milník (jiná obrazovka)
                            )
                            // M2.5: Pro tebe = kurátor (reuse ForYouViewModel).
                            FilmySection.FOR_YOU -> FilmyForYouScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.4: Filmotéka = mřížka plakátů se sekcemi (reuse TvFilmotekaViewModel).
                            FilmySection.FILMOTEKA -> FilmyFilmotekaScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.5: Vzácné klenoty = LAPIDARY řady (reuse TvLapidaryViewModel).
                            FilmySection.GEMS -> FilmyGemsScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.5: Knihovna = JF řady (reuse LibraryRowsViewModel).
                            FilmySection.LIBRARY -> FilmyLibraryScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenJellyfinDetail = {}, // JF-only detail = pozdější milník
                            )
                            // M2.5: Hledat = TMDB (reuse SearchViewModel).
                            FilmySection.SEARCH -> FilmySearchScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.3b: Nastavení = uploader login (ČSFD) + vypínač živého logu.
                            FilmySection.SETTINGS -> FilmySettingsScreen(onMenu = onMenu)
                            // M2.5: Profil = 2 pevné profily + PIN (reuse SettingsViewModel/ProfileRepository).
                            FilmySection.PROFILE -> FilmyProfileScreen(onMenu = onMenu)
                        }
                    }
                }
            }
        }
    }
}

