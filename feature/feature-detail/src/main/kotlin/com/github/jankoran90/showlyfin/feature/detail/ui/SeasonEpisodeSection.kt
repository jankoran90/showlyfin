package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbEpisode
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSeasonSummary

/**
 * TENFOOT WS-C (SHW-87) + TV DETAIL REDESIGN (OTA 299): sekce sezóny/epizody seriálu v detailu.
 * Sdílená telefon + TV. Chipy sezón → epizody. NA TV = HORIZONTÁLNÍ řada landscape karet (jako yellyfin)
 * s indikátorem zhlédnutí/progress + AUTO-SCROLL na první nezhlédnutou epizodu. Telefon = vertikální seznam.
 * Watched/progress/nextUp přichází z Jellyfinu ([DetailUiState.episodeWatched] atd.); klíč = (season, episode).
 *
 * Umísťuje se do vertikálně scrollované `Column` — proto je to prostá `Column`, ne vlastní `LazyColumn`
 * (jinak nested-scroll konflikt); horizontální `LazyRow` uvnitř je OK.
 */
@Composable
fun SeasonEpisodeSection(
    seasons: List<TmdbSeasonSummary>,
    selectedSeason: Int?,
    episodes: List<TmdbEpisode>,
    isLoadingEpisodes: Boolean,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    watched: Set<Pair<Int, Int>> = emptySet(),
    progress: Map<Pair<Int, Int>, Int> = emptyMap(),
    nextUp: Pair<Int, Int>? = null,
    onToggleWatched: ((season: Int, episode: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (seasons.isEmpty()) return
    val isTv = isTvFormFactor()
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Epizody",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seasons) { s ->
                FilterChip(
                    selected = s.season_number == selectedSeason,
                    onClick = { onSelectSeason(s.season_number) },
                    label = { Text(seasonLabel(s)) },
                    modifier = Modifier.tvFocusable(shape = RoundedCornerShape(8.dp)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (isLoadingEpisodes) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val season = selectedSeason ?: seasons.first().season_number
            if (isTv) {
                TvEpisodeStrip(
                    episodes = episodes,
                    season = season,
                    watched = watched,
                    progress = progress,
                    nextUp = nextUp,
                    onPlayEpisode = onPlayEpisode,
                    onToggleWatched = onToggleWatched,
                )
            } else {
                episodes.forEach { ep ->
                    EpisodeRow(
                        episode = ep,
                        onClick = { onPlayEpisode(ep.season_number ?: season, ep.episode_number, ep.name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun seasonLabel(s: TmdbSeasonSummary): String {
    val n = s.name
    return when {
        s.season_number == 0 -> "Speciály"
        !n.isNullOrBlank() && n.any { it.isLetter() } -> n
        else -> "Sezóna ${s.season_number}"
    }
}

/** TV: horizontální řada epizod (landscape still) + auto-scroll na první nezhlédnutou (nextUp / dle watched). */
@Composable
private fun TvEpisodeStrip(
    episodes: List<TmdbEpisode>,
    season: Int,
    watched: Set<Pair<Int, Int>>,
    progress: Map<Pair<Int, Int>, Int>,
    nextUp: Pair<Int, Int>?,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    onToggleWatched: ((season: Int, episode: Int) -> Unit)? = null,
) {
    if (episodes.isEmpty()) return
    val listState = rememberLazyListState()
    // Index první nezhlédnuté: přednostně přesná nextUp epizoda (Jellyfin), jinak první mimo `watched`.
    val focusIdx = remember(episodes, watched, nextUp, season) {
        val byNextUp = if (nextUp != null) {
            episodes.indexOfFirst { (it.season_number ?: season) == nextUp.first && it.episode_number == nextUp.second }
        } else -1
        if (byNextUp >= 0) byNextUp
        else episodes.indexOfFirst { ((it.season_number ?: season) to it.episode_number) !in watched }
    }
    // COUCH T4 (user 2026-07-13): AUTOFOKUS na první nezhlédnutou epizodu (nejen scroll — user „asi nefunguje").
    // Scroll doprostřed + po umístění zaostři tu kartu (D-pad rovnou pokračuje odtud). Jednorázově per sezóna.
    val nextUpFocus = remember { FocusRequester() }
    LaunchedEffect(focusIdx, episodes) {
        if (focusIdx < 0) return@LaunchedEffect
        if (focusIdx > 0) runCatching { listState.scrollToItem(focusIdx) }
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == focusIdx } }.first { it }
        runCatching { nextUpFocus.requestFocus() }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(episodes) { idx, ep ->
            val key = (ep.season_number ?: season) to ep.episode_number
            TvEpisodeCard(
                episode = ep,
                seasonNumber = ep.season_number ?: season,
                watched = key in watched,
                progressPct = progress[key],
                isNextUp = idx == focusIdx,
                onClick = { onPlayEpisode(ep.season_number ?: season, ep.episode_number, ep.name) },
                onLongClick = onToggleWatched?.let { cb -> { cb(ep.season_number ?: season, ep.episode_number) } },
                focusRequester = if (idx == focusIdx) nextUpFocus else null,
            )
        }
    }
}

/** TV landscape karta epizody: still 16:9 + fajfka (zhlédnuto) / „Pokračovat" + progress proužek + S×E·název. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TvEpisodeCard(
    episode: TmdbEpisode,
    seasonNumber: Int,
    watched: Boolean,
    progressPct: Int?,
    isNextUp: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier
            .width(236.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusable(shape = RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            // KOLO2 (J): dlouhý stisk = přepni „zhlédnuto" (zápis do Jellyfinu), krátký = přehrát.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val still = episode.stillUrl()
            if (still != null) {
                AsyncImage(
                    model = still,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Ztmavení + fajfka u zhlédnuté.
            if (watched) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Zhlédnuto",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            // „Pokračovat" štítek na první nezhlédnuté.
            if (isNextUp && !watched) {
                Text(
                    text = "▶ Pokračovat",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            // Progress proužek u rozkoukané epizody (dole).
            if (progressPct != null && progressPct in 1..99) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progressPct / 100f)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "S${seasonNumber}E${episode.episode_number} · " +
                (episode.name?.takeIf { it.isNotBlank() } ?: "Epizoda ${episode.episode_number}"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            episode.runtime?.takeIf { it > 0 }?.let { "$it min" },
            episode.vote_average?.takeIf { it > 0f }?.let { "★ %.1f".format(it) },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

/** Telefon: vertikální řádek epizody (beze změny — WS-C). */
@Composable
private fun EpisodeRow(episode: TmdbEpisode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(shape = RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val still = episode.stillUrl()
            if (still != null) {
                AsyncImage(
                    model = still,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(episode.episode_number)
                    append(". ")
                    append(episode.name?.takeIf { it.isNotBlank() } ?: "Epizoda ${episode.episode_number}")
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                episode.runtime?.takeIf { it > 0 }?.let { "$it min" },
                episode.vote_average?.takeIf { it > 0f }?.let { "★ %.1f".format(it) },
                episode.air_date?.take(4)?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            val overview = episode.overview
            if (!overview.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Přehrát epizodu",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
