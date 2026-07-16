package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.core.ui.LocalUserRatingProvider
import com.github.jankoran90.showlyfin.core.ui.RatingTarget
import com.github.jankoran90.showlyfin.core.ui.UserRatingBadge
import com.github.jankoran90.showlyfin.core.ui.rememberCardRating
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder

/**
 * TENFOOT (SHW-87) — jedna dlaždice v TV mřížce „Sleduj". Tenký obal nad [TvPosterCard]: z [MediaItem]
 * vytáhne plakát/titul/rok. Vzhled a fokusové chování řeší [TvPosterCard].
 */
@Composable
fun TvMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    TvPosterCard(
        posterUrl = item.posterUrl("w342"),
        title = item.title,
        year = item.year,
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        ratingTarget = RatingTarget(
            tmdbId = item.tmdbId,
            imdbId = item.imdbId,
            traktId = item.traktId,
            title = item.displayTitle,
            year = item.year,
            isShow = item.type != MediaType.MOVIE,
        ),
    )
}

/**
 * Sdílená TV plakátová dlaždice (mřížka Sleduj i Hledání). Bere PŘÍMOU URL plakátu (Hledání má hotové
 * TMDB URL, doporučovač staví z `posterPath`) → jeden vzhled napříč obrazovkami.
 *
 * POZOR na pořadí modifikátorů: `tvFocusBorder` (uvnitř má `onFocusChanged`) MUSÍ být PŘED `clickable`,
 * jinak `onFocusChanged` fokusový uzel `clickable` (který je pak výš/vně) nepozoruje → prstenec/lift se
 * nikdy nevykreslí. Přesně tahle chyba dělala „kolem coverů žádné zvýraznění" na TV Home (user 2026-07-12).
 *
 * BESPOKE F3 — [ratingTarget] != null → na fokusnuté kartě tlačítko MENU (dálkový ovladač) otevře vlastní
 * hvězdičkové hodnocení (long-press je na TV řadách obsazen editorem řad, proto samostatná klávesa) + odznak ★N.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvPosterCard(
    posterUrl: String?,
    title: String,
    year: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    ratingTarget: RatingTarget? = null,
) {
    val shape = MaterialTheme.shapes.medium   // tvar z theme (design guard: žádný inline RoundedCornerShape)
    val ratingProvider = LocalUserRatingProvider.current
    val canRate = ratingProvider != null && ratingTarget != null
    val userStars = ratingTarget?.let { rememberCardRating(it.tmdbId, it.imdbId) }
    var focused by remember { mutableStateOf(false) }
    // fillMaxWidth (ne pevná šířka) → dlaždice respektuje šířku buňky mřížky (GridCells.Fixed) místo aby ji přebíjela.
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                // focusRequester PŘED clickable (fokusový target) — autofokus na první dlaždici obsahu.
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .tvFocusBorder(shape = shape)
                .clip(shape)
                // combinedClickable rozliší klik vs. podržení (long-press = editor řady, ne otevření detailu).
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .then(
                    if (canRate) Modifier.onKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyUp && (ev.key == Key.Menu || ev.key == Key.Info)) {
                            ratingProvider!!.requestRate(ratingTarget!!); true
                        } else false
                    } else Modifier,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            userStars?.let {
                UserRatingBadge(stars = it, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            }
            // Nápověda na fokusnuté kartě: MENU = ohodnotit (jen dokud není hodnoceno, ať to neruší).
            if (canRate && focused && userStars == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.StarBorder,
                        contentDescription = "MENU = ohodnotit",
                        tint = Color.White,
                        modifier = Modifier.height(14.dp),
                    )
                }
            }
        }
        // showLabel=false → Netflix immersive: čistý plakát bez popisku (název nese hero nahoře).
        if (showLabel) {
            // Text blok = max 2 ŘÁDKY pod obrázkem (user 2026-07-13): NÁZEV na 1 řádek (ellipsis) + ROK.
            // Pevná výška 46dp = stabilní karta (řada se neořízne).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(top = 6.dp, start = 2.dp, end = 2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                year?.let {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}
