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
 * 🔴 **Plynulost se počítá jen z toho, co teklo přes náš server.** U Jellyfinu a České televize
 * o přenosu nevíme nic — započítat je do „bez zádrhelu" by statistiku nalakovalo do zelena.
 * Proto se u souhrnu píše, z kolika relací se vlastně měřilo.
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
            OpsStat("Bez čekání na data", "${s.smoothPct} % (z ${s.measuredSessions} měřených)")
            OpsStat("Průměrná rychlost", FilmyOpsFormat.speed(s.avgSpeedBps))
            if (s.totalWaits > 0) OpsStat("Celkem se čekalo", "${s.totalWaits}×")
        } else {
            OpsHint("Rychlost a plynulost se měří jen u streamů, které tečou přes náš server " +
                "(sdilej.cz). Jellyfin a Česká televize jdou do zařízení přímo.")
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
        // Řádek o zásobování dává smysl jen u streamů, které jsme sami dodávali.
        if (h.source == "sdilej") {
            Text(
                text = if (h.smooth) {
                    "Zásobování stíhalo" +
                        (if (h.avgSpeedBps > 0) " · ${FilmyOpsFormat.speed(h.avgSpeedBps)}" else "") +
                        (if (h.fromCacheRatio > 0) " · ${FilmyOpsFormat.percent(h.fromCacheRatio)} ze zásoby" else "")
                } else {
                    "Čekalo se na data ${h.waits}×" +
                        (if (h.directTails > 0) " · nouzové dotažení ${h.directTails}×" else "") +
                        (if (h.avgSpeedBps > 0) " · ${FilmyOpsFormat.speed(h.avgSpeedBps)}" else "")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (h.smooth) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}
