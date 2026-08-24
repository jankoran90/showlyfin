package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.core.ui.LocalCsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.LocalCzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.LocalDirectorProvider
import com.github.jankoran90.showlyfin.core.ui.LocalSourceAvailabilityProvider
import com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider
import com.github.jankoran90.showlyfin.data.uploader.ctvSidpOrNull
import com.github.jankoran90.showlyfin.data.uploader.model.CtvTitle
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel
import com.github.jankoran90.showlyfin.feature.detail.rating.RatingDialogHost
import com.github.jankoran90.showlyfin.feature.detail.rating.RatingViewModel
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.ui.phone.CardCsfdViewModel
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinPhoneTheme
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
    // VLTAVA (SHW-110) F6: titul z ČT iVysílání — bez TMDB identity, vlastní karta ([FilmyCtvScreen]).
    data class Ctv(val title: CtvTitle) : FilmyDetailEntry
}

/** M2.6: stav přehrávače nad detailem — externí stream (Real-Debrid/Stremio) NEBO Jellyfin item. */
private data class FilmyPlayer(
    val itemId: String? = null,
    val externalUrl: String? = null,
    val title: String = "",
    val subtitleQuery: SubtitleQuery? = null,
    val posterUrl: String? = null,
    // VLTAVA F6b — vlastní klíč pozice (ČT díl `ctv:<idec>`); jinak si ho přehrávač odvodí z titulků.
    val resumeKey: String? = null,
)

@Composable
private fun FilmyShellContent() {
    // Výchozí sekce dle prefu „Filmotéka jako výchozí" (user 2026-07-18) — jinak Domů. Aplikuje se při otevření appky.
    val startCtx = LocalContext.current
    var current by remember { mutableStateOf(FilmyShellPrefs.startSection(startCtx)) }
    // PŮDORYS (SHW-112): když si uživatel schová sekci, na které zrovna stojí (typicky výchozí Domů),
    // přepni na první viditelnou — jinak by koukal na obrazovku, kterou už v menu nemá. Nastavení
    // a Profil jsou připnuté, ty tomuhle nikdy nepodléhají.
    val layoutVm: FilmyHomeLayoutViewModel = hiltViewModel()
    // Sdílená instance se sekcí „Podle filmu" (obojí bere hiltViewModel() z téhož ViewModelStore aktivity)
    // → akce „Doporuč podobné" z karty filmu do ní nastaví referenci a sekce ji rovnou ukáže.
    val referenceVm: com.github.jankoran90.showlyfin.feature.discover.foryou.ReferenceRecsViewModel = hiltViewModel()
    val storedMenu by layoutVm.menu.collectAsStateWithLifecycle()
    // Klíč je JEN storedMenu: hlídáme ZMĚNU nastavení menu, ne každý přechod mezi sekcemi. Jinak by
    // to zablokovalo cílené otevření skryté sekce zevnitř appky (např. „Doporuč podobné" → Podle filmu).
    LaunchedEffect(storedMenu) {
        if (current == FilmySection.SETTINGS || current == FilmySection.PROFILE) return@LaunchedEffect
        val visible = FilmyMenuConfig.visibleSections(storedMenu, FilmyShellPrefs.defaultFilmoteka(startCtx))
        if (current !in visible) visible.firstOrNull()?.let { current = it }
    }
    // CELLULOID: drží rememberSaveable stav KAŽDÉ sekce (scroll pozice mřížky/seznamu, pager tab) i když
    // ji detail/přehrávač dočasně vystřídá v kompozici → po BACK zůstane scroll na místě. Ruční přepnutí
    // sekce v draweru stav té sekce zahodí (removeState) = úmyslný reset na vrch; reload appky = nový holder.
    val sectionStateHolder = rememberSaveableStateHolder()
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
    // CINEMATHEQUE: odznak „má uložený zdroj" na kartách/řádcích — provider napojený na WorkingSourceStore.
    val sourceAvailVm: FilmySourceAvailabilityViewModel = hiltViewModel()
    // CASCADE: stejná (Activity-scoped) instance DetailViewModelu jako uvnitř DetailScreen — drží candidate
    // list `streams`; po chybě přehrávání (onPlaybackFailed) spustí dalšího kandidáta místo chyby.
    val detailVm: DetailViewModel = hiltViewModel()
    val context = LocalContext.current
    // Stremio fallback (DMCA blok / RD selhal / nekompatibilní formát) → otevři titul ve Stremiu (vzor
    // showlyfin `onStremioItem`). Bez appky Stremio → web download. Bez toho byla ta tlačítka mrtvá.
    val onStremioItem: (MediaItem) -> Unit = { item ->
        val mediaType = if (item.type == MediaType.MOVIE) "movie" else "series"
        val targetId = item.imdbId ?: item.tmdbId?.toString()
        if (targetId != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/$mediaType/$targetId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Throwable) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.stremio.com/downloads")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

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

    // PROFIL (user 2026-07-28 „při přepnutí chci, aby se appka opravdu znovunačetla"): přepnutí profilu
    // mění úplně všechno (Jellyfin creds, Trakt, Oblíbené, uložené zdroje, věkový strop). Reaktivní cesty
    // to většinou stihnou, ale obrazovka s vlastním `remember`/už načteným VM by zůstala na starém obsahu.
    // Re-create Activity = všechno vzniká znovu nad daty NOVÉHO profilu. `rememberSaveable` guard přežije
    // re-create → nezacyklí se.
    val profileSwitch by com.github.jankoran90.showlyfin.core.domain.profile.ProfileSwitchSignal.switches
        .collectAsStateWithLifecycle()
    var lastProfileSwitch by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(profileSwitch) {
        if (profileSwitch > 0 && profileSwitch != lastProfileSwitch) {
            lastProfileSwitch = profileSwitch
            (context as? android.app.Activity)?.recreate()
        }
    }

    // Parity: notifikace kurátora „nová doporučení" (FilmyMainActivity → EXTRA_OPEN_FORYOU) → přepni na
    // sekci „Pro tebe" a zavři případný otevřený detail/přehrávač, ať je sekce reálně vidět.
    val openForYou by ListenNavSignal.openForYou.collectAsStateWithLifecycle()
    // rememberSaveable guard: každou hodnotu signálu zpracuj JEN jednou. Přežije re-create Activity (změna
    // jazyka/písma/density mimo configChanges) → nepřepne násilně zpět na „Pro tebe" ani nezavře přehrávač.
    var lastForYou by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(openForYou) {
        if (openForYou > 0 && openForYou != lastForYou) {
            lastForYou = openForYou
            current = FilmySection.FOR_YOU
            detailStack = emptyList()
            player = null
        }
    }

    // SEZONA-DÁVKA (user 2026-08-21: „stav lišty proklik sem") — proklik notifikace stahování
    // (FilmyMainActivity → EXTRA_OPEN_DOWNLOADS) → přepni na sekci „Stažené", stejný vzor jako výš.
    val openDownloads by ListenNavSignal.openDownloads.collectAsStateWithLifecycle()
    var lastOpenDownloads by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(openDownloads) {
        if (openDownloads > 0 && openDownloads != lastOpenDownloads) {
            lastOpenDownloads = openDownloads
            current = FilmySection.DOWNLOADS
            detailStack = emptyList()
            player = null
        }
    }

    CompositionLocalProvider(
        LocalCsfdRatingProvider provides cardCsfd,
        LocalCzechOverviewProvider provides cardCsfd,
        LocalDirectorProvider provides cardCsfd,
        LocalUserRatingProvider provides ratingVm,
        LocalSourceAvailabilityProvider provides sourceAvailVm,
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
                resumeKey = activePlayer.resumeKey,
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
                // Parity: název sekce u šipky Zpět (dřív prázdné).
                sectionTitle = current.label,
                onCollectionPartClick = pushCollectionPart,
                onPlayJellyfin = { jfId -> playJellyfin(jfId, item.title) },
                onPlayStreamUrl = { url, title, subQuery -> playStream(url, title, subQuery, item.posterUrl()) },
                // Parity: Stremio fallback (DMCA/RD selhal/nekompat) — dřív mrtvé tlačítko.
                onStremio = onStremioItem,
                // M2.6 LAPIDARY: one-click z řady „Uloženo k přehrání" → přehraj zapamatovaný zdroj rovnou.
                autoplayRemembered = detailEntry.autoplay,
                // ORCHARD (user 07-19) — Filmy: cast bez tlačítka, „Přehrát na TV" v ⋮ menu.
                castInOverflow = true,
                // user 2026-08-01 („doporučení by mohlo být v menu karty filmu"): vezmi tenhle titul
                // jako referenci, zavři detail a otevři sekci „Podle filmu" s běžícím výpočtem.
                onSimilar = { picked ->
                    referenceVm.setReference(picked)
                    detailStack = emptyList()
                    current = FilmySection.REFERENCE
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (detailEntry is FilmyDetailEntry.Ctv) {
            // VLTAVA F6: karta ČT titulu (film = Přehrát, pořad = díly). Odkaz resolvuje zařízení.
            BackHandler(onBack = popDetail)
            FilmyCtvScreen(
                title = detailEntry.title,
                onBack = popDetail,
                onPlay = { url, label, poster, resumeKey ->
                    player = FilmyPlayer(
                        externalUrl = url, title = label, posterUrl = poster, resumeKey = resumeKey,
                    )
                },
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
                        // Ruční výběr sekce v draweru = úmyslný reset scrollu té sekce na vrch (i re-klik na aktuální).
                        sectionStateHolder.removeState(section)
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
                        // VLTAVA F6b: titul z ČT (syntetická identita `ctvid:<sidp>`) NEMÁ TMDB kartu —
                        // otevři jeho vlastní ČT kartu. Jediné místo, kde se to rozhoduje, ať to platí
                        // pro Filmotéku, domov i řadu „Uloženo k přehrání".
                        val entryFor: (MediaItem, Boolean) -> FilmyDetailEntry = { item, autoplay ->
                            val sidp = ctvSidpOrNull(item.imdbId)
                            if (sidp != null) {
                                FilmyDetailEntry.Ctv(
                                    CtvTitle(
                                        sidp = sidp,
                                        type = if (item.type == MediaType.SHOW) "series" else "movie",
                                        title = item.title,
                                        thumbnail = item.posterUrl(),
                                    ),
                                )
                            } else {
                                FilmyDetailEntry.Media(item, autoplay = autoplay)
                            }
                        }
                        val openDetail: (MediaItem) -> Unit = { detailStack = detailStack + entryFor(it, false) }
                        // M2.6 LAPIDARY: klik na „Uloženo k přehrání" → detail v režimu okamžitého přehrání.
                        val openDetailPlay: (MediaItem) -> Unit = { detailStack = detailStack + entryFor(it, true) }
                        // M2.6: JF-only položka → dohledá se přes getItemMeta a otevře sdílený detail.
                        val openJfDetail: (String) -> Unit = { jf -> detailStack = detailStack + FilmyDetailEntry.Jellyfin(jf) }
                        sectionStateHolder.SaveableStateProvider(current) {
                        when (current) {
                            // M2.2 domov = řady (reuse TvHomeViewModel); M2.3 klik → detail (push na stack).
                            FilmySection.HOME -> FilmyHomeScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenJellyfinDetail = openJfDetail, // M2.6: JF-only detail zprovozněn
                                onOpenDetailPlay = openDetailPlay,   // M2.6: one-click zapamatovaný zdroj
                            )
                            // CINEMATHEQUE: Chci vidět = celý Trakt watchlist (parita s TV, reuse TvWantToSeeViewModel).
                            FilmySection.WANT_TO_SEE -> FilmyWantToSeeScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // user 2026-07-31: historie sledování na telefonu, ať jde hned ohodnotit (dlouhý stisk).
                            FilmySection.HISTORY -> FilmyHistoryScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // user 2026-07-31: doporučení vázaná na ručně vybrané filmy (reference).
                            FilmySection.REFERENCE -> FilmyReferenceScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.5: Pro tebe = kurátor (reuse ForYouViewModel).
                            FilmySection.FOR_YOU -> FilmyForYouScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.4: Filmotéka = mřížka plakátů se sekcemi (reuse TvFilmotekaViewModel).
                            FilmySection.FILMOTEKA -> FilmyFilmotekaScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenJellyfinDetail = openJfDetail,
                            )
                            // M2.5: Vzácné klenoty = LAPIDARY řady (reuse TvLapidaryViewModel).
                            FilmySection.GEMS -> FilmyGemsScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // M2.5: Knihovna = JF řady (reuse LibraryRowsViewModel).
                            FilmySection.LIBRARY -> FilmyLibraryScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenJellyfinDetail = openJfDetail, // M2.6: JF-only detail zprovozněn
                            )
                            // M2.5: Hledat = TMDB + VLTAVA F6 rozsah „Česká televize" (ČT iVysílání).
                            FilmySection.SEARCH -> FilmySearchScreen(
                                onMenu = onMenu,
                                onOpenDetail = openDetail,
                                onOpenCtv = { t -> detailStack = detailStack + FilmyDetailEntry.Ctv(t) },
                            )
                            // SEZONA-DÁVKA (user 2026-08-21): stažené filmy/seriály (karty, seskupené po dílech).
                            FilmySection.DOWNLOADS -> FilmyDownloadsScreen(onMenu = onMenu, onOpenDetail = openDetail)
                            // PROVOZ (SHW-114): co hraje kde, výkon přenosu, stav zdrojů + akce nad nimi.
                            FilmySection.OPS -> FilmyOpsScreen(onMenu = onMenu)
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
        // BESPOKE F3: hvězdičkový dialog nad obsahem (spouštěč z detailu / MENU karty) — parita s TV.
        RatingDialogHost(ratingVm)
    }
}

