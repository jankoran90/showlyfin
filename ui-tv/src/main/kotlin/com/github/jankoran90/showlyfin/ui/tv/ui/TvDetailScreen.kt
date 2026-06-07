package com.github.jankoran90.showlyfin.ui.tv.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TvDetailScreen(
    item: MediaItem,
    onPlayJellyfin: ((String) -> Unit)?,
    onBack: () -> Unit,
    onPlayStreamUrl: ((String, String, com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery?) -> Unit)? = null,
    onSmartDetect: ((MediaItem) -> Unit)? = null,
    onPartClick: ((com.github.jankoran90.showlyfin.core.ui.CollectionPart) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(item.traktId, item.tmdbId, item.imdbId) {
        viewModel.load(item)
    }

    LaunchedEffect(uiState.pendingPlaybackUrl) {
        val url = uiState.pendingPlaybackUrl ?: return@LaunchedEffect
        onPlayStreamUrl?.invoke(url, uiState.pendingPlaybackTitle, uiState.pendingSubtitleQuery)
        viewModel.consumePlayback()
    }
    LaunchedEffect(uiState.requestStremioFallback) {
        if (uiState.requestStremioFallback) {
            val mt = if (item.type == MediaType.MOVIE) "movie" else "series"
            val sid = item.imdbId ?: item.tmdbId?.toString()
            if (sid != null) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/$mt/$sid"))) }
            }
            viewModel.consumeStremioFallback()
        }
    }
    LaunchedEffect(uiState.captureMessage) {
        uiState.captureMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeCaptureMessage()
        }
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val jfId = uiState.ownedJellyfinId
                if (jfId != null) {
                    // Film je v Jellyfin knihovně → přehrávání
                    Button(onClick = { onPlayJellyfin?.invoke(jfId) }, modifier = Modifier.height(56.dp)) {
                        Text("Přehrát")
                    }
                } else {
                    // Mimo knihovnu → stream / akvizice
                    Button(onClick = { viewModel.openStreamPicker() }, modifier = Modifier.height(56.dp)) {
                        Text("Stremio")
                    }
                    Button(onClick = { viewModel.openDownloadMenu() }, modifier = Modifier.height(56.dp)) {
                        Text("Stáhnout")
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
                uiState.csfdId?.let { csfdId ->
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.csfd.cz/film/$csfdId/")))
                            }
                        },
                        modifier = Modifier.height(56.dp),
                    ) { Text("ČSFD") }
                }
                Button(onClick = onBack, modifier = Modifier.height(56.dp)) {
                    Text("Zpět")
                }
            }
            val plot = uiState.tmdbCzOverview?.takeIf { it.isNotBlank() }
                ?: uiState.csfdPlot?.takeIf { it.isNotBlank() }
                ?: uiState.movieDetails?.overview
                ?: uiState.showDetails?.overview
                ?: item.overview.orEmpty()
            if (plot.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                // focusable → D-pad může najet na plot a verticalScroll ho přiroluje (fix TV scroll)
                Text(
                    text = plot,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.focusable(),
                )
            }
            uiState.mergedCollection?.let { TvPosterRow(it, onPartClick) }
            uiState.directorMovies?.let { TvPosterRow(it, onPartClick) }
            uiState.studioMovies?.let { TvPosterRow(it, onPartClick) }
            if (uiState.cast.isNotEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(text = "Obsazení", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(end = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.cast, key = { it.id }) { person ->
                        Column(
                            modifier = Modifier.width(110.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                person.profile_path?.let { path ->
                                    AsyncImage(
                                        model = "https://image.tmdb.org/t/p/w185$path",
                                        contentDescription = person.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = person.name.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val role = person.character ?: person.roles?.firstOrNull()?.character
                            if (!role.isNullOrBlank()) {
                                Text(
                                    text = role,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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

        // Overlay pickery (Stremio / Stáhnout / Sdílej.cz)
        uiState.rdDownload?.let { rd ->
            TvRdDownloadOverlay(state = rd, onCancel = { viewModel.cancelRdDownload() })
        }
        if (uiState.showStreamPicker) {
            TvStreamPicker(
                streams = uiState.streams,
                isLoading = uiState.isLoadingStreams,
                isResolving = uiState.isResolvingStream,
                error = uiState.streamError,
                onPlay = { viewModel.playStream(it) },
                onDismiss = { viewModel.dismissStreamPicker() },
                isProbing = uiState.isProbingStreams,
            )
        }
        if (uiState.showDownloadMenu) {
            TvDownloadMenu(
                onSdilej = { viewModel.openSdilejPicker() },
                onSmartRemux = { viewModel.dismissDownloadMenu(); onSmartDetect?.invoke(uiState.item ?: item) },
                onDismiss = { viewModel.dismissDownloadMenu() },
            )
        }
        if (uiState.showSdilejPicker) {
            TvSdilejPicker(
                streams = uiState.sdilejStreams,
                isLoading = uiState.isLoadingSdilej,
                error = uiState.sdilejError,
                onCapture = { viewModel.captureSdilej(it) },
                onDismiss = { viewModel.dismissSdilejPicker() },
            )
        }
    }
}

/** Horizontální řada fokusovatelných posterů (kolekce / režisér / studio). Fokus umožní D-pad scroll. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPosterRow(
    collection: com.github.jankoran90.showlyfin.core.ui.MediaCollection,
    onPartClick: ((com.github.jankoran90.showlyfin.core.ui.CollectionPart) -> Unit)?,
) {
    if (collection.parts.isEmpty()) return
    Spacer(Modifier.height(40.dp))
    Text(text = collection.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    LazyRow(
        contentPadding = PaddingValues(end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(collection.parts, key = { it.key }) { part ->
            androidx.tv.material3.Card(
                onClick = { onPartClick?.invoke(part) },
                modifier = Modifier.width(140.dp).aspectRatio(2f / 3f),
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
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

private fun MediaItem.backdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
