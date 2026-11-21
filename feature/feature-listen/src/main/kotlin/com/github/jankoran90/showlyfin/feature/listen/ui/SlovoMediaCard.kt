package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tokens.CoverSize
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing

/**
 * AMPHORA (SHW-124) DS1 — JEDNA karta pro knihu, díl, pořad i YouTube video: obal, název, druhý
 * řádek, průběh, „zbývá“, stav (dohráno) a odznak (NOVÉ, ▶ YT, Děti…). Všechny typy mají průběh, stav
 * a odznaky na stejném místě. Tvary z `MaterialTheme.shapes`, mezery z [Spacing].
 */
data class SlovoMediaModel(
    val title: String,
    val subtitle: String? = null,
    val coverUrl: String? = null,
    /** 0..1, null = bez průběhu (nespuštěné). */
    val progress: Float? = null,
    /** Lidsky: „zbývá 42 min“, „kniha · 2 h 10“. */
    val meta: String? = null,
    val badge: String? = null,
    /** Odznak v barvě akcentu (NOVÉ), jinak tlumený (Děti, ▶ YT). */
    val badgeAccent: Boolean = false,
    val finished: Boolean = false,
    /** 16:9 obal (YouTube, ČT), jinak čtverec. */
    val wide: Boolean = false,
    val playing: Boolean = false,
    val placeholder: ImageVector = Icons.Rounded.Headphones,
)

/** Svislá karta do řad (Domů, Knihovna mřížka). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlovoMediaCard(
    model: SlovoMediaModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = CoverSize.card,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        MediaCover(model, Modifier.fillMaxWidth().aspectRatio(if (model.wide) 16f / 9f else 1f))
        Text(
            model.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (model.playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.s),
        )
        MediaMetaLine(model)
    }
}

/** Vodorovný řádek do seznamů (Fronta, Knihovna seznam, výsledky hledání). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlovoMediaRow(
    model: SlovoMediaModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        MediaCover(model, Modifier.size(CoverSize.row), compact = true)
        Column(Modifier.weight(1f)) {
            Text(
                model.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (model.playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MediaMetaLine(model)
        }
        trailing?.invoke()
    }
}

@Composable
private fun MediaCover(model: SlovoMediaModel, modifier: Modifier, compact: Boolean = false) {
    Box(
        modifier
            .clip(if (compact) MaterialTheme.shapes.small else MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(model.placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.coverUrl != null) {
            AsyncImage(
                model = model.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!compact && model.badge != null) {
            Surface(
                color = if (model.badgeAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                contentColor = if (model.badgeAccent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.s),
            ) {
                Text(model.badge, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs))
            }
        }
        if (model.finished) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Dohráno",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(if (compact) Spacing.l else Spacing.xl),
            )
        }
        val p = model.progress
        if (p != null && p > 0f && !model.finished) {
            LinearProgressIndicator(
                progress = { p.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Spacing.xs),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun MediaMetaLine(model: SlovoMediaModel) {
    val line = listOfNotNull(
        model.subtitle?.takeIf { it.isNotBlank() },
        if (model.finished) "dohráno" else model.meta?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    if (line.isBlank()) return
    Text(
        line,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** „zbývá 42 min“ / „zbývá 1 h 05“ z milisekund; null když není co hlásit. */
fun remainingLabel(posMs: Long, durMs: Long): String? {
    if (durMs <= 0L) return null
    val left = (durMs - posMs).coerceAtLeast(0L)
    return "zbývá ${durationLabel(left)}"
}

/** „42 min“ / „1 h 05“ (bez sekund — na kartách stačí minuty). */
fun durationLabel(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(if (ms > 0) 1 else 0)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "%d h %02d".format(h, m) else "$m min"
}
