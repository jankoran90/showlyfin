package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onSmartDetect: ((MediaItem) -> Unit)? = null,
    onNaTv: ((MediaItem, jellyfinId: String?) -> Unit)? = null,
    onStremio: ((MediaItem) -> Unit)? = null,
    onShare: ((MediaItem) -> Unit)? = null,
    onCollectionPartClick: ((CollectionPart) -> Unit)? = null,
    onPlayJellyfin: ((String) -> Unit)? = null,
    onPlayStreamUrl: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(item.traktId, item.tmdbId, item.imdbId) { viewModel.load(item) }

    val displayItem = uiState.item ?: item

    // Stremio stream resolved → přehraj externí URL
    LaunchedEffect(uiState.pendingPlaybackUrl) {
        val url = uiState.pendingPlaybackUrl ?: return@LaunchedEffect
        onPlayStreamUrl?.invoke(url, uiState.pendingPlaybackTitle)
        viewModel.consumePlayback()
    }
    // RD resolve selhal → fallback do Stremio app
    LaunchedEffect(uiState.requestStremioFallback) {
        if (uiState.requestStremioFallback) {
            onStremio?.invoke(displayItem)
            viewModel.consumeStremioFallback()
        }
    }
    // Sdílej.cz capture → toast
    LaunchedEffect(uiState.captureMessage) {
        uiState.captureMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeCaptureMessage()
        }
    }

    if (uiState.showStreamPicker) {
        StreamPickerSheet(
            streams = uiState.streams,
            isLoading = uiState.isLoadingStreams,
            isResolving = uiState.isResolvingStream,
            error = uiState.streamError,
            strict = uiState.streamStrict,
            onStrictChange = { viewModel.setStreamStrict(it) },
            onPlay = { viewModel.playStream(it) },
            onDismiss = { viewModel.dismissStreamPicker() },
        )
    }
    if (uiState.showDownloadMenu) {
        DownloadMenuSheet(
            onSdilej = { viewModel.openSdilejPicker() },
            onSmartRemux = { viewModel.dismissDownloadMenu(); onSmartDetect?.invoke(displayItem) },
            onDismiss = { viewModel.dismissDownloadMenu() },
        )
    }
    if (uiState.showSdilejPicker) {
        SdilejPickerSheet(
            streams = uiState.sdilejStreams,
            isLoading = uiState.isLoadingSdilej,
            error = uiState.sdilejError,
            onCapture = { viewModel.captureSdilej(it) },
            onDismiss = { viewModel.dismissSdilejPicker() },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(displayItem.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        if (uiState.isLoading && uiState.item == null) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
        ) {
            val backdropUrl = displayItem.backdropUrl()
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = scrollState.value * 0.45f },
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val posterUrl = displayItem.posterUrl()
                if (posterUrl != null) {
                    Box(
                        Modifier
                            .width(100.dp)
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = displayItem.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayItem.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        displayItem.year?.let {
                            Text("$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        displayItem.rating?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                                Text("%.1f".format(rating * 10f / 10f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        uiState.csfdRating?.let { rating ->
                            CsfdRatingBadge(rating = rating)
                        }
                    }
                    val tmdbRating = uiState.movieDetails?.vote_average ?: uiState.showDetails?.vote_average
                    tmdbRating?.let {
                        Text("TMDB: %.1f".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val tmdbOverview = uiState.movieDetails?.overview ?: uiState.showDetails?.overview ?: displayItem.overview
            val tmdbCz = uiState.tmdbCzOverview
            val csfdPlot = uiState.csfdPlot

            val primaryPlot = tmdbCz?.takeIf { it.isNotBlank() }
                ?: csfdPlot?.takeIf { it.isNotBlank() }
                ?: tmdbOverview?.takeIf { it.isNotBlank() }
            val primarySource = when {
                tmdbCz?.isNotBlank() == true -> "TMDB CZ"
                csfdPlot?.isNotBlank() == true -> "ČSFD"
                else -> null
            }

            if (!primaryPlot.isNullOrBlank()) {
                if (primarySource != null) {
                    Text(
                        text = "Popis ($primarySource)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = primaryPlot,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            val showCsfdSeparately = !csfdPlot.isNullOrBlank() && tmdbCz?.isNotBlank() == true && csfdPlot != tmdbCz
            if (showCsfdSeparately) {
                Text(
                    text = "Popis (ČSFD)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = csfdPlot!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            val genres = uiState.movieDetails?.genres?.map { it.name }
                ?: uiState.showDetails?.genres?.map { it.name }
                ?: displayItem.genres
            if (!genres.isNullOrEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    genres.forEach { genre ->
                        AssistChip(onClick = {}, label = { Text(genre, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            DetailActionRow(
                item = displayItem,
                inLibrary = uiState.isOwnedInLibrary && uiState.ownedJellyfinId != null,
                csfdId = uiState.csfdId,
                onPlayNaTv = onNaTv?.let { cb -> { cb(displayItem, uiState.ownedJellyfinId) } },
                onPlayHere = onPlayJellyfin?.let { cb -> { uiState.ownedJellyfinId?.let(cb) } },
                onStream = { viewModel.openStreamPicker() },
                onDownload = { viewModel.openDownloadMenu() },
                onShare = onShare,
                onCsfd = uiState.csfdId?.let { id -> { openCsfd(context, id) } },
            )

            val mergedCollection = uiState.mergedCollection ?: uiState.collection?.let { coll ->
                MediaCollection(
                    name = coll.name ?: "Kolekce",
                    parts = coll.parts.orEmpty().map { part ->
                        CollectionPart(
                            key = "tmdb_${part.id}",
                            tmdbId = part.id,
                            jellyfinId = uiState.ownedTmdbToJellyfin[part.id],
                            title = part.title ?: "",
                            posterUrl = part.poster_path?.let { "https://image.tmdb.org/t/p/w185$it" },
                            year = part.release_date?.take(4),
                            watched = uiState.watchedTmdbIds.contains(part.id),
                        )
                    },
                )
            }
            if (uiState.showCollections) {
                mergedCollection?.let { coll ->
                    CollectionSection(
                        collection = coll,
                        excludeKey = displayItem.tmdbId?.let { "tmdb_$it" },
                        onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                    )
                }
            }
            if (uiState.showDirector) {
                uiState.directorMovies?.let { coll ->
                    CollectionSection(
                        collection = coll,
                        excludeKey = displayItem.tmdbId?.let { "tmdb_$it" },
                        onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                    )
                }
            }
            if (uiState.showStudio) {
                uiState.studioMovies?.let { coll ->
                    CollectionSection(
                        collection = coll,
                        excludeKey = displayItem.tmdbId?.let { "tmdb_$it" },
                        onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                    )
                }
            }

            CsfdReviewsSection(reviews = uiState.csfdReviews)

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun openCsfd(context: android.content.Context, csfdId: Long) {
    val uri = Uri.parse("https://www.csfd.cz/film/$csfdId/")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActionRow(
    item: MediaItem,
    inLibrary: Boolean,
    csfdId: Long?,
    onPlayNaTv: (() -> Unit)?,
    onPlayHere: (() -> Unit)?,
    onStream: () -> Unit,
    onDownload: () -> Unit,
    onShare: ((MediaItem) -> Unit)?,
    onCsfd: (() -> Unit)?,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inLibrary) {
            // Film je v Jellyfin knihovně → přehrávání
            if (onPlayNaTv != null) {
                Button(onClick = onPlayNaTv) {
                    Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Přehrát Na TV")
                }
            }
            if (onPlayHere != null) {
                Button(onClick = onPlayHere) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("V tomto zařízení")
                }
            }
        } else {
            // Mimo knihovnu → akvizice / stream
            AssistChip(
                onClick = onDownload,
                label = { Text("Stáhnout") },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
            )
            AssistChip(
                onClick = onStream,
                label = { Text("Stremio") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            )
        }
        if (onShare != null) {
            AssistChip(
                onClick = { onShare(item) },
                label = { Text("Sdílet") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            )
        }
        if (csfdId != null && onCsfd != null) {
            AssistChip(
                onClick = onCsfd,
                label = { Text("ČSFD") },
                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
