package com.github.jankoran90.showlyfin.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.feature.discover.DiscoverFilters
import com.github.jankoran90.showlyfin.feature.discover.DiscoverSort

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filters: DiscoverFilters,
    availableGenres: List<String>,
    isParentalLocked: Boolean,
    onDismiss: () -> Unit,
    onApply: (DiscoverFilters) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(filters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filtry", style = MaterialTheme.typography.titleLarge)

            if (availableGenres.isNotEmpty()) {
                Text("Žánr", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    availableGenres.forEach { genre ->
                        val selected = draft.selectedGenres.contains(genre)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(
                                    selectedGenres = if (selected) draft.selectedGenres - genre
                                    else draft.selectedGenres + genre,
                                )
                            },
                            label = { Text(genre) },
                        )
                    }
                }
            }

            Text("Rok vydání: ${draft.yearMin} – ${draft.yearMax}", style = MaterialTheme.typography.titleSmall)
            RangeSlider(
                value = draft.yearMin.toFloat()..draft.yearMax.toFloat(),
                onValueChange = { range ->
                    draft = draft.copy(yearMin = range.start.toInt(), yearMax = range.endInclusive.toInt())
                },
                valueRange = 1950f..2030f,
                steps = 79,
            )

            Text("Minimální Trakt rating: %.1f".format(draft.minRating), style = MaterialTheme.typography.titleSmall)
            Slider(
                value = draft.minRating,
                onValueChange = { draft = draft.copy(minRating = it) },
                valueRange = 0f..10f,
                steps = 99,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = draft.hideInJellyfin,
                    onCheckedChange = { draft = draft.copy(hideInJellyfin = it) },
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("Skrýt už vlastněný (Jellyfin)")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = draft.hideInWatchlist,
                    onCheckedChange = { draft = draft.copy(hideInWatchlist = it) },
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("Skrýt z Trakt watchlistu")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = draft.hideWatched,
                    onCheckedChange = { draft = draft.copy(hideWatched = it) },
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("Skrýt zhlédnuté")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = draft.hideRated,
                    onCheckedChange = { draft = draft.copy(hideRated = it) },
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("Skrýt ohodnocené (Trakt)")
            }

            Text("Řazení", style = MaterialTheme.typography.titleSmall)
            var sortMenuOpen by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { sortMenuOpen = true }) {
                Text(draft.sortBy.displayName)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                DiscoverSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.displayName) },
                        onClick = {
                            draft = draft.copy(sortBy = sort)
                            sortMenuOpen = false
                        },
                    )
                }
            }

            if (isParentalLocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Filtr nastaven Jellyfin profilem, věkové omezení nelze měnit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("Resetovat")
                }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) {
                    Text("Použít")
                }
            }
        }
    }
}
