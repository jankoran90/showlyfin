package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.feature.listen.DownloadsViewModel

/**
 * Slovo 2026-10-13 (user „samotná sekce stahování v sidebaru — fronta, staženo, stahování, rychlost
 * a ETA") — Stahuje se (progres, rychlost, zbývá), Ve frontě (+ odhad celé fronty), Selhalo
 * (zkusit znovu), Staženo (přehrát z telefonu, smazat). Díly RSS / YouTube / ČT.
 */
@Composable
fun DownloadsScreen(
    onOpenAudioPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val st by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<OfflineDownload?>(null) }

    if (st.active == null && st.queued.isEmpty() && st.failed.isEmpty() && st.downloaded.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nic se nestahuje a nic není staženo.\nDíl stáhneš v menu ⋮ u dílu, celou sérii v knize tlačítkem Stáhnout.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        st.active?.let { a ->
            item(key = "h_active") { SectionTitle("Stahuje se") }
            item(key = "active_${a.request.key}") {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Column(Modifier.padding(10.dp)) {
                        DownloadRow(a.request.posterUrl, a.request.title, a.request.subtitle) {
                            IconButton(onClick = { viewModel.cancel(a.request.key) }) { Icon(Icons.Default.Close, contentDescription = "Zrušit") }
                        }
                        val s = a.state
                        if (s.preparing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Meta("Server připravuje soubor (ČT díl se skládá z kousků, asi minuta a půl)…")
                        } else {
                            LinearProgressIndicator(progress = { s.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Meta(
                                listOfNotNull(
                                    "${(s.progress * 100).toInt()} %",
                                    s.totalBytes.takeIf { it > 0 }?.let { "${DownloadFormat.bytes(s.downloadedBytes)} / ${DownloadFormat.bytes(it)}" },
                                    s.bytesPerSec.takeIf { it > 0 }?.let(DownloadFormat::speed),
                                    s.etaSec?.let(DownloadFormat::eta),
                                ).joinToString(" · "),
                            )
                        }
                    }
                }
            }
        }
        if (st.queued.isNotEmpty()) {
            item(key = "h_queue") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Ve frontě (${st.queued.size})")
                        st.queueEtaSec?.let { Meta("Celá fronta: ${DownloadFormat.eta(it)} (odhad)") }
                    }
                    TextButton(onClick = viewModel::cancelAll) { Text("Zrušit vše") }
                }
            }
            items(st.queued, key = { "q_${it.request.key}" }) { q ->
                DownloadRow(q.request.posterUrl, q.request.title, listOfNotNull(q.request.subtitle, q.request.durationSec.takeIf { it > 0 }?.let(DownloadFormat::duration)).joinToString(" · ")) {
                    IconButton(onClick = { viewModel.cancel(q.request.key) }) { Icon(Icons.Default.Close, contentDescription = "Odebrat z fronty") }
                }
            }
        }
        if (st.failed.isNotEmpty()) {
            item(key = "h_failed") { SectionTitle("Selhalo (${st.failed.size})") }
            items(st.failed, key = { "f_${it.request.key}" }) { f ->
                DownloadRow(f.request.posterUrl, f.request.title, f.state.error ?: "Stažení selhalo") {
                    IconButton(onClick = { viewModel.retry(f.request.key) }) { Icon(Icons.Default.Refresh, contentDescription = "Zkusit znovu") }
                    IconButton(onClick = { viewModel.cancel(f.request.key) }) { Icon(Icons.Default.Close, contentDescription = "Odebrat") }
                }
            }
        }
        if (st.downloaded.isNotEmpty()) {
            item(key = "h_done") {
                Column {
                    SectionTitle("Staženo (${st.downloaded.size})")
                    Meta("Zabráno ${DownloadFormat.bytes(st.usedBytes)} · volno ${DownloadFormat.bytes(st.freeBytes)}")
                }
            }
            items(st.downloaded, key = { "d_${it.key}" }) { d ->
                DownloadRow(
                    d.posterPath ?: d.posterUrl, d.title,
                    listOfNotNull(d.subtitle, DownloadFormat.bytes(d.sizeBytes)).joinToString(" · "),
                    onClick = { viewModel.play(d); onOpenAudioPlayer() },
                ) {
                    IconButton(onClick = { viewModel.play(d); onOpenAudioPlayer() }) { Icon(Icons.Default.PlayArrow, contentDescription = "Přehrát") }
                    if (!st.isKids) {
                        IconButton(onClick = { confirmDelete = d }) { Icon(Icons.Default.Delete, contentDescription = "Smazat") }
                    }
                }
            }
        }
    }

    confirmDelete?.let { d ->
        ListenEpisodeActionSheet(
            title = d.title,
            infoLine = "Stažený díl se smaže z telefonu (${DownloadFormat.bytes(d.sizeBytes)}).",
            actions = listOf(ListenEpisodeAction(Icons.Default.Delete, "Smazat z telefonu") { viewModel.delete(d.key) }),
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Meta(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DownloadRow(
    image: String?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(72.dp).height(48.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(48.dp).padding(12.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}
