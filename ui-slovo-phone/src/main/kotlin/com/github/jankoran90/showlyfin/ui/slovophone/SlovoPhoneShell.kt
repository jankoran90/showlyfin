package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
import com.github.jankoran90.showlyfin.feature.listen.ui.MiniPlayer

/**
 * Slovo (EXCISE/SHW-103 Krok 2) — kořen telefonní poslechové appky. Obaluje sdílený motiv
 * ([ShowlyfinPhoneTheme] z :core:core-theme — activity-scoped VM, sekce Vzhled ho mění živě).
 *
 * AMPHORA (SHW-124) F1 / KO1 (user 2026-11-21): místo postranního menu a přejíždění stran Domů je
 * spodní lišta ([SlovoBottomBar]: Domů · Knihovna · Pořady · Hledat · Fronta, u dítěte Domů · Poslech ·
 * Stažené) a avatar vlevo nahoře s nabídkou Víc ([SlovoMoreSheet]). Lišta je vidět i nad detaily, schová
 * se jen v plném přehrávači (audio i video); mini přehrávač sedí nad ní. Zpět: detail → o krok, kořen
 * jiné sekce → Domů, Domů → ukončit appku. Obsah sekcí kreslí [SlovoSectionContent].
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
    var current by rememberSaveable { mutableStateOf(SlovoShellPrefs.startSection(ctx)) }
    // Drží scroll/pager stav každé sekce, i když ji detail nebo jiná sekce dočasně vystřídá.
    val sectionStateHolder = rememberSaveableStateHolder()
    // „Ťuk na otevřené místo = na začátek“: nové číslo = nový klíč stavu sekce (starý se zahodí).
    val resetTicks = remember { mutableStateMapOf<SlovoSection, Int>() }
    // Lehký back-stack detailů (push na klik, pop na back). Prázdný = sekce.
    var detailStack by remember { mutableStateOf<List<SlovoDetailEntry>>(emptyList()) }
    var showMore by remember { mutableStateOf(false) }
    // AMPHORA F7 (DE1): dětský skrytý vchod (podržení rohu 3 s) → PIN → Pro rodiče (dřív rovnou Víc).
    var showParent by remember { mutableStateOf(false) }
    // Activity-scoped → tytéž instance jako uvnitř ListenScreen/přehrávače (jednotný stav poslechu).
    val listenVm: ListenViewModel = hiltViewModel()
    val podcastLinkLookup: PodcastLinkLookupViewModel = hiltViewModel()
    val playerVm: AudiobookPlayerViewModel = hiltViewModel()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
    val activeProfile by listenVm.activeProfile.collectAsStateWithLifecycle()
    val isKidsProfile = activeProfile?.isAdmin == false
    // Dětský telefon (test 2026-11-19): při studeném startu je profil ~0,8 s neznámý (null) a dřív
    // se na tu chvíli ukázalo DOSPĚLÉ rozhraní. Dokud profil nenaskočí, jen pozadí; pojistka 4 s.
    var profileWaitOver by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(PROFILE_WAIT_MS)
        profileWaitOver = true
    }
    if (activeProfile == null && !profileWaitOver) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    // AMPHORA F7 (Z1): kam se Děti otevřou, nastavuje rodič (Pro rodiče / Rodičovská appka). Jen jednou
    // po startu — pozdější přepnutí sekce dítětem nepřebíjí.
    var kidsStartApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isKidsProfile) {
        if (isKidsProfile && !kidsStartApplied) {
            kidsStartApplied = true
            val start = listenVm.profileConfig.value.kidsDisplay?.start
            if (start == com.github.jankoran90.showlyfin.core.domain.KidsDisplayPrefs.START_POLICKA) {
                current = SlovoSection.POSLECH
            }
        }
    }
    // Po návratu z Nastavení (pořadí knihoven/podcastů) ať se změna projeví.
    LaunchedEffect(Unit) { listenVm.reloadOrderPrefs() }
    // Místa lišty: dítě pevně, dospělý podle Nastavení (čte se znovu po návratu z Nastavení).
    val barItems = remember(isKidsProfile, current) {
        if (isKidsProfile) KidsBarSections else SlovoShellPrefs.barOrder(ctx)
    }
    // Pojistky sekcí: dítě jen svoje místa + Nastavení/Profil (rodič přes skrytý roh); dospělý nemá
    // dětský Poslech (starší uložený stav) → Domů.
    LaunchedEffect(isKidsProfile, current) {
        val allowed = if (isKidsProfile) {
            current in KidsBarSections || current == SlovoSection.NASTAVENI || current == SlovoSection.PROFIL
        } else current != SlovoSection.POSLECH
        if (!allowed) current = SlovoSection.DOMU
    }
    // PROFIL (2026-08-16): přepnutí profilu = re-create Activity (ViewModely jinak visí na předchozím profilu).
    val profileSwitch by com.github.jankoran90.showlyfin.core.domain.profile.ProfileSwitchSignal.switches
        .collectAsStateWithLifecycle()
    var lastProfileSwitch by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(profileSwitch) {
        if (profileSwitch > 0 && profileSwitch != lastProfileSwitch) {
            lastProfileSwitch = profileSwitch
            (ctx as? android.app.Activity)?.recreate()
        }
    }

    // Dětský profil: každý push detailu přes [kidsGuard] (jen schválené zdroje / série, odkudkoli).
    val onPush: (SlovoDetailEntry) -> Unit = { requested ->
        val entry = if (listenVm.activeProfile.value?.isAdmin == false) {
            kidsGuard(requested, listenVm.profileConfig.value.visibleForKidsSourceKeys, listenVm.sourceLinks.value)
        } else requested
        if (entry != null) detailStack = detailStack + entry
    }
    val onPop: () -> Unit = { detailStack = detailStack.dropLast(1) }
    val selectSection: (SlovoSection) -> Unit = { section ->
        showMore = false
        if (section == current && detailStack.isEmpty() && SlovoShellPrefs.reselectToTop(ctx)) {
            val old = resetTicks[section] ?: 0
            sectionStateHolder.removeState(sectionKey(section, old))
            resetTicks[section] = old + 1
        }
        detailStack = emptyList()
        current = section
    }
    // Offline epizoda z přehrávače → seznam stažených (dítě Poslech, dospělý Pořady › Sledované).
    val onGoToPoslech: () -> Unit = {
        detailStack = emptyList()
        current = if (isKidsProfile) SlovoSection.POSLECH else SlovoSection.PORADY
    }
    val expandMiniPlayer: () -> Unit = { onPush(SlovoDetailEntry.AudiobookPlayer(itemId = null, fromStart = false)) }

    val detailEntry = detailStack.lastOrNull()
    val fullscreen = detailEntry?.isFullscreenPlayer() == true
    BackHandler(enabled = detailEntry != null, onBack = onPop)
    BackHandler(enabled = detailEntry == null && current != SlovoSection.DOMU) { current = SlovoSection.DOMU }

    CompositionLocalProvider(
        // Rodičovská správa (user 2026-11-19 15:57): dětský profil bez avataru (viz [LocalSlovoMenuLocked]).
        LocalSlovoMenuLocked provides isKidsProfile,
        LocalSlovoProfileName provides activeProfile?.name,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Vlastní insety: nahoře je řeší sekce (statusBarsPadding) i detaily samy, dole lišta.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!fullscreen) {
                    SlovoBottomBar(
                        items = barItems,
                        selected = current.takeIf { it in barItems },
                        queueCount = queue.size,
                        onSelect = selectSection,
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                if (detailEntry != null) {
                    SlovoDetail(
                        entry = detailEntry,
                        onPush = onPush,
                        onPop = onPop,
                        onGoToPoslech = onGoToPoslech,
                        listenVm = listenVm,
                        podcastLinkLookup = podcastLinkLookup,
                    )
                } else {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        sectionStateHolder.SaveableStateProvider(sectionKey(current, resetTicks[current] ?: 0)) {
                            SlovoSectionContent(
                                section = current,
                                isKidsProfile = isKidsProfile,
                                listenVm = listenVm,
                                podcastLinkLookup = podcastLinkLookup,
                                onMenu = { if (isKidsProfile) showParent = true else showMore = true },
                                onPush = onPush,
                                onSelectSection = selectSection,
                                expandMiniPlayer = expandMiniPlayer,
                            )
                        }
                    }
                }
                // MiniPlayer nad obsahem i detaily (schová se sám, když nic nehraje), v přehrávači ne.
                if (!fullscreen) {
                    MiniPlayer(
                        onExpand = expandMiniPlayer,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        isListenSection = detailEntry == null && current == SlovoSection.DOMU,
                    )
                }
            }
        }
        if (showParent && isKidsProfile) {
            val listenState by listenVm.uiState.collectAsStateWithLifecycle()
            SlovoParentHost(
                booksCount = listenState.books.size,
                onClose = { showParent = false },
                onSelect = { section ->
                    showParent = false
                    selectSection(section)
                },
            )
        }
        if (showMore) {
            SlovoMoreSheet(
                profileName = activeProfile?.name,
                isKids = isKidsProfile,
                onSelect = selectSection,
                onDismiss = { showMore = false },
            )
        }
    }
}

private fun sectionKey(section: SlovoSection, tick: Int): String = "${section.name}#$tick"

/** Jak dlouho nejvýš čekat na obnovení aktivního profilu, než se ukáže rozhraní (viz SlovoShellContent). */
private const val PROFILE_WAIT_MS = 4_000L
