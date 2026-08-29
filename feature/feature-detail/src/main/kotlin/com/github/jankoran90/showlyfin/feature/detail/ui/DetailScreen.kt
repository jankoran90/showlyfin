package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
// SEZONA (SHW-113) f2 — přepínač zvukové stopy v ⋮ menu.
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.CollectionPart
import com.github.jankoran90.showlyfin.core.ui.CollectionSection
import com.github.jankoran90.showlyfin.core.ui.MediaCollection
import com.github.jankoran90.showlyfin.feature.detail.DetailRowKeys
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // ORCHARD (user 07-19, Filmy) — cast NEMÁ samostatné tlačítko, akce „Přehrát na TV" jde do ⋮ menu. Jen Filmy
    // (showlyfin default false = ikona Cast vedle Přehrát beze změny).
    castInOverflow: Boolean = false,
    // user 2026-08-01 („doporučení by mohlo být v menu karty filmu") — akce „Doporuč podobné" v ⋮:
    // vezme TENHLE titul jako referenci a otevře sekci „Podle filmu". null = shell ji nenapojil (TV).
    onSimilar: ((MediaItem) -> Unit)? = null,
    // VESTIBUL (user 2026-08-24) — kartu otevřel klik na konkrétní díl (řada „Další díly"): vyber
    // jeho sezónu a označ ho. null = běžný vstup na titul.
    focusSeason: Int? = null,
    focusEpisode: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(item.traktId, item.tmdbId, item.imdbId, focusSeason, focusEpisode) {
        viewModel.load(item)
        // POŘADÍ: až PO `load` — ten stav titulu resetuje (`focusedEpisode = null`), takže opačně
        // by označení dílu smazal.
        viewModel.focusEpisode(focusSeason, focusEpisode)
        // TENFOOT KOLO2 (K): po návratu (back) na tento titul obnov stashnutou filmografii, pokud tu je.
        viewModel.reopenPendingPersonSheet(item)
        // CURTAIN: na TV je přehrávač destinace TÉŽE Activity → detail se sem vrací novou kompozicí (ne
        // ON_RESUME). Obnovu stavu epizod proto pověsíme i sem; bez načteného seriálu je to no-op.
        viewModel.refreshEpisodeStatus()
    }

    // CURTAIN (SHW-109): návrat z přehrávače → přenačti fajfky/„Pokračovat" u epizod. `load()` má na tentýž
    // titul early-return, takže bez tohohle by dokoukaný díl vypadal pořád jako nezhlédnutý.
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshEpisodeStatus()
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
        // SUMÁŘ (SHW-122), user 2026-08-28 („1-ano ma nahradit"): když je hotový kurátorský text,
        // jde na kartu MÍSTO oficiálního popisu i místo citací recenzí — jinak by se to na jednu
        // stránku nevešlo. Než se text upeče (16–20 s), karta vypadá jako dosud.
        val curated = uiState.curatedText?.takeIf { it.isNotBlank() }
        val data = com.github.jankoran90.showlyfin.core.ui.ShareCardData(
            title = displayTitle,
            year = displayItem.year,
            csfdPct = uiState.csfdRating,
            directors = uiState.directors.mapNotNull { it.name },
            genres = shareGenres.orEmpty(),
            description = curated ?: sharePlot,
            reviews = if (curated != null) emptyList() else uiState.csfdReviews.filter { (it.rating ?: 0) >= 70 }.take(2)
                .map { com.github.jankoran90.showlyfin.core.ui.ShareReview(it.username, it.rating, it.text) },
            curated = curated != null,
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
            onPin = { viewModel.pinWorkingSource(it) },
            pathLabel = pathLabel,
            onBack = if (path != null) { { viewModel.backToStreamPathChooser() } } else null,
            defaultTitle = uiState.sdilejDefaultTitle,
            defaultYear = uiState.sdilejDefaultYear,
            allowSdilejEdit = path == com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath.CZ_DUB,
            onResearchSdilej = { t, y -> viewModel.researchSdilejStreams(t, y) },
            // SEZONA (SHW-113) f2 — u seriálu navíc „použij pro celou sezónu"; u filmu se nezobrazí.
            seasonNumber = if (displayItem.type == MediaType.SHOW) uiState.selectedSeason else null,
            hasSeasonSource = uiState.hasSeasonSource,
            onPinSeason = { viewModel.pinSeasonSource(it) },
            onForgetSeason = { viewModel.forgetSeasonSource() },
        )
    }
    // user 2026-08-24: *„po ohodnocení by mohl vyskočit dialog, zda se má položka odebrat z Filmotéky
    // — tím se odebere zdroj, Trakt chci vidět a je to čisté"*. Hodnocení je tečka za dokoukáním,
    // takže je to přirozená chvíle na úklid. Ptáme se JEN u titulu, který je zrovna otevřený, a jen
    // jednou (událost se po zobrazení spotřebuje).
    val ratingHost = com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider.current
    var askRemoveAfterRating by remember { mutableStateOf(false) }
    if (ratingHost != null && uiState.askRemoveAfterRating) {
        val justRated by ratingHost.justRated.collectAsStateWithLifecycle()
        LaunchedEffect(justRated) {
            val t = justRated ?: return@LaunchedEffect
            val open = uiState.item
            val sameTitle = open != null && (
                (t.tmdbId != null && t.tmdbId == open.tmdbId) ||
                    (!t.imdbId.isNullOrBlank() && t.imdbId == open.imdbId)
                )
            ratingHost.consumeJustRated()
            if (sameTitle) askRemoveAfterRating = true
        }
    }
    if (askRemoveAfterRating) {
        AlertDialog(
            onDismissRequest = { askRemoveAfterRating = false },
            title = { Text("Odebrat z Filmotéky?") },
            text = {
                Text(
                    "Ohodnoceno — mám titul uklidit? Zapomenu uložené zdroje a odeberu ho z „Chci vidět\" " +
                        "(u nás i na Traktu). Oblíbené a Jellyfin knihovnu nechám být.",
                )
            },
            confirmButton = {
                TextButton(onClick = { askRemoveAfterRating = false; viewModel.removeFromFilmoteka() }) {
                    Text("Odebrat")
                }
            },
            dismissButton = { TextButton(onClick = { askRemoveAfterRating = false }) { Text("Nechat") } },
        )
    }
    // SIEVE S2: po lokálním přehrání Stremio zdroje se zeptej, jestli sedl → zapamatuj fungující zdroj.
    // user 2026-08-29 (SPYGLASS, Big Mouth „Joy" balík): u season-packu nabídni rovnou i "pro celou
    // sezónu" v tomhle dialogu, ať to nezůstane skryté v samostatném ovladači.
    uiState.pendingWorkingConfirm?.let { stream ->
        val srcName = (stream.name ?: stream.description)?.replace("\n", " ")?.trim()?.ifBlank { null } ?: "tento zdroj"
        val isSeasonPack = uiState.selectedSeason != null && stream.quality.seasonPack
        // user 2026-08-29 08:42: „…na celou sezónu A CELÝ SERIÁL" — druhá možnost jen u multi-season
        // seriálů (u jedno-sezónního by byla redundatní s „celou sezónou").
        val multiSeason = uiState.seasons.count { it.season_number >= 1 } > 1
        AlertDialog(
            onDismissRequest = { viewModel.dismissWorkingConfirm() },
            title = { Text("Fungoval tenhle zdroj?") },
            text = {
                Text(
                    if (isSeasonPack && multiSeason) {
                        "Byl to správný díl? Je to balík sezóny — zapamatuju si ho pro tenhle díl, " +
                            "pro celou sezónu, nebo rovnou pro celý seriál (každou sezónu dohledám " +
                            "podle stejného release).\n\n$srcName"
                    } else if (isSeasonPack) {
                        "Byl to správný díl? Je to balík celé sezóny — zapamatuju si ho pro tenhle díl, " +
                            "nebo rovnou pro celou sezónu, ať další díly hrají bez ptaní.\n\n$srcName"
                    } else {
                        "Byl to správný film? Zapamatuju si ho a příště ti ho u tohoto filmu nabídnu rovnou nahoře — i pro přehrání na TV.\n\n$srcName"
                    },
                )
            },
            confirmButton = {
                if (isSeasonPack) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { viewModel.confirmWorkingSource(forSeason = true) }) { Text("Pro celou sezónu ⭐📦") }
                        if (multiSeason) {
                            TextButton(onClick = { viewModel.confirmWorkingSource(forSeries = true) }) { Text("Pro celý seriál ⭐📦") }
                        }
                    }
                } else {
                    TextButton(onClick = { viewModel.confirmWorkingSource() }) { Text("Ano, zapamatovat ⭐") }
                }
            },
            dismissButton = {
                Row {
                    if (isSeasonPack) {
                        TextButton(onClick = { viewModel.confirmWorkingSource() }) { Text("Jen tenhle díl") }
                    }
                    TextButton(onClick = { viewModel.dismissWorkingConfirm() }) { Text("Ne") }
                }
            },
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
    // user 2026-08-18 (Harry Potter 20 let) — ruční vložení odkazu jako zdroj, parita s webem
    // (`episodes.js` `vlozitVlastniZdroj`). Parsování (sdilej.cz → sdilej://) dělá ViewModel.
    if (uiState.showManualUrlDialog) {
        var urlText by remember(uiState.showManualUrlDialog) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissManualUrlDialog() },
            title = { Text("Vložit vlastní odkaz") },
            text = {
                Column {
                    Text(
                        "Přímý odkaz na video (sdilej.cz nebo jiná přímá URL) — nahradí uložený zdroj tohoto titulu.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        singleLine = true,
                        placeholder = { Text("https://sdilej.cz/…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.streamError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveManualSource(urlText) }) { Text("Uložit") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissManualUrlDialog() }) { Text("Zrušit") } },
        )
    }
    if (uiState.showDownloadMenu) {
        val isShowItem = displayItem.type == MediaType.SHOW
        DownloadMenuSheet(
            // HOARD (SHW-84) + SEZONA (SHW-113): stáhnout do telefonu = film z knihovny/zapamatovaný
            // zdroj, NEBO u seriálu vůbec (dovolí kliknout, `downloadCurrentToDevice()` sám poradí
            // "otevři díl", není-li žádný vybraný — stejná zpráva jako dřív, jen teď dostupná z menu).
            canDevice = displayItem.type == MediaType.MOVIE && (uiState.isOwnedInLibrary || uiState.rememberedSource != null) ||
                isShowItem,
            offlineState = uiState.offlineState,
            showServerOptions = !uiState.isOwnedInLibrary,
            onDevice = { viewModel.downloadCurrentToDevice() },
            onDeleteDevice = { viewModel.deleteOfflineCurrent() },
            onSdilej = { viewModel.openSdilejPicker() },
            onSmartRemux = { viewModel.dismissDownloadMenu(); onSmartDetect?.invoke(displayItem) },
            onDismiss = { viewModel.dismissDownloadMenu() },
            isShow = isShowItem,
            onDownloadSeason = if (isShowItem) { { viewModel.downloadSeasonToDevice() } } else null,
            onDownloadAllEpisodes = if (isShowItem) { { viewModel.downloadAllEpisodesToDevice() } } else null,
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
    val filmographyViewMode by viewModel.filmographyViewMode.collectAsStateWithLifecycle()
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
            // SPOTLIGHT (FLM-02): volba mřížka/seznam se drží v ViewModeStore (list je Dialog).
            viewMode = filmographyViewMode,
            onViewMode = { viewModel.setFilmographyViewMode(it) },
            // Filmografie režiséra by měla v každém řádku týž jméno — tam režiséra nepiš.
            showDirectorInRows = uiState.personSheetKind != FavoriteKind.DIRECTOR,
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
                        isQueued = uiState.isQueued,
                        onQueue = uiState.item?.tmdbId?.let { { viewModel.toggleQueue() } },
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
                        onPlayRemembered = { uiState.rememberedSource?.let { viewModel.playRemembered() } },
                        onCastRemembered = { uiState.rememberedSource?.let { viewModel.castStreamToTv(it) } },
                        onRemoveRemembered = { viewModel.removeRememberedSource() },
                        onStremio = { viewModel.openStreamPathChooser() },
                        // REPRISE: přímý vstup do pickeru (strict=false → všechny zdroje s chipy) i když už zdroj máme.
                        onPickSource = { viewModel.openStreamPicker() },
                        onDownload = { viewModel.openDownloadMenu() },
                        // FILMYCAST — „Přehrát na Filmy TV" (poslat zapamatovaný zdroj do Filmy appky na TV).
                        onCastToFilmyTv = { viewModel.castToFilmyTv() },
                        inWatchlist = uiState.isInWatchlist,
                        isTogglingWatchlist = uiState.isTogglingWatchlist,
                        onWatchlist = { viewModel.toggleWatchlist() },
                        onShare = onShareCard,
                        onSimilar = onSimilar?.let { cb -> { cb(displayItem) } },
                        // user 2026-08-18 (Harry Potter 20 let → Splitsville): per-titul přebití —
                        // JEDNA volba řídí OBOJÍ (auto-hledání na pozadí i výběr zvukové stopy),
                        // dřív dva samostatné přepínače, sloučeno po zpětné vazbě usera.
                        titleAudioOverride = uiState.titleAudioOverride,
                        onCycleTitleAudioOverride = { viewModel.cycleTitleAudioOverride() },
                        onManualUrl = { viewModel.openManualUrlDialog() },
                        hasShowSources = uiState.hasAnyShowSource,
                        onForgetShowSources = { viewModel.forgetShowSources() },
                        // user 2026-08-24: „jak odeberu z Filmotéky … aby se znova nehledaly zdroje"
                        onRemoveFromFilmoteka = { viewModel.removeFromFilmoteka() },
                        castInOverflow = castInOverflow,
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
            // 🔴 „Hledám zdroj…" MUSÍ být vidět po celou dobu hledání, ne jen jako bliknutí v liště.
            // User 2026-08-03 13:11: *„Promazáno. Asi hledá. Nevidím nikde progress."* Auto-hledání
            // běží na serveru desítky vteřin a mezitím u dětského profilu zmizí i karta z Filmotéky
            // (ta ukazuje jen tituly se zdrojem) → bez tohohle divák neví, jestli čekat.
            if (uiState.autoSearching) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Hledám zdroj… nech to běžet, dá se to i zavřít.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                    // SVĚTLÝ MOTIV (user 07-19): spodní scrim = colorScheme.background → na světlém motivu je
                    // bílý, takže bílý text ve fanartu mizí. Jemný černý stín drží čitelnost bez ohledu na
                    // motiv i jas fanartu (na AMOLED černé neškodí, na světlém je to ten rozdíl).
                    val heroTextShadow = Shadow(color = Color.Black.copy(alpha = 0.75f), offset = Offset(0f, 1f), blurRadius = 6f)
                    // [název · rok · ČSFD] v JEDNOM řádku
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(shadow = heroTextShadow),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        displayItem.year?.let {
                            Text("$it", style = MaterialTheme.typography.titleMedium.copy(shadow = heroTextShadow), color = Color.White.copy(alpha = 0.85f))
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
                                    Text("%.1f".format(rating), style = MaterialTheme.typography.titleMedium.copy(shadow = heroTextShadow), color = Color.White)
                                }
                            }
                        }
                    }
                    // CELLULOID M2.6 immersive: pod názvem kompaktní meta řádek — Režie + žánry (dosud
                    // viditelné jen v sekci „Tvůrci" níž). Aditivní: nic neubírá, obohacuje hero obou appek.
                    // SPOTLIGHT (FLM-02, user 2026-08-27 „Udelej režii proklikavaci"): jména režisérů už
                    // nejsou mrtvý text — každé je samostatně klikatelné a vede na jeho filmografii
                    // (tatáž cesta, jakou používá pás v sekci „Tvůrci" níž). Žánry zůstávají textem.
                    val directorsShown = uiState.directors.filter { !it.name.isNullOrBlank() }.take(2)
                    val genreLine = genres?.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(" · ").orEmpty()
                    if (directorsShown.isNotEmpty() || genreLine.isNotBlank()) {
                        val metaStyle = MaterialTheme.typography.bodyMedium.copy(shadow = heroTextShadow)
                        // FlowRow se sám zalomí — u dlouhých jmen nebo tří žánrů nic nepřeteče z hera.
                        FlowRow(verticalArrangement = Arrangement.Center) {
                            if (directorsShown.isNotEmpty()) {
                                Text("Režie: ", style = metaStyle, color = Color.White.copy(alpha = 0.85f))
                                directorsShown.forEachIndexed { index, person ->
                                    if (index > 0) Text(", ", style = metaStyle, color = Color.White.copy(alpha = 0.85f))
                                    Text(
                                        text = person.name.orEmpty(),
                                        style = metaStyle,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .tvFocusable(shape = RoundedCornerShape(4.dp))
                                            .clickable { viewModel.openPersonFilmography(person, FavoriteKind.DIRECTOR) },
                                    )
                                }
                                if (genreLine.isNotBlank()) {
                                    Text("   ·   ", style = metaStyle, color = Color.White.copy(alpha = 0.85f))
                                }
                            }
                            if (genreLine.isNotBlank()) {
                                Text(
                                    text = genreLine,
                                    style = metaStyle,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                // SUMÁŘ (SHW-122), user 2026-08-28: „das mi to jako popisek scrollovatelny s tim
                // oficialnim do strany - udelas maly indikator kdyz popisek existuje". Kurátorský
                // text tedy NENÍ další sekce, ale DRUHÁ STRÁNKA popisu; dokud není upečený,
                // vypadá karta přesně jako dosud (žádné prázdné místo, žádný spinner).
                val shrnuti = uiState.curatedText?.takeIf { it.isNotBlank() && uiState.showCuratedText }
                // user 2026-08-28: „v tu chvíli dejme ten generovany jako hlavní a ten původní jako
                // druhy" — pořadí jde přehodit v Nastavení → Detail obsahu.
                val stranky = when {
                    shrnuti == null -> listOf(plot to plotSource)
                    uiState.curatedFirst -> listOf(shrnuti to "diváci", plot to plotSource)
                    else -> listOf(plot to plotSource, shrnuti to "diváci")
                }
                var stranka by remember(plot, shrnuti) { mutableStateOf(0) }
                val aktivni = stranka.coerceIn(0, stranky.lastIndex)
                val (textStranky, zdrojStranky) = stranky[aktivni]

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (zdrojStranky != null) {
                        Text(
                            text = if (zdrojStranky == "diváci") "Co na to diváci" else "Popis ($zdrojStranky)",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    // Malý indikátor — tečka za stránku; ukáže se, JEN když je co listovat.
                    if (stranky.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            stranky.indices.forEach { i ->
                                Box(
                                    Modifier
                                        .size(if (i == aktivni) 7.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == aktivni) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .clickable { stranka = i },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val collapsedLines = uiState.plotCollapsedLines
                val limitActive = collapsedLines > 0 && !plotExpanded
                Text(
                    text = textStranky,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (limitActive) collapsedLines else Int.MAX_VALUE,
                    overflow = if (limitActive) TextOverflow.Ellipsis else TextOverflow.Clip,
                    onTextLayout = { if (limitActive) plotOverflow = it.hasVisualOverflow },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .then(
                            if (stranky.size > 1) {
                                Modifier.pointerInput(stranky.size) {
                                    // Přejetí do strany mezi popisem a shrnutím. Práh 60 px, ať se
                                    // gesto nepere se svislým rolováním karty.
                                    detectHorizontalDragGestures { _, drag ->
                                        if (drag < -60f && stranka < stranky.lastIndex) stranka++
                                        else if (drag > 60f && stranka > 0) stranka--
                                    }
                                }
                            } else Modifier
                        ),
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

            // SIMILAR (user 2026-08-27: „Musime mit v nastaveni tento pruh, i ostatni jestli visible
            // nebo non visible a klidne poradi") — pruhy se kreslí v POŘADÍ z Nastavení, ne napevno.
            // Neznámý klíč se přeskočí (starší uložené pořadí novou verzi nerozbije).
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
            uiState.rowOrder.forEach { klic ->
                when (klic) {
                    DetailRowKeys.CREATORS -> if (uiState.showCreators) {
                        CreatorsSection(
                            cast = uiState.cast,
                            directors = uiState.directors,
                            writers = uiState.writers,
                            cinematographers = uiState.cinematographers,
                            onPersonClick = { person, kind -> viewModel.openPersonFilmography(person, kind) },
                            genres = genres.orEmpty(),
                            detailsVisible = plotExpanded,
                        )
                    }
                    DetailRowKeys.COLLECTION -> if (uiState.showCollections) {
                        mergedCollection?.let { coll ->
                            CollectionSection(
                                collection = coll,
                                excludeKey = displayItem.tmdbId?.let { "tmdb_$it" },
                                onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                            )
                        }
                    }
                    DetailRowKeys.SIMILAR -> if (uiState.showSimilar) {
                        uiState.similarTitles?.let { coll ->
                            CollectionSection(
                                collection = coll,
                                excludeKey = null,
                                onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                            )
                        }
                    }
                    DetailRowKeys.DIRECTOR -> if (uiState.showDirector) {
                        uiState.directorMovies?.let { coll ->
                            CollectionSection(
                                collection = coll,
                                excludeKey = displayItem.tmdbId?.let { "tmdb_$it" },
                                onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                            )
                        }
                    }
                    DetailRowKeys.STUDIO -> if (uiState.showStudio) {
                        uiState.studioMovies?.let { coll ->
                            CollectionSection(
                                collection = coll,
                                excludeKey = displayItem.tmdbId?.let { "tmdb_$it" },
                                onPartClick = { part -> onCollectionPartClick?.invoke(part) },
                            )
                        }
                    }
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
                    // user 2026-07-28: telefon dosud NEUKAZOVAL zhlédnuté epizody ani je neuměl přepnout
                    // (TV to má od KOLO2) → fajfky, progres, „další díl" a označení celé sezóny i tady.
                    watched = uiState.episodeWatched,
                    progress = uiState.episodeProgress,
                    // VESTIBUL: klik na díl z „Další díly" označí TEN díl; jinak „další na řadě".
                    nextUp = uiState.focusedEpisode ?: uiState.nextUpEpisode,
                    onToggleWatched = { s, e -> viewModel.toggleEpisodeWatched(s, e) },
                    onMarkSeasonWatched = { s, w -> viewModel.markSeasonWatched(s, w) },
                    onDownloadEpisode = { s, e -> viewModel.downloadEpisode(s, e) },
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
    onPickSource: (() -> Unit)? = null,
    onDownload: () -> Unit,
    onCastToFilmyTv: (() -> Unit)? = null,
    inWatchlist: Boolean,
    isTogglingWatchlist: Boolean,
    onWatchlist: () -> Unit,
    /** RAMPA (SHW-121) — fronta „K přehrání". null = skryto (titul bez tmdb id). */
    isQueued: Boolean = false,
    onQueue: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    /** „Doporuč podobné" — tenhle titul jako reference pro kurátora (user 2026-08-01). null = skryto. */
    onSimilar: (() -> Unit)? = null,
    // user 2026-08-18 (Splitsville) — PER TITUL přebití jazykové stopy. null = drž se profilu
    // (řádek se pořád zobrazí, jen ukazuje „Profil"). onCycleTitleAudioOverride == null = skryto.
    titleAudioOverride: String? = null,
    onCycleTitleAudioOverride: (() -> Unit)? = null,
    // user 2026-08-18 (Harry Potter 20 let) — otevře dialog ručního vložení odkazu. null = skryto.
    onManualUrl: (() -> Unit)? = null,
    /** SEZONA: seriál má uložený zdroj u některé sezóny/dílu → nabídni zapomenutí i bez otevření dílu. */
    hasShowSources: Boolean = false,
    onForgetShowSources: (() -> Unit)? = null,
    /** „Odebrat z Filmotéky" — zdroje i „Chci vidět" pryč, BEZ nového hledání (user 2026-08-24). */
    onRemoveFromFilmoteka: (() -> Unit)? = null,
    castInOverflow: Boolean = false,
    // user 2026-08-20 ("Zločin je extrémní sport" — obrázky/recenze jiného titulu): jednou špatně
    // vyřešené ČSFD id se dřív v appce drželo natrvalo bez opravy. null = skryto (chybí imdb/tmdb).
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
    // Cast tlačítko (ikona Cast vedle „Přehrát"): u filmu se zapamatovaným zdrojem = přehraj rovnou v NAŠÍ Filmy
    // appce na TV (fronta příkazů). Jinak legacy „na TV" (yellyfin box): knihovní JF položka / ostatní.
    val onTv: (() -> Unit)? = when {
        isMovie && hasRemembered && onCastToFilmyTv != null -> onCastToFilmyTv
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
                // castInOverflow (Filmy) → cast NEMÁ tlačítko, jde do ⋮ menu jako „Přehrát na TV".
                if (onTv != null && !castInOverflow) {
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
                // ORCHARD (user 07-19, Filmy) — „Přehrát na TV" v menu místo Cast tlačítka. Jen když je co castnout.
                if (castInOverflow && onTv != null) {
                    DropdownMenuItem(
                        text = { Text("Přehrát na TV") },
                        leadingIcon = { Icon(Icons.Default.Cast, null) },
                        onClick = { menuOpen = false; onTv() },
                    )
                }
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
                // RAMPA (SHW-121) — fronta „K přehrání". ZÁMĚRNĚ hned pod Oblíbenými a NAD „Chci vidět":
                // je to pořadník na teď, kdežto „Chci vidět" je dlouhodobý watchlist (a jde na Trakt).
                if (onQueue != null) {
                    DropdownMenuItem(
                        text = { Text(if (isQueued) "Odebrat z fronty" else "Přidat k přehrání") },
                        leadingIcon = {
                            // user 2026-08-28: „znacku zvol jinou zadnou zdobenou proste ikonu fronty"
                            Icon(if (isQueued) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd, null)
                        },
                        onClick = { menuOpen = false; onQueue() },
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
                // SEZONA (SHW-113) f2 — profilový přepínač zvukové stopy PŘESUNUT do Nastavení →
                // Obraz a zvuk (user 2026-08-18: „zapnul jsem ho na jednom filmu a teď je všude" —
                // v menu karty vedle per-titul volby matl a hrozilo omylem přepnout VŠECHNY filmy).
                // user 2026-08-18 (Harry Potter 20 let → Splitsville) — PER TITUL přebití přepínače
                // výš, JEDNA volba pro OBOJÍ: auto-hledání na pozadí (CZ = sdilej.cz + CZ/SK zvuk
                // napřed, jako dětský profil) i výběr zvukové stopy při přehrání. Nemění profilový
                // výchozí (řádek výš) — jen tenhle konkrétní titul. Klik cykluje Profil → CZ dabing
                // → Originál → Profil…, HNED znovu nastartuje hledání s novou politikou.
                // (Dřív dva samostatné přepínače — sloučeno po zpětné vazbě usera: „nechápu tu volbu
                // hledat zdroje, obojí se týká jen českého dabingu".)
                if (onCycleTitleAudioOverride != null) {
                    val label = when (titleAudioOverride) {
                        "CZ" -> "Tenhle titul: CZ dabing"
                        "ORIGINAL" -> "Tenhle titul: originál"
                        else -> "Tenhle titul: podle profilu"
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.Translate, null) },
                        onClick = onCycleTitleAudioOverride,
                    )
                }
                // user 2026-08-01: doporučení má být po ruce přímo u filmu, ne jen v samostatné sekci.
                if (onSimilar != null) {
                    DropdownMenuItem(
                        text = { Text("Doporuč podobné") },
                        leadingIcon = { Icon(Icons.Default.MovieFilter, null) },
                        onClick = { menuOpen = false; onSimilar() },
                    )
                }
                // FILMYCAST — přesunuto z ⋮ menu pod Cast tlačítko (viz `onTv` výše). User: akci chci pod cast button.
                if (onShare != null) {
                    DropdownMenuItem(
                        text = { Text("Sdílet kartu") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { menuOpen = false; onShare() },
                    )
                }
                // REPRISE (parita s TV „Zkusit jiný zdroj"): otevři picker i když už zdroj máme —
                // ať jde přepnout na jiný cached/dostupný zdroj (chipy rozlišení/zvuk/velikost + ⭐ pin).
                if (!inLibrary && hasRemembered && onPickSource != null) {
                    DropdownMenuItem(
                        text = { Text("Vybrat jiný zdroj") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                        onClick = { menuOpen = false; onPickSource() },
                    )
                }
                if (!inLibrary && hasRemembered) {
                    DropdownMenuItem(
                        text = { Text("Odebrat zapamatovaný zdroj") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onRemoveRemembered() },
                    )
                }
                // user 2026-08-18 (Harry Potter 20 let) — parita s webem: ruční vložení odkazu jako
                // zdroj, když auto-hledání netrefí přesně tu verzi, kterou user sám ověří.
                if (!inLibrary && onManualUrl != null) {
                    DropdownMenuItem(
                        text = { Text("Vložit vlastní odkaz") },
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        onClick = { menuOpen = false; onManualUrl() },
                    )
                }
                // SEZONA (user 2026-08-02, Arcane: „Nikde nemám možnost"): u seriálu je zdroj uložený
                // per sezóna/díl, takže `hasRemembered` je na kartě prázdné a volba výš se neukázala —
                // a k té v seznamu zdrojů se divák nedostane, protože zapamatovaný díl hraje rovnou.
                if (!inLibrary && !isMovie && hasShowSources && onForgetShowSources != null) {
                    DropdownMenuItem(
                        text = { Text("Zapomenout zdroje seriálu") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onForgetShowSources() },
                    )
                }
                // user 2026-08-24 („když chci ručně odebrat, jak to udělám, aby se znova nehledaly
                // zdroje") — „Zapomenout zdroje" schválně hledá nový; tohle je opak: ať titul zmizí.
                // Nemá smysl u titulu z knihovny (ten drží Jellyfin, ne my).
                if (!inLibrary && onRemoveFromFilmoteka != null) {
                    DropdownMenuItem(
                        text = { Text("Odebrat z Filmotéky") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onRemoveFromFilmoteka() },
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
