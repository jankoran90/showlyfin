package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.theme.FontPrefsViewModel
import com.github.jankoran90.showlyfin.core.theme.ShowlyfinPhoneTheme
import com.github.jankoran90.showlyfin.core.theme.ThemePrefsViewModel
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
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

    val onPush: (SlovoDetailEntry) -> Unit = { detailStack = detailStack + it }
    val onPop: () -> Unit = { detailStack = detailStack.dropLast(1) }
    val onGoToPoslech: () -> Unit = { detailStack = emptyList(); current = SlovoSection.POSLECH }
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

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    val onMenu: () -> Unit = { scope.launch { drawerState.open() } }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SlovoDrawer(current = current) { section ->
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
                sectionStateHolder.SaveableStateProvider(current) {
                    when (current) {
                        SlovoSection.POSLECH -> SlovoSectionScaffold(current.label, onMenu) {
                            ListenScreen(
                                onOpenBook = { onPush(SlovoDetailEntry.AudiobookDetail(it)) },
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
                                onOpenSourceEpisode = { type, ref, srcTitle, epKey ->
                                    onPush(linkedOrPlain(podcastLinkLookup, type, ref, srcTitle, epKey))
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        SlovoSection.OBJEVIT -> SlovoSectionScaffold(current.label, onMenu) {
                            PodcastDiscoveryScreen(modifier = Modifier.fillMaxSize())
                        }
                        SlovoSection.ZDROJE -> SlovoSectionScaffold(current.label, onMenu) {
                            SourceManagerScreen(modifier = Modifier.fillMaxSize())
                        }
                        SlovoSection.NASTAVENI -> SlovoSettingsScreen(onMenu = onMenu)
                    }
                }
                // MiniPlayer ukotvený dole (schová se sám, když se nic nepřehrává).
                MiniPlayer(
                    onExpand = expandMiniPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isListenSection = current == SlovoSection.POSLECH,
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
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SlovoSectionBar(title = title, onMenu = onMenu)
        Box(Modifier.fillMaxSize()) { content() }
    }
}
