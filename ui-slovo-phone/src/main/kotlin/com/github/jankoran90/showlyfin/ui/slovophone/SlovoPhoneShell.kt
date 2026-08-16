package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinPhoneTheme
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.feature.listen.ListenMode
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.MiniPlayer
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoveryScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.SourceManagerScreen
import kotlinx.coroutines.launch

/**
 * Slovo (EXCISE/SHW-103 Krok 2) — kořen telefonní poslechové appky. Zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyPhoneShell]: obaluje sdílený motiv
 * ([ShowlyfinPhoneTheme] z :core:core-theme — activity-scoped VM, sekce Vzhled ho mění živě) a staví
 * shell = postranní menu ([SlovoDrawer]) + horní lišta sekce ([SlovoSectionBar]) + přepínání sekcí +
 * lehký back-stack detailů ([SlovoDetailEntry]) + ukotvený [MiniPlayer]. Žádný film, žádný profil/PIN.
 */
@Composable
fun SlovoPhoneShell() {
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
        SlovoShellContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlovoShellContent() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(SlovoShellPrefs.startSection(ctx)) }
    // Drží scroll/pager stav každé sekce, i když ji detail dočasně vystřídá; ruční přepnutí v draweru resetuje.
    val sectionStateHolder = rememberSaveableStateHolder()
    // Lehký back-stack detailů (push na klik, pop na back). Prázdný = shell sekcí.
    var detailStack by remember { mutableStateOf<List<SlovoDetailEntry>>(emptyList()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Activity-scoped → tytéž instance jako uvnitř ListenScreen/přehrávače (jednotný stav poslechu).
    val listenVm: ListenViewModel = hiltViewModel()
    val podcastLinkLookup: PodcastLinkLookupViewModel = hiltViewModel()
    // User (2026-08-16 17:06, „Dej mi pod Domů sekci Poslech a bude to swipovatelné — první bude
    // Domů obsah a pak se swipne na Poslech") — Poslech už NENÍ samostatná sekce v draweru: žije
    // jako 2. stránka pageru v sekci Domů (strana 0 = rozposlouchané, strana 1 = celý Poslech).
    val domuPager = rememberPagerState(initialPage = 0) { 2 }
    // Profily (2026-08-15): dětský profil → titulek sekce Poslech se změní na "Poslech - děti",
    // v draweru zmizí Objevit/Zdroje (dospělácká vrstva správy zdrojů — user „děti je mít nebudou").
    val activeProfile by listenVm.activeProfile.collectAsStateWithLifecycle()
    val isKidsProfile = activeProfile?.isAdmin == false
    val poslechLabel = if (isKidsProfile) "Poslech - děti" else SlovoSection.POSLECH.label
    // Pojistka: přepnutí NA Děti, když je otevřená Objevit/Zdroje (odjinud, dřív dospělý) → vrať na Poslech.
    // User (2026-08-16 13:49, „nech zobrazit Domů i dětem, ať se můžou rychle vracet") — Domů už
    // NENÍ v tomhle seznamu; [HomeViewModel] správně omezuje obsah dětského profilu na jeho vlastní
    // knihovnu/schválené zdroje (viz `kidsOnlyLibraryIds`), takže dítě tam vidí jen SVÉ rozposlouchané.
    LaunchedEffect(isKidsProfile) {
        if (isKidsProfile && current in setOf(SlovoSection.OBJEVIT, SlovoSection.ZDROJE)) {
            current = SlovoSection.DOMU
        }
    }
    // Pojistka pro starší uložený stav / hluboké odkazy: POSLECH jako sekce už neexistuje →
    // přesměruj na Domů + 2. stránku pageru (ať nic nepadne, když se někde hodnota udržela).
    LaunchedEffect(current) {
        if (current == SlovoSection.POSLECH) {
            current = SlovoSection.DOMU
            domuPager.scrollToPage(1)
        }
    }
    // PROFIL (2026-08-16, user: „přepnutí profilu obrazovku nepřekreslí live, musí shodit a nahodit
    // app") — Slovo tenhle signál na rozdíl od Filmy (FilmyPhoneShell) nikdy neposlouchalo. Dokud byl
    // jen jeden dospělý profil se sdíleným ABS přihlášením, přepnutí na Děti/zpět měnilo jen whitelist
    // (reaktivní cesty to stihly); s Honza/Nel (KAŽDÝ jiný ABS účet) je re-create Activity nutný,
    // jinak zůstanou ViewModely viset na obsahu předchozího profilu. Vzor 1:1 z FilmyPhoneShell.
    val profileSwitch by com.github.jankoran90.showlyfin.core.domain.profile.ProfileSwitchSignal.switches
        .collectAsStateWithLifecycle()
    var lastProfileSwitch by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(profileSwitch) {
        if (profileSwitch > 0 && profileSwitch != lastProfileSwitch) {
            lastProfileSwitch = profileSwitch
            (ctx as? android.app.Activity)?.recreate()
        }
    }

    val onPush: (SlovoDetailEntry) -> Unit = { detailStack = detailStack + it }
    val onPop: () -> Unit = { detailStack = detailStack.dropLast(1) }
    val onGoToPoslech: () -> Unit = {
        detailStack = emptyList()
        current = SlovoSection.DOMU
        scope.launch { domuPager.scrollToPage(1) }
    }
    val expandMiniPlayer: () -> Unit = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) }

    val detailEntry = detailStack.lastOrNull()
    if (detailEntry != null) {
        BackHandler(onBack = onPop)
        Box(Modifier.fillMaxSize()) {
            SlovoDetail(
                entry = detailEntry,
                onPush = onPush,
                onPop = onPop,
                onGoToPoslech = onGoToPoslech,
                listenVm = listenVm,
                podcastLinkLookup = podcastLinkLookup,
            )
            // MiniPlayer nad ne-přehrávačovými detaily (schová se sám, když nic nehraje).
            if (!detailEntry.isFullscreenPlayer()) {
                MiniPlayer(
                    onExpand = expandMiniPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isListenSection = false,
                )
            }
        }
        return
    }

    // Back na 2. straně pageru Domů (Poslech) = vrať se na 1. stranu, dokud není zavřený drawer
    // (drawer handler registrovaný níž má při otevřeném menu přednost).
    BackHandler(enabled = current == SlovoSection.DOMU && domuPager.currentPage == 1) {
        scope.launch { domuPager.animateScrollToPage(0) }
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    val onMenu: () -> Unit = { scope.launch { drawerState.open() } }
    ModalNavigationDrawer(
        drawerState = drawerState,
        // User (2026-08-16 14:26, „zruš swipe sidebar, bude jen vlevo nahoře přístup v ikoně") —
        // edge-swipe gesto vypnuto (kolidovalo by s horizontal scroll uvnitř podsekcí), menu jen ☰.
        gesturesEnabled = false,
        drawerContent = {
            SlovoDrawer(current = current, isAdmin = !isKidsProfile, activeProfileName = activeProfile?.name) { section ->
                sectionStateHolder.removeState(section)
                current = section
                scope.launch { drawerState.close() }
            }
        },
    ) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Sdíleno mezi Domů a Poslech — tap na knihu/epizodu vede na stejné detaily odkudkoli.
                val onOpenBook: (String) -> Unit = { onPush(SlovoDetailEntry.AudiobookDetail(it)) }
                val onOpenSourceEpisode: (String, String, String, String) -> Unit = { type, ref, srcTitle, epKey ->
                    onPush(linkedOrPlain(podcastLinkLookup, type, ref, srcTitle, epKey))
                }
                sectionStateHolder.SaveableStateProvider(current) {
                    when (current) {
                        // POSLECH se vykreslí stejně (redirect výš ho hned přepne na DOMU+stranu 1,
                        // tady jen ať when zůstane exhaustivní a nic neblikne).
                        SlovoSection.DOMU, SlovoSection.POSLECH -> SlovoSectionScaffold(
                            // Titulek lišty žije s pagerem: strana 0 = Domů, strana 1 = Poslech.
                            if (domuPager.currentPage == 0) SlovoSection.DOMU.label else poslechLabel,
                            onMenu,
                            trailing = if (domuPager.currentPage == 0 || isKidsProfile) null else {
                                {
                                    // User (2026-08-16 13:43, „šoupni tlačítka Podcasty/Audioknihy nahoru
                                    // vedle nadpisu Poslech") — přepínač žije v horní liště (jen na straně
                                    // Poslech; dětský profil ho nevidí, má sloučený obsah).
                                    val lState by listenVm.uiState.collectAsStateWithLifecycle()
                                    val modes = if (lState.booksFirst) listOf(ListenMode.BOOKS, ListenMode.PODCASTS)
                                    else listOf(ListenMode.PODCASTS, ListenMode.BOOKS)
                                    SingleChoiceSegmentedButtonRow {
                                        modes.forEachIndexed { i, m ->
                                            SegmentedButton(
                                                selected = lState.mode == m,
                                                onClick = { listenVm.setMode(m) },
                                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                                            ) { Text(if (m == ListenMode.BOOKS) "Audioknihy" else "Podcasty") }
                                        }
                                    }
                                }
                            },
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = domuPager,
                                    modifier = Modifier.weight(1f),
                                ) { page ->
                                    when (page) {
                                        0 -> HomeScreen(
                                            onOpenBook = onOpenBook,
                                            onOpenSourceEpisode = onOpenSourceEpisode,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        else -> ListenScreen(
                                            onOpenBook = onOpenBook,
                                            onEditBook = { id, title, author ->
                                                onPush(SlovoDetailEntry.AudiobookEdit(id, title, author))
                                            },
                                            onOpenPodcast = { onPush(SlovoDetailEntry.PodcastDetail(it)) },
                                            onPlayEpisode = { itemId, episodeId ->
                                                onPush(SlovoDetailEntry.AudiobookPlayer(itemId, fromStart = false, episodeId = episodeId))
                                            },
                                            onOpenSource = { src ->
                                                onPush(
                                                    when (src.type) {
                                                        "youtube" -> SlovoDetailEntry.YoutubeChannel(src.ref, src.title)
                                                        "ctv" -> SlovoDetailEntry.CtvProgram(src.ref, src.title)
                                                        else -> SlovoDetailEntry.RssPodcast(src.ref, src.title)
                                                    }
                                                )
                                            },
                                            onOpenMerged = { gid, gTitle -> onPush(SlovoDetailEntry.MergedPodcast(gid, gTitle)) },
                                            onOpenSourceEpisode = onOpenSourceEpisode,
                                            // SLOVO-KIDS-EPISODE / WATCHDOG — dítě otevře jen schválenou sérii, routing podle typu.
                                            onOpenSourceSeries = { src, slug, seriesTitle ->
                                                onPush(
                                                    when (src.type) {
                                                        "youtube" -> SlovoDetailEntry.YoutubeChannel(src.ref, seriesTitle, seriesFilter = slug)
                                                        "ctv" -> SlovoDetailEntry.CtvProgram(src.ref, seriesTitle, seriesFilter = slug)
                                                        else -> SlovoDetailEntry.RssPodcast(src.ref, seriesTitle, seriesFilter = slug)
                                                    },
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                // Drobný indikátor dvou stran (signalizuje swipovatelnost).
                                DomuPagerIndicator(domuPager.currentPage)
                            }
                        }
                        SlovoSection.OBJEVIT -> SlovoSectionScaffold(current.label, onMenu) {
                            PodcastDiscoveryScreen(modifier = Modifier.fillMaxSize())
                        }
                        SlovoSection.ZDROJE -> SlovoSectionScaffold(current.label, onMenu) {
                            SourceManagerScreen(
                                modifier = Modifier.fillMaxSize(),
                                onUploadAudiobook = { onPush(SlovoDetailEntry.UploadAudiobook) },
                            )
                        }
                        SlovoSection.NASTAVENI -> SlovoSettingsScreen(onMenu = onMenu)
                        SlovoSection.PROFIL -> SlovoProfileScreen(onMenu = onMenu)
                    }
                }
                // MiniPlayer ukotvený dole (schová se sám, když se nic nepřehrává).
                MiniPlayer(
                    onExpand = expandMiniPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isListenSection = current == SlovoSection.DOMU,
                )
            }
        }
    }
}

/** Obal sekce bez vlastní ☰ lišty (poslechové obrazovky ji nemají): horní pruh + obsah pod ním. */
@Composable
private fun SlovoSectionScaffold(
    title: String,
    onMenu: () -> Unit,
    trailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SlovoSectionBar(title = title, onMenu = onMenu, trailing = trailing)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * Dvě tečky pod pagerem sekce Domů (strana 0 = rozposlouchané, strana 1 = Poslech) — jemný
 * signál, že jde swipovat. Z motivu (primary/outlineVariant), žádné hardcoded barvy.
 */
@Composable
private fun DomuPagerIndicator(page: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(2) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
    }
}
