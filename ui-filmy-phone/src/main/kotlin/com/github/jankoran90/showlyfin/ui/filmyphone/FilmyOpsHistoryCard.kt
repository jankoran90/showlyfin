package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.uploader.model.OpsHistoryItem
import com.github.jankoran90.showlyfin.data.uploader.model.OpsHistoryResponse

/**
 * PROVOZ (SHW-114) — karta „Historie".
 *
 * Zadání usera (2026-08-03 14:50): *„Možná i historii a nějaký poměr, zda se stíhalo zásobovat, nebo
 * nějaký statistiky, který můžou být v historii výhodný vidět."*
 *
 * 🔴 **Výkon měří přehrávač, ne server** (user 2026-08-03: *„chci vidět výkon u všeho a reálný"*),
 * takže čísla platí i pro Jellyfin, Českou televizi a přímé odkazy. Zádrhel = přehrávač se zastavil
 * a čekal na data; průměrná rychlost to neprozradí.
 */
@Composable
internal fun FilmyOpsHistoryCard(history: OpsHistoryResponse?) {
    FilmyCollapsibleSection(
        title = "Historie",
        icon = Icons.Rounded.History,
        initiallyExpanded = false,
    ) {
        val items = history?.items.orEmpty()
        val s = history?.summary
        if (s == null || s.sessions == 0) {
            OpsHint("Zatím tu nic není. Historie se plní sama, jakmile něco dokoukáš.")
            return@FilmyCollapsibleSection
        }
        OpsStat("Přehrání za ${s.days} dní", "${s.sessions}")
        OpsStat("Nakoukáno", FilmyOpsFormat.duration(s.watchedMs))
        OpsStat("Dokoukáno do konce", "${s.finished}")
        if (s.smoothPct != null) {
            OpsStat("Bez zastavení", "${s.smoothPct} % (z ${s.measuredSessions} přehrání)")
            if (s.avgBandwidthBps > 0) {
                OpsStat("Průměrná rychlost linky", FilmyOpsFormat.speed(s.avgBandwidthBps))
            }
            if (s.totalStalls > 0) {
                OpsStat("Celkem zastavení", "${s.totalStalls}× · ${FilmyOpsFormat.duration(s.stalledMs)}")
            }
            if (s.avgSpeedBps > 0) OpsStat("Rychlost z našeho serveru", FilmyOpsFormat.speed(s.avgSpeedBps))
            if (s.totalWaits > 0) OpsStat("Čekání na náš server", "${s.totalWaits}×")
        } else {
            OpsHint("U starších přehrání appka výkon ještě neměřila, takže se do poměru nepočítají.")
        }
        s.bySource.take(5).forEach { u ->
            OpsStat(
                label = FilmyOpsFormat.sourceLabel(u.source, u.source),
                value = "${u.sessions}× · ${FilmyOpsFormat.duration(u.watchedMs)}",
            )
        }
        if (items.isNotEmpty()) {
            Text(
                text = "Poslední přehrání",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
            )
            items.take(30).forEach { HistoryRow(it) }
        }
    }
}

@Composable
private fun HistoryRow(h: OpsHistoryItem) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = h.title + if (h.subtitle.isNotBlank()) " — ${h.subtitle}" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(FilmyOpsFormat.ago(h.at))
                if (h.device.isNotBlank()) append(" · ").append(h.device)
                if (h.profileName.isNotBlank()) append(" · ").append(h.profileName)
                append(" · ").append(FilmyOpsFormat.sourceLabel(h.source, h.source))
                append(" · ").append(FilmyOpsFormat.duration(h.watchedMs))
                if (h.completedPct > 0) append(" (").append(h.completedPct).append(" %)")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Výkon měřil přehrávač → řádek dává smysl u KAŽDÉHO zdroje, ne jen u našeho.
        val measured = h.bandwidthBps > 0 || h.stalls > 0 || h.source == "sdilej"
        if (measured) {
            Text(
                text = buildString {
                    if (h.smooth) append("Hrálo plynule") else {
                        append("Zastavilo se ").append(maxOf(h.stalls, h.waits)).append("×")
                        if (h.stalledMs > 0) append(" · ").append(FilmyOpsFormat.duration(h.stalledMs))
                    }
                    if (h.bandwidthBps > 0) append(" · linka ").append(FilmyOpsFormat.speed(h.bandwidthBps))
                    if (h.videoHeight > 0) append(" · ").append(h.videoHeight).append("p")
                    if (h.fromCacheRatio > 0) {
                        append(" · ").append(FilmyOpsFormat.percent(h.fromCacheRatio)).append(" ze zásoby")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (h.smooth) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}
