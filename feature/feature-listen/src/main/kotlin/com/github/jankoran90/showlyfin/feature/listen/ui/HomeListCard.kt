package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.CoverCardShape
import com.github.jankoran90.showlyfin.feature.listen.HomeLayout

/**
 * User (2026-09-29 13:12–13:14) — jednosloupcové Domů: „cover velký, ať se vejde vždy název epizody,
 * kompaktní popisek, časový údaj do konce; vždy jeden díl a jeho celý obsah na jednu obrazovku".
 * Velký cover = celá šířka v 16:9 (výška ~šířka·0.56 → karta se vejde na displej i s popisem, který je
 * omezený [HomeLayout.descLines]). [wideImage] = video náhled (YouTube/ČT) → Crop (odstřihne i zapečené
 * pruhy hqdefault); čtvercová obálka knihy/RSS → Fit, celá, bez ořezu. Malý cover = 112 dp vlevo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeListCard(
    title: String,
    subtitle: String?,
    description: String?,
    imageUrl: String?,
    wideImage: Boolean,
    progress: Float,
    posMs: Long,
    durMs: Long,
    layout: HomeLayout,
    placeholder: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onEndListening: (() -> Unit)? = null,
    /** User (2026-09-29, „mini play/pause na kartě, ať Domů nezmizí") — null = tlačítko se neukáže. */
    playing: Boolean? = null,
    onPlayPause: () -> Unit = {},
) {
    val clickMod =
        if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        else Modifier.clickable(onClick = onClick)
    val cover: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(CoverCardShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (wideImage || !layout.bigCover) ContentScale.Crop else ContentScale.Fit,
                )
            } else {
                Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            }
            CoverBadges(isPlaying, onEndListening)
        }
    }
    val texts: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = if (layout.bigCover) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            description?.takeIf { layout.descLines > 0 && it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = layout.descLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TimeLine(progress, posMs, durMs, layout.remaining, playing, onPlayPause)
        }
    }
    if (layout.bigCover) {
        Column(modifier.fillMaxWidth().clip(CoverCardShape).then(clickMod).padding(4.dp)) {
            cover(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            texts(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp))
        }
    } else {
        Row(modifier.fillMaxWidth().clip(CoverCardShape).then(clickMod).padding(4.dp)) {
            cover(Modifier.size(112.dp))
            texts(Modifier.weight(1f).padding(start = 12.dp))
        }
    }
}

@Composable
private fun BoxScope.CoverBadges(isPlaying: Boolean, onEndListening: (() -> Unit)?) {
    if (isPlaying) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Hraje",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(3.dp)
                .size(16.dp),
        )
    }
    if (onEndListening != null) {
        EndListeningButton(compact = true, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp), onConfirm = onEndListening)
    }
}

@Composable
private fun TimeLine(progress: Float, posMs: Long, durMs: Long, remaining: Boolean, playing: Boolean?, onPlayPause: () -> Unit) {
    val text = when {
        durMs <= 0L -> null
        remaining -> "zbývá ${formatHomeClock((durMs - posMs).coerceAtLeast(0L))}"
        else -> "${formatHomeClock(posMs)} / ${formatHomeClock(durMs)}"
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        text?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (playing != null) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.padding(start = 10.dp).size(40.dp),
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pauza" else "Poslouchat",
                )
            }
        }
    }
}

/** „1:23:45" / „12:05". */
internal fun formatHomeClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Popis epizody pro seznam — RSS nese HTML, YouTube syrový text s odřádkováním. */
internal fun homeDescription(raw: String?): String? =
    raw?.replace(Regex("<[^>]*>"), " ")
        ?.replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        ?.replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
            // YouTube popisy nesou emoji jako číselné entity (&#127911;) — převést na znak, jinak čte user syrový kód.
            val cp = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else ""
        }
        ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
