package com.github.jankoran90.showlyfin.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Plan STRATA B3/B4 (Path A) — **sjednocená lišta podsekcí Sleduj.**
 *
 * Jedna sdílená komponenta napříč Objevit / Chci vidět / Historie → layout/design se NEMĚNÍ mezi
 * sekcemi, mění se jen obsah. Vše v JEDNÉ liště (žádná horní tab-lišta):
 *   [segment Filmy/Seriály (resp. pohledy)] + [lupa→expand hledání] + [chipy dodané volajícím]
 *
 * Hledání je konzistentně **lupa → rozbalené pole** (dřív Discover lupa-expand, ale Chci vidět/Historie
 * měly trvalé pole). Komponenta si drží `searchExpanded` stav sama; když query není prázdné, drží pole
 * otevřené. Řazení sjednoceno přes [SectionSortChip].
 */
@Composable
fun SectionBar(
    modifier: Modifier = Modifier,
    segments: List<String>? = null,
    selectedSegment: Int = 0,
    onSegmentSelected: (Int) -> Unit = {},
    searchQuery: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    searchPlaceholder: String = "Hledat…",
    // VANTAGE (SHW-48) / PANORAMA (SHW-78): přepínač zobrazení jako PRVNÍ prvek lišty. null = skrytý.
    viewMode: ViewMode? = null,
    onSelectViewMode: (ViewMode) -> Unit = {},
    chips: (LazyListScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        val hasSegments = !segments.isNullOrEmpty()
        val hasSearch = searchQuery != null
        var searchExpanded by remember { mutableStateOf(false) }
        val expanded = hasSearch && (searchExpanded || !searchQuery.isNullOrBlank())

        if (expanded) {
            OutlinedTextField(
                value = searchQuery ?: "",
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text(searchPlaceholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                            searchExpanded = false
                        },
                        modifier = Modifier.tvFocusable(),
                    ) { Icon(Icons.Default.Close, contentDescription = "Vymazat") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        } else if (viewMode != null || hasSegments || hasSearch || chips != null) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // PANORAMA A: přepínač zobrazení (4 režimy) jako PRVNÍ prvek lišty.
                if (viewMode != null) {
                    item { ViewModeSelector(current = viewMode, onSelect = onSelectViewMode) }
                }
                // VANTAGE B: Filmy/Seriály (resp. pohledy) = dropdown (dřív segmenty) přímo v liště.
                if (hasSegments) {
                    item { SegmentDropdown(segments = segments!!, selected = selectedSegment, onSelect = onSegmentSelected) }
                }
                if (hasSearch) {
                    item {
                        val active = !searchQuery.isNullOrBlank()
                        IconButton(
                            onClick = { searchExpanded = true },
                            modifier = Modifier.tvFocusable(),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Hledat",
                                tint = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                chips?.invoke(this)
            }
        }
    }
}

/** PANORAMA (SHW-78): rozbalovací přepínač 4 režimů zobrazení (Mřížka/Seznam/Na šířku/+popis). */
@Composable
private fun ViewModeSelector(current: ViewMode, onSelect: (ViewMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(current.label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.tvFocusable(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = { onSelect(mode); open = false },
                    trailingIcon = if (mode == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

/** VANTAGE B (SHW-48): Filmy/Seriály (resp. pohledy) jako rozbalovací menu místo segmentů. */
@Composable
private fun SegmentDropdown(segments: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(segments.getOrElse(selected) { segments.firstOrNull().orEmpty() }) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.tvFocusable(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            segments.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(index); open = false },
                    trailingIcon = if (index == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

/**
 * Sjednocený řadicí chip (AssistChip + dropdown). [label] nese i aktivní hodnotu (např. „Řazení: A–Z"),
 * takže není potřeba selected-stav chipu. Použito v Objevit / Chci vidět místo dvou různých vzhledů.
 */
@Composable
fun <T> SectionSortChip(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = modifier.tvFocusable(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        open = false
                    },
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}
