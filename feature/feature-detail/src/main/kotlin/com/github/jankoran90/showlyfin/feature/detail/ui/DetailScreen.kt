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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Heuristika „text je v češtině" — přítomnost znaku z české abecedy s diakritikou.
 *  Použito pro fallback popisu/názvu na ČSFD, když TMDB vrátí prázdný/cizojazyčný text. */
private fun looksCzech(t: String?): Boolean =
    !t.isNullOrBlank() && t.any { it in "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" }

/** Český zobrazovaný název: český TMDB → ČSFD → (jakýkoliv cs/ČSFD) → originál.
 *  Stejný fallback princip jako u popisu (TMDB chybí / neplatný / cizojazyčný → ČSFD). */
private fun czechDisplayTitle(tmdbCzTitle: String?, csfdTitle: String?, original: String): String =
    tmdbCzTitle?.takeIf { looksCzech(it) }
        ?: csfdTitle?.takeIf { looksCzech(it) }
        ?: tmdbCzTitle?.takeIf { it.isNotBlank() }
        ?: csfdTitle?.takeIf { it.isNotBlank() }
        ?: original

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onSmartDetect: ((MediaItem) -> Unit)? = null,
    onNaTv: ((MediaItem, jellyfinId: String?) -> Unit)? = null,
    onStremio: ((MediaItem) -> Unit)? = null,
    onCollectionPartClick: ((CollectionPart) -> Unit)? = null,
    onPlayJellyfin: ((String) -> Unit)? = null,
    onPlayStreamUrl: ((String, String, com.github.jankoran90.showlyfin.data.uploader.model.SubtitleQuery?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(item.traktId, item.tmdbId, item.imdbId) { viewModel.load(item) }

    val displayItem = uiState.item ?: item
    val displayTitle = czechDisplayTitle(uiState.tmdbCzTitle, uiState.csfdTitle, displayItem.title)
    var showReviewsSheet by remember { mutableStateOf(false) }
    var plotExpanded by remember { mutableStateOf(false) }
    var plotOverflow by remember { mutableStateOf(false) }

    // Stremio stream resolved → přehraj externí URL
    LaunchedEffect(uiState.pendingPlaybackUrl) {
        val url = uiState.pendingPlaybackUrl ?: return@LaunchedEffect
        onPlayStreamUrl?.invoke(url, uiState.pendingPlaybackTitle, uiState.pendingSubtitleQuery)
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
    // CASCADE Fáze 4: auto-advance po chybě přehrávání → krátká info hláška
    LaunchedEffect(uiState.autoAdvanceInfo) {
        uiState.autoAdvanceInfo?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeAutoAdvanceInfo()
        }
    }

    uiState.rdDownload?.let { rd ->
        RdDownloadDialog(state = rd, onCancel = { viewModel.cancelRdDownload() })
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
            isProbing = uiState.isProbingStreams,
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
    if (showReviewsSheet) {
        CsfdReviewsBottomSheet(
            reviews = uiState.csfdReviews,
            title = displayTitle,
            year = displayItem.year,
            onDismiss = { showReviewsSheet = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(displayTitle, maxLines = 1) },
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
                    // Bez parallax posunu — obrázek i gradient overlay scrollují jako jeden celek,
                    // takže se spodní fade vždy kryje s obrázkem (dřív parallax rozjížděl overlay nad obrázek).
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
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
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        displayItem.year?.let {
                            Text("$it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // ČSFD hodnocení v % (místo TMDB); fallback na hvězdičkové hodnocení, když ČSFD chybí
                        val csfdRating = uiState.csfdRating
                        if (csfdRating != null) {
                            CsfdRatingBadge(rating = csfdRating, big = true)
                        } else {
                            displayItem.rating?.let { rating ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                                    Text("%.1f".format(rating), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }

            // ── Žánrové odznáčky (pod rokem + hodnocením ČSFD) ──
            val genres = uiState.movieDetails?.genres?.map { it.name }
                ?: uiState.showDetails?.genres?.map { it.name }
                ?: displayItem.genres
            if (!genres.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
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

            Spacer(Modifier.height(16.dp))

            // ── Popis (JEDEN, fallback) — sbalený na N řádků + šipka rozbalit/sbalit ──
            val tmdbOverview = uiState.movieDetails?.overview ?: uiState.showDetails?.overview ?: displayItem.overview
            val tmdbCz = uiState.tmdbCzOverview
            val csfdPlot = uiState.csfdPlot

            // ČSFD popis = ČISTÝ FALLBACK. Použije se jen když český TMDB popis chybí, je prázdný
            // nebo není v češtině (TMDB u `cs` občas vrátí cizojazyčný text). Vždy JEDEN popis.
            val czechTmdb = tmdbCz?.takeIf { looksCzech(it) }
            val plot: String?
            val plotSource: String?
            when {
                czechTmdb != null -> { plot = czechTmdb; plotSource = null }
                !csfdPlot.isNullOrBlank() -> { plot = csfdPlot; plotSource = "ČSFD" }
                !tmdbCz.isNullOrBlank() -> { plot = tmdbCz; plotSource = null }
                else -> { plot = tmdbOverview?.takeIf { it.isNotBlank() }; plotSource = null }
            }

            if (!plot.isNullOrBlank()) {
                if (plotSource != null) {
                    Text(
                        text = "Popis ($plotSource)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                val collapsedLines = uiState.plotCollapsedLines
                val limitActive = collapsedLines > 0 && !plotExpanded
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (limitActive) collapsedLines else Int.MAX_VALUE,
                    overflow = if (limitActive) TextOverflow.Ellipsis else TextOverflow.Clip,
                    onTextLayout = { if (limitActive) plotOverflow = it.hasVisualOverflow },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (plotOverflow || plotExpanded) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { plotExpanded = !plotExpanded }) {
                            Icon(
                                imageVector = if (plotExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (plotExpanded) "Sbalit popis" else "Zobrazit celý popis",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── ČSFD recenze (vystředěný button pod plot, nad akčními tlačítky) ──
            if (uiState.csfdReviews.isNotEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { showReviewsSheet = true }) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("ČSFD recenze (${uiState.csfdReviews.size})")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            DetailActionRow(
                inLibrary = uiState.isOwnedInLibrary && uiState.ownedJellyfinId != null,
                onPlayNaTv = onNaTv?.let { cb -> { cb(displayItem, uiState.ownedJellyfinId) } },
                onPlayHere = onPlayJellyfin?.let { cb -> { uiState.ownedJellyfinId?.let(cb) } },
                onStream = { viewModel.openStreamPicker() },
                onDownload = { viewModel.openDownloadMenu() },
                inWatchlist = uiState.isInWatchlist,
                isTogglingWatchlist = uiState.isTogglingWatchlist,
                onWatchlist = { viewModel.toggleWatchlist() },
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

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActionRow(
    inLibrary: Boolean,
    onPlayNaTv: (() -> Unit)?,
    onPlayHere: (() -> Unit)?,
    onStream: () -> Unit,
    onDownload: () -> Unit,
    inWatchlist: Boolean,
    isTogglingWatchlist: Boolean,
    onWatchlist: () -> Unit,
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
        FilterChip(
            selected = inWatchlist,
            onClick = onWatchlist,
            enabled = !isTogglingWatchlist,
            label = { Text(if (inWatchlist) "V seznamu" else "Chci vidět") },
            leadingIcon = {
                if (isTogglingWatchlist) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                    )
                }
            },
        )
    }
    Spacer(Modifier.height(8.dp))
}
