package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.feature.listen.audioteka.AudiotekaPrefs

/** Nastavení → Katalog Audioteky (user 2026-11-03). Sekci v menu skryje/přesune „Pořadí menu". */
@Composable
internal fun SlovoAudiotekaSection() {
    val ctx = LocalContext.current
    var suggestions by remember { mutableStateOf(AudiotekaPrefs.suggestionsInBook(ctx)) }
    var hideOwned by remember { mutableStateOf(AudiotekaPrefs.hideOwned(ctx)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingSwitchRow(
            title = "Návrhy u knihy",
            subtitle = "V detailu audioknihy ukázat z Audioteky další díly série, kolekce, knihy od autora, interpreta a z kategorie",
            checked = suggestions,
            onCheckedChange = { suggestions = it; AudiotekaPrefs.setSuggestionsInBook(ctx, it) },
        )
        SettingSwitchRow(
            title = "V návrzích skrýt, co už mám",
            subtitle = "Platí pro autora, interpreta a kategorii. Série a kolekce ukazují vždy všechny díly s odznakem V knihovně",
            checked = hideOwned,
            onCheckedChange = { hideOwned = it; AudiotekaPrefs.setHideOwned(ctx, it) },
        )
        Text(
            "Sekci Katalog v menu skryješ nebo přesuneš v Pořadí menu. Nákup otevře stránku knihy na Audiotéce.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
