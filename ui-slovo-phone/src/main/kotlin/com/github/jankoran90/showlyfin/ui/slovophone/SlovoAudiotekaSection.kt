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
    var autoAfterPurchase by remember { mutableStateOf(AudiotekaPrefs.autoAfterPurchase(ctx)) }
    var autoImport by remember { mutableStateOf(AudiotekaPrefs.autoImport(ctx)) }
    var recordPages by remember { mutableStateOf(AudiotekaPrefs.recordPurchasePages(ctx)) }
    var webGpay by remember { mutableStateOf(AudiotekaPrefs.webViewGooglePay(ctx)) }
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
                title = "Google Pay přímo ve Slovu",
                subtitle = "Zatím ho Google pro Slovo neschválil (chyba OR_BIBED_11). Vypnuto = v košíku tlačítko Zaplatit v Chrome",
                checked = webGpay,
                onCheckedChange = { webGpay = it; AudiotekaPrefs.setWebViewGooglePay(ctx, it) },
            )
            SettingSwitchRow(
                title = "Dovážet stažené knihy do knihovny",
                subtitle = "Stažení z Poličky Slovo samo nahraje do knihovny. Vypnuto = jen uložit do Stažených souborů",
                checked = importDownloads,
                onCheckedChange = { importDownloads = it; AudiotekaPrefs.setImportDownloads(ctx, it) },
            )
            if (importDownloads) {
                SettingSwitchRow(
                    title = "Po zaplacení samo stáhnout",
                    subtitle = "Po návratu z pokladny Slovo samo otevře Poličku a u koupené knihy ťukne na Stáhnout",
                    checked = autoAfterPurchase,
                    onCheckedChange = { autoAfterPurchase = it; AudiotekaPrefs.setAutoAfterPurchase(ctx, it) },
                )
                SettingSwitchRow(
                    title = "Dovézt bez ptaní do navržené knihovny",
                    subtitle = "Knihovnu Slovo navrhne podle kategorie Audioteky (Pohádky → dětská). Vypnuto = vždy se zeptá s předvybranou knihovnou",
                    checked = autoImport,
                    onCheckedChange = { autoImport = it; AudiotekaPrefs.setAutoImport(ctx, it) },
                )
                SettingSwitchRow(
                    title = "Zapisovat průběh nákupu",
                    subtitle = "Adresy stránek nákupu (bez osobních údajů) jdou do logu na server, aby šlo samočinný dovoz doladit",
                    checked = recordPages,
                    onCheckedChange = { recordPages = it; AudiotekaPrefs.setRecordPurchasePages(ctx, it) },
                )
            }
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
