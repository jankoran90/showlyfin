package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.github.jankoran90.showlyfin.core.domain.MediaType
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: MediaItem,
    onBack: () -> Unit,
    sectionTitle: String = "",
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
            runtimeMin = uiState.movieDetails?.runtime,
            rememberedSource = uiState.rememberedSource,
            onForgetRemembered = { viewModel.forgetWorkingSource() },
        )
    }
    // SIEVE S2: po lokálním přehrání Stremio zdroje se zeptej, jestli sedl → zapamatuj fungující zdroj.
    uiState.pendingWorkingConfirm?.let { stream ->
        val srcName = (stream.name ?: stream.description)?.replace("\n", " ")?.trim()?.ifBlank { null } ?: "tento zdroj"
        AlertDialog(
            onDismissRequest = { viewModel.dismissWorkingConfirm() },
            title = { Text("Fungoval tenhle zdroj?") },
            text = { Text("Byl to správný film? Zapamatuju si ho a příště ti ho u tohoto filmu nabídnu rovnou nahoře — i pro přehrání na TV.\n\n$srcName") },
            confirmButton = { TextButton(onClick = { viewModel.confirmWorkingSource() }) { Text("Ano, zapamatovat ⭐") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissWorkingConfirm() }) { Text("Ne") } },
        )
    }
    // Plan WINNOW (SHW-41, item 1): titul blokovaný na RD (DMCA) → jasný dialog místo tichého skoku.
    uiState.blockedDmcaMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeBlockedDmca() },
            title = { Text("Titul je blokovaný (DMCA)") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.consumeBlockedDmca()
                    onStremio?.invoke(displayItem)
                }) { Text("Otevřít ve Stremiu") }
            },
            dismissButton = { TextButton(onClick = { viewModel.consumeBlockedDmca() }) { Text("Zavřít") } },
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
    // Plan ENSEMBLE (SHW-45): tvorba zvolené osoby (klik na herce/režii/scénář/kameru).
    if (uiState.showPersonSheet) {
        PersonFilmographySheet(
            name = uiState.personSheetName,
            loading = uiState.personSheetLoading,
            collection = uiState.personFilmography,
            roleLabel = uiState.personSheetRoleLabel,
            onPartClick = { part -> viewModel.closePersonSheet(); onCollectionPartClick?.invoke(part) },
            onDismiss = { viewModel.closePersonSheet() },
            canFavorite = uiState.personSheetKind != null,
            isFavorite = uiState.isPersonFavorite,
            onToggleFavorite = { viewModel.togglePersonFavorite() },
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
                // VANTAGE D2: text u šipky Zpět = NÁZEV SEKCE odkud jdu (ne název filmu).
                title = { Text(sectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusable(shape = CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                // VANTAGE D1: akční tlačítka NAD fanart do horní lišty vpravo (kulaté ikony).
                actions = {
                    DetailActionBar(
                        order = uiState.actionOrder,
                        isMovie = uiState.item?.type == MediaType.MOVIE,
                        isFavorite = uiState.isFavorite,
                        onFavorite = { viewModel.toggleFavorite() },
                        inLibrary = uiState.isOwnedInLibrary && uiState.ownedJellyfinId != null,
                        hasRemembered = uiState.rememberedSource != null,
                        onPlayHere = onPlayJellyfin?.let { cb -> { uiState.ownedJellyfinId?.let(cb) } },
                        onNaTv = onNaTv?.let { cb -> { cb(displayItem, uiState.ownedJellyfinId) } },
                        onPlayRemembered = { uiState.rememberedSource?.let { viewModel.playStream(it) } },
                        onCastRemembered = { uiState.rememberedSource?.let { viewModel.castStreamToTv(it) } },
                        onRemoveRemembered = { viewModel.removeRememberedSource() },
                        onStremio = { viewModel.openStreamPicker() },
                        onDownload = { viewModel.openDownloadMenu() },
                        inWatchlist = uiState.isInWatchlist,
                        isTogglingWatchlist = uiState.isTogglingWatchlist,
                        onWatchlist = { viewModel.toggleWatchlist() },
                    )
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

        // VISTA V4: detail se nenačetl (typicky výpadek sítě) → srozumitelná hláška + „Zkusit znovu",
        // místo prázdného/zaseknutého detailu (dřív se ukázal i starý film z race).
        if (uiState.error != null && uiState.movieDetails == null && uiState.showDetails == null && !uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        uiState.error ?: "Detail se nepodařilo načíst.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retry() }) { Text("Zkusit znovu") }
                }
            }
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
            // CANVAS A: klik na COVER (poster) → galerie; klik na ČSFD badge → recenze; kompaktní
            // kulatá akční lišta nahoře vpravo (u Oblíbených).
            val backdropUrl = displayItem.backdropUrl()
            val posterUrl = displayItem.posterUrl()
            val hasGallery = uiState.csfdId != null && uiState.uploaderConfigured
            val hasReviews = uiState.csfdReviews.isNotEmpty()
            // Žánry — předávají se do sekce „Tvůrci" jako druhý sloupec (VANTAGE D4), z fanartu pryč.
            val genres = uiState.movieDetails?.genres?.map { it.name }
                ?: uiState.showDetails?.genres?.map { it.name }
                ?: displayItem.genres
            // Výška se přizpůsobí obsahu (min 200dp) — fanart kreslíme na pozadí přes matchParentSize,
            // takže delší název / víc žánrů nikdy neořízne hero info.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                // VANTAGE D3: cover art nahoře, pod ním řádek [název · rok · ČSFD] dole ve stínu
                // (akční tlačítka přesunuta do horní lišty; žánry do sekce „Tvůrci").
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (posterUrl != null) {
                        Box(
                            Modifier
                                .width(96.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                // CANVAS A1: klik na cover = galerie (zrušeno samostatné tlačítko Galerie).
                                .then(if (hasGallery) Modifier.clickable { viewModel.openGallery() }.tvFocusable(shape = RoundedCornerShape(8.dp)) else Modifier),
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = displayItem.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    // [název · rok · ČSFD] v JEDNOM řádku
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        displayItem.year?.let {
                            Text("$it", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                        // ČSFD hodnocení v % (místo TMDB); fallback na hvězdičkové hodnocení, když ČSFD chybí.
                        val csfdRating = uiState.csfdRating
                        if (csfdRating != null) {
                            // CANVAS A2: klik na ČSFD badge = recenze (zrušeno samostatné tlačítko).
                            CsfdRatingBadge(
                                rating = csfdRating,
                                big = true,
                                modifier = if (hasReviews) Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showReviewsSheet = true }
                                    .tvFocusable(shape = RoundedCornerShape(6.dp)) else Modifier,
                            )
                        } else {
                            displayItem.rating?.let { rating ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                                    Text("%.1f".format(rating), style = MaterialTheme.typography.titleMedium, color = Color.White)
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

            // VANTAGE (SHW-48): šipka rozbalení popisu odhalí i blok Tvůrci (Scénář/Kamera + žánry).
            // Pás herců + režisérů je vidět vždy; šipka se ukáže i u krátkého/chybějícího popisu,
            // pokud je co odhalit.
            val hasRevealableDetails = uiState.showCreators &&
                (uiState.writers.isNotEmpty() || uiState.cinematographers.isNotEmpty() || !genres.isNullOrEmpty())
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
            }
            if (plotOverflow || plotExpanded || hasRevealableDetails) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { plotExpanded = !plotExpanded }, modifier = Modifier.tvFocusable(shape = CircleShape)) {
                        Icon(
                            imageVector = if (plotExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (plotExpanded) "Sbalit" else "Zobrazit víc",
                        )
                    }
                }
            }
            if (!plot.isNullOrBlank()) Spacer(Modifier.height(12.dp))

            // CANVAS A: akce (Galerie přes cover, ČSFD recenze přes badge, Přehrát/Na TV/Stremio/
            // Stáhnout/Oblíbené/Chci vidět) jsou v kompaktní kulaté liště v hero (viz DetailActionBar výše).

            // Plan ENSEMBLE (SHW-45): sekce „Tvůrci" (pás herců + Režie/Scénář/Kamera) NAD kolekcemi.
            if (uiState.showCreators) {
                CreatorsSection(
                    cast = uiState.cast,
                    directors = uiState.directors,
                    writers = uiState.writers,
                    cinematographers = uiState.cinematographers,
                    onPersonClick = { person, kind -> viewModel.openPersonFilmography(person, kind) },
                    // VANTAGE D4: žánry z fanartu sem jako druhý sloupec (proklik žánr×režisér = později).
                    genres = genres.orEmpty(),
                    // VANTAGE (SHW-48): Scénář/Kamera + žánry skryté, odhalí je rozbalení popisu.
                    detailsVisible = plotExpanded,
                )
            }

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

/**
 * CANVAS (SHW-47) A3/A4: kompaktní kulatá akční lišta v hero (u Oblíbených). Pořadí podle
 * [order] (konfigurovatelné v Nastavení); každá akce se ukáže jen když dává v daném kontextu smysl
 * (v knihovně = Přehrát zde / Na TV; mimo = Stremio / Stáhnout; ⭐ zapamatovaný zdroj = Přehrát/Na TV).
 */
@Composable
private fun DetailActionBar(
    order: List<String>,
    isMovie: Boolean,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    inLibrary: Boolean,
    hasRemembered: Boolean,
    onPlayHere: (() -> Unit)?,
    onNaTv: (() -> Unit)?,
    onPlayRemembered: () -> Unit,
    onCastRemembered: () -> Unit,
    onRemoveRemembered: () -> Unit,
    onStremio: () -> Unit,
    onDownload: () -> Unit,
    inWatchlist: Boolean,
    isTogglingWatchlist: Boolean,
    onWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        order.forEach { key ->
            when (key) {
                "favorite" -> if (isMovie) {
                    HeroAction(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Oblíbené", onFavorite, active = isFavorite)
                }
                "play" -> {
                    val cb = if (inLibrary) onPlayHere else if (hasRemembered) onPlayRemembered else null
                    if (cb != null) HeroAction(Icons.Default.PhoneAndroid, "Přehrát zde", cb)
                }
                "tv" -> {
                    val cb = if (inLibrary) onNaTv else if (hasRemembered) onCastRemembered else null
                    if (cb != null) HeroAction(Icons.Default.Cast, "Přehrát na TV", cb)
                }
                "stremio" -> if (!inLibrary) HeroAction(Icons.Default.PlayArrow, "Stremio", onStremio)
                "download" -> if (!inLibrary) HeroAction(Icons.Default.Download, "Stáhnout", onDownload)
                "watchlist" -> HeroAction(
                    if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                    if (inWatchlist) "V seznamu" else "Chci vidět",
                    onWatchlist, active = inWatchlist, loading = isTogglingWatchlist,
                )
            }
        }
        // Odebrání zapamatovaného zdroje (mimo lištu/pořadí, jen když je co odebrat).
        if (!inLibrary && hasRemembered) {
            HeroAction(Icons.Default.Delete, "Odebrat zapamatovaný zdroj", onRemoveRemembered, danger = true)
        }
    }
}

/** Jedno kulaté akční tlačítko v hero — tmavý scrim kroužek + ikona (čitelné na fanartu). */
@Composable
private fun HeroAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    active: Boolean = false,
    danger: Boolean = false,
    loading: Boolean = false,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .tvFocusable(shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = when {
                    danger -> MaterialTheme.colorScheme.error
                    active -> MaterialTheme.colorScheme.primary
                    else -> Color.White
                },
            )
        }
    }
}
