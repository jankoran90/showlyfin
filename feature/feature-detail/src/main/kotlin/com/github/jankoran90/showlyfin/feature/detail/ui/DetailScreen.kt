package com.github.jankoran90.showlyfin.feature.detail.ui

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.feature.detail.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onSmartDetect: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(item.traktId) { viewModel.load(item) }

    val displayItem = uiState.item ?: item

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
            if (!tmdbOverview.isNullOrBlank()) {
                Text(
                    text = tmdbOverview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            val csfdPlot = uiState.csfdPlot
            if (!csfdPlot.isNullOrBlank() && csfdPlot != tmdbOverview) {
                Text(
                    text = "Popis (ČSFD)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = csfdPlot,
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

            if (onSmartDetect != null && displayItem.imdbId != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onSmartDetect(displayItem) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text("Smart Remux (4K + CZ audio)")
                }
                Spacer(Modifier.height(8.dp))
            }

            CsfdReviewsSection(reviews = uiState.csfdReviews)

            Spacer(Modifier.height(24.dp))
        }
    }
}
