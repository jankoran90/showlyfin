package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.util.concurrent.ConcurrentHashMap

// ── CANVAS (SHW-47) B: kanonická poster karta (UNISON) ──────────────────────────────
// Jeden zdroj pravdy pro karty filmů/seriálů: plakát + spodní scrim s řádkem
// [titulek · rok · ČSFD] a ≤4 žánrovými štítky. Používá MediaCard (Objevit) i CollectionPartCard
// (kolekce/filmografie/Oblíbení) — viz CLAUDE.md „Design konzistence".

private val PosterCardShape = RoundedCornerShape(14.dp)

// ČSFD badge barvy = JEDINÝ zdroj pravdy (UNISON). <70 % = pastelově modrá střední světlost.
private val CsfdHighBg = ShowlyfinStatus.CsfdHigh   // stejná červená jako detail (CsfdComponents)
private val CsfdLowBg = ShowlyfinStatus.CsfdLow    // pastelově modrá, střední světlost (slabší filmy)
private fun csfdBg(rating: Int): Color = if (rating < 70) CsfdLowBg else CsfdHighBg

/** Líný per-karta poskytovatel ČSFD hodnocení (provádí ho ui vrstva přes CsfdRepository). */
interface CsfdRatingProvider {
    suspend fun rating(imdbId: String?, tmdbId: Long?, title: String, year: Int?): Int?
}

val LocalCsfdRatingProvider = staticCompositionLocalOf<CsfdRatingProvider?> { null }

// Procesní cache (klíč = tmdb/imdb). ConcurrentHashMap NEDOVOLÍ null hodnotu → pro „dotaženo,
// ČSFD nemá" ukládáme sentinel CSFD_NONE (dřív se ukládalo null → ConcurrentHashMap.put NPE → pád).
private const val CSFD_NONE = -1
private val csfdCardCache = ConcurrentHashMap<String, Int>()

private fun csfdKey(tmdbId: Long?, imdbId: String?): String? =
    tmdbId?.let { "t$it" } ?: imdbId?.takeIf { it.isNotBlank() }?.let { "i$it" }

/** Cache → rating (sentinel CSFD_NONE = dotaženo bez hodnocení → vrať null). */
private fun cachedCsfdRating(key: String?): Int? =
    key?.let { csfdCardCache[it] }?.takeIf { it != CSFD_NONE }

/**
 * ČSFD hodnocení pro kartu — z procesní cache, jinak líně dotáhne přes [LocalCsfdRatingProvider]
 * (volá se jen pro viditelné karty v LazyGridu). Bez providera (TV) / bez id → null = badge se neukáže.
 */
@Composable
fun rememberCsfdCardRating(imdbId: String?, tmdbId: Long?, title: String, year: Int?): Int? {
    val key = csfdKey(tmdbId, imdbId)
    val provider = LocalCsfdRatingProvider.current
    var rating by remember(key) { mutableStateOf(cachedCsfdRating(key)) }
    LaunchedEffect(key, provider) {
        if (key == null || provider == null) return@LaunchedEffect
        if (csfdCardCache.containsKey(key)) { rating = cachedCsfdRating(key); return@LaunchedEffect }
        val r = runCatching { provider.rating(imdbId, tmdbId, title, year) }.getOrNull()
        csfdCardCache[key] = r ?: CSFD_NONE
        rating = r
    }
    return rating
}

// ── Český popis pro seznamové řádky (MediaRow) — stejný fallback jako detail filmu ──────
// Detail řeší český popis: TMDB cs překlad (looksCzech) → ČSFD popis → jakýkoli TMDB. Řádky
// seznamů (Objevit/Chci vidět/Historie/Na RD) dostaly z enrichmentu jen overviewCz/overview,
// které často nejsou české → tady to líně dorovnáme přes [LocalCzechOverviewProvider] na stejný
// výsledek jako detail (provider wrappuje TMDB + ČSFD v ui vrstvě, viz CardCsfdViewModel).

/** Heuristika „vypadá česky" = obsahuje českou diakritiku (mirror DetailScreen.looksCzech). */
fun looksCzech(t: String?): Boolean =
    !t.isNullOrBlank() && t.any { it in "áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ" }

/** Líný per-řádek poskytovatel ČESKÉHO popisu (TMDB cs → ČSFD → fallback). */
interface CzechOverviewProvider {
    suspend fun overview(
        imdbId: String?, tmdbId: Long?, title: String, titleCz: String?, year: Int?, fallback: String?,
    ): String?
}

val LocalCzechOverviewProvider = staticCompositionLocalOf<CzechOverviewProvider?> { null }

// 🔴 2026-08-29 (user, dvě hlášky): „na kartě Tři časy, v seznamu Three Times" + „All the Long
// Nights na kartě, ale 夜明けのすべて v seznamu". Položky seznamů často NEMAJÍ titleCz (doplňuje
// se jen na detailu) → řádek spadne na originální název. Líné dorovnání stejným vzorem jako
// [CzechOverviewProvider]. KANON (user 2026-08-30 12:11): **česky → anglicky → originál** — stejná
// politika jako karta detailu, tedy i s ČSFD rungem (Tři časy/Dvě sezóny dva cizinci mají češtinu
// JEN na ČSFD, TMDB cs překlad nemají).
/** Líný per-řádek/karta poskytovatel ZOBRAZOVACÍHO titulku (TMDB cs → ČSFD název → TMDB en). */
interface RowTitleProvider {
    /** [title]/[year] = název z položky (pro ČSFD title-search, u cizojazyčných titulů nutné). */
    suspend fun rowTitle(
        imdbId: String?, tmdbId: Long?, title: String, year: Int?, isShow: Boolean,
    ): String?
}

val LocalRowTitleProvider = staticCompositionLocalOf<RowTitleProvider?> { null }

// SEJF (FLM-03, user 09:52 „udělej to taky do všech seznamů"): badge „ve vlastní filmotéce"
// pro ŘÁDKY — provider jednou natáhne index složek z dellhome (TTL cache ve VM) a řádek se
// jen zeptá „je můj <titul> (<rok>) mezi nimi?" (match na CZ i originální variantu názvu).
interface SejfArchiveProvider {
    /** variantDirs = "<titul> (<rok>)" varianty z položky; imdb/tmdb pro dohledání EN názvu,
     *  když místní titul nesedí (japonský originál vs EN název složky — user 10:49 „Není tam"). */
    suspend fun isArchived(imdbId: String?, tmdbId: Long?, isShow: Boolean, year: Int?, variantDirs: List<String>): Boolean
}

val LocalSejfArchiveProvider = staticCompositionLocalOf<SejfArchiveProvider?> { null }

@Composable
fun rememberSejfArchived(imdbId: String?, tmdbId: Long?, isShow: Boolean, year: Int?, variantDirs: List<String>): Boolean {
    val provider = LocalSejfArchiveProvider.current
    var archived by remember(variantDirs) { mutableStateOf(false) }
    LaunchedEffect(provider, variantDirs) {
        if (provider == null || variantDirs.isEmpty()) return@LaunchedEffect
        archived = runCatching { provider.isArchived(imdbId, tmdbId, isShow, year, variantDirs) }.getOrDefault(false)
    }
    return archived
}

private const val ROW_TITLE_NONE = " "
private val rowTitleCache = ConcurrentHashMap<String, String>()

/**
 * Titulek řádku, když položka nemá český název — líně přes [LocalRowTitleProvider].
 * Vrací null, když není co dorovnávat (titleCz známe / bez id / bez providera / nic nenašlo se)
 * → volající pak použije [com.github.jankoran90.showlyfin.core.domain.MediaItem.displayTitle].
 */
@Composable
fun rememberRowTitle(
    imdbId: String?, tmdbId: Long?, titleCz: String?, isShow: Boolean,
    title: String = "", year: Int? = null,
): String? {
    if (!titleCz.isNullOrBlank()) return null
    val key = csfdKey(tmdbId, imdbId) ?: return null
    val provider = LocalRowTitleProvider.current
    var text by remember(key) {
        mutableStateOf(rowTitleCache[key]?.takeIf { it != ROW_TITLE_NONE })
    }
    LaunchedEffect(key, provider, title, year) {
        if (provider == null) return@LaunchedEffect
        rowTitleCache[key]?.let { c -> text = c.takeIf { it != ROW_TITLE_NONE }; return@LaunchedEffect }
        val r = runCatching { provider.rowTitle(imdbId, tmdbId, title, year, isShow) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        rowTitleCache[key] = r ?: ROW_TITLE_NONE
        text = r
    }
    return text
}

// Procesní cache popisů (klíč jako u ČSFD). Sentinel = „dotaženo, nic lepšího než fallback".
private const val OVERVIEW_NONE = " "
private val overviewCache = ConcurrentHashMap<String, String>()

/**
 * Český popis pro řádek — z procesní cache, jinak líně přes [LocalCzechOverviewProvider]
 * (jen pro viditelné řádky). Než se dotáhne, ukazuje [fallback] (overviewCz/overview z položky).
 * Bez providera / bez id → vrátí [fallback].
 */
@Composable
fun rememberCzechOverview(
    imdbId: String?, tmdbId: Long?, title: String, titleCz: String?, year: Int?, fallback: String?,
): String? {
    // Už máme český popis (typicky populární filmy z TMDB) → neřeš, neplýtvej sítí.
    if (looksCzech(fallback)) return fallback
    val key = csfdKey(tmdbId, imdbId)
    val provider = LocalCzechOverviewProvider.current
    var text by remember(key, fallback) {
        // key může být null (položka bez tmdb/imdb id — např. resume řada na TV hero). ConcurrentHashMap
        // NEPODPORUJE null klíč (get(null) → NPE). Proto null-safe lookup jako u rememberCsfdCardRating.
        mutableStateOf(key?.let { overviewCache[it] }?.takeIf { it != OVERVIEW_NONE } ?: fallback)
    }
    LaunchedEffect(key, provider) {
        if (key == null || provider == null) {
            android.util.Log.i("SHGOVR", "skip key=$key providerNull=${provider == null} title='$title'")
            return@LaunchedEffect
        }
        overviewCache[key]?.let { c -> text = c.takeIf { it != OVERVIEW_NONE } ?: fallback; return@LaunchedEffect }
        val r = runCatching { provider.overview(imdbId, tmdbId, title, titleCz, year, fallback) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        overviewCache[key] = r ?: OVERVIEW_NONE
        text = r ?: fallback
    }
    return text
}

// ── Líný režisér pro immersive header (TENFOOT) ────────────────────────────────────────
// Filmotéka má stovky titulů → NENAČÍTÁME režii v enrichmentu. Stejně jako ČSFD/CZ popis to
// dorovnáme líně přes provider JEN pro právě fokusovaný titul v headeru (viz CardCsfdViewModel).

/** Líný per-titul poskytovatel jména režiséra (max 2, joinnuto ", "; TMDB credits ve ui vrstvě). */
interface DirectorProvider {
    suspend fun director(
        imdbId: String?, tmdbId: Long?, type: com.github.jankoran90.showlyfin.core.domain.MediaType,
        title: String, year: Int?,
    ): String?
}

val LocalDirectorProvider = staticCompositionLocalOf<DirectorProvider?> { null }

// Procesní cache (klíč jako u ČSFD). Sentinel = „dotaženo, režie neznámá".
private const val DIRECTOR_NONE = " "
private val directorCache = ConcurrentHashMap<String, String>()

/**
 * CELLULOID (SHW-98) M2.3c — po přihlášení k uploader serveru (ČSFD backend) zahoď procesní cache
 * popisu + hodnocení, ať se český ČSFD popis/rating dotáhne bez restartu appky (návrat na obrazovku
 * = nová composition → líné dotažení znovu). Režii (`directorCache`, TMDB) není třeba čistit.
 */
fun clearCsfdEnrichmentCaches() {
    overviewCache.clear()
    csfdCardCache.clear()
}

/**
 * Jméno režiséra pro immersive header — z procesní cache, jinak líně přes [LocalDirectorProvider]
 * (volá se jen pro PRÁVĚ fokusovaný titul). Bez providera (nezapojený) / bez id → null = nezobrazí se.
 */
@Composable
fun rememberDirector(
    imdbId: String?, tmdbId: Long?, type: com.github.jankoran90.showlyfin.core.domain.MediaType,
    title: String, year: Int?,
): String? {
    val key = csfdKey(tmdbId, imdbId)
    val provider = LocalDirectorProvider.current
    var director by remember(key) {
        mutableStateOf(key?.let { directorCache[it] }?.takeIf { it != DIRECTOR_NONE })
    }
    LaunchedEffect(key, provider) {
        if (key == null || provider == null) return@LaunchedEffect
        directorCache[key]?.let { c -> director = c.takeIf { it != DIRECTOR_NONE }; return@LaunchedEffect }
        val r = runCatching { provider.director(imdbId, tmdbId, type, title, year) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        directorCache[key] = r ?: DIRECTOR_NONE
        director = r
    }
    return director
}

/**
 * Režisér z procesní cache — pro hledání podle režiséra ve Filmotéce (bez composition/fetch).
 * Null = ještě nedotažen NEBO dotažen a neznámý (sentinel). Naplní [rememberDirector] i [warmDirector].
 */
fun cachedDirector(tmdbId: Long?, imdbId: String?): String? =
    csfdKey(tmdbId, imdbId)?.let { directorCache[it] }?.takeIf { it != DIRECTOR_NONE }

/**
 * Idempotentní warm-up cache režiséra (mimo composition) — pro předvyplnění indexu hledání napříč celou
 * Filmotékou, ať jde hledat i podle režiséra u titulů, které user ještě neviděl. Už dotažené přeskočí.
 */
suspend fun warmDirector(
    provider: DirectorProvider, imdbId: String?, tmdbId: Long?,
    type: com.github.jankoran90.showlyfin.core.domain.MediaType, title: String, year: Int?,
): String? {
    val key = csfdKey(tmdbId, imdbId) ?: return null
    directorCache[key]?.let { return it.takeIf { c -> c != DIRECTOR_NONE } }
    val r = runCatching { provider.director(imdbId, tmdbId, type, title, year) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
    directorCache[key] = r ?: DIRECTOR_NONE
    return r
}

/** Malý ČSFD štítek na kartě (ČSFD + %). Pozadí dle hodnocení (jeden zdroj pravdy [csfdBg]). */
@Composable
fun CsfdMiniBadge(rating: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(csfdBg(rating), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "ČSFD $rating%",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** COUCH (SHW-88): malý štítek TMDB hodnocení (★ x.x) na kartě sekce. Neutrální tmavý scrim, ať se
 *  netluče s barevným ČSFD štítkem. */
@Composable
fun TmdbMiniBadge(rating: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "★ %.1f".format(rating),
            color = Color(0xFFFFC107),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PosterShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400), RepeatMode.Restart),
        label = "shimmer-shift",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = androidx.compose.ui.geometry.Offset(shift, 0f),
                end = androidx.compose.ui.geometry.Offset(shift + 400f, 400f),
            ),
        ),
    )
}

/**
 * Kanonická poster karta. Plakát vyplní kartu (2:3, plakát „top-left"), spodní scrim nese
 * řádek [titulek · rok · ČSFD]. Výška = poměr 2:3 (řádek mřížky beze změny).
 * (VANTAGE F: žánrové štítky z karet odebrány — žánry se řeší na detailu, sekce „Tvůrci".)
 *
 * [csfdRating] = známé hodnocení (jinak se líně dotáhne přes [imdbId]/[tmdbId]).
 * [ratingTarget] != null → dlouhý stisk karty otevře vlastní hvězdičkové hodnocení (F3) + odznak ★N.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    posterUrl: String?,
    title: String,
    year: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isShow: Boolean = false,
    csfdRating: Int? = null,
    imdbId: String? = null,
    tmdbId: Long? = null,
    csfdYear: Int? = null,
    enableCsfd: Boolean = true,
    inLibrary: Boolean = false,
    watched: Boolean = false,
    progress: Float? = null,
    ratingTarget: RatingTarget? = null,
    /**
     * VLTAVA F6b (user 2026-07-28 „ať je tam vždy vidět název, ať se to ořízne rozumně") — obrázek je
     * ŠIROKÝ (16:9), ne plakát 2:3. Typicky titul z ČT iVysílání, který svislý plakát nemá. Crop by z něj
     * uřízl skoro celý obraz včetně názvu vypáleného v grafice → vykreslíme ho CELÝ (Fit) na tmavém
     * podkladu a titulek pod ním dostane dva řádky.
     */
    wideArtwork: Boolean = false,
) {
    val lazyRating = rememberCsfdCardRating(imdbId, tmdbId, title, csfdYear)
    val rating = csfdRating ?: (if (enableCsfd) lazyRating else null)
    // CINEMATHEQUE (user 2026-07-18) — odznak „má uložený zdroj" (self-serve přes provider, jako ČSFD).
    val hasSource = rememberHasSource(tmdbId, imdbId)
    // RAMPA (SHW-121) — „ve frontě K přehrání", touž cestou přes provider (karta se nikoho neptá).
    val inQueue = rememberInQueue(tmdbId, isShow)
    // BESPOKE F3 — vlastní hvězdy: odznak + dlouhý stisk otevře dialog (přes LocalUserRatingProvider).
    val ratingProvider = LocalUserRatingProvider.current
    val userStars = ratingTarget?.let { rememberCardRating(it.tmdbId, it.imdbId) }
    val onRateLongPress: (() -> Unit)? =
        if (ratingProvider != null && ratingTarget != null) {
            { ratingProvider.requestRate(ratingTarget) }
        } else null

    // KOLO2 (C): kanonický TV fokus vzor — `tvFocusable` (scale + záře) PŘED `clip`, na TÉMŽE clickable
    // uzlu. Dřívější `Card(onClick)` mělo `clip` PŘED `tvFocusable` (záře kreslená ven se ořízla tvarem
    // karty) a fokus uvnitř Cardu (onFocusChanged.isFocused nikdy nesepnul) → poster karty neměly na TV
    // viditelný fokus. `shadow` drží stín z původního elevatedCard (parita na telefonu; na TV překryje záře).
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .tvFocusable(shape = PosterCardShape)
            .shadow(6.dp, PosterCardShape)
            .clip(PosterCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onRateLongPress != null)
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onRateLongPress)
                else Modifier.clickable(onClick = onClick),
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    // Široká grafika (ČT) se vejde celá; plakát 2:3 dál vyplňuje kartu jako dřív.
                    contentScale = if (wideArtwork) ContentScale.Fit else ContentScale.Crop,
                )
            } else {
                PosterShimmer(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isShow) Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (inLibrary) {
                InLibraryTitleBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            if (watched) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopStart))
            }
            // ── Spodní scrim: [titulek · rok · ČSFD] ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSource) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Má uložený zdroj",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 2.dp))
                    }
                    // RAMPA (SHW-121), user 2026-08-28: „muzes to dat vedle znacky zdroje?" — proto
                    // TADY, ne jako samostatné kolečko v rohu (tak to bylo v 1.2.96 a v seznamu
                    // chybělo úplně). Mřížka i seznam teď ukazují obě značky vedle sebe.
                    if (inQueue) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Ve frontě K přehrání",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 2.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        // Široká grafika (ČT) nemá název vypálený tak, aby byl vždy čitelný → dva řádky,
                        // ať se dlouhý název („Dakar 2025 – Síla odhodlání") neusekne po prvním slově.
                        maxLines = if (wideArtwork) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    year?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    if (userStars != null) {
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        UserRatingBadge(stars = userStars)
                    }
                    if (rating != null) {
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        CsfdMiniBadge(rating = rating)
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
