package com.github.jankoran90.showlyfin.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.data.uploader.model.LibraryItem
import com.github.jankoran90.showlyfin.feature.detail.ui.DetailScreen
import com.github.jankoran90.showlyfin.feature.discover.ui.DiscoverScreen
import com.github.jankoran90.showlyfin.feature.jellyfin.ui.JellyfinBrowserScreen
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
import com.github.jankoran90.showlyfin.feature.watchlist.ui.WatchlistScreen

private sealed interface Destination {
    // Bottom tabs
    data object Discover : Destination
    data object Watchlist : Destination
    data object Jellyfin : Destination
    data object Uploader : Destination
    data object Settings : Destination

    // Sub-screens
    data class Detail(val item: MediaItem) : Destination
    data class SmartDetect(val imdbId: String, val title: String, val titleCs: String, val year: Int?, val mediaType: String) : Destination
    data class ReviewStep(val sid: String, val fid: String, val filename: String) : Destination
    data class MoveStep(val sid: String) : Destination
    data object LibraryBrowser : Destination
    data class LibraryDetail(val library: String, val item: LibraryItem) : Destination
    data class RemuxPicker(val library: String, val folder: String) : Destination
    data class RemuxProgress(val jobId: String, val folder: String) : Destination
    data object RemuxHistory : Destination
    data class JellyfinLibrary(val libraryId: String, val libraryName: String) : Destination
    data class JellyfinPlayback(val itemId: String, val libraryId: String, val libraryName: String) : Destination
}

private val bottomTabs = listOf(
    Destination.Discover, Destination.Watchlist, Destination.Jellyfin, Destination.Uploader, Destination.Settings,
)

@Composable
fun ShowlyfinPhoneApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var currentDestination by remember { mutableStateOf<Destination>(Destination.Discover) }
        var bottomTab by remember { mutableStateOf<Destination>(Destination.Discover) }

        val isSubScreen = currentDestination !in bottomTabs

        BackHandler(enabled = isSubScreen) {
            val current = currentDestination
            currentDestination = when (current) {
                is Destination.ReviewStep, is Destination.MoveStep -> Destination.Uploader
                is Destination.LibraryDetail -> Destination.LibraryBrowser
                is Destination.LibraryBrowser -> Destination.Uploader
                is Destination.RemuxPicker -> Destination.LibraryBrowser
                is Destination.RemuxProgress -> Destination.LibraryBrowser
                is Destination.RemuxHistory -> Destination.Uploader
                is Destination.JellyfinLibrary -> Destination.Jellyfin
                is Destination.JellyfinPlayback -> Destination.JellyfinLibrary(current.libraryId, current.libraryName)
                else -> bottomTab
            }
        }

        Scaffold(
            containerColor = Color(0xFF0D0D1A),
            bottomBar = {
                if (!isSubScreen) {
                    NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                        NavigationBarItem(
                            selected = bottomTab is Destination.Discover,
                            onClick = { bottomTab = Destination.Discover; currentDestination = Destination.Discover },
                            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            label = { Text("Discover") },
                        )
                        NavigationBarItem(
                            selected = bottomTab is Destination.Watchlist,
                            onClick = { bottomTab = Destination.Watchlist; currentDestination = Destination.Watchlist },
                            icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                            label = { Text("Watchlist") },
                        )
                        NavigationBarItem(
                            selected = bottomTab is Destination.Jellyfin,
                            onClick = { bottomTab = Destination.Jellyfin; currentDestination = Destination.Jellyfin },
                            icon = { Icon(Icons.Default.Tv, contentDescription = null) },
                            label = { Text("Jellyfin") },
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
            },
        ) { paddingValues ->
            when (val dest = currentDestination) {
                is Destination.Discover -> DiscoverScreen(
                    onItemClick = { item -> bottomTab = Destination.Discover; currentDestination = Destination.Detail(item) },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Watchlist -> WatchlistScreen(
                    onItemClick = { item -> bottomTab = Destination.Watchlist; currentDestination = Destination.Detail(item) },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Jellyfin -> JellyfinBrowserScreen(
                    onLibraryClick = { libraryId, libraryName ->
                        currentDestination = Destination.JellyfinLibrary(libraryId, libraryName)
                    },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.JellyfinLibrary -> JellyfinLibraryItemsScreen(
                    libraryId = dest.libraryId,
                    libraryName = dest.libraryName,
                    onBack = { currentDestination = Destination.Jellyfin },
                    onItemPlay = { itemId ->
                        currentDestination = Destination.JellyfinPlayback(itemId, dest.libraryId, dest.libraryName)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.JellyfinPlayback -> PlaybackScreen(
                    itemId = dest.itemId,
                    onBack = {
                        currentDestination = Destination.JellyfinLibrary(dest.libraryId, dest.libraryName)
                    },
                )
                is Destination.Uploader -> UploaderScreen(
                    onOpenReviewStep = { sid, fid, filename ->
                        currentDestination = Destination.ReviewStep(sid, fid, filename)
                    },
                    onOpenMoveStep = { sid -> currentDestination = Destination.MoveStep(sid) },
                    onOpenLibrary = { currentDestination = Destination.LibraryBrowser },
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Settings -> SettingsScreen(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
                is Destination.Detail -> DetailScreen(
                    item = dest.item,
                    onBack = { currentDestination = bottomTab },
                    onSmartDetect = { item ->
                        currentDestination = Destination.SmartDetect(
                            imdbId = item.imdbId ?: "",
                            title = item.title,
                            titleCs = item.title,
                            year = item.year,
                            mediaType = item.type.name.lowercase(),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
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
    }
}
