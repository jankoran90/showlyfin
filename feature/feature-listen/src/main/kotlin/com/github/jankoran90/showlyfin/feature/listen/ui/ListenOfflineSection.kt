package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.data.abs.model.EpisodeDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.feature.listen.ListenViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Offline vrstva sekce Poslech — vyříznuto z ListenScreen.kt (anti-monolit). Banner, správa stažení
// (ABS + RSS/YouTube), a offline grid/detail stažených podcastů. Symboly sdílené se shellem = `internal`.

/** Plan CASTAWAY — proužek „Offline", když není síť; vysvětlí, proč jsou vidět jen stažené věci. */
@Composable
internal fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Offline — zobrazeny jen stažené položky.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Správa offline stažení Poslechu: ABS epizody + stažené RSS/YouTube podcasty (LEVER L3) v jednom
 * seznamu — klik = přehrát offline, koš = smazat, „Smazat vše" = obojí (NE filmy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsSheet(
    downloads: List<EpisodeDownload>,
    podcastDownloads: List<OfflineDownload>,
    onDismiss: () -> Unit,
    onPlay: (EpisodeDownload) -> Unit,
    onPlayPodcast: (OfflineDownload) -> Unit,
    onDelete: (String) -> Unit,
    onDeletePodcast: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val total = downloads.size + podcastDownloads.size
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Stažené · $total",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (total > 0) {
                TextButton(onClick = onDeleteAll) { Text("Smazat vše") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (total == 0) {
            Text(
                "Žádné stažené epizody.\nStáhni je v detailu podcastu přes ⋮ → Stáhnout do telefonu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(Modifier.height(420.dp)) {
                columnItems(downloads, key = { "abs_${it.episodeId}" }) { dl ->
                    val meta = buildList {
                        dl.podcastTitle?.let { add(it) }
                        if (dl.sizeBytes > 0) add(formatSize(dl.sizeBytes))
                    }.joinToString(" · ")
                    DownloadEntryRow(dl.title, meta, onPlay = { onPlay(dl) }, onDelete = { onDelete(dl.episodeId) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                columnItems(podcastDownloads, key = { "off_${it.key}" }) { dl ->
                    val meta = buildList {
                        dl.subtitle?.let { add(it) }
                        add(dl.sourceLabel)
                        if (dl.sizeBytes > 0) add(formatSize(dl.sizeBytes))
                    }.joinToString(" · ")
                    DownloadEntryRow(dl.title, meta, onPlay = { onPlayPodcast(dl) }, onDelete = { onDeletePodcast(dl.key) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun DownloadEntryRow(
    title: String,
    meta: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Smazat stažení", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}

/** WEFT (SHW-75/W6): offline pořad = stažené epizody seskupené pod jednu kartu (parita s Audioknihy grid). */
private data class OfflinePodcastShow(
    val title: String,
    val poster: String?,
    val sourceLabel: String,
    val episodes: List<OfflineDownload>,
)

private fun episodesWord(n: Int): String = when {
    n == 1 -> "epizoda"
    n in 2..4 -> "epizody"
    else -> "epizod"
}

/**
 * Offline sekce Podcasty — stažené epizody jako GRID KARET POŘADŮ (parita s Audioknihy/Sledované grid),
 * NE plochý seznam. Karta = jeden pořad (obálka + počet stažených), klik → [OfflinePodcastDetailScreen]
 * se staženými epizodami toho pořadu. Nahrazuje online taby (Timeline/Sledované/Objev), které bez sítě
 * nemají data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineDownloadedPodcasts(
    podcastDownloads: List<OfflineDownload>,
    viewModel: ListenViewModel,
) {
    if (podcastDownloads.isEmpty()) {
        CenteredMessage(
            "Jsi offline a nemáš žádné stažené epizody.\nStáhni je v detailu podcastu přes ⋮ → Stáhnout do telefonu, dokud jsi připojený.",
        )
        return
    }
    // Seskup stažené epizody dle pořadu (subtitle = název pořadu; fallback zdroj) → jedna karta na pořad.
    // RESONANCE (SHW-81): epizody NEJNOVĚJŠÍ NAHOŘE dle data vydání (fallback datum stažení u starých).
    val shows = remember(podcastDownloads) {
        podcastDownloads
            .groupBy { it.subtitle?.takeIf { s -> s.isNotBlank() } ?: it.sourceLabel }
            .map { (title, eps) ->
                OfflinePodcastShow(
                    title = title,
                    poster = eps.firstNotNullOfOrNull { it.posterPath ?: it.posterUrl },
                    sourceLabel = eps.first().sourceLabel,
                    episodes = eps.sortedByDescending { it.publishedAt ?: it.addedAt },
                )
            }
            .sortedBy { it.title.lowercase(Locale("cs")) }
    }
    var openShow by remember { mutableStateOf<OfflinePodcastShow?>(null) }
    var highlightKey by remember { mutableStateOf<String?>(null) }

    // RESONANCE (SHW-81): proklik z přehrávače (offline epizoda) → otevři kartu pořadu + zvýrazni epizodu.
    val requested by viewModel.requestedOfflineShow.collectAsStateWithLifecycle()
    LaunchedEffect(requested, shows) {
        requested?.let { (title, epKey) ->
            shows.firstOrNull { it.title == title }?.let { openShow = it; highlightKey = epKey }
            viewModel.consumeOfflineRequest()
        }
    }

    // Grid + detail v jednom Boxu → detail (Scaffold, neprůhledné pozadí) PŘEKRYJE grid. Kdyby byl
    // detail sourozencem gridu v Column, `fillMaxSize` grid by mu nenechal výšku (0 px = „nic se neděje").
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shows, key = { it.title }) { show ->
                OfflinePodcastCard(show = show, onClick = { openShow = show })
            }
        }

        openShow?.let { opened ->
            // Živý přemap (po smazání epizody ať se detail zaktualizuje / zavře, když pořad zmizí).
            val live = shows.firstOrNull { it.title == opened.title }
            LaunchedEffect(live) { if (live == null) openShow = null }
            if (live != null) {
                OfflinePodcastDetailScreen(
                    show = live,
                    viewModel = viewModel,
                    highlightEpisodeKey = highlightKey,
                    onBack = { openShow = null; highlightKey = null },
                )
            }
        }
    }
}

/** WEFT (SHW-75/W6): karta staženého pořadu — CHORUS Osa 2 delegát nad [CoverCard] (badge počtu). */
@Composable
private fun OfflinePodcastCard(
    show: OfflinePodcastShow,
    onClick: () -> Unit,
) {
    CoverCard(
        title = show.title,
        subtitle = "${show.sourceLabel} · ${show.episodes.size} ${episodesWord(show.episodes.size)}",
        imageUrl = show.poster,
        onClick = onClick,
        placeholder = Icons.Default.Podcasts,
        overlay = {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(show.episodes.size.toString())
            }
        },
    )
}

/**
 * RESONANCE (SHW-81): offline detail pořadu = PARITA s online vzhledem. Plná obrazitka (TopAppBar
 * s názvem pořadu, cover + zdroj + počet), epizody přes sdílený [PodcastEpisodeRow] (obrázek + název
 * + datum + popis + [Poslech/Pokračovat] + [⋮]). Nejnovější nahoře. Koš NENÍ v popředí → jen v ⋮ menu
 * (Přehrát · Smazat z telefonu). Zvýrazní + resume právě hranou epizodu (`playerState`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflinePodcastDetailScreen(
    show: OfflinePodcastShow,
    viewModel: ListenViewModel,
    highlightEpisodeKey: String? = null,
    onBack: () -> Unit,
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val playingKey = playerState.currentEpisodeId?.takeIf { playerState.isActive }
    var actionEp by remember { mutableStateOf<OfflineDownload?>(null) }
    var newestFirst by remember { mutableStateOf(viewModel.offlinePodcastNewestFirst) }
    // show.episodes je defaultně nejnovější nahoře (publishedAt?:addedAt); obrácení = reversed.
    val episodes = remember(show.episodes, newestFirst) {
        if (newestFirst) show.episodes else show.episodes.reversed()
    }
    val listState = rememberLazyListState()
    // Proklik z přehrávače: doskroluj na zvýrazněnou/hranou epizodu (+2 kvůli hlavičce a nadpisu „Epizody").
    LaunchedEffect(highlightEpisodeKey, episodes) {
        val key = highlightEpisodeKey ?: playingKey
        if (key != null) {
            val idx = episodes.indexOfFirst { it.key == key }
            if (idx >= 0) listState.scrollToItem((idx + 2).coerceAtMost(episodes.size + 1))
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(show.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        newestFirst = !newestFirst
                        viewModel.offlinePodcastNewestFirst = newestFirst
                    }) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = if (newestFirst) "Řazení: nejnovější nahoře (ťuk = obrátit)"
                            else "Řazení: nejstarší nahoře (ťuk = obrátit)",
                        )
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
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Row {
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (show.poster != null) {
                            AsyncImage(
                                model = show.poster,
                                contentDescription = show.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Column(
                        Modifier.padding(start = 16.dp).height(120.dp).weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            show.sourceLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${show.episodes.size} ${episodesWord(show.episodes.size)} · staženo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
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
            columnItems(episodes, key = { "off_${it.key}" }) { dl ->
                val isCurrent = dl.key == playingKey
                // Progress: hraje-li → z přehrávače; jinak z uložené resume pozice stažené epizody.
                val progress: Float? = when {
                    isCurrent && playerState.durationMs > 0 ->
                        (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                    dl.resumePositionMs > 0 && dl.durationSec > 0 ->
                        (dl.resumePositionMs / 1000f / dl.durationSec.toFloat()).coerceIn(0f, 1f)
                    else -> null
                }
                PodcastEpisodeRow(
                    title = dl.title,
                    image = dl.posterPath ?: dl.posterUrl,
                    date = msToIso(dl.publishedAt),
                    duration = dl.durationSec.takeIf { it > 0 }?.toLong()?.toString(),
                    description = dl.description,
                    downloaded = false, // v offline detailu je vše staženo → ikona redundantní
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playerState.isPlaying,
                    progress = progress,
                    canResume = !isCurrent && dl.resumePositionMs > 0,
                    remainingLabel = null,
                    hasVideo = false, // offline podcast = zatím jen audio
                    highlighted = dl.key == highlightEpisodeKey,
                    onPlay = { viewModel.playOfflinePodcast(dl) },
                    onVideo = {},
                    onMore = { actionEp = dl },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }

    actionEp?.let { dl ->
        ListenEpisodeActionSheet(
            title = dl.title,
            actions = listOf(
                ListenEpisodeAction(Icons.Default.PlayArrow, "Přehrát") {
                    viewModel.playOfflinePodcast(dl); actionEp = null
                },
                ListenEpisodeAction(Icons.Default.Delete, "Smazat z telefonu") {
                    viewModel.deleteOfflinePodcast(dl.key); actionEp = null
                },
            ),
            onDismiss = { actionEp = null },
        )
    }
}

/** RESONANCE (SHW-81): epoch ms → "yyyy-MM-dd" pro [formatRssDate] v řádku epizody (jednotné datum). */
private fun msToIso(ms: Long?): String? {
    if (ms == null || ms <= 0L) return null
    return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms)) }.getOrNull()
}
