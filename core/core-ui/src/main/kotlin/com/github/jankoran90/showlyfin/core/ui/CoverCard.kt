package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

// ── CHORUS (SHW/HUB-68) Osa 2: kanonický „square cover" skelet (UNISON) ─────────────────
// Jeden zdroj pravdy pro čtvercové karty Poslechu (podcast / audiokniha / zdroj / sloučený /
// offline pořad). Skelet = Column[ Box(1:1 obálka + overlay sloty) + titulek + podtitulek ].
// Odznaky/progress dodá volající přes [overlay] (BoxScope → align si řeší sám) → 1:1 vzhled
// zachován, tvar/typografie z jednoho místa. Delegáti: PodcastCard/AudiobookCard/SourceCard.

/** Kanonický tvar čtvercových karet (odstraňuje privátní duplicity CoverShape/SourceCoverShape). */
val CoverCardShape = RoundedCornerShape(16.dp)

/**
 * Kanonická čtvercová karta obálky. [imageUrl] vyplní obálku (1:1), jinak [placeholder] uprostřed.
 * [overlay] běží v [BoxScope] obálky (odznaky přes `Modifier.align(...)`, progress dole). [title]
 * = titleSmall, [subtitle] = bodySmall (barva [subtitleColor], default onSurfaceVariant; null =
 * řádek se vynechá). [onLongClick] != null → combinedClickable (propojení zdrojů apod.).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    placeholder: ImageVector = Icons.Default.Podcasts,
    subtitleColor: Color? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val clickMod =
        if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        else Modifier.clickable(onClick = onClick)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CoverCardShape)
            .then(clickMod)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CoverCardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = placeholder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            overlay?.invoke(this)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
