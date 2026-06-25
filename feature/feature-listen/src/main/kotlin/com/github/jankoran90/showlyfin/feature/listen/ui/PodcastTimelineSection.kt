package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.feature.listen.PodcastTimelineViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AGORA (Timeline): chronologický feed nových epizod ze všech sledovaných zdrojů, bucketovaný po čase
 * (Dnes / Tento týden / Minulý týden / po týdnech a měsících). Každý řádek je PŘEHLEDNÝ: obálka,
 * **tučný název pořadu** + datum, název epizody, pár řádků popisu (klik = rozbalit celý) a akce
 * Přehrát · Do fronty · Stáhnout. Vše čte z [MaterialTheme] tokenů (UNISON) a respektuje zobrazovací
 * volby z Nastavení (popis on/off, počet řádků popisu, datum on/off).
 *
 * [refreshKey] = libovolná hodnota, jejíž změna vynutí refresh (např. zavření filtru přepočítá feed).
 */
@Composable
fun PodcastTimelineSection(
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Any? = null,
    viewModel: PodcastTimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val offlineStates by viewModel.offlineStates.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(refreshKey) {
        if (refreshKey != null) viewModel.refresh()
    }

    when {
        state.loading && state.buckets.isEmpty() ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        state.error != null && state.buckets.isEmpty() ->
            TimelineMessage(modifier, state.error!!)

        state.noSources ->
            TimelineMessage(
                modifier,
                "Zatím nesleduješ žádné zdroje.\nPřidej podcast nebo YouTube kanál v záložce Objev.",
                actionLabel = "Přejít na Objev",
                onAction = onOpenDiscover,
            )

        state.empty ->
            TimelineMessage(
                modifier,
                "Za zvolené období žádné nové epizody.\nZkus rozšířit časový rozsah ve filtru.",
            )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.buckets.forEach { bucket ->
                item(key = "h:${bucket.label}") {
                    Text(
                        bucket.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(bucket.items, key = { "e:${it.key}" }) { item ->
                    val thisKey = item.key
                    val offlineStatus = offlineStates[thisKey]?.status ?: OfflineStatus.NONE
                    TimelineRow(
                        item = item,
                        display = state.display,
                        isPlaying = player.isActive && player.currentEpisodeId == thisKey,
                        offlineStatus = offlineStatus,
                        onPlay = { viewModel.play(item) },
                        onEnqueue = { viewModel.enqueue(item) },
                        onDownload = { viewModel.download(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    item: PodcastTimelineViewModel.TimelineItem,
    display: PodcastTimelineViewModel.DisplayPrefs,
    isPlaying: Boolean,
    offlineStatus: OfflineStatus,
    onPlay: () -> Unit,
    onEnqueue: () -> Unit,
    onDownload: () -> Unit,
) {
    val ep = item.episode
    var descExpanded by remember(item.key) { mutableStateOf(false) }
    val dateLabel = remember(ep.date) { if (display.showDate) formatEpisodeDate(item.timestampMs) else null }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onPlay)
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .animateContentSize(),
    ) {
        // ── Hlavička: obálka · (tučný pořad + datum) + název epizody · indikátor hraje ──
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (ep.imageUrl != null) {
                    AsyncImage(
                        model = ep.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        if (item.sourceType == "youtube") Icons.Default.PlayArrow else Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                // Tučný název pořadu (host/pořad) + datum vpravo — v mergnutém feedu = odkud epizoda je.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.sourceTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (dateLabel != null) {
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    ep.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (isPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Právě hraje",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(start = 4.dp),
                )
            }
        }

        // ── Popis „o čem epizoda je" (3–5 řádků dle Nastavení, klik = celý) ──
        val desc = ep.description?.let { cleanDescription(it) }
        if (display.showDescription && !desc.isNullOrBlank()) {
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (descExpanded) Int.MAX_VALUE else display.descriptionLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { descExpanded = !descExpanded },
            )
        }

        // ── Akce: Přehrát · Do fronty · Stáhnout ──
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionChip(Icons.Default.PlayArrow, "Přehrát", onClick = onPlay)
            ActionChip(Icons.Default.QueueMusic, "Do fronty", onClick = onEnqueue)
            DownloadChip(offlineStatus, onClick = onDownload)
        }
    }
}

/** Malé akční tlačítko (ikona + text) v tokenech — primary akcent na pozadí. */
@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Akce Stáhnout s indikací stavu (stahuje se / staženo / k dispozici). */
@Composable
private fun DownloadChip(status: OfflineStatus, onClick: () -> Unit) {
    when (status) {
        OfflineStatus.DOWNLOADED ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Staženo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Staženo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

        OfflineStatus.QUEUED, OfflineStatus.DOWNLOADING ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Stahuji…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        else -> ActionChip(Icons.Default.Download, "Stáhnout", onClick = onClick)
    }
}

@Composable
private fun TimelineMessage(
    modifier: Modifier,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.TextButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}

// ───────────────────────── Pomocné formátování ─────────────────────────

/** Datum epizody čitelně: „Dnes" / „Včera" / „25. 6." (letos) / „25. 6. 2025" (jiný rok). */
private fun formatEpisodeDate(timestampMs: Long): String {
    val now = Calendar.getInstance()
    val day = Calendar.getInstance().apply { timeInMillis = timestampMs }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(day, now) -> "Dnes"
        sameDay(day, yesterday) -> "Včera"
        day.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("d. M.", Locale("cs")).format(Date(timestampMs))
        else -> SimpleDateFormat("d. M. yyyy", Locale("cs")).format(Date(timestampMs))
    }
}

/** Očistí popis od HTML tagů a nadbytečných bílých znaků (RSS popisy bývají HTML). */
private fun cleanDescription(raw: String): String =
    raw.replace(Regex("<[^>]*>"), " ")
        .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()
