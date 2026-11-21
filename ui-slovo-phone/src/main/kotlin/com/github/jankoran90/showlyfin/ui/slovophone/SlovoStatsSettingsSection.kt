package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.stats.ListenStatsPrefs

/**
 * AMPHORA (SHW-124) F8 / CH2 — Nastavení › Přehrávač › Statistiky poslechu: počítat ano / ne a kdy
 * ukázat kartu „Slovo za rok“. Statistiky jsou ve Víc › Statistiky.
 */
@Composable
internal fun SlovoStatsSettingsSection() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(ListenStatsPrefs.enabled(ctx)) }
    var year by remember { mutableStateOf(ListenStatsPrefs.yearCard(ctx)) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingSwitchRow(
            title = "Počítat poslech",
            subtitle = "Kolik opravdu posloucháš (bez pauz), sdíleno mezi telefony profilu. Vypnuto = nic se nezapisuje.",
            checked = enabled,
            onCheckedChange = { ListenStatsPrefs.setEnabled(ctx, it); enabled = it },
        )
        SettingChips(
            label = "Karta Slovo za rok",
            subtitle = "Shrnutí celého roku nahoře ve Statistikách",
            options = ListenStatsPrefs.YearCard.entries,
            selected = year,
            labelOf = { it.label },
            onSelect = { ListenStatsPrefs.setYearCard(ctx, it); year = it },
        )
    }
}
