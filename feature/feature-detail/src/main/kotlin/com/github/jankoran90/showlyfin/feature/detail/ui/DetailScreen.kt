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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
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
    // Plan FERRY (SHW-37): výsledek odeslání na TV → toast
    LaunchedEffect(uiState.castToTvResult) {
        uiState.castToTvResult?.let {
            val msg = when (it) {
                com.github.jankoran90.showlyfin.data.jellyfin.CastResult.SENT -> "Odesláno na TV ▶"
                com.github.jankoran90.showlyfin.data.jellyfin.CastResult.NO_SESSION -> "Žádná TV neběží — zapni Yellyfin na boxu (Ovladač)."
                com.github.jankoran90.showlyfin.data.jellyfin.CastResult.NO_CREDS -> "Chybí přihlášení k Jellyfinu (Nastavení)."
                com.github.jankoran90.showlyfin.data.jellyfin.CastResult.FAILED -> "Odeslání na TV selhalo."
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeCastResult()
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
            onCastToTv = { viewModel.castStreamToTv(it) },
            isCasting = uiState.isCastingToTv,
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
    if (uiState.showGallery) {
        CsfdGalleryDialog(
            urls = uiState.csfdGallery,
            isLoading = uiState.isGalleryLoading,
            onDismiss = { viewModel.dismissGallery() },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(displayTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusable(shape = CircleShape)) {
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
            // ── HERO: fanart s cover posterem + názvem + rokem + ČSFD% zarovnanými dolů do stínu ──
            // Klik na fanart (i poster/info) → fullscreen ČSFD galerie (F3, lazy load).
            val backdropUrl = displayItem.backdropUrl()
            val posterUrl = displayItem.posterUrl()
            val hasGallery = uiState.csfdId != null && uiState.uploaderConfigured
            // Žánry — zobrazují se v hero sloupci pod rokem, vedle cover artu (max 3 v řadě).
            val genres = uiState.movieDetails?.genres?.map { it.name }
                ?: uiState.showDetails?.genres?.map { it.name }
                ?: displayItem.genres
            // Výška se přizpůsobí obsahu (min 200dp) — fanart kreslíme na pozadí přes matchParentSize,
            // takže delší název / víc žánrů nikdy neořízne hero info.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (hasGallery) Modifier.clickable { viewModel.openGallery() }.tvFocusable(shape = RoundedCornerShape(0.dp)) else Modifier),
            ) {
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Silný spodní scrim → text i poster čitelné i na světlém fanartu; plynule přejde do pozadí.
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            0.7f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            1.0f to MaterialTheme.colorScheme.background,
                        )
                    )
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (posterUrl != null) {
                        Box(
                            Modifier
                                .width(96.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
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
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            displayItem.year?.let {
                                Text("$it", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
                            }
                            // ČSFD hodnocení v % (místo TMDB); fallback na hvězdičkové hodnocení, když ČSFD chybí
                            val csfdRating = uiState.csfdRating
                            if (csfdRating != null) {
                                CsfdRatingBadge(rating = csfdRating, big = true)
                            } else {
                                displayItem.rating?.let { rating ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                                        Text("%.1f".format(rating), style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    }
                                }
                            }
                        }
                        // ── Žánrové štítky pod rokem, vedle cover artu — max 3 vedle sebe, zbytek na další řádek ──
                        if (!genres.isNullOrEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                maxItemsInEachRow = 3,
                            ) {
                                genres.forEach { genre ->
                                    AssistChip(onClick = {}, label = { Text(genre, style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }
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
                        IconButton(onClick = { plotExpanded = !plotExpanded }, modifier = Modifier.tvFocusable(shape = CircleShape)) {
                            Icon(
                                imageVector = if (plotExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (plotExpanded) "Sbalit popis" else "Zobrazit celý popis",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── ČSFD akce (galerie + recenze) — vystředěné pod plot, nad akčními tlačítky ──
            if (hasGallery || uiState.csfdReviews.isNotEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasGallery) {
                            OutlinedButton(onClick = { viewModel.openGallery() }, modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50))) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Galerie")
                            }
                        }
                        if (uiState.csfdReviews.isNotEmpty()) {
                            OutlinedButton(onClick = { showReviewsSheet = true }, modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50))) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("ČSFD recenze (${uiState.csfdReviews.size})")
                            }
                        }
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
                Button(onClick = onPlayNaTv, modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50))) {
                    Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Přehrát Na TV")
                }
            }
            if (onPlayHere != null) {
                Button(onClick = onPlayHere, modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50))) {
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
                modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50)),
            )
            AssistChip(
                onClick = onStream,
                label = { Text("Stremio") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50)),
            )
        }
        FilterChip(
            selected = inWatchlist,
            onClick = onWatchlist,
            enabled = !isTogglingWatchlist,
            modifier = Modifier.tvFocusable(shape = RoundedCornerShape(percent = 50)),
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
