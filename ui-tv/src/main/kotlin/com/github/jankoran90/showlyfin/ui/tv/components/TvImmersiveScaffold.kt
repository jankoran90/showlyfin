package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.rememberCsfdCardRating
import com.github.jankoran90.showlyfin.core.ui.rememberCzechOverview
import com.github.jankoran90.showlyfin.core.ui.rememberDirector
import com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem

/**
 * TENFOOT — Netflix/Kodi immersive pozadí. Fokusovaná karta řídí fanart přes CELOU obrazovku (za
 * sidebarem i obsahem); nahoře volitelný metadata header (název/žánr/hodnocení/synopse). Sdílené pro
 * Domů + Objevovat. Zapínatelné ([HomeLayoutStore.immersiveBackground]).
 */
data class ImmersiveInfo(
    val backdropUrl: String?,
    val title: String,
    /** Fallback popis (může být EN) — header ho líně nahradí českým přes provider. */
    val overview: String?,
    // Strukturovaná meta pro sestavení řádku „rok · žánr · hodnocení" v headeru (ČSFD % má přednost před ★).
    val year: Int?,
    val genre: String?,
    val tmdbRating: Float?,
    /** U resume řad (bez MediaItem) hotový meta řádek „S×E · epizoda"; má přednost před rok/žánr/rating. */
    val subtitleMeta: String?,
    // Klíče pro líný ČSFD/CZ lookup (stejný zdroj jako karty). Prázdné id = jen fallback.
    val imdbId: String?,
    val tmdbId: Long?,
    val rawTitle: String,
    val titleCz: String?,
    /** Typ pro líný TMDB credits lookup režiséra (movie vs. tv/aggregate_credits). */
    val type: MediaType,
)

/** Full-screen fanart + čitelnostní scrim (vlevo tmavý → doprava průhledný, dole tmavý). Crossfade při změně. */
@Composable
fun TvImmersiveBackground(info: ImmersiveInfo?, modifier: Modifier = Modifier) {
    // DVĚ vrstvy proti blikání (user feedback OTA 297): `shown` = poslední ÚSPĚŠNĚ načtený backdrop drží
    // obraz dole, `incoming` = nový cíl se dolaďuje NAD ním (Coil crossfade fade-in). Dřívější Compose
    // `Crossfade` odfadoval starý na alpha 0 dřív, než nový (async AsyncImage) dekódoval → prosvítala černá
    // = záblesk při každé změně řady. Takto se starý drží, dokud nový nedoloadí → nikdy probliknutí do černé.
    // Podržení poslední ne-null hodnoty (fokus na kartu bez fanartu pozadí neshodí) je zachováno přes `incoming`.
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(info?.backdropUrl) {
        info?.backdropUrl?.let { if (it != shown) incoming = it }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        shown?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        incoming?.takeIf { it != shown }?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).crossfade(400).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { shown = url },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val bg = MaterialTheme.colorScheme.background
        // Levý gradient (text vlevo čitelný) + spodní gradient (řady dole čitelné).
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(bg.copy(alpha = 0.92f), bg.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, bg.copy(alpha = 0.55f), bg)),
            ),
        )
    }
}

/**
 * CONVERGE (SHW-97) — počet řádků popisu v immersive hlavičce. 0 = AUTO (dopočítá se z výšky hero pásu
 * a řádkování písma, ať se popis neuřízne pod obsah ani při jiné velikosti UI/písma); 1..N = pevný počet.
 * Provided v [com.github.jankoran90.showlyfin.ui.tv.TvShell] z `HomeLayoutStore.immersiveHeaderLines`.
 */
val LocalImmersiveHeaderLines = staticCompositionLocalOf { 0 }

/** Metadata header (Netflix hero) — vlevo nahoře nad řadami. Prázdné [info] → nic. */
@Composable
fun TvImmersiveHeader(info: ImmersiveInfo?, modifier: Modifier = Modifier) {
    info ?: return
    // ČSFD % + český popis líně ze STEJNÉHO zdroje co karty (provider zapojený v ShowlyfinTvApp). Než dorazí,
    // ukazuje se fallback (TMDB ★ / EN popis). Parita s telefonem a s detailem (user feedback OTA 295).
    val csfd = rememberCsfdCardRating(info.imdbId, info.tmdbId, info.rawTitle, info.year)
    val czOverview = rememberCzechOverview(
        info.imdbId, info.tmdbId, info.rawTitle, info.titleCz, info.year, info.overview,
    )
    // TENFOOT: režisér líně ze STEJNÉHO zdroje co karty (jen pro fokusovaný titul; NE u resume epizod).
    val director = rememberDirector(info.imdbId, info.tmdbId, info.type, info.rawTitle, info.year)
    // Resume řady mají hotový meta řádek (S×E · epizoda); jinak poskládej rok · žánr · Režie X · ČSFD % (fallback ★).
    val meta = info.subtitleMeta?.takeIf { it.isNotBlank() } ?: listOfNotNull(
        info.year?.toString(),
        info.genre,
        director?.takeIf { it.isNotBlank() }?.let { "Režie $it" },
        csfd?.let { "ČSFD $it%" } ?: info.tmdbRating?.takeIf { it > 0f }?.let { "★ %.1f".format(it) },
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    // CONVERGE (SHW-97): počet řádků popisu — ruční (>0) nebo AUTO (dopočet z hero pásu + řádkování písma).
    val headerLines = LocalImmersiveHeaderLines.current
    val descLines = if (headerLines > 0) headerLines else autoDescLines()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = info.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        meta?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        czOverview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                maxLines = descLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 760.dp).padding(top = 2.dp),
            )
        }
    }
}

/**
 * AUTO počet řádků popisu: z výšky hero pásu (0.32f obrazovky, viz `TvRailList`) mínus název+meta, děleno
 * řádkováním popisu. `fontSize.toDp()` respektuje škálu písma/UI → větší písmo = méně řádků, takže se popis
 * neuřízne pod řady karet. Strop 1..8. Vzor sdílený všemi immersive sekcemi (nic overlapped/cropped).
 */
@Composable
private fun autoDescLines(): Int {
    val heroDp = (LocalConfiguration.current.screenHeightDp * 0.32f).dp
    val density = LocalDensity.current
    val typo = MaterialTheme.typography
    fun lineDp(size: androidx.compose.ui.unit.TextUnit) = (with(density) { size.toDp() }) * 1.4f
    val descLh = lineDp(typo.bodyMedium.fontSize)
    val titleBlock = lineDp(typo.headlineLarge.fontSize) + lineDp(typo.titleSmall.fontSize) + 14.dp
    val avail = (heroDp - titleBlock).coerceAtLeast(descLh)
    return if (descLh > 0.dp) (avail / descLh).toInt().coerceIn(1, 8) else 3
}

/** Sestav [ImmersiveInfo] z položky řady domova (Trakt/TMDB [MediaItem] preferováno, jinak Jellyfin data). */
fun HomeRowItem.toImmersiveInfo(): ImmersiveInfo {
    val mi = mediaItem
    return ImmersiveInfo(
        // 4K TV: plné rozlišení TMDB fanartu má přednost (landscapeUrl je jen 640–780 px pro karty →
        // fullscreen backdrop pixeloval). Fallback na landscapeUrl (Jellyfin řady bez TMDB backdropu).
        // NIKDY poster fallback — 2:3 plakát roztažený Crop do 16:9 = ošklivý zoom.
        backdropUrl = mi?.backdropUrl("original") ?: landscapeUrl,
        title = mi?.displayTitle ?: title,
        overview = mi?.let { it.overviewCz?.takeIf { o -> o.isNotBlank() } ?: it.overview },
        year = mi?.year ?: year,
        genre = mi?.genres?.firstOrNull()?.replaceFirstChar { it.uppercase() },
        tmdbRating = mi?.rating,
        // U resume řad (mediaItem == null) meta z podtitulu (S×E · epizoda).
        subtitleMeta = if (mi == null) subtitle?.takeIf { it.isNotBlank() } else null,
        imdbId = mi?.imdbId,
        tmdbId = mi?.tmdbId,
        rawTitle = mi?.title ?: title,
        titleCz = mi?.titleCz,
        type = mi?.type ?: MediaType.MOVIE,
    )
}

/** Sestav [ImmersiveInfo] z [MediaItem] (Objevovat). */
fun MediaItem.toImmersiveInfo(): ImmersiveInfo = ImmersiveInfo(
    backdropUrl = backdropUrl("original"), // 4K: plné rozlišení; bez poster fallbacku (viz výše)
    title = displayTitle,
    overview = overviewCz?.takeIf { it.isNotBlank() } ?: overview,
    year = year,
    genre = genres?.firstOrNull()?.replaceFirstChar { it.uppercase() },
    tmdbRating = rating,
    subtitleMeta = null,
    imdbId = imdbId,
    tmdbId = tmdbId,
    rawTitle = title,
    titleCz = titleCz,
    type = type,
)
