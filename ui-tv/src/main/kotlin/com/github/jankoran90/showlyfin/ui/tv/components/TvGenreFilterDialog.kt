package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvFocusable

/**
 * GENRE-FILTER — TV overlay pro multi-select filtr žánrů (parita s telefonním `GenreFilterSheet`).
 * D-pad fokusovatelné chipy; Back zavře. Barvy/tvary z motivu.
 *
 * Vytaženo z `TvFilmotekaScreen` do sdílených komponent (user 2026-07-31: „plus lišta žánrů do sekce
 * Pro tebe, jako máme jinde") — tentýž dialog používá Filmotéka i „Pro tebe", takže se nerozejdou.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvGenreFilterDialog(
    available: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstChipFocus = remember { FocusRequester() }
    LaunchedEffect(available) { if (available.isNotEmpty()) runCatching { firstChipFocus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .padding(32.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Filtr žánrů",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (available.isEmpty()) {
                    Text(
                        text = "Žádné žánry k dispozici.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        available.forEachIndexed { idx, g ->
                            FilterChip(
                                selected = g in selected,
                                onClick = { onToggle(g) },
                                label = { Text(g) },
                                modifier = (if (idx == 0) Modifier.focusRequester(firstChipFocus) else Modifier)
                                    .tvFocusable(),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = onClear, modifier = Modifier.tvFocusable()) { Text("Zrušit filtr") }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.tvFocusable()) { Text("Zavřít") }
                }
            }
        }
    }
}
