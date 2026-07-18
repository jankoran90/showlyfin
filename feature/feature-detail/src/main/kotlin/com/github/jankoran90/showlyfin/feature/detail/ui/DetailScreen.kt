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
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Reviews
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
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
    // PROJECTOR (HUB-74): hlasový cast — po načtení detailu rovnou castni na TV/Zenbook.
    autoCastTarget: String? = null,
    autoCastAudioPath: String? = null,
    // LAPIDARY S4b: one-click z řady „Uloženo k přehrání" — po hydrataci přehraj zapamatovaný zdroj rovnou.
    autoplayRemembered: Boolean = false,
    // CONVERGE V1 — TV: D-pad doleva od nejlevější akce → Nastavení (drill). null = feature vypnutá (telefon).
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(item.traktId, item.tmdbId, item.imdbId) {
        viewModel.load(item)
        // TENFOOT KOLO2 (K): po návratu (back) na tento titul obnov stashnutou filmografii, pokud tu je.
        viewModel.reopenPendingPersonSheet(item)
    }

    // PROJECTOR (HUB-74): hlasový cast na TV/Zenbook — jednou po vstupu (auto-výběr zdroje řeší VM:
    // připnutý → knihovna → sdílej/RD dle path). Toast při odmítnutí (žádný zdroj / offline-only).
    LaunchedEffect(autoCastTarget, item.tmdbId) {
        if (autoCastTarget != null) viewModel.autoCastToTarget(autoCastTarget, autoCastAudioPath)
    }
    // LAPIDARY S4b: one-click — po hydrataci detailu přehraj zapamatovaný zdroj rovnou (guard ve VM = jednou).
    LaunchedEffect(autoplayRemembered, item.tmdbId) {
        if (autoplayRemembered) viewModel.autoplayRemembered()
    }
    LaunchedEffect(uiState.autoCastMessage) {
        uiState.autoCastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeAutoCastMessage()
        }
    }

    val displayItem = uiState.item ?: item
    val displayTitle = czechDisplayTitle(uiState.tmdbCzTitle, uiState.csfdTitle, displayItem.title)
    var showReviewsSheet by remember { mutableStateOf(false) }
    var plotExpanded by remember { mutableStateOf(false) }
    var plotOverflow by remember { mutableStateOf(false) }

    // PLAKÁT (SHW-98): sdílecí karta filmu jako obrázek (pod „⋮"). Data se skládají zde (viditelná pro
    // topBar.actions); žánry+popis stejnou fallback logikou jako hero/popis níž. Render+odeslání = core-ui ShareCard.
    val shareScope = rememberCoroutineScope()
    val onShareCard: () -> Unit = {
        val shareGenres = uiState.movieDetails?.genres
            ?.let { com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres.names(it.map { g -> g.id }, isShow = false) }?.takeIf { it.isNotEmpty() }
            ?: uiState.showDetails?.genres
                ?.let { com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres.names(it.map { g -> g.id }, isShow = true) }?.takeIf { it.isNotEmpty() }
            ?: displayItem.genres
        val tmdbOv = uiState.movieDetails?.overview ?: uiState.showDetails?.overview ?: displayItem.overview
        val tmdbCz = uiState.tmdbCzOverview
        val sharePlot = when {
            tmdbCz?.takeIf { looksCzech(it) } != null -> tmdbCz
            !uiState.csfdPlot.isNullOrBlank() -> uiState.csfdPlot
            !tmdbCz.isNullOrBlank() -> tmdbCz
            else -> tmdbOv?.takeIf { it.isNotBlank() }
        }
        val data = com.github.jankoran90.showlyfin.core.ui.ShareCardData(
            title = displayTitle,
            year = displayItem.year,
            csfdPct = uiState.csfdRating,
            directors = uiState.directors.mapNotNull { it.name },
            genres = shareGenres.orEmpty(),
            description = sharePlot,
            reviews = uiState.csfdReviews.filter { (it.rating ?: 0) >= 70 }.take(2)
                .map { com.github.jankoran90.showlyfin.core.ui.ShareReview(it.username, it.rating, it.text) },
        )
        shareScope.launch {
            try {
                com.github.jankoran90.showlyfin.core.ui.ShareCard.shareFilm(
                    context, data, displayItem.posterUrl("w500"), displayItem.backdropUrl("w1280"),
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Sdílení karty se nepovedlo", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
    // CONDUIT (SHW-58): rozcestník po ▶ Přehrát — vyber cestu zvuku, pak filtrovaný picker.
    if (uiState.showStreamPathChooser) {
        StreamPathChooserSheet(
            czCount = uiState.streams.count { isCzDub(it) },
            origCount = uiState.streams.count { !isCzDub(it) },
            isLoading = uiState.isLoadingStreams || uiState.isProbingStreams,
            onChoose = { viewModel.chooseStreamPath(it) },
            onDismiss = { viewModel.dismissStreamPathChooser() },
        )
    }
    if (uiState.showStreamPicker) {
        val path = uiState.streamAudioPath
        // CONDUIT: filtr seznamu dle zvolené cesty (null = REPRISE „zkusit jiný zdroj" → vše).
        val pathStreams = when (path) {
            com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath.CZ_DUB -> uiState.streams.filter { isCzDub(it) }
            com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath.ORIGINAL -> uiState.streams.filterNot { isCzDub(it) }
            null -> uiState.streams
        }
        val pathLabel = when (path) {
            com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath.CZ_DUB -> "CZ dabing"
            com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath.ORIGINAL -> "Originál + CZ titulky"
            null -> null
        }
        StreamPickerSheet(
            streams = pathStreams,
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
            pathLabel = pathLabel,
            onBack = if (path != null) { { viewModel.backToStreamPathChooser() } } else null,
            defaultTitle = uiState.sdilejDefaultTitle,
            defaultYear = uiState.sdilejDefaultYear,
            allowSdilejEdit = path == com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath.CZ_DUB,
            onResearchSdilej = { t, y -> viewModel.researchSdilejStreams(t, y) },
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
    // REPRISE (SHW-54): soubor nejde přehrát kvůli kontejneru/kodeku (Criterion MKV se zlib stopou apod.)
    // → jasný dialog místo tichého skoku do Stremia. „Zkusit jiný zdroj" otevře výběr v režimu Vše
    // (strict=false) → ukáže všechny alternativy (zdrojů bývá dost, jen necacheované).
    uiState.incompatibleFormatMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeIncompatibleFormat() },
            title = { Text("Soubor nejde přehrát") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.consumeIncompatibleFormat()
                    viewModel.setStreamStrict(false)
                    viewModel.openStreamPicker()
                }) { Text("Zkusit jiný zdroj") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.consumeIncompatibleFormat()
                    onStremio?.invoke(displayItem)
                }) { Text("Otevřít ve Stremiu") }
            },
        )
    }
    if (uiState.showDownloadMenu) {
        DownloadMenuSheet(
            // HOARD (SHW-84): stáhnout do telefonu = film z knihovny NEBO film se zapamatovaným zdrojem.
            canDevice = displayItem.type == MediaType.MOVIE && (uiState.isOwnedInLibrary || uiState.rememberedSource != null),
            offlineState = uiState.offlineState,
            showServerOptions = !uiState.isOwnedInLibrary,
            onDevice = { viewModel.downloadCurrentToDevice() },
            onDeleteDevice = { viewModel.deleteOfflineCurrent() },
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
            defaultTitle = uiState.sdilejDefaultTitle,
            defaultYear = uiState.sdilejDefaultYear,
            onCapture = { viewModel.captureSdilej(it) },
            onResearch = { title, year -> viewModel.researchSdilej(title, year) },
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
            // TENFOOT KOLO2 (K): stash filmografie (ne zavření) → po Zpět z filmu se sem vrátíme s obsahem.
            onPartClick = { part -> viewModel.stashPersonSheetForReturn(item); onCollectionPartClick?.invoke(part) },
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

    // TENFOOT (SHW-87) Fáze 2: na TV nativní 10-foot tělo (immersive fanart hero + akce pod popiskem
    // s auto-fokusem + „Skončí ve…"). Sdílené sheety/dialogy/LaunchedEffecty výše platí pro obě větve
    // (výběr zdroje = tentýž StreamPicker přes AdaptivePickerScaffold, playback signaling atd.).
    if (isTvFormFactor()) {
        TvDetailBody(
            displayItem = displayItem,
            displayTitle = displayTitle,
            uiState = uiState,
            viewModel = viewModel,
            onBack = onBack,
            onPlayJellyfin = onPlayJellyfin,
            onOpenReviews = { showReviewsSheet = true },
            onCollectionPartClick = onCollectionPartClick,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
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
                        ratingTarget = uiState.item?.let { m ->
                            com.github.jankoran90.showlyfin.core.ui.RatingTarget(
                                tmdbId = m.tmdbId, imdbId = m.imdbId, traktId = m.traktId,
                                title = uiState.tmdbCzTitle ?: m.displayTitle, year = m.year,
                                isShow = m.type != MediaType.MOVIE,
                            )
                        },
                        inLibrary = uiState.isOwnedInLibrary && uiState.ownedJellyfinId != null,
                        hasRemembered = uiState.rememberedSource != null,
                        onPlayHere = onPlayJellyfin?.let { cb -> { uiState.ownedJellyfinId?.let(cb) } },
                        onNaTv = onNaTv?.let { cb -> { cb(displayItem, uiState.ownedJellyfinId) } },
                        onPlayRemembered = { uiState.rememberedSource?.let { viewModel.playStream(it) } },
                        onCastRemembered = { uiState.rememberedSource?.let { viewModel.castStreamToTv(it) } },
                        onRemoveRemembered = { viewModel.removeRememberedSource() },
                        onStremio = { viewModel.openStreamPathChooser() },
                        onDownload = { viewModel.openDownloadMenu() },
                        inWatchlist = uiState.isInWatchlist,
                        isTogglingWatchlist = uiState.isTogglingWatchlist,
                        onWatchlist = { viewModel.toggleWatchlist() },
                        onShare = onShareCard,
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
            // Mapujeme genre_id → český název (TmdbGenres), ne anglické .name z TMDB detailu. Fallback na
            // displayItem.genres (už české z karty), kdyby mapa žánr neznala nebo detail nedorazil.
            val genres = uiState.movieDetails?.genres
                ?.let { com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres.names(it.map { g -> g.id }, isShow = false) }
                ?.takeIf { it.isNotEmpty() }
                ?: uiState.showDetails?.genres
                    ?.let { com.github.jankoran90.showlyfin.data.tmdb.model.TmdbGenres.names(it.map { g -> g.id }, isShow = true) }
                    ?.takeIf { it.isNotEmpty() }
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
                    // CELLULOID M2.6 immersive: pod názvem kompaktní meta řádek — Režie + žánry (dosud
                    // viditelné jen v sekci „Tvůrci" níž). Aditivní: nic neubírá, obohacuje hero obou appek.
                    val directorLine = uiState.directors.mapNotNull { it.name }.filter { it.isNotBlank() }.take(2).joinToString(", ")
                    val metaParts = buildList {
                        if (directorLine.isNotBlank()) add("Režie: $directorLine")
                        genres?.takeIf { it.isNotEmpty() }?.let { add(it.take(3).joinToString(" · ")) }
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString("   ·   "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                // Kompaktní šipka (ne 48dp IconButton) — ať pod ní nezbývá zbytečná mezera před „Tvůrci".
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (plotExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (plotExpanded) "Sbalit" else "Zobrazit víc",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { plotExpanded = !plotExpanded }
                            .tvFocusable(shape = CircleShape)
                            .padding(4.dp)
                            .size(28.dp),
                    )
                }
            }
            if (!plot.isNullOrBlank()) Spacer(Modifier.height(4.dp))

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

            // TENFOOT WS-C (SHW-87): sezóny/epizody seriálu (telefon i TV; klik → stream flow epizody).
            if (uiState.showSeasons && displayItem.type == MediaType.SHOW && uiState.seasons.isNotEmpty()) {
                SeasonEpisodeSection(
                    seasons = uiState.seasons,
                    selectedSeason = uiState.selectedSeason,
                    episodes = uiState.seasonEpisodes,
                    isLoadingEpisodes = uiState.isLoadingEpisodes,
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onPlayEpisode = { s, e, t -> viewModel.playEpisode(s, e, t) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Kompaktní akční lišta detailu (telefon, v TopAppBar). Redesign CELLULOID (2026-07-18, user „zkompaktnit"):
 * primární vyplněné „Přehrát" (v knihovně / zapamatovaný zdroj) + „na TV" hned vedle; když zdroj ještě není,
 * ODLIŠNÉ obrysové „Hledat zdroje". Vše ostatní (hodnocení, oblíbené, watchlist, stáhnout, odebrat zdroj) je
 * schované pod jedno přetékací „⋮". [order]/[isTogglingWatchlist] zůstávají v signatuře kvůli volajícímu.
 */
@Composable
private fun DetailActionBar(
    order: List<String>,
    isMovie: Boolean,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    ratingTarget: com.github.jankoran90.showlyfin.core.ui.RatingTarget? = null,
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
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // BESPOKE F3 — vlastní hvězdičkové hodnocení (sdílený dialog přes LocalUserRatingProvider).
    val ratingProvider = com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider.current
    val stars = if (ratingTarget != null) {
        com.github.jankoran90.showlyfin.core.ui.rememberCardRating(ratingTarget.tmdbId, ratingTarget.imdbId)
    } else null

    // Primární přehrávání: v knihovně = Přehrát zde; zapamatovaný zdroj = Přehrát; jinak zatím není co hrát.
    val onPlayPrimary: (() -> Unit)? = when {
        inLibrary -> onPlayHere
        hasRemembered -> onPlayRemembered
        else -> null
    }
    val onTv: (() -> Unit)? = when {
        inLibrary -> onNaTv
        hasRemembered -> onCastRemembered
        else -> null
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            // Máme co přehrát rovnou → vyplněné primární „Přehrát" (akcent) + „na TV" hned vedle.
            onPlayPrimary != null -> {
                Button(onClick = onPlayPrimary, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Přehrát")
                }
                if (onTv != null) {
                    OutlinedIconButton(onClick = onTv) { Icon(Icons.Default.Cast, "Přehrát na TV") }
                }
            }
            // Ještě nemáme zdroj → ODLIŠNÉ obrysové „Hledat zdroje" (ať se neplete s instant-play).
            !inLibrary -> {
                OutlinedButton(onClick = onStremio, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Hledat zdroje")
                }
            }
        }

        // Vše ostatní kompaktně pod jedno „⋮" (hodnocení, oblíbené, watchlist, stáhnout, odebrat zdroj).
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Další akce") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (ratingTarget != null && ratingProvider != null) {
                    DropdownMenuItem(
                        text = { Text(if (stars != null) "Hodnocení $stars/10" else "Ohodnotit") },
                        leadingIcon = { Icon(Icons.Filled.Reviews, null) },
                        onClick = { menuOpen = false; ratingProvider.requestRate(ratingTarget) },
                    )
                }
                if (isMovie) {
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "V oblíbených" else "Přidat do oblíbených") },
                        leadingIcon = { Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, null) },
                        onClick = { menuOpen = false; onFavorite() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (inWatchlist) "Odebrat ze seznamu" else "Chci vidět") },
                    leadingIcon = { Icon(if (inWatchlist) Icons.Default.Check else Icons.Default.Add, null) },
                    onClick = { menuOpen = false; onWatchlist() },
                )
                DropdownMenuItem(
                    text = { Text("Stáhnout") },
                    leadingIcon = { Icon(Icons.Default.Download, null) },
                    onClick = { menuOpen = false; onDownload() },
                )
                if (onShare != null) {
                    DropdownMenuItem(
                        text = { Text("Sdílet kartu") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { menuOpen = false; onShare() },
                    )
                }
                if (!inLibrary && hasRemembered) {
                    DropdownMenuItem(
                        text = { Text("Odebrat zapamatovaný zdroj") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onRemoveRemembered() },
                    )
                }
            }
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
