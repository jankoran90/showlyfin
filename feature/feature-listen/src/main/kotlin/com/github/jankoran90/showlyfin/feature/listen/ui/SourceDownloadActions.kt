package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.feature.listen.KidsShelfActionsViewModel

/** Na čem se pracuje: [primary] = zdroj, ze kterého se stahuje; [keys] = všichni členové (sloučený pořad). */
internal data class SourceDownloadTarget(
    val primary: PodcastSource?,
    val keys: Set<String>,
    val seriesSlug: String?,
    val title: String,
)

/**
 * Slovo 2026-10-13 (user „musíme celkově umět pracovat se staženými; ať je to stejné nehledě na typ
 * zdroje, i ovládání") — JEDNY volby stažení pro dlouhý stisk na pořadu / sérii, u dětí i dospělých:
 * Stáhnout vše · Stáhnout znovu · Odstranit stažené (odstranit jde i offline). Vrací info řádek + akce.
 */
@Composable
internal fun sourceDownloadActions(
    target: SourceDownloadTarget,
    offline: Boolean,
    vm: KidsShelfActionsViewModel,
): Pair<String, List<ListenEpisodeAction>> {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val count = remember(target, downloads) { vm.downloadedCount(target.keys, target.seriesSlug) }
    val src = target.primary
    val info = if (count > 0) "V telefonu: $count dílů" else "V telefonu zatím nic"
    return info to listOfNotNull(
        if (!offline && src != null) ListenEpisodeAction(Icons.Default.Download, "Stáhnout všechny díly a zařadit do fronty") {
            vm.downloadAndQueue(src, target.seriesSlug, target.title)
        } else null,
        if (!offline && src != null && count > 0) ListenEpisodeAction(
            Icons.Default.Refresh, "Stáhnout znovu",
            confirmMessage = "Stažené díly se smažou a stáhnou nanovo (třeba po úpravě na serveru).",
        ) { vm.redownload(src, target.keys, target.seriesSlug, target.title) } else null,
        if (count > 0) ListenEpisodeAction(
            Icons.Default.DeleteOutline, "Odstranit stažené ($count dílů)",
            confirmMessage = "Stažené díly se smažou z telefonu. Online je půjde poslouchat dál.",
        ) { vm.deleteDownloaded(target.keys, target.seriesSlug) } else null,
    )
}
