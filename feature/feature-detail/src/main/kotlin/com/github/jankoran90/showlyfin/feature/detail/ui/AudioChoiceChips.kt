package com.github.jankoran90.showlyfin.feature.detail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.data.uploader.AudioPathStore

/**
 * SEZONA (SHW-113) f2 — přepínač zvukové stopy na kartě filmu/seriálu/pořadu.
 *
 * User 2026-08-01 16:45: *„ten jazykový chip plošně na celý profil — karty filmu, seriálu, pořadu"*,
 * takže chip nemění jeden titul, ale **nastavení celého profilu**; karta je jen nejbližší místo, odkud
 * se na to dá sáhnout. Výchozí stav se nikam neukládá — plyne z věku profilu (dětský čeština, dospělý
 * originál), takže nový profil je rovnou správně.
 *
 * Volba se propisuje na dvě místa: předvybraná cesta v rozcestníku zdrojů a **preferovaný jazyk zvukové
 * stopy v přehrávači** (bez něj hrál Media3 první stopu v pořadí — u Breaking Bad německou).
 */
@Composable
fun AudioChoiceChips(
    choice: AudioPathStore.Choice,
    onChoose: (AudioPathStore.Choice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = choice == AudioPathStore.Choice.ORIGINAL,
            onClick = { onChoose(AudioPathStore.Choice.ORIGINAL) },
            label = { Text("Originál", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.tvFocusable(),
        )
        FilterChip(
            selected = choice == AudioPathStore.Choice.CZ,
            onClick = { onChoose(AudioPathStore.Choice.CZ) },
            label = { Text("Český dabing", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.tvFocusable(),
        )
    }
}
