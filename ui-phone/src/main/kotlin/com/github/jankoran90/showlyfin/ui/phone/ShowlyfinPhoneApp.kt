package com.github.jankoran90.showlyfin.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.core.ui.LocalCsfdRatingProvider
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.EpisodePickerScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinDetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinLibraryItemsScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookDetailScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.AudiobookPlayerScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.MiniPlayer
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDetailScreen
import com.github.jankoran90.showlyfin.feature.playback.ui.PlaybackScreen
import com.github.jankoran90.showlyfin.feature.remux.RemuxHistoryScreen
import com.github.jankoran90.showlyfin.feature.remux.RemuxPickerScreen
import com.github.jankoran90.showlyfin.feature.remux.RemuxProgressScreen
import com.github.jankoran90.showlyfin.feature.remux.SmartDetectScreen
import com.github.jankoran90.showlyfin.feature.uploader.LibraryBrowserScreen
import com.github.jankoran90.showlyfin.feature.uploader.LibraryDetailScreen
import com.github.jankoran90.showlyfin.feature.uploader.MoveStepScreen
import com.github.jankoran90.showlyfin.feature.uploader.ReviewStepScreen
import com.github.jankoran90.showlyfin.feature.uploader.UploaderScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.MainLoginScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ProfileGateViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ProfilePickerScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ServerSetupScreen
import com.github.jankoran90.showlyfin.ui.phone.theme.ShowlyfinPhoneTheme

internal sealed interface Destination {
    // COMPASS C1: top-level cíle drawer (drawer = jediná nav, spodní lišta zrušena)
    data object Hlavni : Destination   // label „Sleduj"
    data object Ovladac : Destination  // RELAY — sekce Ovladač
    data object Listen : Destination   // label „Poslech"
    data object Oblibeni : Destination // COMPASS — sekce Oblíbení
    data object Settings : Destination
    data object Admin : Destination     // Plan HELM — admin destinace (jen pro admin profil)

    // Uploader — už není tab lišty, otevírá se z Nastavení
    data object Uploader : Destination

    // COMPASS C3 — univerzální hledání (sub-screen, otevírá se z horního pole)
    data object Search : Destination

    // Poslech sub-screens
    data class AudiobookDetail(val itemId: String, val parent: Destination) : Destination
    data class PodcastDetail(val itemId: String, val parent: Destination) : Destination
    data class AudiobookPlayer(val itemId: String?, val fromStart: Boolean, val startSec: Double? = null, val episodeId: String? = null, val parent: Destination) : Destination

    // Sub-screens
    data class Detail(val item: MediaItem, val parent: Destination) : Destination
    data class SmartDetect(val imdbId: String, val title: String, val titleCs: String, val year: Int?, val mediaType: String) : Destination
    data class ReviewStep(val sid: String, val fid: String, val filename: String) : Destination
    data class MoveStep(val sid: String) : Destination
    data object LibraryBrowser : Destination
    data class LibraryDetail(val library: String, val item: LibraryItem) : Destination
    data class RemuxPicker(val library: String, val folder: String) : Destination
    data class RemuxProgress(val jobId: String, val folder: String) : Destination
    data object RemuxHistory : Destination
    data class JellyfinLibrary(
        val libraryId: String,
        val libraryName: String,
        val collectionType: String? = null,
        val parentItemType: String? = null,
        val ancestors: List<JellyfinLibraryRef> = emptyList(),
    ) : Destination
    data class JellyfinDetail(val itemId: String, val parent: Destination) : Destination
    data class EpisodePicker(val seriesId: String, val seriesName: String, val parent: Destination) : Destination
    data class JellyfinPlayback(val itemId: String, val parent: JellyfinDetail) : Destination
    data class Player(val itemId: String?, val externalUrl: String?, val title: String, val parent: Destination, val subtitleQuery: com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery? = null) : Destination
}

internal data class JellyfinLibraryRef(
    val libraryId: String,
    val libraryName: String,
    val collectionType: String?,
    val parentItemType: String?,
)

private fun Destination.JellyfinLibrary.toRef() = JellyfinLibraryRef(
    libraryId = libraryId,
    libraryName = libraryName,
    collectionType = collectionType,
    parentItemType = parentItemType,
)

/** VANTAGE D2: lidský název sekce, ze které se otevřel detail (text u šipky Zpět na detailu).
 *  Detail→detail (proklik kolekcí) projde řetězcem rodičů k původní sekci. */
private fun Destination.sectionLabel(activeSubsection: String?): String = when (this) {
    is Destination.Detail -> parent.sectionLabel(activeSubsection)
    is Destination.JellyfinDetail -> parent.sectionLabel(activeSubsection)
    Destination.Hlavni -> when (activeSubsection) {
        ProfileConfig.Sections.KNIHOVNA -> "Knihovna"
        ProfileConfig.Sections.CHCI_VIDET -> "Chci vidět"
        ProfileConfig.Sections.OBJEVIT -> "Objevit"
        ProfileConfig.Sections.HISTORIE -> "Historie"
        ProfileConfig.Sections.NA_RD -> "Na RD"
        else -> "Sleduj"
    }
    Destination.Oblibeni -> "Oblíbení"
    Destination.Search -> "Hledání"
    Destination.Listen -> "Poslech"
    else -> "Zpět"
}

private fun JellyfinLibraryRef.toDestination(ancestors: List<JellyfinLibraryRef>) = Destination.JellyfinLibrary(
    libraryId = libraryId,
    libraryName = libraryName,
    collectionType = collectionType,
    parentItemType = parentItemType,
    ancestors = ancestors,
)

// COMPASS C1: top-level cíle (drawer); jméno ponecháno kvůli minimální změně. „isSubScreen" = mimo ně.
private val bottomTabs = listOf(
    Destination.Hlavni, Destination.Ovladac, Destination.Listen, Destination.Oblibeni, Destination.Settings, Destination.Admin,
)

/** FUSE F1: jedna definice navigačních cílů → vykreslí se buď ve spodní liště (telefon),
 * nebo ve fokus railu (TV). */
internal data class ShellNavItem(
    val dest: Destination,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun ShowlyfinApp(isTv: Boolean = false) {
    ShowlyfinPhoneTheme {
        val gateViewModel: ProfileGateViewModel = hiltViewModel()
        val gateState by gateViewModel.state.collectAsStateWithLifecycle()

        // Plan GATEKEY G-A3/G-A4: během stahování rosteru i hydratace profilu drž spinner
        // (ať neproblikne ServerSetup a JF obrazovky nenaběhnou nepřihlášené).
        if (gateState.isLoading || gateState.seeding || gateState.activating) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { CircularProgressIndicator() }
            return@ShowlyfinPhoneTheme
        }

        // Plan GATEKEY G-A1: čistá instalace → hlavní login (backend heslo) PŘED ServerSetup/pickerem.
        if (gateState.needsMainLogin) {
            MainLoginScreen(
                isLoading = gateState.mainLoginLoading,
                error = gateState.mainLoginError,
                onLogin = { password, urlOverride -> gateViewModel.submitMainLogin(password, urlOverride) },
                modifier = Modifier.fillMaxSize(),
            )
            return@ShowlyfinPhoneTheme
        }

        if (gateState.isAddingProfile || gateState.profiles.isEmpty()) {
            ServerSetupScreen(
                onDone = { gateViewModel.cancelAddProfile() },
                modifier = Modifier.fillMaxSize(),
            )
            return@ShowlyfinPhoneTheme
        }

        if (gateState.activeProfile == null) {
            ProfilePickerScreen(
                profiles = gateState.profiles,
                onProfileClicked = { gateViewModel.onProfileClicked(it) },
                onAddProfile = { gateViewModel.startAddProfile() },
                pinPromptName = gateState.pendingPinProfile?.name,
                pinError = gateState.pinError,
                onSubmitPin = { gateViewModel.submitPin(it) },
                onCancelPin = { gateViewModel.cancelPin() },
                errorMessage = gateState.activationError,
                modifier = Modifier.fillMaxSize(),
            )
            return@ShowlyfinPhoneTheme
        }

        // Plan STRATA Fáze C: počkej na config aktivního profilu (defaultSection + viditelnost), ať
        // úvodní záložka nezmrzne na Knihovně místo výchozího Poslechu (cold-start race).
        if (!gateState.configLoaded) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { CircularProgressIndicator() }
            return@ShowlyfinPhoneTheme
        }

        // Plan STRATA: blocklist viditelnost dle aktivního profilu (prázdné = vše vidět). TV má vlastní
        // sadu skrytých (hiddenSectionsTv); null = zrcadlí telefon. + Fáze E: pořadí nav/podsekcí.
        val hiddenSections = if (isTv) gateState.hiddenSectionsTv ?: gateState.hiddenSections else gateState.hiddenSections
        fun sectionVisible(key: String): Boolean = key !in hiddenSections
        val orderCfg = ProfileConfig(sectionOrder = gateState.sectionOrder, subsectionOrder = gateState.subsectionOrder)
        val poslechVisible = sectionVisible(ProfileConfig.Sections.POSLECH)
        val ovladacVisible = sectionVisible(ProfileConfig.Sections.OVLADAC)
        val visibleSubsections = orderCfg.orderedSubsections().filter { sectionVisible(it) }

        // Plan PROFILES Fáze 4: „hlavní" sekce — která sekce se profilu otevře po vstupu.
        // Poslech → spodní tab Listen; podsekce Sleduj → Hlavni s předvybranou podsekcí.
        // Plan VAULT V10: Sleduj je nově skrývatelná → fallback Poslech, pak Nastavení.
        val sledujVisible = sectionVisible(ProfileConfig.Sections.SLEDUJ)
        val defaultSection = gateState.defaultSection?.takeIf { it.isNotBlank() }
        val startBottomTab: Destination = when {
            defaultSection == ProfileConfig.Sections.POSLECH && poslechVisible -> Destination.Listen
            sledujVisible -> Destination.Hlavni
            poslechVisible -> Destination.Listen
            else -> Destination.Settings
        }
        // Předvybraná podsekce „Sleduj" (jen je-li viditelná), jinak první viditelná.
        val initialSubsection: String? = defaultSection
            ?.takeIf { it in visibleSubsections }

        // Keyed na aktivní profil → přepnutí profilu znovu aplikuje jeho „hlavní" sekci.
        val profileKey = gateState.activeProfile?.id
        var currentDestination by remember(profileKey) { mutableStateOf<Destination>(startBottomTab) }
        var bottomTab by remember(profileKey) { mutableStateOf<Destination>(startBottomTab) }
        // GLIDE: zapamatuj aktivní podsekci „Sleduj" napříč navigací → Zpět z detailu se vrátí na ni
        // (ne na výchozí záložku). Aktualizuje MainScreen přes onSubsectionChange.
        var mainSubsection by remember(profileKey) { mutableStateOf(initialSubsection) }
        val context = LocalContext.current
        val naTvCoordinator: NaTvCoordinator = hiltViewModel()
        // CANVAS B: poskytovatel ČSFD hodnocení pro karty (líně + cache) → LocalCsfdRatingProvider.
        val cardCsfd: CardCsfdViewModel = hiltViewModel()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            naTvCoordinator.messages.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        // R1/#3: klepnutí na media notifikaci audioknihy → otevřít rovnou fullscreen přehrávač
        // (itemId=null = expand z běžící session, jako mini-player). Back vrací na grid Poslech.
        val openListenSignal by ListenNavSignal.openListen.collectAsStateWithLifecycle()
        LaunchedEffect(openListenSignal) {
            if (openListenSignal > 0) {
                bottomTab = Destination.Listen
                currentDestination = Destination.AudiobookPlayer(
                    itemId = null, fromStart = false, parent = Destination.Listen,
                )
            }
        }

        // MAESTRO: „Přehrát na TV" → přepni rovnou na sekci „Ovladač" (jen když je viditelná).
        val openOvladacSignal by ListenNavSignal.openOvladac.collectAsStateWithLifecycle()
        LaunchedEffect(openOvladacSignal) {
            if (openOvladacSignal > 0 && ovladacVisible) {
                bottomTab = Destination.Ovladac
                currentDestination = Destination.Ovladac
            }
        }

        // VERDICT: proklik z doporučovače `showlyfin://detail?tmdb=` → otevři detail filmu.
        // Stejná stub-cesta jako onCollectionPartClick (detail se hydratuje z tmdbId).
        val openDetailReq by ListenNavSignal.openDetail.collectAsStateWithLifecycle()
        LaunchedEffect(openDetailReq) {
            val req = openDetailReq ?: return@LaunchedEffect
            currentDestination = Destination.Detail(
                MediaItem(
                    traktId = 0L,
                    tmdbId = req.tmdb,
                    imdbId = null,
                    title = req.title,
                    year = req.year,
                    overview = null,
                    rating = null,
                    genres = null,
                    type = MediaType.MOVIE,
                    posterPath = null,
                    backdropPath = null,
                ),
                parent = bottomTab,
            )
        }

        val onCollectionPartClick: (CollectionPart) -> Unit = { part ->
            val jfId = part.jellyfinId
            val tmdb = part.tmdbId
            timber.log.Timber.d("[CollectionClick] part='${part.title}' jfId=$jfId tmdb=$tmdb currentDest=${currentDestination::class.simpleName}")
            if (jfId != null) {
                val currentJellyfinParent = (currentDestination as? Destination.JellyfinDetail)?.parent
                    ?: (currentDestination as? Destination.JellyfinLibrary)
                    ?: Destination.JellyfinLibrary(libraryId = "", libraryName = "")
                currentDestination = Destination.JellyfinDetail(jfId, currentJellyfinParent)
            } else if (tmdb != null) {
                val stub = MediaItem(
                    traktId = 0L,
                    tmdbId = tmdb,
                    imdbId = null,
                    title = part.title,
                    year = part.year?.toIntOrNull(),
                    overview = null,
                    rating = null,
                    genres = null,
                    type = MediaType.MOVIE,
                    posterPath = null,
                    backdropPath = null,
                )
                currentDestination = Destination.Detail(stub, parent = currentDestination)
            }
        }

        val onStremioItem: (MediaItem) -> Unit = { item ->
            val mediaType = if (item.type == MediaType.MOVIE) "movie" else "series"
            val targetId = item.imdbId ?: item.tmdbId?.toString()
            if (targetId != null) {
                val uri = Uri.parse("stremio:///detail/$mediaType/$targetId")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    val storeUri = Uri.parse("https://www.stremio.com/downloads")
                    context.startActivity(Intent(Intent.ACTION_VIEW, storeUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        }

        val isSubScreen = currentDestination !in bottomTabs
        // CADENCE: podsekce Poslechu (detail audioknihy/podcastu) drží spodní lištu přehrávání viditelnou
        // (jen MiniPlayer, bez nav baru) — narozdíl od ostatních sub-screens.
        val isListenDetailSub = currentDestination is Destination.AudiobookDetail ||
            currentDestination is Destination.PodcastDetail

        val navItems = buildList {
            // Plan STRATA Fáze E: top-level nav v pořadí profilu (Nastavení/Správa vždy fixně na konci).
            orderCfg.orderedSections().forEach { key ->
                when (key) {
                    ProfileConfig.Sections.SLEDUJ -> if (sledujVisible) add(ShellNavItem(Destination.Hlavni, Icons.Default.Home, "Sleduj"))
                    ProfileConfig.Sections.OVLADAC -> if (ovladacVisible) add(ShellNavItem(Destination.Ovladac, Icons.Default.SettingsRemote, "Ovladač"))
                    ProfileConfig.Sections.POSLECH -> if (poslechVisible) add(ShellNavItem(Destination.Listen, Icons.Default.Headphones, "Poslech"))
                }
            }
            // COMPASS: Oblíbení vlastní top-level cíl (mezi sekcemi a správou).
            add(ShellNavItem(Destination.Oblibeni, Icons.Default.Star, "Oblíbení"))
            add(ShellNavItem(Destination.Settings, Icons.Default.Settings, "Nastavení"))
            // Plan HELM — admin destinace (správa profilů/šablon/zálohy) jen pro admin profil.
            if (gateState.activeProfile?.isAdmin == true) {
                add(ShellNavItem(Destination.Admin, Icons.Default.AdminPanelSettings, "Správa"))
            }
        }

        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        // COMPASS C1: spodní lišta zrušena → měříme jen výšku MiniPlayeru, ať obsah nezmizí za ním.
        val measuredBottomPx = remember { mutableFloatStateOf(0f) }

        // CANVAS F (GLIDE Fáze 2): skrývání horní lišty (search) při scrollu dolů, návrat při scrollu nahoru.
        var topBarVisible by remember { mutableStateOf(true) }
        val hideBarOnScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < -3f) topBarVisible = false
                    else if (available.y > 3f) topBarVisible = true
                    return Offset.Zero
                }
            }
        }
        // Po přepnutí sekce/obrazovky vždy ukaž lištu (ať nezůstane schovaná z minula).
        LaunchedEffect(currentDestination) { topBarVisible = true }
        // CANVAS F2: zachování scrollu/pozice obrazovek napříč navigací (LazyGrid/List state je saveable;
        // VM jsou activity-scoped → data zůstávají, restoruje se jen pozice scrollu).
        val stateHolder = rememberSaveableStateHolder()

        BackHandler(enabled = isSubScreen) {
            val current = currentDestination
            currentDestination = when (current) {
                is Destination.ReviewStep, is Destination.MoveStep -> Destination.Uploader
                is Destination.LibraryDetail -> Destination.LibraryBrowser
                is Destination.LibraryBrowser -> Destination.Uploader
                is Destination.RemuxPicker -> Destination.LibraryBrowser
                is Destination.RemuxProgress -> Destination.LibraryBrowser
                is Destination.RemuxHistory -> Destination.Uploader
                is Destination.JellyfinLibrary -> {
                    val ancestors = current.ancestors
                    if (ancestors.isEmpty()) {
                        Destination.Hlavni
                    } else {
                        ancestors.last().toDestination(ancestors.dropLast(1))
                    }
                }
                is Destination.JellyfinDetail -> current.parent
                is Destination.EpisodePicker -> current.parent
                is Destination.JellyfinPlayback -> current.parent
                is Destination.Player -> current.parent
                is Destination.Detail -> current.parent
                is Destination.Uploader -> Destination.Settings
                is Destination.AudiobookDetail -> current.parent
                is Destination.PodcastDetail -> current.parent
                is Destination.AudiobookPlayer -> current.parent
                else -> bottomTab
            }
        }

        CompositionLocalProvider(LocalCsfdRatingProvider provides cardCsfd) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isTv && !isSubScreen,
            drawerContent = {
                if (!isTv) {
                    AppDrawer(
                        items = navItems,
                        selected = bottomTab,
                        onSelect = { dest ->
                            bottomTab = dest
                            currentDestination = dest
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!isTv && !isSubScreen) {
                    AnimatedVisibility(visible = topBarVisible) {
                        AppTopBar(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = { currentDestination = Destination.Search },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            val showMiniColumn = if (isTv) !isSubScreen else (!isSubScreen || isListenDetailSub)
            val effectiveBottomDp = if (showMiniColumn) {
                with(density) { measuredBottomPx.floatValue.toDp() }
            } else 0.dp
            Row(modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .consumeWindowInsets(paddingValues)
            ) {
                // FUSE F1: na TV fokus rail vlevo místo spodní lišty (D-pad navigace).
                if (isTv && !isSubScreen) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = bottomTab == item.dest,
                                onClick = { bottomTab = item.dest; currentDestination = item.dest },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                                modifier = Modifier.tvFocusable(),
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = effectiveBottomDp)
                    .nestedScroll(hideBarOnScroll)
                ) {
            stateHolder.SaveableStateProvider(currentDestination.toString()) {
            when (val dest = currentDestination) {
                is Destination.Hlavni -> MainScreen(
                    visibleSubsections = visibleSubsections,
                    initialSubsection = mainSubsection,
                    onSubsectionChange = { mainSubsection = it },
                    onTraktItemClick = { item ->
                        bottomTab = Destination.Hlavni
                        currentDestination = Destination.Detail(item, parent = Destination.Hlavni)
                    },
                    onJellyfinItemClick = { jellyfinId ->
                        bottomTab = Destination.Hlavni
                        currentDestination = Destination.JellyfinDetail(jellyfinId, parent = Destination.Hlavni)
                    },
                    onOpenLibrary = { libraryId, libraryName, collectionType ->
                        currentDestination = Destination.JellyfinLibrary(
                            libraryId = libraryId,
                            libraryName = libraryName,
                            collectionType = collectionType,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.JellyfinLibrary -> JellyfinLibraryItemsScreen(
                    libraryId = dest.libraryId,
                    libraryName = dest.libraryName,
                    collectionType = dest.collectionType,
                    parentItemType = dest.parentItemType,
                    onBack = {
                        currentDestination = if (dest.ancestors.isEmpty()) {
                            Destination.Hlavni
                        } else {
                            dest.ancestors.last().toDestination(dest.ancestors.dropLast(1))
                        }
                    },
                    onItemPlay = { itemId ->
                        currentDestination = Destination.JellyfinDetail(itemId, dest)
                    },
                    onItemOpenRich = { media ->
                        currentDestination = Destination.Detail(media, parent = dest)
                    },
                    onItemDrillIn = { itemId, itemName, itemType ->
                        currentDestination = Destination.JellyfinLibrary(
                            libraryId = itemId,
                            libraryName = itemName,
                            collectionType = null,
                            parentItemType = itemType,
                            ancestors = dest.ancestors + dest.toRef(),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.JellyfinDetail -> JellyfinDetailScreen(
                    itemId = dest.itemId,
                    onBack = { currentDestination = dest.parent },
                    onPlay = { itemId ->
                        currentDestination = Destination.JellyfinPlayback(itemId, dest)
                    },
                    onOpenEpisodes = { seriesId, name ->
                        currentDestination = Destination.EpisodePicker(seriesId, name, dest)
                    },
                    onCollectionPartClick = onCollectionPartClick,
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.EpisodePicker -> EpisodePickerScreen(
                    seriesId = dest.seriesId,
                    seriesName = dest.seriesName,
                    onBack = { currentDestination = dest.parent },
                    onPlayEpisode = { epId ->
                        currentDestination = Destination.Player(itemId = epId, externalUrl = null, title = dest.seriesName, parent = dest)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.JellyfinPlayback -> PlaybackScreen(
                    itemId = dest.itemId,
                    onBack = { currentDestination = dest.parent },
                )
                is Destination.Uploader -> UploaderScreen(
                    onOpenReviewStep = { sid, fid, filename ->
                        currentDestination = Destination.ReviewStep(sid, fid, filename)
                    },
                    onOpenMoveStep = { sid -> currentDestination = Destination.MoveStep(sid) },
                    onOpenLibrary = { currentDestination = Destination.LibraryBrowser },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Settings -> SettingsScreen(
                    isAdmin = gateState.activeProfile?.isAdmin != false,
                    onOpenUploader = { currentDestination = Destination.Uploader },
                    onOpenAdmin = { currentDestination = Destination.Admin },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Admin -> AdminScreen(
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Oblibeni -> OblibeniScreen(
                    onOpenDetail = { tmdb, title ->
                        currentDestination = Destination.Detail(
                            MediaItem(
                                traktId = 0L,
                                tmdbId = tmdb,
                                imdbId = null,
                                title = title,
                                year = null,
                                overview = null,
                                rating = null,
                                genres = null,
                                type = MediaType.MOVIE,
                                posterPath = null,
                                backdropPath = null,
                            ),
                            parent = Destination.Oblibeni,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Search -> SearchScreen(
                    onOpenDetail = { tmdb, title, isShow ->
                        currentDestination = Destination.Detail(
                            MediaItem(
                                traktId = 0L,
                                tmdbId = tmdb,
                                imdbId = null,
                                title = title,
                                year = null,
                                overview = null,
                                rating = null,
                                genres = null,
                                type = if (isShow) MediaType.SHOW else MediaType.MOVIE,
                                posterPath = null,
                                backdropPath = null,
                            ),
                            parent = Destination.Search,
                        )
                    },
                    onBack = { currentDestination = bottomTab },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Ovladac -> OvladacScreen(
                    onOpenDetail = { itemId ->
                        bottomTab = Destination.Ovladac
                        currentDestination = Destination.JellyfinDetail(itemId, parent = Destination.Ovladac)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Listen -> ListenScreen(
                    onOpenBook = { itemId ->
                        bottomTab = Destination.Listen
                        currentDestination = Destination.AudiobookDetail(itemId, parent = Destination.Listen)
                    },
                    onOpenPodcast = { itemId ->
                        bottomTab = Destination.Listen
                        currentDestination = Destination.PodcastDetail(itemId, parent = Destination.Listen)
                    },
                    onPlayEpisode = { itemId, episodeId ->
                        bottomTab = Destination.Listen
                        currentDestination = Destination.AudiobookPlayer(
                            itemId = itemId, fromStart = false, episodeId = episodeId,
                            parent = Destination.Listen,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.AudiobookDetail -> AudiobookDetailScreen(
                    itemId = dest.itemId,
                    onBack = { currentDestination = dest.parent },
                    onPlay = { itemId, fromStart, startSec ->
                        currentDestination = Destination.AudiobookPlayer(itemId, fromStart, startSec, parent = dest)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.PodcastDetail -> PodcastDetailScreen(
                    itemId = dest.itemId,
                    onBack = { currentDestination = dest.parent },
                    onPlayEpisode = { itemId, episodeId, fromStart, startSec ->
                        currentDestination = Destination.AudiobookPlayer(
                            itemId = itemId, fromStart = fromStart, startSec = startSec,
                            episodeId = episodeId, parent = dest,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.AudiobookPlayer -> AudiobookPlayerScreen(
                    itemId = dest.itemId,
                    fromStart = dest.fromStart,
                    startSec = dest.startSec,
                    episodeId = dest.episodeId,
                    onBack = { currentDestination = dest.parent },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Detail -> DetailScreen(
                    item = dest.item,
                    onBack = { currentDestination = dest.parent },
                    sectionTitle = dest.parent.sectionLabel(mainSubsection),
                    onSmartDetect = { item ->
                        currentDestination = Destination.SmartDetect(
                            imdbId = item.imdbId ?: "",
                            title = item.title,
                            titleCs = item.title,
                            year = item.year,
                            mediaType = item.type.name.lowercase(),
                        )
                    },
                    onNaTv = { item, jfId -> naTvCoordinator.playOnTv(item, jfId) },
                    onStremio = onStremioItem,
                    onCollectionPartClick = onCollectionPartClick,
                    onPlayJellyfin = { jfId ->
                        currentDestination = if (dest.item.type == MediaType.SHOW) {
                            Destination.EpisodePicker(jfId, dest.item.title, dest)
                        } else {
                            Destination.Player(itemId = jfId, externalUrl = null, title = dest.item.title, parent = dest)
                        }
                    },
                    onPlayStreamUrl = { url, title, subQuery ->
                        currentDestination = Destination.Player(itemId = null, externalUrl = url, title = title, parent = dest, subtitleQuery = subQuery)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Player -> {
                    // CASCADE Fáze 4: stejná (Activity-scoped) instance DetailViewModelu jako na Detailu —
                    // drží candidate list `streams`; po chybě přehrávání spustíme dalšího kandidáta.
                    val detailVm: DetailViewModel = hiltViewModel()
                    PlaybackScreen(
                        itemId = dest.itemId ?: "",
                        externalUrl = dest.externalUrl,
                        externalTitle = dest.title,
                        subtitleQuery = dest.subtitleQuery,
                        onBack = { currentDestination = dest.parent },
                        onPlaybackFailed = { code ->
                            currentDestination = dest.parent   // pop na Detail, kde žije candidate list
                            detailVm.onPlaybackFailed(code)
                        },
                    )
                }
                is Destination.SmartDetect -> SmartDetectScreen(
                    imdbId = dest.imdbId,
                    title = dest.title,
                    titleCs = dest.titleCs,
                    year = dest.year,
                    mediaType = dest.mediaType,
                    onBack = { currentDestination = bottomTab },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.ReviewStep -> ReviewStepScreen(
                    sid = dest.sid,
                    fid = dest.fid,
                    filename = dest.filename,
                    onBack = { currentDestination = Destination.Uploader },
                    onConfirmed = { currentDestination = Destination.Uploader },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.MoveStep -> MoveStepScreen(
                    sid = dest.sid,
                    onBack = { currentDestination = Destination.Uploader },
                    onMoved = { currentDestination = Destination.Uploader },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.LibraryBrowser -> LibraryBrowserScreen(
                    onBack = { currentDestination = Destination.Uploader },
                    onItemClick = { library, item -> currentDestination = Destination.LibraryDetail(library, item) },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.LibraryDetail -> LibraryDetailScreen(
                    library = dest.library,
                    item = dest.item,
                    onBack = { currentDestination = Destination.LibraryBrowser },
                    onRemuxClick = { lib, folder -> currentDestination = Destination.RemuxPicker(lib, folder) },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.RemuxPicker -> RemuxPickerScreen(
                    library = dest.library,
                    folder = dest.folder,
                    onBack = { currentDestination = Destination.LibraryBrowser },
                    onJobStarted = { jobId -> currentDestination = Destination.RemuxProgress(jobId, dest.folder) },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.RemuxProgress -> RemuxProgressScreen(
                    jobId = dest.jobId,
                    folder = dest.folder,
                    onBack = { currentDestination = Destination.LibraryBrowser },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.RemuxHistory -> RemuxHistoryScreen(
                    onBack = { currentDestination = Destination.Uploader },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            }
                }

                // COMPASS C1: spodní navigační lišta zrušena (nav je v levém draweru). Zůstává jen
                // MiniPlayer ukotvený dole; měříme jeho výšku pro spodní padding obsahu (phone i TV).
                if (showMiniColumn) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { measuredBottomPx.floatValue = it.height.toFloat() },
                    ) {
                        MiniPlayer(
                            onExpand = {
                                currentDestination = Destination.AudiobookPlayer(
                                    itemId = null, fromStart = false, parent = currentDestination,
                                )
                            },
                            isListenSection = bottomTab is Destination.Listen,
                        )
                    }
                }
                }
            }
        }
        }
        }
    }
}
