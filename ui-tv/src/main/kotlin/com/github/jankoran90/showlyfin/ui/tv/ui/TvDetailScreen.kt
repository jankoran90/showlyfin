package com.github.jankoran90.showlyfin.ui.tv.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailScreen(
    item: MediaItem,
    onPlayJellyfin: ((String) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(item.traktId, item.tmdbId, item.imdbId) {
        viewModel.load(item)
    }

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        val backdrop = uiState.item?.backdropUrl() ?: item.backdropUrl()
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 64.dp, vertical = 48.dp),
        ) {
            val title = uiState.tmdbCzTitle?.takeIf { it.isNotBlank() }
                ?: item.titleCz?.takeIf { it.isNotBlank() }
                ?: item.title
            Text(text = title, color = Color.White, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            val year = item.year
            val rating = uiState.movieDetails?.vote_average ?: uiState.showDetails?.vote_average
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                year?.let { Text("$it", color = Color.White.copy(alpha = 0.7f)) }
                rating?.let { Text("TMDB: ${"%.1f".format(it)}", color = Color.White.copy(alpha = 0.7f)) }
                uiState.csfdRating?.let { Text("ČSFD: $it%", color = Color.White.copy(alpha = 0.7f)) }
                if (uiState.isOwnedInLibrary) {
                    Text("✓ V knihovně", color = MaterialTheme.colorScheme.primary)
                }
                if (uiState.isWatched) {
                    Text("👁 Zhlédnuto", color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(24.dp))
            val plot = uiState.tmdbCzOverview?.takeIf { it.isNotBlank() }
                ?: uiState.csfdPlot?.takeIf { it.isNotBlank() }
                ?: uiState.movieDetails?.overview
                ?: uiState.showDetails?.overview
                ?: item.overview.orEmpty()
            if (plot.isNotBlank()) {
                Text(text = plot, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                uiState.ownedJellyfinId?.let { jfId ->
                    Button(onClick = { onPlayJellyfin?.invoke(jfId) }, modifier = Modifier.height(56.dp)) {
                        Text("Přehrát z Jellyfin")
                    }
                }
                val streamId = item.imdbId ?: item.tmdbId?.toString()
                if (streamId != null) {
                    Button(
                        onClick = {
                            val mt = if (item.type == MediaType.MOVIE) "movie" else "series"
                            val open = runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/$mt/$streamId")),
                                )
                            }.isSuccess
                            if (!open) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.stremio.com/downloads")),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("Stream přes Stremio")
                    }
                }
                if (uiState.isTraktLoggedIn) {
                    Button(
                        onClick = { viewModel.toggleWatchlist() },
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(
                            when {
                                uiState.isTogglingWatchlist -> "…"
                                uiState.isInWatchlist -> "✓ Ve watchlistu"
                                else -> "+ Watchlist"
                            },
                        )
                    }
                }
                Button(onClick = onBack, modifier = Modifier.height(56.dp)) {
                    Text("Zpět")
                }
            }
            uiState.mergedCollection?.let { coll ->
                Spacer(Modifier.height(40.dp))
                Text(text = coll.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
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
            if (uiState.isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private fun MediaItem.backdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
