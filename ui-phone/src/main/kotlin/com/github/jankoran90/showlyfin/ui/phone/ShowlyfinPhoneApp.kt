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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Podcasts
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
import com.github.jankoran90.showlyfin.core.ui.LocalCzechOverviewProvider
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem
import com.github.jankoran90.showlyfin.data.uploader.model.SourceSearchResult
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
import com.github.jankoran90.showlyfin.feature.listen.ui.YoutubeChannelScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.SourceManagerScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoveryScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.RssPodcastScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.CtvProgramScreen
import com.github.jankoran90.showlyfin.feature.listen.ui.MergedPodcastScreen
import com.github.jankoran90.showlyfin.feature.listen.ListenSourceTarget
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import com.github.jankoran90.showlyfin.feature.listen.PodcastLinkLookupViewModel
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

    // COMPASS C3 — univerzální hledání (sub-screen, otevírá se z horního pole).
    // [podcasts] = kontextové směrování dle aktuální sekce: v Poslechu hledá PODCASTY, jinak filmy/lidi.
    data class Search(val podcasts: Boolean = false) : Destination

    // NOMAD (SHW-60) — sekce „Stažené" (offline obsah v telefonu)
    data object Downloads : Destination

    // Poslech sub-screens
    data class AudiobookDetail(val itemId: String, val parent: Destination) : Destination
    data class PodcastDetail(val itemId: String, val parent: Destination) : Destination
    data class AudiobookPlayer(val itemId: String?, val fromStart: Boolean, val startSec: Double? = null, val episodeId: String? = null, val parent: Destination) : Destination

    // TUNER (SHW-62) — YouTube kanál jako podcast (video+audio streaming)
    // NAVIGATE (SHW-73): highlightEpisodeKey = epizoda ke zvýraznění/scrollu (z Timeline řádku / cover prokliku).
    data class YoutubeChannel(val handle: String, val title: String, val parent: Destination, val highlightEpisodeKey: String? = null) : Destination

    // PRESET (SHW-65) — správce zdrojů Poslechu (drawer nad adminem) + RSS podcast obrazovka
    data object SourceManager : Destination
    // AGORA — objevovací modul podcastů (drawer „Objevit podcasty", vedle Zdrojů podcastů)
    data object PodcastDiscovery : Destination
    data class RssPodcast(val feedUrl: String, val title: String, val parent: Destination, val highlightEpisodeKey: String? = null) : Destination

    // KAVKA (SHW-76) — ČT iVysílání pořad jako podcast (DASH video+audio streaming). ctvId = sidp pořadu.
    data class CtvProgram(val ctvId: String, val title: String, val parent: Destination, val highlightEpisodeKey: String? = null) : Destination

    // TWINE (SHW-74 / plán F7) — sloučený pohled propojeného pořadu (audio RSS + video YouTube).
    // WEFT (SHW-75/W2-FIX): highlightEpisodeKey = klíč epizody (`rss:`/`yt:`) k zvýraznění + scroll
    // (z časové osy / z coveru přehrávače) — sloučená obrazovka dřív highlight neuměla (gap z TWINE).
    data class MergedPodcast(
        val groupId: String,
        val title: String,
        val parent: Destination,
        val highlightEpisodeKey: String? = null,
    ) : Destination

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
    data class Player(val itemId: String?, val externalUrl: String?, val title: String, val parent: Destination, val subtitleQuery: com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery? = null, val posterUrl: String? = null, val localVideoPath: String? = null, val localSubtitlePath: String? = null, val localPosterPath: String? = null, val offlineKey: String = "", val resumeKey: String? = null) : Destination
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
    is Destination.Search -> "Hledání"
    Destination.Downloads -> "Stažené"
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
    Destination.Hlavni, Destination.Ovladac, Destination.Listen, Destination.Oblibeni, Destination.Downloads, Destination.SourceManager, Destination.PodcastDiscovery, Destination.Settings, Destination.Admin,
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
    // CHORUS Osa 3 (kánon motivu): volba fontu I motivu (pozadí, skin, posuvníky) se čte v kořeni a
    // předává do motivu (theme-level pref). Activity-scoped VM → sekce Vzhled mění tytéž instance živě.
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
        // WEFT (SHW-75/W2): lookup propojených pořadů (TWINE) → proklik z Timeline / cover v přehrávači
        // u slinkovaného zdroje míří na SLOUČENOU obrazovku, ne na původní audio/video.
        val podcastLinkLookup: PodcastLinkLookupViewModel = hiltViewModel()
        // RESONANCE (SHW-81): sdílená instance (žádný NavHost → Activity-scoped, tatáž jako v ListenScreen)
        // → proklik z přehrávače u offline epizody předá „otevři offline pořad" do ListenScreen.
        val listenVm: ListenViewModel = hiltViewModel()
        // AIRWAVE II Fáze C: lookup stažené kopie filmu (deep-link `showlyfin://detail?...&play=offline`).
        val downloadsVm: DownloadsViewModel = hiltViewModel()
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
            val detailDest = Destination.Detail(
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
            currentDestination = detailDest
            // AIRWAVE II Fáze C (část B): play=offline → je-li film stažený, spusť rovnou offline kopii.
            // Není-li stažený, zůstaň jen na kartě (nic navíc — NEspouštět jiný zdroj).
            if (req.playOffline) {
                val tmdbInt = req.tmdb.toInt()
                val dl = downloadsVm.findMovieByTmdb(tmdbInt)
                if (dl != null) {
                    currentDestination = Destination.Player(
                        itemId = null,
                        externalUrl = null,
                        title = dl.title,
                        parent = detailDest,
                        localVideoPath = dl.videoPath,
                        localSubtitlePath = dl.subtitlePath,
                        localPosterPath = dl.posterPath,
                        offlineKey = dl.key,
                    )
                }
            }
        }

        // BEAM (SHW-63): sdílený odkaz `showlyfin://listen?type=…` → přepni na Poslech a otevři plochu.
        val openListenItemReq by ListenNavSignal.openListenItem.collectAsStateWithLifecycle()
        LaunchedEffect(openListenItemReq) {
            val req = openListenItemReq ?: return@LaunchedEffect
            bottomTab = Destination.Listen
            currentDestination = when (req.type) {
                "audiobook" -> Destination.AudiobookDetail(req.id, parent = Destination.Listen)
                "yt" -> Destination.YoutubeChannel(handle = req.id, title = req.title, parent = Destination.Listen)
                else -> Destination.PodcastDetail(req.id, parent = Destination.Listen)
            }
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
            // NOMAD (SHW-60): offline „Stažené" jako vlastní top-level cíl.
            add(ShellNavItem(Destination.Downloads, Icons.Default.Download, "Stažené"))
            add(ShellNavItem(Destination.Settings, Icons.Default.Settings, "Nastavení"))
            // PRESET (SHW-65) — správa zdrojů Poslechu z postranního menu jen pro SPRÁVCE + účet yellman
            // (profil „Honza") — rozhodnutí usera 2026-06-16: rodinné zdroje nesmí měnit ostatní profily.
            val isSourcesOwner = gateState.activeProfile?.let { p ->
                p.isAdmin && (p.name.trim().equals("Honza", ignoreCase = true) ||
                    p.name.trim().equals("yellman", ignoreCase = true))
            } == true
            if (isSourcesOwner) {
                add(ShellNavItem(Destination.SourceManager, Icons.Default.Podcasts, "Zdroje podcastů"))
                add(ShellNavItem(Destination.PodcastDiscovery, Icons.Default.Explore, "Objevit podcasty"))
            }
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

        // Nastavení je top-level cíl (v bottomTabs → isSubScreen=false), takže globální BackHandler by
        // na něm byl vypnutý a systémové Zpět z rozcestníku Nastavení by ukončilo celou aplikaci. Proto
        // ho zapínáme i pro Settings a vracíme na domovskou záložku (podstránky Nastavení řeší vlastní
        // BackHandler v SettingsCategoryScreen, který je vnořenější → chytí Zpět dřív a vrátí na rozcestník).
        BackHandler(enabled = isSubScreen || currentDestination == Destination.Settings) {
            val current = currentDestination
            if (current == Destination.Settings) {
                bottomTab = startBottomTab
                currentDestination = startBottomTab
                return@BackHandler
            }
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
                is Destination.YoutubeChannel -> current.parent
                is Destination.RssPodcast -> current.parent
                is Destination.CtvProgram -> current.parent
                is Destination.MergedPodcast -> current.parent
                else -> bottomTab
            }
        }

        CompositionLocalProvider(
            LocalCsfdRatingProvider provides cardCsfd,
            LocalCzechOverviewProvider provides cardCsfd,
        ) {
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
                // CHORUS Osa 1: v Nastavení má vlastní pole „Hledat v nastavení" + rozcestník →
                // globální vyjíždějící lišta (menu + hledání médií, autohide) by se s ním prala → skryj ji.
                if (!isTv && !isSubScreen && currentDestination != Destination.Settings) {
                    AnimatedVisibility(visible = topBarVisible) {
                        AppTopBar(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            // Kontextové hledání: v sekci Poslech hledá PODCASTY, jinak filmy/seriály/lidi.
                            onSearchClick = {
                                currentDestination = Destination.Search(podcasts = bottomTab is Destination.Listen)
                            },
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
                    podcasts = dest.podcasts,
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
                            parent = dest,
                        )
                    },
                    // Otevření podcastového zdroje z výsledků hledání (YouTube → kanál, ČT → pořad, RSS → epizody).
                    onOpenSource = { src ->
                        currentDestination = when (src.type) {
                            "youtube" -> Destination.YoutubeChannel(handle = src.ref, title = src.title, parent = dest)
                            "ctv" -> Destination.CtvProgram(ctvId = src.ref, title = src.title, parent = dest)
                            else -> Destination.RssPodcast(feedUrl = src.ref, title = src.title, parent = dest)
                        }
                    },
                    onBack = { currentDestination = bottomTab },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Downloads -> DownloadsScreen(
                    onPlay = { dl ->
                        currentDestination = Destination.Player(
                            itemId = null,
                            externalUrl = null,
                            title = dl.title,
                            parent = Destination.Downloads,
                            localVideoPath = dl.videoPath,
                            localSubtitlePath = dl.subtitlePath,
                            localPosterPath = dl.posterPath,
                            offlineKey = dl.key,
                        )
                    },
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
                    onOpenSource = { src ->
                        // PRESET (SHW-65) — vlastní zdroj z Podcastů: YouTube → kanál, ČT → pořad, RSS → epizody.
                        bottomTab = Destination.Listen
                        currentDestination = when (src.type) {
                            "youtube" -> Destination.YoutubeChannel(handle = src.ref, title = src.title, parent = Destination.Listen)
                            "ctv" -> Destination.CtvProgram(ctvId = src.ref, title = src.title, parent = Destination.Listen)
                            else -> Destination.RssPodcast(feedUrl = src.ref, title = src.title, parent = Destination.Listen)
                        }
                    },
                    // TWINE (SHW-74) — sloučený pohled propojeného pořadu (audio+video).
                    onOpenMerged = { gid, gTitle ->
                        bottomTab = Destination.Listen
                        currentDestination = Destination.MergedPodcast(gid, gTitle, parent = Destination.Listen)
                    },
                    // NAVIGATE (SHW-73) — klik na řádek v Timeline → obsah zdroje + zvýrazni epizodu.
                    // WEFT (SHW-75/W2): u propojeného pořadu (TWINE) otevři SLOUČENOU obrazovku.
                    onOpenSourceEpisode = { type, ref, srcTitle, epKey ->
                        bottomTab = Destination.Listen
                        val group = podcastLinkLookup.groupFor(type, ref)
                        currentDestination = when {
                            // WEFT (SHW-75/W2-FIX): u sloučeného pořadu zvýrazni epizodu i ve SLOUČENÉ obrazovce.
                            group != null -> Destination.MergedPodcast(group.id, group.title ?: srcTitle, parent = Destination.Listen, highlightEpisodeKey = epKey)
                            type == "youtube" -> Destination.YoutubeChannel(handle = ref, title = srcTitle, parent = Destination.Listen, highlightEpisodeKey = epKey)
                            type == "ctv" -> Destination.CtvProgram(ctvId = ref, title = srcTitle, parent = Destination.Listen, highlightEpisodeKey = epKey)
                            else -> Destination.RssPodcast(feedUrl = ref, title = srcTitle, parent = Destination.Listen, highlightEpisodeKey = epKey)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.SourceManager -> SourceManagerScreen(
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.PodcastDiscovery -> PodcastDiscoveryScreen(
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.RssPodcast -> RssPodcastScreen(
                    feedUrl = dest.feedUrl,
                    title = dest.title,
                    highlightEpisodeKey = dest.highlightEpisodeKey,
                    onBack = { currentDestination = dest.parent },
                    onOpenAudioPlayer = {
                        currentDestination = Destination.AudiobookPlayer(
                            itemId = null, fromStart = false, parent = dest,
                        )
                    },
                    // EXODUS E2: video epizody NaVýbornou = JF knihovní položka → standardní přehrávač.
                    // REWIND (SHW-68): resumeKey = klíč epizody (sdílený s audio řádkem) → resume/progres videa.
                    onPlayVideo = { jfItemId, videoTitle, resumeKey ->
                        currentDestination = Destination.Player(
                            itemId = jfItemId, externalUrl = null, title = videoTitle, parent = dest,
                            resumeKey = resumeKey,
                        )
                    },
                    // AGORA (F5): video verze epizody z YouTube → externí přehrávač (jako YouTube kanál).
                    onPlayYoutubeVideo = { url, videoTitle, poster ->
                        currentDestination = Destination.Player(
                            itemId = null, externalUrl = url, title = videoTitle, parent = dest, posterUrl = poster,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // TWINE (SHW-74): sloučený pohled propojeného pořadu (audio RSS + video YouTube).
                is Destination.MergedPodcast -> MergedPodcastScreen(
                    groupId = dest.groupId,
                    title = dest.title,
                    highlightEpisodeKey = dest.highlightEpisodeKey,
                    onBack = { currentDestination = dest.parent },
                    onOpenAudioPlayer = {
                        currentDestination = Destination.AudiobookPlayer(
                            itemId = null, fromStart = false, parent = dest,
                        )
                    },
                    onPlayVideo = { url, videoTitle, poster ->
                        currentDestination = Destination.Player(
                            itemId = null, externalUrl = url, title = videoTitle, parent = dest, posterUrl = poster,
                        )
                    },
                    // Po zrušení propojení se zdroje vrátí jako samostatné karty → zpět do Poslechu.
                    onUnlinked = { currentDestination = dest.parent },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.YoutubeChannel -> YoutubeChannelScreen(
                    channel = dest.handle,
                    channelTitle = dest.title,
                    highlightEpisodeKey = dest.highlightEpisodeKey,
                    onBack = { currentDestination = dest.parent },
                    onPlayVideo = { url, title, poster ->
                        currentDestination = Destination.Player(
                            itemId = null, externalUrl = url, title = title, parent = dest, posterUrl = poster,
                        )
                    },
                    onOpenAudioPlayer = {
                        currentDestination = Destination.AudiobookPlayer(
                            itemId = null, fromStart = false, parent = dest,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // KAVKA (SHW-76) — ČT iVysílání pořad jako podcast (DASH video+audio, cast na TV).
                is Destination.CtvProgram -> CtvProgramScreen(
                    ctvId = dest.ctvId,
                    title = dest.title,
                    highlightEpisodeKey = dest.highlightEpisodeKey,
                    onBack = { currentDestination = dest.parent },
                    onPlayVideo = { url, title, poster ->
                        currentDestination = Destination.Player(
                            itemId = null, externalUrl = url, title = title, parent = dest, posterUrl = poster,
                        )
                    },
                    onOpenAudioPlayer = {
                        currentDestination = Destination.AudiobookPlayer(
                            itemId = null, fromStart = false, parent = dest,
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
                    // PERCH (SHW-69): klik na cover → seznam dílů rodiče právě hraného (napříč zdroji
                    // Poslechu). Zpět ze seznamu → návrat do přehrávače (parent = tato destinace).
                    onOpenSource = { target ->
                        currentDestination = when (target) {
                            // RESONANCE (SHW-81): offline epizoda → přepni na Poslech a otevři offline detail
                            // pořadu přes sdílený ListenViewModel (offline detail není Destination).
                            is ListenSourceTarget.Offline -> {
                                listenVm.openOfflinePodcast(target.showTitle, target.episodeKey)
                                bottomTab = Destination.Listen
                                Destination.Listen
                            }
                            is ListenSourceTarget.Audiobook -> Destination.AudiobookDetail(target.itemId, parent = dest)
                            is ListenSourceTarget.Podcast -> Destination.PodcastDetail(target.itemId, parent = dest)
                            // NAVIGATE (SHW-73): zvýrazni právě hranou epizodu v seznamu dílů zdroje.
                            // WEFT (SHW-75/W2): u propojeného pořadu (TWINE) otevři SLOUČENOU obrazovku.
                            is ListenSourceTarget.Rss -> podcastLinkLookup.groupFor("rss", target.feedUrl)?.let {
                                Destination.MergedPodcast(it.id, it.title ?: target.title, parent = dest, highlightEpisodeKey = target.episodeKey)
                            } ?: Destination.RssPodcast(target.feedUrl, target.title, parent = dest, highlightEpisodeKey = target.episodeKey)
                            is ListenSourceTarget.Youtube -> podcastLinkLookup.groupFor("youtube", target.handle)?.let {
                                Destination.MergedPodcast(it.id, it.title ?: target.title, parent = dest, highlightEpisodeKey = target.episodeKey)
                            } ?: Destination.YoutubeChannel(target.handle, target.title, parent = dest, highlightEpisodeKey = target.episodeKey)
                        }
                    },
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
                        currentDestination = Destination.Player(itemId = null, externalUrl = url, title = title, parent = dest, subtitleQuery = subQuery, posterUrl = dest.item.posterUrl())
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
                        externalPosterUrl = dest.posterUrl,
                        localVideoPath = dest.localVideoPath,
                        localSubtitlePath = dest.localSubtitlePath,
                        localPosterPath = dest.localPosterPath,
                        offlineKey = dest.offlineKey,
                        resumeKey = dest.resumeKey,
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
