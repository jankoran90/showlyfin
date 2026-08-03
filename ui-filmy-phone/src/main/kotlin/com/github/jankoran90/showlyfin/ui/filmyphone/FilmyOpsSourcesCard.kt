package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSourcedItem
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSourcesResponse

/**
 * PROVOZ (SHW-114) — karta „Zdroje".
 *
 * Zadání usera: *„proklik na stavy zdrojů, co se hledá, má dohledat, spustí autodohledání chybějících,
 * upraví, odstraní, a hlavně ukáže potvrzení, že zdroj je zdravý."*
 *
 * Zdravý zdroj = má odkaz a víme o něm rozlišení i jazyk zvuku. Když jazyk vyšel jen z názvu souboru
 * (`audioGuessed`), řekneme to nahlas — přesně tohle už jednou vyrobilo „český" film bez češtiny.
 */
@Composable
internal fun FilmyOpsSourcesCard(
    sources: OpsSourcesResponse?,
    policy: String?,
    busy: String?,
    onSweep: () -> Unit,
    onVerify: () -> Unit,
    onRemove: (tmdb: Long, imdb: String, title: String) -> Unit,
    onPolicy: (String) -> Unit,
) {
    val counts = sources?.counts
    FilmyCollapsibleSection(
        title = if (counts == null) "Zdroje" else "Zdroje (${counts.withSource}/${counts.wanted})",
        icon = Icons.Rounded.Inventory2,
        initiallyExpanded = true,
    ) {
        if (sources == null) {
            OpsHint("Stav zdrojů se nepodařilo načíst.")
            return@FilmyCollapsibleSection
        }
        OpsStat("Má dohledaný zdroj", "${counts?.withSource ?: 0}")
        OpsStat("Chybí zdroj", "${counts?.missing ?: 0}")
        if ((counts?.unhealthy ?: 0) > 0) OpsStat("Nekompletní údaje", "${counts?.unhealthy}")
        val queue = sources.queue
        if (queue.queued > 0 || queue.inflight > 0) {
            OpsStat("Právě se hledá", "${queue.inflight} z ${queue.queued} ve frontě")
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onSweep, enabled = busy == null) {
                Text(if (busy == "sweep") "Hledám…" else "Dohledat chybějící")
            }
            TextButton(onClick = onVerify, enabled = busy == null) {
                Text(if (busy == "verify") "Kontroluji…" else "Ověřit zdraví")
            }
        }

        PolicyRow(policy, busy, onPolicy)

        if (sources.missing.isNotEmpty()) {
            SubHeader("Bez zdroje")
            sources.missing.take(20).forEach { m ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        text = m.title.ifBlank { "—" } + (m.year?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (m.kind == "show") "seriál — zdroj se hledá" else "film — zdroj se hledá",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (sources.withSource.isNotEmpty()) {
            SubHeader("S dohledaným zdrojem")
            sources.withSource.take(50).forEach { item -> SourcedRow(item, busy, onRemove) }
        }
    }
}

@Composable
private fun PolicyRow(policy: String?, busy: String?, onPolicy: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Jak automat hledá",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = policy == "child",
                onClick = { onPolicy("child") },
                enabled = busy == null,
                label = { Text("Česky (sdilej napřed)") },
            )
            FilterChip(
                selected = policy == "original",
                onClick = { onPolicy("original") },
                enabled = busy == null,
                label = { Text("Původní znění") },
            )
        }
    }
}

@Composable
private fun SourcedRow(
    item: OpsSourcedItem,
    busy: String?,
    onRemove: (tmdb: Long, imdb: String, title: String) -> Unit,
) {
    var confirming by remember(item.tmdb) { mutableStateOf(false) }
    val s = item.source
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (s.healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = if (s.healthy) "zdroj je v pořádku" else "u zdroje chybí údaje",
                tint = if (s.healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                text = item.title.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = buildString {
                append(FilmyOpsFormat.sourceLabel(s.kind, s.provider))
                if (s.resolution.isNotBlank()) append(" · ").append(s.resolution)
                append(" · ").append(FilmyOpsFormat.language(s.audioLanguage, s.audioGuessed))
                if (s.channels.isNotBlank()) append(" · ").append(s.channels)
                s.sizeGB?.let { append(" · ").append(String.format(java.util.Locale("cs"), "%.1f GB", it)) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { },
                enabled = false,
                label = {
                    Text(if (s.confirmedByUser) "vybráno ručně" else "dohledáno automaticky")
                },
            )
            if (s.savedAtMs > 0) {
                Text(
                    text = "uloženo ${FilmyOpsFormat.ago(s.savedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (confirming) {
                TextButton(
                    onClick = { confirming = false; onRemove(item.tmdb, item.imdb, item.title) },
                    enabled = busy == null,
                ) { Text("Opravdu odebrat") }
            } else {
                TextButton(onClick = { confirming = true }, enabled = busy == null) { Text("Odebrat") }
            }
        }
        if (!s.healthy && s.missingInfo.isNotEmpty()) {
            Text(
                text = "Chybí údaj: " + s.missingInfo.joinToString(", ") {
                    when (it) {
                        "resolution" -> "rozlišení"
                        "audioLanguage" -> "jazyk zvuku"
                        else -> it
                    }
                } + " — zdroj se přehraje, ale nevíme, co divák uslyší.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}
