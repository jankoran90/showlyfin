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
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.LocalCsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.LocalCzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.LocalDirectorProvider
import com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel
import com.github.jankoran90.showlyfin.feature.detail.rating.RatingDialogHost
import com.github.jankoran90.showlyfin.feature.detail.rating.RatingViewModel
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
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

/** Vstup do lehkého back-stacku detailů: bohatý titul ([Media]) nebo JF-only položka ([Jellyfin]) k dohledání. */
private sealed interface FilmyDetailEntry {
    // autoplay=true (LAPIDARY one-click z řady „Uloženo k přehrání") → po hydrataci přehraj zapamatovaný zdroj.
    data class Media(val item: MediaItem, val autoplay: Boolean = false) : FilmyDetailEntry
    data class Jellyfin(val id: String) : FilmyDetailEntry
}

/** M2.6: stav přehrávače nad detailem — externí stream (Real-Debrid/Stremio) NEBO Jellyfin item. */
private data class FilmyPlayer(
    val itemId: String? = null,
    val externalUrl: String? = null,
    val title: String = "",
    val subtitleQuery: SubtitleQuery? = null,
    val posterUrl: String? = null,
)

@Composable
private fun FilmyShellContent() {
    var current by remember { mutableStateOf(FilmySection.HOME) }
    // M2.3: lehký back-stack detailů (klik na řádek/CollectionPart → push; back → pop). Prázdný = shell sekcí.
    // M2.6: sjednocen na FilmyDetailEntry, aby uměl i JF-only položky (dohledání přes getItemMeta).
    var detailStack by remember { mutableStateOf<List<FilmyDetailEntry>>(emptyList()) }
    // M2.6: přehrávač nad detailem (null = nehraje se). Reuse sdíleného PlaybackScreen (ExoPlayer + FFmpeg).
    var player by remember { mutableStateOf<FilmyPlayer?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Obohacení karet/řádků (ČSFD %, český popis, režisér) — líně z TMDB/ČSFD + cache. Filmy má vlastní
    // shell (ne ShowlyfinPhoneApp), proto providery zapojujeme tady, jinak by režie/ČSFD/popis byly null.
    val cardCsfd: CardCsfdViewModel = hiltViewModel()
    // BESPOKE F3 — vlastní hvězdičkové hodnocení (parita s TV `ShowlyfinTvApp`): sdílený mozek → provider
    // (★ odznak na kartách + spouštěč hodnocení v detailu) + dialog host níže. Bez toho telefon hodnotit neuměl
    // a kurátorův „palec dolů" filtr nedostával telefonem nastavená hodnocení.
    val ratingVm: RatingViewModel = hiltViewModel()
    // CASCADE: stejná (Activity-scoped) instance DetailViewModelu jako uvnitř DetailScreen — drží candidate
    // list `streams`; po chybě přehrávání (onPlaybackFailed) spustí dalšího kandidáta místo chyby.
    val detailVm: DetailViewModel = hiltViewModel()

    // Klik na část kolekce s tmdbId → další (bohatý) detail na stacku.
    val pushCollectionPart: (CollectionPart) -> Unit = { part ->
        part.tmdbId?.let { tmdb ->
            detailStack = detailStack + FilmyDetailEntry.Media(
                MediaItem(
                    traktId = 0L, tmdbId = tmdb, imdbId = null, title = part.title,
                    year = part.year?.toIntOrNull(), overview = null, rating = null,
                    genres = null, type = MediaType.MOVIE,
                )
            )
        }
    }
    // M2.6: resolvnutá URL (Real-Debrid/Stremio) → spusť přehrávač. Poster do notifikace z titulu detailu.
    val playStream: (String, String, SubtitleQuery?, String?) -> Unit = { url, title, subQuery, poster ->
        player = FilmyPlayer(externalUrl = url, title = title, subtitleQuery = subQuery, posterUrl = poster)
    }
    val playJellyfin: (String, String) -> Unit = { jfId, title ->
        player = FilmyPlayer(itemId = jfId, title = title)
    }
    val popDetail: () -> Unit = { detailStack = detailStack.dropLast(1) }

    CompositionLocalProvider(
        LocalCsfdRatingProvider provides cardCsfd,
        LocalCzechOverviewProvider provides cardCsfd,
        LocalDirectorProvider provides cardCsfd,
        LocalUserRatingProvider provides ratingVm,
    ) {
        val activePlayer = player
        val detailEntry = detailStack.lastOrNull()
        if (activePlayer != null) {
            // M2.6: přehrávač je nad vším. Back = zavřít přehrávač (návrat na detail).
            BackHandler { player = null }
            PlaybackScreen(
                itemId = activePlayer.itemId ?: "",
                externalUrl = activePlayer.externalUrl,
                externalTitle = activePlayer.title,
                subtitleQuery = activePlayer.subtitleQuery,
                externalPosterUrl = activePlayer.posterUrl,
                onBack = { player = null },
                onPlaybackFailed = { code ->
                    player = null            // zpět na detail, kde žije candidate list
                    detailVm.onPlaybackFailed(code)
                },
            )
        } else if (detailEntry is FilmyDetailEntry.Media) {
            // M2.3/M2.6: karta detailu = sdílený DetailScreen (telefonní větev) + přehrávání.
            val item = detailEntry.item
            BackHandler(onBack = popDetail)
            DetailScreen(
                item = item,
                onBack = popDetail,
                onCollectionPartClick = pushCollectionPart,
                onPlayJellyfin = { jfId -> playJellyfin(jfId, item.title) },
                onPlayStreamUrl = { url, title, subQuery -> playStream(url, title, subQuery, item.posterUrl()) },
                // M2.6 LAPIDARY: one-click z řady „Uloženo k přehrání" → přehraj zapamatovaný zdroj rovnou.
                autoplayRemembered = detailEntry.autoplay,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (detailEntry is FilmyDetailEntry.Jellyfin) {
            // M2.6: JF-only položka (z Knihovny/domova) — dohledej tmdb/imdb a otevři sdílený detail.
            BackHandler(onBack = popDetail)
            FilmyJellyfinDetail(
                itemId = detailEntry.id,
                onBack = popDetail,
                onCollectionPartClick = pushCollectionPart,
                onOpenJellyfinDetail = { jf -> detailStack = detailStack + FilmyDetailEntry.Jellyfin(jf) },
                onPlayJellyfin = { jfId -> playJellyfin(jfId, "") },
                onPlayStreamUrl = { url, title, subQuery -> playStream(url, title, subQuery, null) },
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
                        val openDetail: (MediaItem) -> Unit = { detailStack = detailStack + FilmyDetailEntry.Media(it) }
                        // M2.6 LAPIDARY: klik na „Uloženo k přehrání" → detail v režimu okamžitého přehrání.
                        val openDetailPlay: (MediaItem) -> Unit = { detailStack = detailStack + FilmyDetailEntry.Media(it, autoplay = true) }
                        // M2.6: JF-only položka → dohledá se přes getItemMeta a otevře sdílený detail.
                        val openJfDetail: (String) -> Unit = { jf -> detailStack = detailStack + FilmyDetailEntry.Jellyfin(jf) }
                        when (current) {
                            // M2.2 domov = řady (reuse TvHomeViewModel); M2.3 klik → detail (push na stack).
                            FilmySection.HOME -> FilmyHomeScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenJellyfinDetail = openJfDetail, // M2.6: JF-only detail zprovozněn
                                onOpenDetailPlay = openDetailPlay,   // M2.6: one-click zapamatovaný zdroj
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
                                onOpenJellyfinDetail = openJfDetail, // M2.6: JF-only detail zprovozněn
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
        // BESPOKE F3: hvězdičkový dialog nad obsahem (spouštěč z detailu / MENU karty) — parita s TV.
        RatingDialogHost(ratingVm)
    }
}

