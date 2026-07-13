package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                // combinedClickable rozliší klik vs. podržení (long-press = editor řady, ne otevření detailu).
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // showLabel=false → Netflix immersive: čistý plakát bez popisku (název nese hero nahoře).
        if (showLabel) {
            // Text blok PEVNÉ výšky (stabilní výška karty → řada se neořízne), ale název BEZ minLines:
            // krátký název nevytvoří prázdný 2. řádek, takže rok sedí TĚSNĚ pod názvem (nahoře), zbytek bloku
            // je prázdný dole — fix „rok daleko dole / obří mezera" (user feedback 2026-07-13). Column je
            // Top-zarovnaný (default), proto se text drží u sebe nahoře, ne rozstřelený přes celý blok.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 66dp: pojme 2 řádky názvu (2×20dp lineHeight) + rok (16dp) + horní padding, aby se 3. „řádek"
                    // (rok pod dvouřádkovým názvem) neořízl. Stabilní výška karty zůstává. (user feedback OTA 297)
                    .height(66.dp)
                    .padding(top = 6.dp, start = 2.dp, end = 2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
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
