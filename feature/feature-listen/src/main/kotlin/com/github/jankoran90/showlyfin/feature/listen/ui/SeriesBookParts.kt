package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.offline.OfflineState
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.feature.listen.ChapterProgress
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookChapter

/**
 * Řádek stažení celé knihy ze série (v hlavičce). Nic nestaženo = „Stáhnout vše"; běží = kolik kapitol
 * je hotovo, % aktuální, rychlost a odhad (klik zruší); část stažena = „Staženo X/Y · stáhnout zbytek";
 * vše = „Staženo" (dlouhý stisk smaže). Zdroj bez stažení (žádná kapitola s requestem) = nic.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesBookDownloadRow(
    chapters: List<SeriesBookChapter>,
    states: Map<String, OfflineState>,
    onDownloadAll: () -> Unit,
    onCancel: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadable = chapters.filter { it.download != null }
    if (downloadable.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val st = downloadable.map { states[it.key] ?: OfflineState() }
    val done = st.count { it.status == OfflineStatus.DOWNLOADED }
    val active = st.firstOrNull { it.status == OfflineStatus.DOWNLOADING }
    val running = st.any { it.status == OfflineStatus.DOWNLOADING || it.status == OfflineStatus.QUEUED }
    val failed = st.count { it.status == OfflineStatus.FAILED }
    val total = downloadable.size

    val click = when {
        running -> Modifier.clickable(onClick = onCancel)
        done == total -> Modifier.combinedClickable(onClick = {}, onLongClick = onDeleteAll)
        else -> Modifier.clickable(onClick = onDownloadAll)
    }
    Row(modifier.then(click).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            running -> {
                if (active != null && !active.preparing) {
                    CircularProgressIndicator(progress = { active.progress.coerceIn(0f, 1f) }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                }
                val detail = when {
                    active == null -> "čeká"
                    active.preparing -> "server připravuje"
                    else -> listOfNotNull(
                        "${(active.progress * 100).toInt()} %",
                        active.bytesPerSec.takeIf { it > 0 }?.let(DownloadFormat::speed),
                    ).joinToString(" · ")
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("Stahuji ${done + 1}/$total · $detail", style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Ťukni pro zrušení", style = MaterialTheme.typography.labelSmall, color = muted.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.Close, contentDescription = "Zrušit", tint = muted, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
            done == total -> {
                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text("Staženo ($total kap.)", style = MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.padding(start = 8.dp))
            }
            failed > 0 -> {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text("Staženo $done/$total · stáhnout znovu", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
            }
            done > 0 -> {
                Icon(Icons.Default.Download, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                Text("Staženo $done/$total · stáhnout zbytek", style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(start = 8.dp))
            }
            else -> {
                Icon(Icons.Default.Download, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                Text("Stáhnout", style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Kapitola = díl: číslo, název, délka / zbývá, progres poslechu, stav stažení, ⋮ menu (i dlouhý stisk). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesBookChapterRow(
    number: Int,
    chapter: SeriesBookChapter,
    progress: ChapterProgress?,
    offline: OfflineState?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val emphasized = isCurrent || highlighted
    val finished = progress?.isFinished == true
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .background(if (emphasized) accent.copy(alpha = 0.16f) else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                isCurrent && isPlaying -> Icons.Default.GraphicEq
                finished -> Icons.Default.CheckCircle
                else -> Icons.Default.PlayArrow
            },
            contentDescription = when { isCurrent && isPlaying -> "Hraje"; finished -> "Poslechnuto"; else -> null },
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                "%02d  %s".format(number, chapter.title),
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) accent else MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val remaining = progress?.takeIf { !it.isFinished && it.durMs > 0 }
                ?.let { DownloadFormat.eta((it.durMs - it.posMs).coerceAtLeast(0L) / 1000) }
            val meta = listOfNotNull(DownloadFormat.duration(chapter.durationSec).takeIf { chapter.durationSec > 0 }, remaining)
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress != null && !finished && progress.durMs > 0) {
                LinearProgressIndicator(
                    progress = { (progress.posMs.toFloat() / progress.durMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        ChapterDownloadBadge(offline)
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "Další akce", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ChapterDownloadBadge(state: OfflineState?) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (state?.status) {
        OfflineStatus.DOWNLOADED ->
            Icon(Icons.Default.DownloadDone, contentDescription = "Staženo", tint = accent, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        OfflineStatus.DOWNLOADING ->
            if (state.preparing) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 6.dp).size(16.dp), strokeWidth = 2.dp, color = accent)
            } else {
                CircularProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.padding(start = 6.dp).size(16.dp), strokeWidth = 2.dp, color = accent)
            }
        OfflineStatus.QUEUED ->
            Icon(Icons.Default.HourglassTop, contentDescription = "Ve frontě", tint = muted, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        OfflineStatus.FAILED ->
            Icon(Icons.Default.ErrorOutline, contentDescription = "Stažení selhalo", tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp).size(16.dp))
        else -> Unit
    }
}
