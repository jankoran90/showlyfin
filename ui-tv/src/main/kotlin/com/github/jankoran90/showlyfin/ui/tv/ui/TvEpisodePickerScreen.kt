package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.jellyfin.EpisodePickerViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.EpisodeRow

/**
 * TV výběr epizody seriálu (D-pad). Sdílí [EpisodePickerViewModel] s telefonem.
 * Next Up zvýrazněná + vycentrovaná, zhlédnuté odlišené, klik = přehrát epizodu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEpisodePickerScreen(
    seriesId: String,
    seriesName: String,
    onPlayEpisode: (episodeId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodePickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId, seriesName) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.nextUpIndex) {
        if (state.nextUpIndex >= 0) {
            runCatching { listState.scrollToItem((state.nextUpIndex - 1).coerceAtLeast(0)) }
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
            Text(
                text = state.seriesName.ifBlank { seriesName },
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.error != null -> Text(
                        text = state.error!!,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                    state.episodes.isEmpty() -> Text(
                        text = "Žádné epizody",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.episodes, key = { it.id }) { ep ->
                            TvEpisodeCard(ep = ep, onClick = { onPlayEpisode(ep.id) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvEpisodeCard(ep: EpisodeRow, onClick: () -> Unit) {
    val label = buildString {
        ep.seasonNumber?.let { append("S$it") }
        ep.episodeNumber?.let { append("E$it") }
        if (isNotEmpty()) append(" · ")
        append(ep.name)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (ep.isNextUp) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else Color(0xFF15152B),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) {
                AsyncImage(
                    model = ep.imageUrl,
                    contentDescription = ep.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                ep.progressPct?.takeIf { it > 0 }?.let { pct ->
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                if (ep.isNextUp) {
                    Text(
                        "▶ POKRAČOVAT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (ep.watched) Color.White.copy(alpha = 0.55f) else Color.White,
                    fontWeight = if (ep.isNextUp) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (ep.watched) {
                Text("✓", color = Color(0xFF66BB6A), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
