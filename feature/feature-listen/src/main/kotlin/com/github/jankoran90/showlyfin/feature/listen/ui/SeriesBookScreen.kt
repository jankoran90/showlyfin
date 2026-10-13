package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookChapter
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookViewModel

/**
 * Slovo 2026-10-13 (user „Po jedné stopě, Trabantem, Ďábelské káry jako audioknihy — kapitoly", vzor
 * obrazovka knihy „Pohádky z vesmíru") — série dílů jako kniha: obálka, autor (= pořad), délka a počet
 * kapitol, Stáhnout vše, Přehrát/Pokračovat, pod tím kapitoly = díly. [chapters] jsou už v pořadí knihy.
 * [wideCover] = 16:9 obrázky (YouTube/ČT) nad hlavičkou místo čtvercové obálky vedle ní.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesBookScreen(
    title: String,
    author: String?,
    cover: String?,
    chapters: List<SeriesBookChapter>,
    onBack: () -> Unit,
    onOpenAudioPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    wideCover: Boolean = false,
    highlightKey: String? = null,
    /** Ruční kniha → tužka v liště (upravit / zrušit knihu). */
    onEdit: (() -> Unit)? = null,
    /** Admin: schválení série dětem (dřív jen dlouhý stisk na kartě série, ta se u pořadu o jedné sérii přeskočí). */
    kidsVisible: Boolean? = null,
    onToggleKids: (visible: Boolean) -> Unit = {},
    viewModel: SeriesBookViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    // Pozice/stažení čte VM synchronně (progressOf) — sběr flowů tu jen vynutí překreslení při změně.
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    val videoMarks by viewModel.videoResumeMarks.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    var menuChapter by remember { mutableStateOf<SeriesBookChapter?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val continueIdx = remember(chapters, resumeMarks, videoMarks, playerState.currentEpisodeId) { viewModel.continueIndex(chapters) }
    val anyProgress = remember(chapters, resumeMarks, videoMarks) { chapters.any { viewModel.progressOf(it.key) != null } }
    val totalSec = chapters.sumOf { it.durationSec }
    val years = chapters.mapNotNull { PodcastTimelineViewModel.parseEpisodeDate(it.date) }
        .map { java.util.Calendar.getInstance().apply { timeInMillis = it }.get(java.util.Calendar.YEAR) }
        .distinct().sorted()
    val yearLabel = when {
        years.isEmpty() -> null
        years.size == 1 -> "${years.first()}"
        else -> "${years.first()}–${years.last()}"
    }

    val listState = rememberLazyListState()
    LaunchedEffect(highlightKey, chapters) {
        // Prvních pár kapitol se vejde pod hlavičku → nescrolluj (Pokračovat/Stáhnout zůstanou vidět);
        // dál nech nad zvýrazněnou jednu kapitolu pro orientaci (emulátor 2026-10-13).
        val idx = chapters.indexOfFirst { it.key == highlightKey }
        if (idx >= 4) listState.scrollToItem(idx + HEADER_ITEMS - 1)
    }

    ListenExpressiveTheme {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") }
                    },
                    actions = {
                        if (kidsVisible != null) {
                            IconButton(onClick = { onToggleKids(!kidsVisible) }) {
                                Icon(
                                    Icons.Default.ChildCare,
                                    contentDescription = if (kidsVisible) "Skrýt dětem" else "Zobrazit dětem",
                                    tint = if (kidsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (onEdit != null) {
                            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Upravit knihu") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { pad ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = pad.calculateTopPadding() + 12.dp,
                    bottom = pad.calculateBottomPadding() + 96.dp,
                ),
            ) {
                item(key = "header") {
                    Column {
                        if (wideCover && cover != null) {
                            AsyncImage(
                                model = cover,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                            )
                        }
                        Row(Modifier.padding(top = if (wideCover) 12.dp else 0.dp)) {
                            if (!wideCover) {
                                Box(
                                    Modifier.size(140.dp).clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    if (cover != null) {
                                        AsyncImage(model = cover, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                            Column(
                                Modifier.weight(1f).padding(start = if (wideCover) 0.dp else 16.dp)
                                    .then(if (wideCover) Modifier else Modifier.height(140.dp)),
                                verticalArrangement = if (wideCover) Arrangement.Top else Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    author?.let {
                                        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    val meta = listOfNotNull(yearLabel, DownloadFormat.duration(totalSec).takeIf { totalSec > 0 }, "${chapters.size} kap.")
                                        .joinToString(" · ")
                                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                                }
                                SeriesBookDownloadRow(
                                    chapters = chapters,
                                    states = offlineStates,
                                    onDownloadAll = { viewModel.downloadAll(chapters) },
                                    onCancel = { viewModel.cancelDownloads(chapters) },
                                    onDeleteAll = { confirmDeleteAll = true },
                                    modifier = Modifier.padding(top = if (wideCover) 8.dp else 0.dp),
                                )
                            }
                        }
                    }
                }
                item(key = "play") {
                    val target = chapters.getOrNull(continueIdx)
                    val p = target?.let { viewModel.progressOf(it.key) }
                    val isCurrent = target != null && playerState.isActive && playerState.currentEpisodeId == target.key
                    val label = when {
                        isCurrent && playerState.isPlaying -> "Hraje: ${target!!.title}"
                        anyProgress && target != null -> "Pokračovat: ${target.title}"
                        else -> "Přehrát"
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (isCurrent && playerState.isPlaying) onOpenAudioPlayer()
                                else { viewModel.playFrom(chapters, continueIdx); onOpenAudioPlayer() }
                            },
                            enabled = chapters.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(label, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (anyProgress) {
                            FilledTonalButton(onClick = { viewModel.playFromStart(chapters); onOpenAudioPlayer() }) {
                                Icon(Icons.Default.Replay, contentDescription = "Od začátku", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (p != null && !p.isFinished && p.durMs > 0 && !isCurrent) {
                        Text(
                            DownloadFormat.eta((p.durMs - p.posMs).coerceAtLeast(0L) / 1000).replace("zbývá", "Z kapitoly zbývá"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                item(key = "chaptersTitle") {
                    Text(
                        "Kapitoly",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(chapters, key = { _, ch -> ch.key }) { idx, ch ->
                    SeriesBookChapterRow(
                        number = idx + 1,
                        chapter = ch,
                        progress = viewModel.progressOf(ch.key),
                        offline = offlineStates[ch.key],
                        isCurrent = playerState.isActive && playerState.currentEpisodeId == ch.key,
                        isPlaying = playerState.isPlaying,
                        highlighted = ch.key == highlightKey,
                        onClick = { viewModel.playFrom(chapters, idx); onOpenAudioPlayer() },
                        onMore = { menuChapter = ch },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        }
    }

    menuChapter?.let { ch ->
        val idx = chapters.indexOfFirst { it.key == ch.key }
        val p = viewModel.progressOf(ch.key)
        val dl = offlineStates[ch.key]?.status
        ListenEpisodeActionSheet(
            title = ch.title,
            actions = listOfNotNull(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát od této kapitoly") {
                    viewModel.playFrom(chapters, idx); onOpenAudioPlayer()
                },
                ch.onVideo?.let { v -> ListenEpisodeAction(Icons.Default.OndemandVideo, "Přehrát video", onClick = v) },
                when {
                    ch.download == null -> null
                    dl == OfflineStatus.DOWNLOADED -> ListenEpisodeAction(
                        Icons.Default.Delete, "Smazat z telefonu", confirmMessage = "Stažený díl se smaže z telefonu.",
                    ) { viewModel.deleteDownload(ch) }
                    dl == OfflineStatus.QUEUED || dl == OfflineStatus.DOWNLOADING ->
                        ListenEpisodeAction(Icons.Default.Close, "Zrušit stahování") { viewModel.cancelDownloads(listOf(ch)) }
                    else -> ListenEpisodeAction(Icons.Default.Download, "Stáhnout do telefonu") { viewModel.download(ch) }
                },
                if (p?.isFinished != true) {
                    ListenEpisodeAction(Icons.Default.CheckCircle, "Označit jako poslechnuté") { viewModel.markFinished(ch) }
                } else null,
                if (p != null) {
                    ListenEpisodeAction(
                        Icons.Default.Close, "Ukončit poslech",
                        confirmMessage = "Smaže se uložená pozice poslechu této kapitoly.",
                    ) { viewModel.resetPosition(ch) }
                } else null,
            ),
            onDismiss = { menuChapter = null },
        )
    }

    if (confirmDeleteAll) {
        ListenEpisodeActionSheet(
            title = "Smazat staženou knihu?",
            infoLine = "Smažou se všechny stažené kapitoly této série z telefonu.",
            actions = listOf(
                ListenEpisodeAction(Icons.Default.Delete, "Smazat všechny stažené kapitoly") { viewModel.deleteAllDownloads(chapters) },
            ),
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

/** Počet položek LazyColumn před první kapitolou (hlavička, tlačítka, nadpis) — pro scroll na zvýrazněnou. */
private const val HEADER_ITEMS = 3
