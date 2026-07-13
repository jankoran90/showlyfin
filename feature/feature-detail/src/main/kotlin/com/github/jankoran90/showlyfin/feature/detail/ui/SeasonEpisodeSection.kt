package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbEpisode
import com.github.jankoran90.showlyfin.data.tmdb.model.TmdbSeasonSummary

/**
 * TENFOOT WS-C (SHW-87): sekce sezóny/epizody seriálu v detailu. Sdílená telefon + TV (na TV je
 * `tvFocusable` glow, telefon no-op). Chipy sezón → seznam epizod (náhled 16:9 + S×E·název + runtime/
 * rating), klik na epizodu → [onPlayEpisode] spustí stream flow (uploader query nese season/episode).
 *
 * Umísťuje se do vertikálně scrollované `Column` (telefon DetailScreen i [TvDetailSections]) — proto
 * je to prostá `Column`, ne vlastní `LazyColumn` (jinak nested-scroll konflikt).
 */
@Composable
fun SeasonEpisodeSection(
    seasons: List<TmdbSeasonSummary>,
    selectedSeason: Int?,
    episodes: List<TmdbEpisode>,
    isLoadingEpisodes: Boolean,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (season: Int, episode: Int, title: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (seasons.isEmpty()) return
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
            episodes.forEach { ep ->
                EpisodeRow(
                    episode = ep,
                    onClick = { onPlayEpisode(ep.season_number ?: season, ep.episode_number, ep.name) },
                )
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
