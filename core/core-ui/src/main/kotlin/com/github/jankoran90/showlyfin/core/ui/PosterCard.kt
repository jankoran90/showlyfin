package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
private fun PosterShimmer(modifier: Modifier = Modifier) {
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
 */
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
) {
    val lazyRating = rememberCsfdCardRating(imdbId, tmdbId, title, csfdYear)
    val rating = csfdRating ?: (if (enableCsfd) lazyRating else null)

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(PosterCardShape)
            .tvFocusable(shape = PosterCardShape),
        shape = PosterCardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
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
