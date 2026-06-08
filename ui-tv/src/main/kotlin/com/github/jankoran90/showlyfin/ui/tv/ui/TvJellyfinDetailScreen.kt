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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.jellyfin.JellyfinDetailViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvJellyfinDetailScreen(
    itemId: String,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
    onOpenEpisodes: ((seriesId: String, name: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: JellyfinDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(itemId) { viewModel.load(itemId) }

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        val detail = state.detail
        if (detail?.backdropUrl != null) {
            AsyncImage(
                model = detail.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF07071A).copy(alpha = 0.9f), Color(0xFF07071A)),
                    ),
                ),
            )
        }

        when {
            state.isLoading && detail == null -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            state.error != null && detail == null -> Text(
                text = state.error!!,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            detail != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 64.dp, vertical = 48.dp),
            ) {
                Text(detail.name, color = Color.White, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    detail.year?.let { Text("$it", color = Color.White.copy(alpha = 0.7f)) }
                    detail.runtimeMinutes?.let { Text("$it min", color = Color.White.copy(alpha = 0.7f)) }
                    detail.rating?.let { Text("★ ${"%.1f".format(it)}", color = Color.White.copy(alpha = 0.7f)) }
                    detail.officialRating?.let { Text(it, color = Color.White.copy(alpha = 0.7f)) }
                }
                if (detail.genres.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(detail.genres.joinToString(" · "), color = Color.White.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(24.dp))
                val isSeries = detail.type.equals("SERIES", ignoreCase = true)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            if (isSeries && onOpenEpisodes != null) onOpenEpisodes(detail.id, detail.name)
                            else onPlay(detail.id)
                        },
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(if (isSeries) "Epizody" else "Přehrát")
                    }
                    Button(onClick = onBack, modifier = Modifier.height(56.dp)) {
                        Text("Zpět")
                    }
                }
                if (!detail.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(detail.overview!!, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
                }
                state.collection?.let { coll ->
                    Spacer(Modifier.height(40.dp))
                    Text(coll.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(end = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(coll.parts, key = { it.key }) { part ->
                            Box(
                                Modifier
                                    .width(140.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (part.posterUrl != null) {
                                    AsyncImage(
                                        model = part.posterUrl,
                                        contentDescription = part.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
