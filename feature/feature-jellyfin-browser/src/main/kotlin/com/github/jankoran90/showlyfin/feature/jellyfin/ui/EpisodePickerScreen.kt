package com.github.jankoran90.showlyfin.feature.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.jellyfin.EpisodePickerViewModel
import com.github.jankoran90.showlyfin.feature.jellyfin.EpisodeRow

/**
 * Výběr epizody u seriálu: aktuální „Pokračovat" (Next Up) výrazně zvýrazněná a vycentrovaná,
 * zhlédnuté/nezhlédnuté jasně odlišené, klik přehraje libovolnou epizodu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodePickerScreen(
    seriesId: String,
    seriesName: String,
    onBack: () -> Unit,
    onPlayEpisode: (episodeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodePickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId, seriesName) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Vycentruj aktuální epizodu (o 1 výš, ať je vidět kontext).
    LaunchedEffect(state.nextUpIndex) {
        if (state.nextUpIndex >= 0) {
            runCatching { listState.scrollToItem((state.nextUpIndex - 1).coerceAtLeast(0)) }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF0D0D1A),
        topBar = {
            TopAppBar(
                title = { Text(state.seriesName.ifBlank { seriesName }, color = Color.White, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E)),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                state.episodes.isEmpty() -> Text(
                    "Žádné epizody",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.episodes, key = { it.id }) { ep ->
                        EpisodeItemRow(ep = ep, onClick = { onPlayEpisode(ep.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeItemRow(ep: EpisodeRow, onClick: () -> Unit) {
    val label = buildString {
        ep.seasonNumber?.let { append("S$it") }
        ep.episodeNumber?.let { append("E$it") }
        if (isNotEmpty()) append(" · ")
        append(ep.name)
    }
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(if (ep.isNextUp) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF15152B))
    val withBorder = if (ep.isNextUp) {
        base.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
    } else {
        base
    }
    Row(
        modifier = withBorder
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) {
            AsyncImage(
                model = ep.imageUrl,
                contentDescription = ep.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            ep.progressPct?.takeIf { it > 0 }?.let { pct ->
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            if (ep.isNextUp) {
                Text(
                    "▶ POKRAČOVAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ep.watched) Color.White.copy(alpha = 0.55f) else Color.White,
                fontWeight = if (ep.isNextUp) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (ep.watched) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Zhlédnuto",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.width(22.dp),
            )
        } else {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Přehrát",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(22.dp),
            )
        }
    }
}
