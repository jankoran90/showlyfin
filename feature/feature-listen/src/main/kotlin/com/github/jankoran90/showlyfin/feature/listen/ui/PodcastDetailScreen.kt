package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.abs.model.DownloadState
import com.github.jankoran90.showlyfin.data.abs.model.DownloadStatus
import com.github.jankoran90.showlyfin.data.abs.model.FeedEpisode
import com.github.jankoran90.showlyfin.data.abs.model.PodcastDetail
import com.github.jankoran90.showlyfin.data.abs.model.PodcastEpisode
import com.github.jankoran90.showlyfin.feature.listen.FindEpisodesState
import com.github.jankoran90.showlyfin.feature.listen.PodcastDetailViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.QueuedEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlayEpisode: (itemId: String, episodeId: String, fromStart: Boolean, startSec: Double?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val playingEpisodeId = playerState.currentEpisodeId?.takeIf { playerState.isActive }
    val quickAction = viewModel.episodeQuickAction
    val display = viewModel.episodeDisplay
    val deviceAutoDownloadOn by viewModel.deviceAutoDownloadOn.collectAsStateWithLifecycle()
    val deviceAutoDownloadSelective = viewModel.deviceAutoDownloadSelective
    val findState by viewModel.findState.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { viewModel.load(itemId) }

    // Výsledek stažení na server → snackbar/toast.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(findState.resultMessage) {
        findState.resultMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeFindResult()
        }
    }

    var actionEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    ListenExpressiveTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(state.detail?.podcast?.title ?: "Podcast", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { pad ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.detail != null -> DetailContent(
                    detail = state.detail!!,
                    queueSize = queue.size,
                    downloadStates = downloadStates,
                    playingEpisodeId = playingEpisodeId,
                    quickAction = quickAction,
                    display = display,
                    deviceAutoDownloadSelective = deviceAutoDownloadSelective,
                    deviceAutoDownloadOn = deviceAutoDownloadOn,
                    canManageEpisodes = viewModel.canManageEpisodes,
                    onToggleDeviceAutoDownload = { viewModel.toggleDeviceAutoDownload() },
                    onFindEpisodes = { viewModel.openFindEpisodes() },
                    onPlay = { ep -> onPlayEpisode(itemId, ep.id, false, null) },
                    onLongPress = { ep -> actionEpisode = ep },
                    onOpenQueue = { showQueue = true },
                    onDownload = { ep -> viewModel.downloadEpisode(ep) },
                    onCancelDownload = { ep -> viewModel.cancelDownload(ep.id) },
                    onEnqueueEnd = { ep -> viewModel.enqueue(ep, atFront = false) },
                    onEnqueueNext = { ep -> viewModel.enqueue(ep, atFront = true) },
                    modifier = Modifier.fillMaxSize().padding(pad),
                )
            }
        }
    }

    // Long-press menu epizody
    actionEpisode?.let { ep ->
        EpisodeActionSheet(
            episode = ep,
            downloadStatus = (downloadStates[ep.id] ?: DownloadState()).status,
            onDismiss = { actionEpisode = null },
            onPlay = { onPlayEpisode(itemId, ep.id, false, null); actionEpisode = null },
            onToggleFinished = { viewModel.setEpisodeFinished(ep, !ep.isFinished); actionEpisode = null },
            onEnqueueNext = { viewModel.enqueue(ep, atFront = true); actionEpisode = null },
            onEnqueueEnd = { viewModel.enqueue(ep, atFront = false); actionEpisode = null },
            onDownload = { viewModel.downloadEpisode(ep); actionEpisode = null },
            onCancelDownload = { viewModel.cancelDownload(ep.id); actionEpisode = null },
            onDeleteDownload = { viewModel.deleteDownload(ep.id); actionEpisode = null },
        )
    }

    // Správa fronty
    if (showQueue) {
        QueueSheet(
            queue = queue,
            playingEpisodeId = playingEpisodeId,
            display = display,
            onDismiss = { showQueue = false },
            onPlay = { viewModel.playQueued(it); showQueue = false },
            onRemove = { viewModel.removeFromQueue(it) },
            onClear = { viewModel.clearAll(); showQueue = false },
        )
    }

    // „Prohledat epizody" — dostupné RSS epizody k stažení na ABS server
    if (findState.visible) {
        FindEpisodesSheet(
            state = findState,
            display = display,
            onDismiss = { viewModel.closeFindEpisodes() },
            onToggle = { viewModel.toggleFindSelection(it) },
            onSelectAll = { viewModel.selectAllFind() },
            onClearSelection = { viewModel.clearFindSelection() },
            onConfirm = { viewModel.downloadSelectedToServer() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: PodcastDetail,
    queueSize: Int,
    downloadStates: Map<String, DownloadState>,
    playingEpisodeId: String?,
    quickAction: Int,
    display: EpisodeDisplaySettings,
    deviceAutoDownloadSelective: Boolean,
    deviceAutoDownloadOn: Boolean,
    canManageEpisodes: Boolean,
    onToggleDeviceAutoDownload: () -> Unit,
    onFindEpisodes: () -> Unit,
    onPlay: (PodcastEpisode) -> Unit,
    onLongPress: (PodcastEpisode) -> Unit,
    onOpenQueue: () -> Unit,
    onDownload: (PodcastEpisode) -> Unit,
    onCancelDownload: (PodcastEpisode) -> Unit,
    onEnqueueEnd: (PodcastEpisode) -> Unit,
    onEnqueueNext: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val podcast = detail.podcast
    var descExpanded by remember { mutableStateOf(false) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp)) {
        item {
            // Hlavička: cover vlevo, info vpravo VYPLNÍ výšku coveru (název je jen v app-baru).
            Row {
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (podcast.coverUrl != null) {
                        AsyncImage(
                            model = podcast.coverUrl,
                            contentDescription = podcast.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .height(120.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        podcast.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val meta = buildList {
                            add("${podcast.numEpisodes} epizod")
                            if (podcast.numUnfinished > 0) add("${podcast.numUnfinished} nepřehraných")
                        }.joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    // „Prohledat epizody" = ABS upload-akce → jen admin profil (ostatní účty 403). Bug 2026-06-12.
                    if (canManageEpisodes) {
                        AssistChip(
                            onClick = onFindEpisodes,
                            label = { Text("Prohledat epizody") },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }

        // Řádek pod hlavičkou: chip Fronta (dřív „Prohledat epizody") + volitelně auto-download do telefonu.
        // (Auto-download na server je per-podcast přepínač přesunut do Nastavení → Poslech.)
        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (queueSize > 0) {
                    AssistChip(
                        onClick = onOpenQueue,
                        label = { Text("Fronta · $queueSize") },
                        leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                if (deviceAutoDownloadSelective) {
                    FilterChip(
                        selected = deviceAutoDownloadOn,
                        onClick = onToggleDeviceAutoDownload,
                        label = { Text("Auto do telefonu") },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        detail.description?.let { desc ->
            item {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                    maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clickable { descExpanded = !descExpanded },
                )
            }
        }

        item {
            Text(
                "Epizody",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
            )
        }
        items(detail.episodes, key = { it.id }) { ep ->
            EpisodeRow(
                ep,
                isCurrent = ep.id == playingEpisodeId,
                downloadState = downloadStates[ep.id] ?: DownloadState(),
                quickAction = quickAction,
                display = display,
                onDownload = { onDownload(ep) },
                onCancelDownload = { onCancelDownload(ep) },
                onEnqueueEnd = { onEnqueueEnd(ep) },
                onEnqueueNext = { onEnqueueNext(ep) },
                modifier = Modifier.combinedClickable(
                    onClick = { onPlay(ep) },
                    onLongClick = { onLongPress(ep) },
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: PodcastEpisode,
    isCurrent: Boolean,
    downloadState: DownloadState,
    quickAction: Int,
    display: EpisodeDisplaySettings,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onEnqueueEnd: () -> Unit,
    onEnqueueNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canResume = ep.currentTimeSec > 1.0 && !ep.isFinished
    val accent = MaterialTheme.colorScheme.primary
    // Subtilní styl jako seznam kapitol v detailu audioknihy: malá ikona, žádný velký avatar.
    Row(
        modifier
            .fillMaxWidth()
            .background(if (isCurrent) accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            when {
                isCurrent -> Icons.Default.GraphicEq
                ep.isFinished -> Icons.Default.CheckCircle
                else -> Icons.Default.PlayArrow
            },
            contentDescription = null,
            tint = if (ep.isFinished && !isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            // Host jako poutač NAD titulkem (když je rozpoznán).
            GuestBanner(ep.guest, display)
            Text(
                text = ep.title,
                style = episodeTitleStyle(display),
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
                maxLines = display.titleLines,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                formatDate(ep.publishedAt)?.let { add(it) }
                if (ep.durationSec > 0) add(formatEpisodeDuration(ep.durationSec))
                if (canResume) add("zbývá ${formatEpisodeDuration((ep.durationSec - ep.currentTimeSec).coerceAtLeast(0.0))}")
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Popis epizody (host je už jako poutač nad titulkem) — jednotně.
            EpisodeDescriptionText(description = ep.description, display = display)
            AnimatedVisibility(visible = canResume) {
                LinearProgressIndicator(
                    progress = { ep.progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        // Trailing rychlá akce dle nastavení (default = přidat do fronty na konec).
        val trailingMod = Modifier.padding(start = 8.dp)
        if (downloadState.status == DownloadStatus.DOWNLOADED || downloadState.status == DownloadStatus.DOWNLOADING) {
            // Probíhá/staženo → vždy ukaž stav stahování (jinak by uživatel neviděl progres).
            DownloadControl(state = downloadState, onDownload = onDownload, onCancel = onCancelDownload, modifier = trailingMod)
        } else when (quickAction) {
            1 -> IconButton(onClick = onEnqueueNext, modifier = trailingMod) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Přidat do fronty (další)", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            2 -> DownloadControl(state = downloadState, onDownload = onDownload, onCancel = onCancelDownload, modifier = trailingMod)
            else -> IconButton(onClick = onEnqueueEnd, modifier = trailingMod) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Přidat do fronty (na konec)", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Trailing badge stažení epizody: stáhnout / probíhá (klik = zrušit) / staženo / chyba (klik = znovu). */
@Composable
private fun DownloadControl(
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        when (state.status) {
            DownloadStatus.DOWNLOADED -> Icon(
                Icons.Default.DownloadDone,
                contentDescription = "Staženo (offline)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            DownloadStatus.DOWNLOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Zrušit stahování", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DownloadStatus.FAILED -> IconButton(onClick = onDownload) {
                Icon(Icons.Default.ErrorOutline, contentDescription = "Stažení selhalo — zkusit znovu", tint = MaterialTheme.colorScheme.error)
            }
            DownloadStatus.NONE -> IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Stáhnout pro offline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeActionSheet(
    episode: PodcastEpisode,
    downloadStatus: DownloadStatus,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFinished: () -> Unit,
    onEnqueueNext: () -> Unit,
    onEnqueueEnd: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            episode.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ActionRow(Icons.Default.PlayArrow, "Přehrát", onPlay)
        ActionRow(
            if (episode.isFinished) Icons.Outlined.Circle else Icons.Default.CheckCircle,
            if (episode.isFinished) "Označit jako nedokončené" else "Označit jako dokončené",
            onToggleFinished,
        )
        ActionRow(Icons.AutoMirrored.Filled.PlaylistPlay, "Přidat do fronty (další)", onEnqueueNext)
        ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Přidat do fronty (na konec)", onEnqueueEnd)
        when (downloadStatus) {
            DownloadStatus.DOWNLOADED ->
                ActionRow(Icons.Default.Delete, "Smazat stažení", onDeleteDownload)
            DownloadStatus.DOWNLOADING ->
                ActionRow(Icons.Default.Close, "Zrušit stahování", onCancelDownload)
            DownloadStatus.FAILED ->
                ActionRow(Icons.Default.Download, "Stáhnout znovu", onDownload)
            DownloadStatus.NONE ->
                ActionRow(Icons.Default.Download, "Stáhnout pro offline", onDownload)
        }
        Box(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<QueuedEpisode>,
    playingEpisodeId: String?,
    display: EpisodeDisplaySettings,
    onDismiss: () -> Unit,
    onPlay: (QueuedEpisode) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Fronta · ${queue.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Vymazat vše") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (queue.isEmpty()) {
            Text(
                "Fronta je prázdná.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn {
                items(queue, key = { it.episodeId }) { q ->
                    val isCur = q.episodeId == playingEpisodeId
                    val accent = MaterialTheme.colorScheme.primary
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isCur) accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPlay(q) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCur) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "Hraje", tint = accent, modifier = Modifier.size(18.dp).padding(end = 8.dp, top = 2.dp).align(Alignment.Top))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Přehrát", tint = accent, modifier = Modifier.size(18.dp).padding(end = 8.dp, top = 2.dp).align(Alignment.Top))
                        }
                        Column(Modifier.weight(1f)) {
                            GuestBanner(q.guest, display)
                            Text(
                                q.title,
                                style = episodeTitleStyle(display),
                                color = if (isCur) accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = display.titleLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                            q.podcastTitle?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            EpisodeDescriptionText(description = q.description, display = display)
                        }
                        IconButton(onClick = { onRemove(q.episodeId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Odebrat z fronty", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

/** Sheet „Prohledat epizody": dostupné RSS epizody (nestažené na serveru) + multi-select → stáhnout na server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindEpisodesSheet(
    state: FindEpisodesState,
    display: EpisodeDisplaySettings,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Prohledat epizody", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Dostupné v RSS, zatím nestažené na serveru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.episodes.isNotEmpty()) {
                val allSelected = state.selectedIds.size == state.episodes.size
                TextButton(onClick = { if (allSelected) onClearSelection() else onSelectAll() }) {
                    Text(if (allSelected) "Zrušit výběr" else "Vybrat vše")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        when {
            state.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Text(
                state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp),
            )
            state.episodes.isEmpty() -> Text(
                "Žádné nové epizody — server má všechny z feedu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(state.episodes, key = { it.id }) { ep ->
                    val checked = ep.id in state.selectedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(ep.id) }
                            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(ep.id) })
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            GuestBanner(ep.guest, display)
                            Text(
                                ep.title,
                                style = episodeTitleStyle(display),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = display.titleLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildList {
                                formatDate(ep.publishedAt)?.let { add(it) }
                                ep.durationSec?.takeIf { it > 0 }?.let { add(formatEpisodeDuration(it)) }
                            }.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            EpisodeDescriptionText(description = ep.description, display = display)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        if (state.episodes.isNotEmpty()) {
            Button(
                onClick = onConfirm,
                enabled = state.selectedIds.isNotEmpty() && !state.submitting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Stáhnout na server (${state.selectedIds.size})")
                }
            }
        }
        Box(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatDate(ms: Long?): String? {
    if (ms == null || ms <= 0L) return null
    return runCatching {
        SimpleDateFormat("d. M. yyyy", Locale("cs")).format(Date(ms))
    }.getOrNull()
}

private fun formatEpisodeDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min"
        else -> "<1 min"
    }
}
