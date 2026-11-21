package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.SourceEpisode
import com.github.jankoran90.showlyfin.feature.listen.PodcastSearchViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.choosePlaybackResume
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * TRAWL (Slovo, 2026-09-02): fulltext hledání epizod. FOCUS (2026-09-03, user „hlavně bych měl hledat
 * na jedním zdrojem, ne plošně") — primárně otevíráno SCOPED na jeden zdroj (lupa v obrazovce YouTube
 * kanálu / RSS / Na Výbornou, nahrazuje starý pevný textový filtr); [scopeSource] = null zachovává
 * původní chování (napříč VŠEMI sledovanými zdroji, FAB na Timeline/Sledované jako doplněk).
 * YouTube = živě celá historie kanálu, RSS/NaVýbornou = velký fetch + klientský filtr. „Poslech"
 * PŘEHRAJE audio (sdílený resume klíč se zdrojovou obrazovkou); „Video" (2026-09-04, user screenshot
 * — chybělo, hledání nabízelo jen audio) se zobrazí jen u YouTube/ČT výsledků, parita se zdrojovou
 * obrazovkou (RSS video jde jen přes jfItemId, ten hledání nenese).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scopeSource: PodcastSource? = null,
    scopeLabel: String? = null,
    /** SLOVO-KIDS-EPISODE: dětský profil = video volba se v hledání ani nenabídne (parita se
     *  zdrojovou obrazovkou YoutubeChannel/CtvProgram, `showVideo = !audioOnly`). */
    audioOnly: Boolean = false,
    /** BUG (2026-09-04, user screenshot): chybělo úplně, hledání nabízelo jen audio. */
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit = { _, _, _ -> },
    viewModel: PodcastSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // BUG (2026-09-04, user „nevidím pokračovat"): resume stav řeší [EpisodeSearchResult].
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(scopeSource) { viewModel.setScope(scopeSource) }

    // BUG (2026-09-03, user screenshot): obrazovka běží bez Scaffoldu (overlay přes celou plochu,
    // ne přes topBar) → bez vlastního odsazení lišta kolidovala se stavovým řádkem Androidu a
    // zpětná šipka/zavřít nešly ovládat. Stejná konvence jako ui-phone/SearchScreen.kt.
    Column(modifier.fillMaxSize().statusBarsPadding().background(MaterialTheme.colorScheme.background)) {
        // Vyhledávací pole + zpět — stejný vzor jako SearchScreen (Filmy TMDB hledání).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zpět")
            }
            TextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text(if (scopeLabel != null) "Hledat v $scopeLabel…" else "Hledat v podcastech…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Vymazat")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }

        // Řazení — vidět, jen když je co řadit (drží lištu klidnou u prázdného stavu).
        if (state.results.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PodcastSearchViewModel.SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.sort == mode,
                        onClick = { viewModel.setSort(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.query.isBlank() -> CenteredHint(
                    if (scopeLabel != null) "Hledej v $scopeLabel — celá historie, i v popisu epizody."
                    else "Hledej napříč YouTube i RSS podcasty — i v popisu epizody.",
                )
                state.searched && state.results.isEmpty() -> CenteredHint("Nic nenalezeno pro dotaz: ${state.query}")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValuesVertical) {
                    items(state.results, key = { it.resumeKey ?: it.id }) { ep ->
                        EpisodeSearchResult(ep, viewModel, audioOnly, onPlayVideo)
                    }
                }
            }
        }
    }
}

/**
 * Jeden nalezený díl se stavem poslechu, staženosti a akcemi. Sdíleno s jednotným hledáním
 * (AMPHORA F3, skupina Díly) — stejné chování jako hledání ve zdroji.
 */
@Composable
internal fun EpisodeSearchResult(
    ep: SourceEpisode,
    viewModel: PodcastSearchViewModel,
    audioOnly: Boolean,
    onPlayVideo: (url: String, title: String, posterUrl: String?) -> Unit,
    onBeforeAction: () -> Unit = {},
) {
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val resumeMarks by viewModel.resumeMarks.collectAsStateWithLifecycle()
    val videoResumeMarks by viewModel.videoResumeMarks.collectAsStateWithLifecycle()
    val key = ep.resumeKey ?: ep.id
    val videoUrl = if (audioOnly) null else viewModel.videoUrl(ep)
    // ADAPT (2026-09-04): vyhrává POKROČILEJŠÍ pozice (ne poslední zápis, ne pouhá
    // přítomnost videa) — parita s RssPodcastScreen/YoutubeChannelScreen/CtvProgramScreen.
    val videoMark = videoResumeMarks[key]
    val mark = resumeMarks[key]
    val choice = choosePlaybackResume(mark?.posMs, mark?.durMs, mark?.isFinished == true, videoMark?.posMs, videoMark?.durMs, mark?.updatedAt ?: 0L, videoMark?.updatedAt ?: 0L)
    SearchResultRow(
        episode = ep,
        isCurrent = player.currentEpisodeId == key,
        isPlaying = player.isPlaying && player.currentEpisodeId == key,
        markPos = choice?.posMs,
        markDur = choice?.durMs,
        isFinished = choice?.isFinished == true,
        offlineStatus = offlineStates[key]?.status ?: OfflineStatus.NONE,
        onPlay = { onBeforeAction(); viewModel.play(ep) },
        onVideo = videoUrl?.let { url -> { onBeforeAction(); viewModel.onVideoTap(ep); onPlayVideo(url, ep.title, ep.imageUrl) } },
        onEnqueue = { onBeforeAction(); viewModel.enqueue(ep) },
        onDownload = { onBeforeAction(); viewModel.download(ep) },
        onDeleteOffline = { viewModel.deleteOffline(ep) },
    )
}

private val PaddingValuesVertical = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchResultRow(
    episode: SourceEpisode,
    isCurrent: Boolean,
    isPlaying: Boolean,
    /** BUG (2026-09-04): resume pozice (audio nebo video, „poslední vyhrává") — null = nikdy nespuštěno. */
    markPos: Long?,
    markDur: Long?,
    isFinished: Boolean,
    offlineStatus: OfflineStatus,
    onPlay: () -> Unit,
    /** null = zdroj video vůbec nenabízí (RSS bez jfItemId) — tlačítko se pak nezobrazí. */
    onVideo: (() -> Unit)?,
    onEnqueue: () -> Unit,
    onDownload: () -> Unit,
    onDeleteOffline: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    // BUG (2026-09-04): canResume/progress/remainingLabel — parita s YoutubeChannelScreen/CtvProgramScreen.
    val canResume = !isCurrent && markPos != null && !isFinished
    val progress: Float? = when {
        isCurrent -> null // aktuální řádek nemá vlastní progress bar — má ho MiniPlayer/přehrávač.
        markPos != null && markDur != null && markDur > 0 -> (markPos.toFloat() / markDur).coerceIn(0f, 1f)
        else -> null
    }
    val remainingLabel = if (canResume && markDur != null && markDur > 0)
        "zbývá ${formatSearchDuration((markDur - markPos!!).coerceAtLeast(0L) / 1000.0)}" else null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).height(72.dp)) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
                )
                val meta = listOfNotNull(
                    episode.subtitle,
                    formatSearchDate(episode.date),
                    formatSearchDuration(episode.durationSec),
                    formatViewCount(episode.viewCount),
                    remainingLabel,
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        val description = episode.description
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { expanded = !expanded },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (playIcon, playLabel) = when {
                isCurrent && isPlaying -> Icons.Rounded.GraphicEq to "Hraje"
                isCurrent -> Icons.Rounded.PlayArrow to "Pokračovat"
                canResume -> Icons.Rounded.PlayArrow to "Pokračovat"
                isFinished -> Icons.Rounded.Headphones to "Přehrát znovu"
                else -> Icons.Rounded.Headphones to "Poslech"
            }
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(playIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(playLabel, Modifier.padding(start = 6.dp))
            }
            if (onVideo != null) {
                FilledTonalButton(onClick = onVideo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Video", Modifier.padding(start = 6.dp))
                }
            }
            IconButton(onClick = onEnqueue) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Přidat do fronty")
            }
            when (offlineStatus) {
                OfflineStatus.DOWNLOADED -> IconButton(onClick = onDeleteOffline) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Smazat staženou epizodu", tint = accent)
                }
                OfflineStatus.DOWNLOADING, OfflineStatus.QUEUED -> IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Rounded.Download, contentDescription = "Stahuje se")
                }
                else -> IconButton(onClick = onDownload) {
                    Icon(Icons.Rounded.Download, contentDescription = "Stáhnout do telefonu")
                }
            }
        }
    }
}

/** Formáty smíchané napříč zdroji — YouTube „YYYYMMDD", RSS „YYYY-MM-DD"/RFC822 — sjednoceno přes
 *  [com.github.jankoran90.showlyfin.feature.listen.parseEpisodeDateMs] (millis), ne string-split. */
private fun formatSearchDate(raw: String?): String? {
    val ms = com.github.jankoran90.showlyfin.feature.listen.parseEpisodeDateMs(raw) ?: return null
    return runCatching { SimpleDateFormat("d. M. yyyy", Locale("cs")).format(java.util.Date(ms)) }.getOrNull()
}

private fun formatSearchDuration(sec: Double): String? {
    if (sec <= 0.0) return null
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "%d:%02d h".format(h, m) else "%d min".format(m.coerceAtLeast(1))
}

private fun formatViewCount(count: Long?): String? {
    if (count == null || count < 0) return null
    val label = when {
        count >= 1_000_000 -> "%.1f mil.".format(count / 1_000_000.0)
        count >= 1_000 -> "%.0f tis.".format(count / 1_000.0)
        else -> count.toString()
    }
    return "$label zhlédnutí"
}
