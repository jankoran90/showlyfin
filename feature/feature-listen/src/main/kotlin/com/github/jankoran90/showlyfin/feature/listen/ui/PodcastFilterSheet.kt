package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.uploader.model.SourceCategory

/**
 * AGORA-TABS: filtr „co chci vidět" v sekci Podcasty (otevírá ikona filtru jako první v tab řadě).
 * Časový rozsah Timeline, typ zdroje, min. počet epizod a skrytí kategorií. Každá volba má odraz v
 * Nastavení → Poslech → „Objevování podcastů" (parita). Čte z [MaterialTheme] tokenů (UNISON).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastFilterSheet(
    timelineRangeDays: Int,
    onSetRange: (Int) -> Unit,
    sourceType: String,
    onSetSourceType: (String) -> Unit,
    minEpisodes: Int,
    onSetMinEpisodes: (Int) -> Unit,
    onlyDownloaded: Boolean,
    onSetOnlyDownloaded: (Boolean) -> Unit,
    downloadCount: Int,
    onOpenDownloads: () -> Unit,
    categories: List<SourceCategory>,
    excluded: Set<Int>,
    onToggleCategory: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Filtr podcastů",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // Stažené (offline): vstup do přehledu stažených + přepínač „jen stažené" v Timeline.
            SubHeader("Stažené (offline)")
            SwitchRow(
                label = "Jen stažené epizody (Timeline)",
                checked = onlyDownloaded,
                onChange = onSetOnlyDownloaded,
            )
            if (downloadCount > 0) {
                AssistChip(
                    onClick = onOpenDownloads,
                    label = { Text("Stažené · $downloadCount") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SubHeader("Časový rozsah (Timeline)")
            ChipRow(
                options = RANGE_OPTIONS,
                isSelected = { it.first == timelineRangeDays },
                label = { it.second },
                onSelect = { onSetRange(it.first) },
            )

            SubHeader("Typ zdroje")
            ChipRow(
                options = TYPE_OPTIONS,
                isSelected = { it.first == sourceType },
                label = { it.second },
                onSelect = { onSetSourceType(it.first) },
            )

            SubHeader("Min. počet epizod")
            ChipRow(
                options = MIN_EP_OPTIONS,
                isSelected = { it == minEpisodes },
                label = { if (it == 0) "Bez filtru" else "$it+" },
                onSelect = { onSetMinEpisodes(it) },
            )

            SubHeader("Skrýt kategorie")
            if (categories.isEmpty()) {
                Text(
                    "Kategorie jsou dostupné jen pro ČR (objevování).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = cat.id in excluded,
                            onClick = { onToggleCategory(cat.id) },
                            label = { Text(cat.name) },
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

private val RANGE_OPTIONS = listOf(
    7 to "1 týden", 30 to "1 měsíc", 90 to "3 měsíce", 180 to "6 měsíců",
    365 to "1 rok", 730 to "2 roky", 0 to "Vše",
)
private val TYPE_OPTIONS = listOf("all" to "Vše", "rss" to "Podcasty", "youtube" to "YouTube")
private val MIN_EP_OPTIONS = listOf(0, 5, 10, 25, 50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { opt ->
            FilterChip(
                selected = isSelected(opt),
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
            )
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
