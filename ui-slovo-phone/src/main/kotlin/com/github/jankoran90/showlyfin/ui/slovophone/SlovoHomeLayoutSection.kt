package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.HomeLayoutPrefs
import com.github.jankoran90.showlyfin.feature.listen.HomeLayoutViewModel

/**
 * User (2026-09-29 13:12–13:14) — Nastavení → „Zobrazení Domů": mřížka (2 sloupce, dosavadní) nebo jeden
 * sloupec s popisem epizody a časovým údajem. Progressive disclosure: volby seznamu jen když je seznam zapnutý.
 */
@Composable
internal fun SlovoHomeLayoutSection(viewModel: HomeLayoutViewModel = hiltViewModel()) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingChips(
            label = "Rozložení",
            options = listOf(false, true),
            selected = layout.list,
            labelOf = { if (it) "Seznam (1 sloupec)" else "Mřížka (2 sloupce)" },
            onSelect = { v -> viewModel.update { it.copy(list = v) } },
        )
        if (layout.list) {
            SettingChips(
                label = "Cover v seznamu",
                subtitle = "Velký = přes celou šířku, díl se vždy vejde celý na obrazovku. Malý = hustší seznam.",
                options = listOf(true, false),
                selected = layout.bigCover,
                labelOf = { if (it) "Velký" else "Malý vlevo" },
                onSelect = { v -> viewModel.update { it.copy(bigCover = v) } },
            )
            SettingChips(
                label = "Popis epizody",
                options = HomeLayoutPrefs.DESC_LINE_OPTIONS,
                selected = layout.descLines,
                labelOf = { n -> when { n == 0 -> "Skrýt"; n >= 5 -> "$n řádků"; else -> "$n řádky" } },
                onSelect = { v -> viewModel.update { it.copy(descLines = v) } },
            )
            SettingChips(
                label = "Časový údaj",
                options = listOf(true, false),
                selected = layout.remaining,
                labelOf = { if (it) "Zbývá do konce" else "Poslechnuto / celkem" },
                onSelect = { v -> viewModel.update { it.copy(remaining = v) } },
            )
        }
    }
}
