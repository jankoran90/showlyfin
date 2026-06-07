package com.github.jankoran90.showlyfin.ui.phone

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.EpisodePickerScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinDetailScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinLibraryItemsScreen
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
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ProfileGateViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ProfilePickerScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.ServerSetupScreen
import com.github.jankoran90.showlyfin.ui.phone.theme.ShowlyfinPhoneTheme

private sealed interface Destination {
    // Bottom tabs
    data object Hlavni : Destination
    data object Uploader : Destination
    data object Settings : Destination

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

private data class JellyfinLibraryRef(
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

private fun JellyfinLibraryRef.toDestination(ancestors: List<JellyfinLibraryRef>) = Destination.JellyfinLibrary(
    libraryId = libraryId,
    libraryName = libraryName,
    collectionType = collectionType,
    parentItemType = parentItemType,
    ancestors = ancestors,
)

private val bottomTabs = listOf(
    Destination.Hlavni, Destination.Uploader, Destination.Settings,
)

@Composable
fun ShowlyfinPhoneApp() {
    ShowlyfinPhoneTheme {
        val gateViewModel: ProfileGateViewModel = hiltViewModel()
        val gateState by gateViewModel.state.collectAsStateWithLifecycle()

        if (gateState.isLoading) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { CircularProgressIndicator() }
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
                onProfileSelected = { gateViewModel.selectProfile(it) },
                onAddProfile = { gateViewModel.startAddProfile() },
                modifier = Modifier.fillMaxSize(),
            )
            return@ShowlyfinPhoneTheme
        }

        var currentDestination by remember { mutableStateOf<Destination>(Destination.Hlavni) }
        var bottomTab by remember { mutableStateOf<Destination>(Destination.Hlavni) }
        val context = LocalContext.current
        val naTvCoordinator: NaTvCoordinator = hiltViewModel()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            naTvCoordinator.messages.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        val onShareItem: (MediaItem) -> Unit = { item ->
            val slug = item.title.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .trim()
                .replace(Regex("\\s+"), "-")
            val mediaPath = if (item.type == MediaType.MOVIE) "movies" else "shows"
            val url = "https://trakt.tv/$mediaPath/$slug"
            val text = buildString {
                append(item.title)
                item.year?.let { append(" ($it)") }
                append('\n')
                append(url)
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, item.title)
            }
            context.startActivity(Intent.createChooser(intent, "Sdílet").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
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

        val density = LocalDensity.current
        val measuredBarHeightPx = remember { mutableFloatStateOf(with(density) { 80.dp.toPx() }) }
        val bottomBarOffsetPx = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(currentDestination) { bottomBarOffsetPx.floatValue = 0f }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val newOffset = (bottomBarOffsetPx.floatValue - available.y).coerceIn(0f, measuredBarHeightPx.floatValue)
                    bottomBarOffsetPx.floatValue = newOffset
                    return Offset.Zero
                }
            }
        }

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
                else -> bottomTab
            }
        }

        Scaffold(
            containerColor = Color(0xFF0D0D1A),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            val effectiveBottomDp = if (isSubScreen) {
                0.dp
            } else {
                with(density) { (measuredBarHeightPx.floatValue - bottomBarOffsetPx.floatValue).coerceAtLeast(0f).toDp() }
            }
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .consumeWindowInsets(paddingValues)
            ) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .padding(bottom = effectiveBottomDp)
                ) {
            when (val dest = currentDestination) {
                is Destination.Hlavni -> MainScreen(
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
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.Detail -> DetailScreen(
                    item = dest.item,
                    onBack = { currentDestination = dest.parent },
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
                    onShare = onShareItem,
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
                is Destination.Player -> PlaybackScreen(
                    itemId = dest.itemId ?: "",
                    externalUrl = dest.externalUrl,
                    externalTitle = dest.title,
                    subtitleQuery = dest.subtitleQuery,
                    onBack = { currentDestination = dest.parent },
                )
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

                if (!isSubScreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { measuredBarHeightPx.floatValue = it.height.toFloat() }
                            .offset { IntOffset(0, bottomBarOffsetPx.floatValue.roundToInt()) },
                    ) {
                        NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                            NavigationBarItem(
                                selected = bottomTab is Destination.Hlavni,
                                onClick = { bottomTab = Destination.Hlavni; currentDestination = Destination.Hlavni },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Hlavní") },
                            )
                            NavigationBarItem(
                                selected = bottomTab is Destination.Uploader,
                                onClick = { bottomTab = Destination.Uploader; currentDestination = Destination.Uploader },
                                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                label = { Text("Uploader") },
                            )
                            NavigationBarItem(
                                selected = bottomTab is Destination.Settings,
                                onClick = { bottomTab = Destination.Settings; currentDestination = Destination.Settings },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Nastavení") },
                            )
                        }
                    }
                }
            }
        }
    }
}
