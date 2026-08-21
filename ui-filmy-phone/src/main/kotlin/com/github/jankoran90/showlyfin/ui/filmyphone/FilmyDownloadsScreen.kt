package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.github.jankoran90.showlyfin.core.domain.MediaType
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.ui.phone.ActiveRow
import com.github.jankoran90.showlyfin.ui.phone.DownloadsViewModel

/**
 * SEZONA-DÁVKA (user 2026-08-21: „udelej mi sekci stazeno kde uvidím kartu filmu/seriálu") —
 * telefonní sekce „Stažené" appky Filmy. Na rozdíl od staré [com.github.jankoran90.showlyfin.
 * ui.phone.DownloadsScreen] (plochý seznam řádků, showlyfin base app) tahle obrazovka seskupuje
 * stažené DÍLY STEJNÉHO seriálu do JEDNÉ karty (jako všude jinde ve Filmotéce/Pro tebe) — sdílí
 * [DownloadsViewModel] (žádná nová logika stahování), jen jiné seskupení/vykreslení stejných dat.
 * Klik na kartu otevře sdílený detail; offline stav/smazání řeší existující menu „Stáhnout" tam
 * (viz `DetailViewModel`/`DownloadMenuSheet`, SEZONA-DÁVKA fáze 1).
 */
@Composable
fun FilmyDownloadsScreen(
    onMenu: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val allDownloads by viewModel.downloads.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()

    // LEVER L3: audio podcasty (Poslech) sem nepatří — jsou ve „Stažené" v Poslechu (stejný filtr
    // jako stará ui-phone/DownloadsScreen).
    val downloads = allDownloads.filterNot { it.type == OfflineRequest.TYPE_PODCAST }
    val active = states.entries
        .filter { it.value.status == OfflineStatus.DOWNLOADING || it.value.status == OfflineStatus.QUEUED || it.value.status == OfflineStatus.FAILED }
        .filterNot { viewModel.isPodcast(it.key) }
        .sortedBy { it.key }

    val groups = remember(downloads) { groupDownloadsIntoCards(downloads) }
    val used = remember(downloads) { viewModel.usedBytes() }
    val free = remember(downloads) { viewModel.freeBytes() }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(title = "Stažené", onMenu = onMenu)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Zabráno ${formatDownloadBytes(used)} · volných ${formatDownloadBytes(free)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (downloads.isNotEmpty()) {
                TextButton(onClick = { viewModel.deleteAll() }) { Text("Smazat vše") }
            }
        }
        if (active.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                active.forEach { entry ->
                    ActiveRow(
                        title = viewModel.titleFor(entry.key),
                        status = entry.value.status,
                        progress = entry.value.progress,
                        downloadedBytes = entry.value.downloadedBytes,
                        totalBytes = entry.value.totalBytes,
                        error = entry.value.error,
                        onCancel = { viewModel.cancel(entry.key) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (groups.isEmpty() && active.isEmpty()) {
            FilmyEmpty(
                icon = Icons.Rounded.Download,
                title = "Zatím nic staženého",
                text = "V detailu filmu/seriálu zvol \"Stáhnout\" a po stažení ho uvidíš tady i bez sítě.",
            )
        } else {
            FilmyMediaGrid(items = groups, onOpenDetail = onOpenDetail)
        }
    }
}

/**
 * Filmy = 1 karta na film ([OfflineRequest.TYPE_MOVIE]); díly seriálu se seskupí (tmdb, jinak imdb)
 * do JEDNÉ karty s počtem stažených dílů v popisu, seřazeno podle nejnovějšího přírůstku.
 */
private fun groupDownloadsIntoCards(downloads: List<OfflineDownload>): List<MediaItem> {
    data class Sortable(val item: MediaItem, val sortKey: Long)

    val movies = downloads.filter { it.type == OfflineRequest.TYPE_MOVIE }.map { dl ->
        Sortable(
            item = MediaItem(
                traktId = 0L, tmdbId = dl.tmdb?.toLong(), imdbId = dl.imdb, title = dl.title, year = null,
                overview = dl.description, rating = null, genres = null, type = MediaType.MOVIE,
                fallbackPosterUrl = dl.posterUrl,
            ),
            sortKey = dl.addedAt,
        )
    }
    val showGroups = downloads.filter { it.type == OfflineRequest.TYPE_EPISODE }
        .groupBy { it.tmdb?.let { t -> "tmdb:$t" } ?: it.imdb?.let { i -> "imdb:$i" } ?: it.key }
        .map { (_, eps) ->
            val newest = eps.maxBy { it.addedAt }
            Sortable(
                item = MediaItem(
                    traktId = 0L, tmdbId = newest.tmdb?.toLong(), imdbId = newest.imdb, title = newest.title,
                    year = null, overview = "${eps.size} " + pluralDilu(eps.size) + " stažen" + (if (eps.size == 1) "ý" else "o"),
                    rating = null, genres = null, type = MediaType.SHOW, fallbackPosterUrl = newest.posterUrl,
                ),
                sortKey = eps.maxOf { it.addedAt },
            )
        }
    return (movies + showGroups).sortedByDescending { it.sortKey }.map { it.item }
}

private fun pluralDilu(n: Int): String = when {
    n == 1 -> "díl"
    n in 2..4 -> "díly"
    else -> "dílů"
}

private fun formatDownloadBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val units = arrayOf("B", "kB", "MB", "GB", "TB")
    var value = b.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024.0
        i++
    }
    return if (i == 0) "%.0f %s".format(value, units[i]) else "%.1f %s".format(value, units[i])
}
