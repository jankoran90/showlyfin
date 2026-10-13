package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.feature.listen.player.SeriesRule

/** Rozpracovaná ruční kniha: [original] = název upravované knihy (null = nová). */
data class SeriesRuleDraft(val original: String?, val title: String, val match: String) {
    companion object {
        /** Návrh z dílu: série z názvu (před dvojtečkou), jinak první tři slova názvu. */
        fun fromEpisode(episodeTitle: String): SeriesRuleDraft {
            val guess = PodcastEpisodeSeriesGrouping.detectSeriesTitle(episodeTitle)
                ?: episodeTitle.replace(Regex("^\\([^)]*\\)\\s*"), "").split(' ').take(3).joinToString(" ")
            return SeriesRuleDraft(null, guess, guess)
        }
    }
}

/**
 * Slovo 2026-10-13 — „Udělat knihu z dílů…": název knihy + text, který díly mají v názvu (bez ohledu
 * na velikost písmen a diakritiku). Upravovanou knihu jde i zrušit (díly se vrátí do seznamu).
 */
@Composable
internal fun SeriesRuleDialog(
    draft: MutableState<SeriesRuleDraft?>,
    onSave: (original: String?, rule: SeriesRule) -> Unit,
    onDelete: (title: String) -> Unit,
) {
    val d = draft.value ?: return
    var title by remember(d) { mutableStateOf(d.title) }
    var match by remember(d) { mutableStateOf(d.match) }
    AlertDialog(
        onDismissRequest = { draft.value = null },
        title = { Text(if (d.original == null) "Udělat knihu z dílů" else "Upravit knihu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Do knihy patří všechny díly pořadu, které mají v názvu zadaný text, i ty, co teprve vyjdou.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Název knihy") }, singleLine = true)
                OutlinedTextField(value = match, onValueChange = { match = it }, label = { Text("Díly obsahující text") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && match.trim().length >= 3,
                onClick = {
                    onSave(d.original, SeriesRule(title.trim(), match.trim()))
                    draft.value = null
                },
            ) { Text("Uložit") }
        },
        dismissButton = {
            Row {
                if (d.original != null) {
                    TextButton(onClick = { onDelete(d.original); draft.value = null }) {
                        Text("Zrušit knihu", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { draft.value = null }) { Text("Zpět") }
            }
        },
    )
}

/** „Upravit knihu" v liště: ruční kniha = její pravidlo; automatická série = nové pravidlo (jde přejmenovat). */
internal fun bookRuleDraft(rule: SeriesRule?, title: String): SeriesRuleDraft =
    rule?.let { SeriesRuleDraft(it.title, it.title, it.match) } ?: SeriesRuleDraft(null, title, title)
