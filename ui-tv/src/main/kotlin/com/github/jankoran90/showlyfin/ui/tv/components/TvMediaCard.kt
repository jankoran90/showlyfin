package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.domain.MediaItem
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
    )
}

/**
 * Sdílená TV plakátová dlaždice (mřížka Sleduj i Hledání). Bere PŘÍMOU URL plakátu (Hledání má hotové
 * TMDB URL, doporučovač staví z `posterPath`) → jeden vzhled napříč obrazovkami.
 *
 * POZOR na pořadí modifikátorů: `tvFocusBorder` (uvnitř má `onFocusChanged`) MUSÍ být PŘED `clickable`,
 * jinak `onFocusChanged` fokusový uzel `clickable` (který je pak výš/vně) nepozoruje → prstenec/lift se
 * nikdy nevykreslí. Přesně tahle chyba dělala „kolem coverů žádné zvýraznění" na TV Home (user 2026-07-12).
 */
@Composable
fun TvPosterCard(
    posterUrl: String?,
    title: String,
    year: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = true,
) {
    val shape = MaterialTheme.shapes.medium   // tvar z theme (design guard: žádný inline RoundedCornerShape)
    // fillMaxWidth (ne pevná šířka) → dlaždice respektuje šířku buňky mřížky (GridCells.Fixed) místo aby ji přebíjela.
    Column(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                // focusRequester PŘED clickable (fokusový target) — autofokus na první dlaždici obsahu.
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .tvFocusBorder(shape = shape)
                .clip(shape)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // showLabel=false → Netflix immersive: čistý plakát bez popisku (název nese hero nahoře).
        if (showLabel) {
            // Titulek VŽDY 2 řádky (min=max) — jinak karty s dlouhým názvem byly vyšší, řada zabrala výšku nejvyšší
            // karty a druhá řada plakátů se ořízla (user feedback 2026-07-12). Fixní výška = stabilní mřížka.
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
            year?.let {
                Text(
                    text = "$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 1.dp),
                )
            }
        }
    }
}
