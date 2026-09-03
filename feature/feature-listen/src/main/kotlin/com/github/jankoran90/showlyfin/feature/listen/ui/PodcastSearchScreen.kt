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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
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
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * TRAWL (Slovo, 2026-09-02): fulltext hledání epizod. FOCUS (2026-09-03, user „hlavně bych měl hledat
 * na jedním zdrojem, ne plošně") — primárně otevíráno SCOPED na jeden zdroj (lupa v obrazovce YouTube
 * kanálu / RSS / Na Výbornou, nahrazuje starý pevný textový filtr); [scopeSource] = null zachovává
 * původní chování (napříč VŠEMI sledovanými zdroji, FAB na Timeline/Sledované jako doplněk).
 * YouTube = živě celá historie kanálu, RSS/NaVýbornou = velký fetch + klientský filtr. Tap na výsledek
 * rovnou PŘEHRAJE (audio, sdílený resume klíč se zdrojovou obrazovkou).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSearchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scopeSource: PodcastSource? = null,
    scopeLabel: String? = null,
    viewModel: PodcastSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(scopeSource) { viewModel.setScope(scopeSource) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Vyhledávací pole + zpět — stejný vzor jako SearchScreen (Filmy TMDB hledání).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
            }
            TextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text(if (scopeLabel != null) "Hledat v $scopeLabel…" else "Hledat v podcastech…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Vymazat")
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
                        val key = ep.resumeKey ?: ep.id
                        SearchResultRow(
                            episode = ep,
                            isCurrent = player.currentEpisodeId == key,
                            isPlaying = player.isPlaying && player.currentEpisodeId == key,
                            offlineStatus = offlineStates[key]?.status ?: OfflineStatus.NONE,
                            onPlay = { viewModel.play(ep) },
                            onEnqueue = { viewModel.enqueue(ep) },
                            onDownload = { viewModel.download(ep) },
                            onDeleteOffline = { viewModel.deleteOffline(ep) },
                        )
                    }
                }
            }
        }
    }
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
    offlineStatus: OfflineStatus,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onDownload: () -> Unit,
    onDeleteOffline: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = episode.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(72.dp).height(72.dp).clip(RoundedCornerShape(8.dp)),
            )
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
            val (playIcon, playLabel) = if (isCurrent && isPlaying) Icons.Default.GraphicEq to "Hraje" else Icons.Default.Headphones to "Poslech"
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(playIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(playLabel, Modifier.padding(start = 6.dp))
            }
            IconButton(onClick = onEnqueue) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Přidat do fronty")
            }
            when (offlineStatus) {
                OfflineStatus.DOWNLOADED -> IconButton(onClick = onDeleteOffline) {
                    Icon(Icons.Default.Delete, contentDescription = "Smazat staženou epizodu", tint = accent)
                }
                OfflineStatus.DOWNLOADING, OfflineStatus.QUEUED -> IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Download, contentDescription = "Stahuje se")
                }
                else -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Stáhnout do telefonu")
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
