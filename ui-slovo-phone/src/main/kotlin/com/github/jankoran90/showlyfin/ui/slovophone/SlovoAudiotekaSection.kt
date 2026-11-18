package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
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
    var buyInApp by remember { mutableStateOf(AudiotekaPrefs.buyInApp(ctx)) }
    var importDownloads by remember { mutableStateOf(AudiotekaPrefs.importDownloads(ctx)) }
    var deleteAfter by remember { mutableStateOf(AudiotekaPrefs.deleteAfterImport(ctx)) }
    var pending by remember { mutableStateOf(AudiotekaPrefs.pendingImportBytes(ctx)) }
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
        SettingSwitchRow(
            title = "Nakupovat uvnitř Slova",
            subtitle = "Koupit otevře stránku Audioteky přímo ve Slovu (přihlásíš se jednou). Vypnuto = prohlížeč telefonu",
            checked = buyInApp,
            onCheckedChange = { buyInApp = it; AudiotekaPrefs.setBuyInApp(ctx, it) },
        )
        if (buyInApp) {
            SettingSwitchRow(
                title = "Dovážet stažené knihy do knihovny",
                subtitle = "Stáhni v Poličce → Slovo se zeptá na knihovnu a knihu nahraje. Vypnuto = jen uložit do Stažených souborů",
                checked = importDownloads,
                onCheckedChange = { importDownloads = it; AudiotekaPrefs.setImportDownloads(ctx, it) },
            )
            SettingSwitchRow(
                title = "Po dovozu smazat stažený soubor z telefonu",
                subtitle = "Kniha zůstane v knihovně na serveru, v telefonu se uvolní místo",
                checked = deleteAfter,
                onCheckedChange = { deleteAfter = it; AudiotekaPrefs.setDeleteAfterImport(ctx, it) },
            )
            if (pending > 0) TextButton(onClick = { AudiotekaPrefs.clearImportFiles(ctx); pending = 0 }) {
                Text("Smazat nedovezené stažené knihy (${pending / 1_000_000} MB)")
            }
            TextButton(onClick = {
                AudiotekaPrefs.logout(ctx)
                Toast.makeText(ctx, "Odhlášeno z Audioteky ve Slovu", Toast.LENGTH_SHORT).show()
            }) { Text("Odhlásit z Audioteky") }
        }
        Text(
            "Sekci Katalog v menu skryješ nebo přesuneš v Pořadí menu. Polička je v Katalogu pod hledáním.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
