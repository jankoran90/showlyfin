package com.github.jankoran90.showlyfin.feature.jellyfin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.feature.jellyfin.EpisodeRow
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinDetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JellyfinDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlay: (itemId: String) -> Unit,
    onOpenEpisodes: ((seriesId: String, name: String) -> Unit)? = null,
    onCollectionPartClick: ((CollectionPart) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: JellyfinDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.name ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusable(shape = CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            state.detail != null -> {
                val detail = state.detail!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = detail.backdropUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                    ),
                                ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        detail.year?.let {
                            Text("$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        detail.runtimeMinutes?.takeIf { it > 0 }?.let {
                            Text("$it min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        detail.rating?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                                Text("%.1f".format(rating), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        detail.officialRating?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    val isSeries = detail.type.equals("SERIES", ignoreCase = true)
                    val nextUp = state.nextUp
                    // Autofokus jde přímo na obsah/akci (Pokračovat u seriálu, Přehrát u filmu), NE na Zpět/lištu
                    // (user 2026-07-12: „vždy na obsah, ne na sekce/tlačítka").
                    val primaryFocus = remember { FocusRequester() }
                    // Počkej na umístění uzlu (1 snímek), pak fokus — jinak „not placed" (spolknuto) a fokus
                    // zůstane na Zpět/liště. Column je ne-lazy, jeden snímek stačí.
                    LaunchedEffect(detail.id, nextUp) {
                        withFrameNanos { }
                        runCatching { primaryFocus.requestFocus() }
                    }

                    if (isSeries && nextUp != null) {
                        NextUpCard(
                            episode = nextUp,
                            onContinue = { onPlay(nextUp.id) },
                            onEpisodes = { onOpenEpisodes?.invoke(detail.id, detail.name) },
                            continueFocus = primaryFocus,
                        )
                    } else {
                        Button(
                            onClick = {
                                if (isSeries && onOpenEpisodes != null) onOpenEpisodes(detail.id, detail.name)
                                else onPlay(detail.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .focusRequester(primaryFocus)
                                .tvFocusable(shape = RoundedCornerShape(percent = 50)),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(if (isSeries) "Epizody" else "Přehrát")
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (detail.genres.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            detail.genres.forEach { genre ->
                                AssistChip(onClick = {}, label = { Text(genre, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }

                    state.collection?.let { coll ->
                        CollectionSection(
                            collection = coll,
                            excludeKey = "jellyfin_${detail.id}",
                            onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * TENFOOT — náhled „další na řadě" epizody na kartě seriálu (yellyfin-like): miniatura + „S1E4 · název"
 * + progress u rozkoukané; tlačítko **Pokračovat** (přímé přehrání nextUp epizody, dostává autofokus) vedle
 * **Epizody** (plný výběr). Nahrazuje prosté tlačítko „Epizody" jen když má seriál next-up epizodu.
 */
@Composable
private fun NextUpCard(
    episode: EpisodeRow,
    onContinue: () -> Unit,
    onEpisodes: () -> Unit,
    continueFocus: FocusRequester,
) {
    val se = buildString {
        episode.seasonNumber?.let { append("S$it") }
        episode.episodeNumber?.let { append("E$it") }
    }
    val subtitle = listOfNotNull(se.ifBlank { null }, episode.name).joinToString(" · ")

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (episode.watched) "Přehrát znovu" else "Další na řadě",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                episode.progressPct?.takeIf { it in 1..99 }?.let { pct ->
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(continueFocus)
                    .tvFocusable(shape = RoundedCornerShape(percent = 50)),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(if (episode.watched) "Přehrát" else "Pokračovat")
            }
            OutlinedButton(
                onClick = onEpisodes,
                modifier = Modifier
                    .weight(1f)
                    .tvFocusable(shape = RoundedCornerShape(percent = 50)),
            ) {
                Text("Epizody")
            }
        }
    }
}
