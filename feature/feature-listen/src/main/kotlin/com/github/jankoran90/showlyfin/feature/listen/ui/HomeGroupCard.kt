package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import com.github.jankoran90.showlyfin.feature.listen.HomeGroupViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.core.ui.dilyCz
import com.github.jankoran90.showlyfin.feature.listen.HomeLayout
import com.github.jankoran90.showlyfin.feature.listen.HomeViewModel.ContinueItem
import com.github.jankoran90.showlyfin.feature.listen.player.PlayerState

/**
 * Hlavní karta skupiny rozposlouchaných ([HomeEntry.Group]): obrázek série / pořadu, počet rozposlouchaných a
 * naposled poslouchaná položka. Ťuk rozbalí / sbalí, ▶ pokračuje naposled poslouchanou položkou.
 */
@Composable
internal fun HomeGroupCard(
    group: HomeEntry.Group,
    expanded: Boolean,
    layout: HomeLayout,
    /** Naposled poslouchaná položka skupiny je načtená v přehrávači. */
    latestLoaded: Boolean,
    playing: Boolean,
    playerState: PlayerState,
    onToggle: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val count = group.items.size
    val countLine = "Rozposlouchané: " + if (group.isBooks) knihyCz(count) else dilyCz(count)
    val latestTitle = when (val l = group.latest) {
        is ContinueItem.Book -> l.book.title
        is ContinueItem.Episode -> l.episode.title
    }
    // Revize: čas a živý průběh jako u samostatné karty (hrající položka = pozice z přehrávače).
    val live = latestLoaded && playerState.isPlaying && playerState.durationMs > 0
    val (savedPos, savedDur, savedProgress) = when (val l = group.latest) {
        is ContinueItem.Book -> Triple((l.book.currentTimeSec * 1000).toLong(), (l.book.durationSec * 1000).toLong(), l.book.progress.toFloat())
        is ContinueItem.Episode -> Triple(l.posMs, l.durMs, l.progress)
    }
    val posMs = if (live) playerState.positionMs else savedPos
    val durMs = if (live) playerState.durationMs else savedDur
    val progress = if (live) playerState.positionMs.toFloat() / playerState.durationMs else savedProgress
    val placeholder = if (group.isBooks) Icons.Default.Headphones else Icons.Default.Podcasts
    if (layout.list) Column {
        HomeListCard(
            title = group.title,
            subtitle = countLine,
            description = "Naposled: $latestTitle",
            imageUrl = group.imageUrl,
            wideImage = group.wideImage,
            progress = progress,
            posMs = posMs,
            durMs = durMs,
            layout = layout,
            placeholder = placeholder,
            onClick = onToggle,
            isPlaying = latestLoaded,
            playing = playing,
            onPlayPause = onPlayPause,
        )
        ExpandHint(expanded, onToggle)
    } else {
        CoverCard(
            title = group.title,
            subtitle = "$countLine · naposled $latestTitle",
            imageUrl = group.imageUrl,
            onClick = onToggle,
            placeholder = placeholder,
            overlay = {
                if (progress > 0.001f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                }
                // Počet rozposlouchaných + šipka = je to skupina, ťuk ji rozbalí.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Sbalit" else "Rozbalit",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pauza" else "Pokračovat naposled poslouchaným",
                    )
                }
            },
        )
    }
}

@Composable
private fun ExpandHint(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        TextButton(onClick = onToggle) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            Text(if (expanded) "Sbalit" else "Rozbalit")
        }
    }
}

private fun knihyCz(count: Int): String = when {
    count == 1 -> "1 kniha"
    count in 2..4 -> "$count knihy"
    else -> "$count knih"
}

/**
 * Rozbalená skupina (user 2026-11-19 17:33/17:35): pruh přes celou šířku mřížky — hlavička (název, počet, Sbalit)
 * a pod ní VŠECHNY díly pořadu / knihy série od prvního ([HomeGroupViewModel]); rozposlouchané s průběhem, pruh hned
 * odjede na naposled poslouchaný a ten je zvýrazněný. Než se celý seznam načte, ukazují se jen rozposlouchané.
 */
@Composable
internal fun HomeGroupPanel(
    group: HomeEntry.Group,
    layout: HomeLayout,
    onCollapse: () -> Unit,
    child: @Composable (ContinueItem) -> Unit,
    other: @Composable (HomeGroupViewModel.Entry) -> Unit,
    groupVm: HomeGroupViewModel = hiltViewModel(),
) {
    LaunchedEffect(group.key) { groupVm.load(group.key, group.items) }
    val full by groupVm.full.collectAsStateWithLifecycle()
    val marks by groupVm.resumeMarks.collectAsStateWithLifecycle()
    val entries = full[group.key] ?: group.items.sortedBy { it.updatedAt }.map { HomeGroupViewModel.Entry.Listened(it) }
    val latestKey = continueItemKey(group.latest)
    val latestIndex = entries.indexOfFirst { it is HomeGroupViewModel.Entry.Listened && continueItemKey(it.item) == latestKey }
    val count = group.items.size
    val countLine = buildString {
        append("Rozposlouchané: ")
        append(if (group.isBooks) knihyCz(count) else dilyCz(count))
        full[group.key]?.let { append(" z ${it.size}") }
    }
    @Composable
    fun entryCard(e: HomeGroupViewModel.Entry, highlight: Boolean, mod: Modifier) {
        val hl = if (highlight) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier
        val finished = when (e) {
            is HomeGroupViewModel.Entry.Episode -> marks[e.episode.resumeKey ?: e.episode.id]?.isFinished == true
            is HomeGroupViewModel.Entry.Book -> e.book.isFinished == true
            is HomeGroupViewModel.Entry.Listened -> false
        }
        Box(mod.then(hl)) {
            when (e) {
                // Tester 2026-11-19: rozposlouchaný díl v pruhu stejným tvarem jako ostatní („7/12 název“), jen s průběhem.
                is HomeGroupViewModel.Entry.Listened -> if (e.item is ContinueItem.Book) child(e.item) else other(e)
                else -> other(e)
            }
            // Tester 2026-11-19: dohraný díl / kniha v pruhu = fajfka v rohu obalu.
            if (finished) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Poslechnuto",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .size(22.dp),
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(countLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onCollapse) {
                Icon(Icons.Default.ExpandLess, contentDescription = null)
                Text("Sbalit")
            }
        }
        val rowState = rememberLazyListState()
        LaunchedEffect(latestIndex, entries.size) {
            if (latestIndex > 0) rowState.scrollToItem((latestIndex - 1).coerceAtLeast(0))
        }
        // Tester 2026-11-19 + user 18:14: i v seznamu vodorovný pruh menších dlaždic (svislý seznam s obaly přes
        // celou šířku pobral jen ~1,5 dílu). [layout] zatím jen pro budoucí odlišení.
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            itemsIndexed(entries, key = { i, e -> entryKey(e, i) }) { i, e ->
                entryCard(e, highlight = i == latestIndex, mod = Modifier.width(if (layout.list) 150.dp else 160.dp))
            }
        }
    }
}

private fun entryKey(e: HomeGroupViewModel.Entry, index: Int): String = when (e) {
    is HomeGroupViewModel.Entry.Listened -> continueItemKey(e.item)
    is HomeGroupViewModel.Entry.Episode -> "ep:${e.sourceType}:${e.sourceRef}:${e.episode.id}"
    is HomeGroupViewModel.Entry.Book -> "book:${e.book.id}"
} + "#$index"

