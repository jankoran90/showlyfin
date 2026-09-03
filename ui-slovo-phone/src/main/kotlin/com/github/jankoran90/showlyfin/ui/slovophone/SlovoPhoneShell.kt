package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinPhoneTheme
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
import com.github.jankoran90.showlyfin.feature.listen.ui.BooksContent
import com.github.jankoran90.showlyfin.feature.listen.ui.FollowingContent
import com.github.jankoran90.showlyfin.feature.listen.ui.HomeScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.MiniPlayer
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoveryScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastSearchScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.SourceManagerScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.TimelinePage
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
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
    // FOCUS (2026-09-03) — FAB doplněk: hledání napříč VŠEMI zdroji (Timeline/Sledované).
    var showGlobalSearch by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Activity-scoped → tytéž instance jako uvnitř ListenScreen/přehrávače (jednotný stav poslechu).
    val listenVm: ListenViewModel = hiltViewModel()
    val podcastLinkLookup: PodcastLinkLookupViewModel = hiltViewModel()
    // Profily (2026-08-15): dětský profil → titulek sekce Poslech se změní na "Poslech - děti",
    // v draweru zmizí Objevit/Zdroje (dospělácká vrstva správy zdrojů — user „děti je mít nebudou").
    val activeProfile by listenVm.activeProfile.collectAsStateWithLifecycle()
    val isKidsProfile = activeProfile?.isAdmin == false
    // User (2026-08-15) dětský profil = KidsListenContent uvnitř ListenScreen (sloučený obsah, žádný
    // rozpad na Timeline/Sledované/Audioknihy) — nezměněno, jen dospělácká cesta se dnes (2026-08-16
    // 18:23, „chci mít Poslech čistý jako Domů") rozpadá na samostatné swipe strany.
    val poslechLabel = if (isKidsProfile) "Poslech - děti" else SlovoSection.POSLECH.label
    // User (2026-08-16 17:06, „Dej mi pod Domů sekci Poslech a bude to swipovatelné") + (18:27, „5
    // sekcí bude swipovatelných — Domů, Timeline, Sledované, Audioknihy; Objevit jen v sidebaru") —
    // dětský profil má pořád jen 2 strany (Domů + sloučený Poslech), dospělý 4.
    val domuPager = rememberPagerState(initialPage = 0) { if (isKidsProfile) 2 else 4 }
    val domuPageLabels = if (isKidsProfile) listOf(SlovoSection.DOMU.label, poslechLabel)
    else listOf(SlovoSection.DOMU.label, "Timeline", "Sledované", "Audioknihy")
    val listenState by listenVm.uiState.collectAsStateWithLifecycle()
    val podcastDownloads by listenVm.offlinePodcasts.collectAsStateWithLifecycle()
    // Dřív se volalo uvnitř ListenScreen (init) — dospělácká cesta ho teď obchází, zavolej sama, ať
    // se po návratu z Nastavení (pořadí knihoven/podcastů) projeví změna.
    LaunchedEffect(Unit) { listenVm.reloadOrderPrefs() }
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

    // Back na jakékoli straně pageru Domů kromě 1. (Timeline/Sledované/Audioknihy, dřív jen Poslech)
    // = vrať se na 1. stranu (Domů), dokud není zavřený drawer (handler níž má přednost).
    BackHandler(enabled = current == SlovoSection.DOMU && domuPager.currentPage > 0) {
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
                            onMenu = onMenu,
                            // User (2026-08-16 17:38, „ať se ta nabídka, co není aktivní, taky
                            // objeví nahoře jako karta a při swipnutí se rozsvítí/zhasne") — všechny
                            // strany viditelné v liště zároveň, aktivní svítí, neaktivní ztlumená.
                            titleContent = {
                                DomuPagerTabs(
                                    pagerState = domuPager,
                                    labels = domuPageLabels,
                                    onSelect = { page -> scope.launch { domuPager.animateScrollToPage(page) } },
                                )
                            },
                            // User (2026-08-16 18:23, „ať zmizí úplně všechna tlačítka jako na Domů
                            // obrazovce — čisté") — přepínač Podcasty/Audioknihy zmizel úplně, Audioknihy
                            // je teď vlastní swipe strana.
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = domuPager,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    if (isKidsProfile) {
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
                                                onOpenSource = { src -> onPush(sourceDetail(src)) },
                                                onOpenMerged = { gid, gTitle -> onPush(SlovoDetailEntry.MergedPodcast(gid, gTitle)) },
                                                onOpenSourceEpisode = onOpenSourceEpisode,
                                                // SLOVO-KIDS-EPISODE / WATCHDOG — dítě otevře jen schválenou sérii, routing podle typu.
                                                onOpenSourceSeries = { src, slug, seriesTitle ->
                                                    onPush(sourceSeriesDetail(src, slug, seriesTitle))
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    } else if (page == 0) {
                                        HomeScreen(
                                            onOpenBook = onOpenBook,
                                            onOpenSourceEpisode = onOpenSourceEpisode,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else if (!listenState.isConfigured) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                "Poslech zatím není nastaven.\nPřihlas se k Audiobookshelf serveru v Nastavení → Poslech (Audiobookshelf).",
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(24.dp),
                                            )
                                        }
                                    } else {
                                        when (page) {
                                            1 -> TimelinePage(
                                                state = listenState,
                                                viewModel = listenVm,
                                                podcastDownloads = podcastDownloads,
                                                onOpenSourceEpisode = onOpenSourceEpisode,
                                                onGoToObjevit = { current = SlovoSection.OBJEVIT },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            2 -> FollowingContent(
                                                state = listenState,
                                                viewModel = listenVm,
                                                onOpenPodcast = { onPush(SlovoDetailEntry.PodcastDetail(it)) },
                                                onOpenSource = { src -> onPush(sourceDetail(src)) },
                                                onOpenMerged = { gid, gTitle -> onPush(SlovoDetailEntry.MergedPodcast(gid, gTitle)) },
                                                downloadCount = podcastDownloads.size,
                                                sourceType = "all",
                                            )
                                            else -> BooksContent(
                                                state = listenState,
                                                viewModel = listenVm,
                                                onOpenBook = onOpenBook,
                                                onEditBook = { id, title, author ->
                                                    onPush(SlovoDetailEntry.AudiobookEdit(id, title, author))
                                                },
                                            )
                                        }
                                    }
                                }
                                // FOCUS (2026-09-03, doplněk k per-zdroj hledání): FAB napříč VŠEMI
                                // zdroji najednou, jen na Timeline/Sledované (user primárně chce
                                // per-zdroj lupu v YoutubeChannel/Rss/CtvProgram, tohle je záloha
                                // „nevím, ve kterém zdroji epizoda je").
                                if (!isKidsProfile && !showGlobalSearch &&
                                    (domuPager.currentPage == 1 || domuPager.currentPage == 2)
                                ) {
                                    FloatingActionButton(
                                        onClick = { showGlobalSearch = true },
                                        // BUG (2026-09-03, user screenshot): MiniPlayer se kreslí AŽ PO
                                        // téhle sekci (outer Box, BottomCenter, skoro celá šířka) → bez
                                        // odsazení FAB při hraní úplně zmizel pod ním. 96.dp = stejná
                                        // rezerva, jakou LazyColumn obrazovky (YoutubeChannelScreen aj.)
                                        // dávají do `contentPadding` pro mini-player, vždy (ne jen když hraje).
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp),
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Hledat ve všech podcastech")
                                    }
                                }
                                if (showGlobalSearch) {
                                    PodcastSearchScreen(
                                        onBack = { showGlobalSearch = false },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SlovoSectionBar(title = title, onMenu = onMenu, trailing = trailing)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** Varianta s vlastním obsahem titulku místo prostého textu (viz [DomuPagerTabs]). */
@Composable
private fun SlovoSectionScaffold(
    onMenu: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleContent: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SlovoSectionBar(onMenu = onMenu, trailing = trailing, content = titleContent)
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/**
 * Všechny strany pageru sekce Domů viditelné v liště zároveň — aktivní strana svítí plnou barvou,
 * neaktivní jsou ztlumené; přechod sleduje živě samotné swipnutí prstem (ne jen doskok stránky).
 * Tap na label = přeskok na tu stránku. User (2026-08-16 17:38): dřív byl titulek jen text, co se
 * přehodil při doskoku, a tečkový indikátor dole — tohle je nahrazuje. User (2026-08-16 18:27, „5
 * sekcí bude swipovatelných") — rozšířeno z pevných 2 (Domů/Poslech) na obecný seznam [labels].
 */
@Composable
private fun DomuPagerTabs(
    pagerState: PagerState,
    labels: List<String>,
    onSelect: (Int) -> Unit,
) {
    val maxIndex = (labels.size - 1).coerceAtLeast(0)
    val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, maxIndex.toFloat())
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        labels.forEachIndexed { index, label ->
            val distance = (position - index).absoluteValue.coerceIn(0f, 1f)
            val alpha = lerp(0.4f, 1f, 1f - distance)
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (index == pagerState.currentPage) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onSelect(index) },
            )
        }
    }
}

/** Sdíleno mezi kartou zdroje ve Sledovaných a Timeline řádkem (viz [SlovoDetailHost.linkedOrPlain]). */
private fun sourceDetail(src: PodcastSource): SlovoDetailEntry = when (src.type) {
    "youtube" -> SlovoDetailEntry.YoutubeChannel(src.ref, src.title)
    "ctv" -> SlovoDetailEntry.CtvProgram(src.ref, src.title)
    else -> SlovoDetailEntry.RssPodcast(src.ref, src.title)
}

/** SLOVO-KIDS-EPISODE / WATCHDOG — dítě otevře jen schválenou sérii, routing podle typu zdroje. */
private fun sourceSeriesDetail(src: PodcastSource, slug: String, seriesTitle: String): SlovoDetailEntry =
    when (src.type) {
        "youtube" -> SlovoDetailEntry.YoutubeChannel(src.ref, seriesTitle, seriesFilter = slug)
        "ctv" -> SlovoDetailEntry.CtvProgram(src.ref, seriesTitle, seriesFilter = slug)
        else -> SlovoDetailEntry.RssPodcast(src.ref, seriesTitle, seriesFilter = slug)
    }
