package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAllSort
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaAxis
import com.github.jankoran90.showlyfin.core.domain.filmoteka.CinematographyRegion
import com.github.jankoran90.showlyfin.core.ui.ViewMode

/**
 * RAMPA (SHW-121) — VŠECHNY ovladače procházení pod jednu ikonu.
 *
 * Zadání usera 2026-08-28 14:13 (se dvěma snímky): *„Mozna komplet prepinace presun do jedne ikony,
 * kde je kategoricky serad k sobě, první nech lupu, pak view style pak řazení, a zeme atd. Tim padem
 * se tam vejde nazev tabu"* + 14:14: *„A ikonu filtru dej uplne doprava, kdybych chtel pridat sekci
 * do filmoteky dalsi"*.
 *
 * Proč: v liště se dřív tísnily ☰, tři osy, počet titulů, lupa a přepínač zobrazení — a hned pod tím
 * DRUHÝ řádek s řazením. Na userově snímku se chip „Země" překrýval s textem „127 filmů". Teď je
 * nahoře jen ☰ + názvy stránek + tahle jediná ikona vpravo; druhá řada zmizela úplně.
 *
 * Pořadí kategorií je userovo: **hledání → zobrazení → řazení → osa → filtry**.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmyBrowseControlsSheet(
    axis: FilmotekaAxis,
    allSort: FilmotekaAllSort,
    viewMode: ViewMode,
    total: Int,
    query: String,
    genreFilter: Set<String>,
    countryFilter: Set<CinematographyRegion>,
    onQuery: (String) -> Unit,
    onAxis: (FilmotekaAxis) -> Unit,
    onAllSort: (FilmotekaAllSort) -> Unit,
    onToggleView: () -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRemoveCountry: (CinematographyRegion) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    // Hledání je první kategorie → ať se rovnou píše, aby otevření panelu nestálo klik navíc.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 1) HLEDÁNÍ
            SheetCategory("Hledat")
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Název, popis nebo režie") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Clear, "Smazat hledání") }
                    }
                },
            )

            // 2) ZOBRAZENÍ
            SheetCategory("Zobrazení")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = viewMode == ViewMode.GRID,
                    onClick = { if (viewMode != ViewMode.GRID) onToggleView() },
                    leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                    label = { Text("Mřížka") },
                )
                FilterChip(
                    selected = viewMode == ViewMode.LIST,
                    onClick = { if (viewMode != ViewMode.LIST) onToggleView() },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ViewList, null) },
                    label = { Text("Seznam") },
                )
            }

            // 3) ŘAZENÍ — klik při jiné ose osu rovnou přepne na „Vše"; skrývat sekci by mátlo víc.
            SheetCategory("Řazení")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAllSort.entries.forEach { s ->
                    FilterChip(
                        selected = axis == FilmotekaAxis.ALL && allSort == s,
                        onClick = {
                            if (axis != FilmotekaAxis.ALL) onAxis(FilmotekaAxis.ALL)
                            onAllSort(s)
                        },
                        label = { Text(s.chipLabel) },
                    )
                }
            }

            // 4) OSA — klik na Žánr/Země navíc otevře výběr (řeší volající), takže panel zavíráme.
            SheetCategory("Rozdělení")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilmotekaAxis.entries.forEach { a ->
                    FilterChip(
                        selected = axis == a,
                        onClick = { onAxis(a); if (a != FilmotekaAxis.ALL) onDismiss() },
                        label = { Text(a.chipLabel) },
                    )
                }
            }

            // 5) FILTRY — jen když nějaké jsou (prázdná kategorie by byla šum).
            if (genreFilter.isNotEmpty() || countryFilter.isNotEmpty()) {
                SheetCategory("Filtry")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genreFilter.forEach { g ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveGenre(g) },
                            label = { Text(g) },
                            trailingIcon = { Icon(Icons.Rounded.Clear, "Zrušit filtr") },
                        )
                    }
                    countryFilter.forEach { c ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveCountry(c) },
                            label = { Text(c.label) },
                            trailingIcon = { Icon(Icons.Rounded.Clear, "Zrušit filtr") },
                        )
                    }
                }
            }

            // Počet titulů měl dřív místo v liště, kde se přetahoval s chipem „Země" — patří sem.
            if (total > 0) {
                Text(
                    "Celkem $total titulů",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Nadpis jedné kategorie v panelu — drží ovladače opticky u sebe (user: „kategoricky serad k sobě"). */
@Composable
private fun SheetCategory(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}
