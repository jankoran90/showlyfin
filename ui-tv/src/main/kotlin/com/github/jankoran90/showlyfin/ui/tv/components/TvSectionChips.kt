package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.TvSectionSort
import com.github.jankoran90.showlyfin.core.ui.ViewMode
import com.github.jankoran90.showlyfin.core.ui.tvFocusable

/**
 * FOYER (SHW-107) — KÁNON ovladačů ploché TV sekce vedle jejího názvu ([TvSectionHeader] actions):
 * „Mřížka | Řada" + jeden chip řazení, který se klikem PŘEPÍNÁ dokola (D-pad friendly — jeden fokus
 * místo pěti chipů). Výchozí stav (mřížka + abecedně) řeší `TvSectionViewModel`, tady je jen ovládání.
 */
@Composable
fun TvViewChips(
    viewMode: ViewMode,
    sort: TvSectionSort,
    onViewMode: (ViewMode) -> Unit,
    onSort: (TvSectionSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = viewMode == ViewMode.GRID,
            onClick = { onViewMode(ViewMode.GRID) },
            label = { Text("Mřížka") },
            modifier = Modifier.tvFocusable(),
        )
        FilterChip(
            selected = viewMode == ViewMode.LIST,
            onClick = { onViewMode(ViewMode.LIST) },
            label = { Text("Řada") },
            modifier = Modifier.tvFocusable(),
        )
        FilterChip(
            selected = sort != TvSectionSort.VYCHOZI,
            onClick = { onSort(sort.next()) },
            label = { Text("Řazení: ${sort.label}") },
            modifier = Modifier.tvFocusable(),
        )
    }
}

/**
 * FOYER — samotný chip řazení pro sekce, které mají JEN mřížku (Chci vidět, Oblíbené, Pro tebe…).
 * Klik cykluje: Abecedně → Nedávno → Rok → Hodnocení → Výchozí → … Výchozí = ABECEDNĚ (TV).
 */
@Composable
fun TvSortChip(
    sort: TvSectionSort,
    onSort: (TvSectionSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = sort != TvSectionSort.VYCHOZI,
        onClick = { onSort(sort.next()) },
        label = { Text("Řazení: ${sort.label}") },
        modifier = modifier.tvFocusable(),
    )
}

/** Další řazení v kruhu (klik na chip cykluje). */
private fun TvSectionSort.next(): TvSectionSort {
    val all = TvSectionSort.entries
    return all[(all.indexOf(this) + 1) % all.size]
}
