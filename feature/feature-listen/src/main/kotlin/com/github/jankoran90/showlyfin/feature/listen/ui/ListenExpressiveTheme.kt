package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.runtime.Composable

/**
 * Téma poslechové sekce.
 *
 * CHORUS Osa 3 (2026-07-04): dřív natvrdo přepisoval `darkColorScheme` (AMOLED černá + amber akcent) →
 * sekce Poslech byla vždy tmavá a nereagovala na zvolený motiv (světlý/skin/posuvníky). Kánon CHORUS =
 * VŠE sourodé podle jednoho motivu → wrapper už NIC nepřepisuje a prostupuje globální
 * `ShowlyfinPhoneTheme` (pozadí + skin + dynamické ladění). Ponecháno jako pass-through kvůli
 * volajícím (jediné místo, kde by se případně řešila odlišnost Poslechu — dnes žádná).
 */
@Composable
fun ListenExpressiveTheme(content: @Composable () -> Unit) {
    content()
}
