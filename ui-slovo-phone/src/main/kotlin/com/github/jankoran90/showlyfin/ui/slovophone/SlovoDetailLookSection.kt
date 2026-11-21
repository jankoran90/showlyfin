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

/**
 * AMPHORA (SHW-124) F8 / DS2 — Nastavení › Domů a vzhled › Detaily a přechody: hlavička detailu
 * (rozmazaný obal, barva z obalu) a animace přechodů mezi obrazovkami. Bez VM ([SlovoShellPrefs]).
 */
@Composable
internal fun SlovoDetailLookSection() {
    val ctx = LocalContext.current
    var transitions by remember { mutableStateOf(SlovoShellPrefs.transitions(ctx)) }
    var backdrop by remember { mutableStateOf(SlovoShellPrefs.heroBackdrop(ctx)) }
    var tint by remember { mutableStateOf(SlovoShellPrefs.heroTint(ctx)) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SettingSwitchRow(
            title = "Rozmazaný obal za hlavičkou",
            subtitle = "V detailu pořadu, knihy, série, YouTube kanálu a pořadu ČT",
            checked = backdrop,
            onCheckedChange = { SlovoShellPrefs.setHeroBackdrop(ctx, it); backdrop = it },
        )
        SettingSwitchRow(
            title = "Barva z obalu v hlavičce",
            subtitle = "Hlavička dostane nádech hlavní barvy obalu",
            checked = tint,
            onCheckedChange = { SlovoShellPrefs.setHeroTint(ctx, it); tint = it },
        )
        SettingChips(
            label = "Animace přechodů",
            subtitle = "Plné = posun a prolnutí, Jen prolnutí = klidnější, Vypnuté = okamžitě",
            options = SlovoShellPrefs.Transitions.entries,
            selected = transitions,
            labelOf = { it.label },
            onSelect = { SlovoShellPrefs.setTransitions(ctx, it); transitions = it },
        )
    }
}
