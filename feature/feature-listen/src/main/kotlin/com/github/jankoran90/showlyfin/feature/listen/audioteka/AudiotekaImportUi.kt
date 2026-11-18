package com.github.jankoran90.showlyfin.feature.listen.audioteka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager

/**
 * Pruh nad stránkou Audioteky: kam se kniha po zaplacení doveze (user 2026-11-18 19:53: „návrh pro jaký profil
 * … už u platby"). [text] = doplňující hláška (např. „Zaplaceno — otevírám Poličku").
 */
@Composable
internal fun ImportTargetBar(
    target: AudiotekaImporter.ImportTarget?,
    auto: Boolean,
    text: String?,
    onChange: () -> Unit,
    onClose: () -> Unit,
) {
    val main = when {
        target == null -> "Po stažení se zeptám, do které knihovny knihu dovézt."
        auto -> "Po zaplacení dovezu do knihovny ${target.libraryName}" + (target.reason?.let { " (podle kategorie $it)" } ?: "") + "."
        else -> "Po stažení navrhnu knihovnu ${target.libraryName}" + (target.reason?.let { " (podle kategorie $it)" } ?: "") + "."
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            Text(main, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onChange) { Text(if (target == null) "Vybrat" else "Změnit") }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Skrýt") }
    }
}

/**
 * Košík / pokladna: Google Pay ve Slovu Google odmítá (OR_BIBED_11) → zaplatit v Chrome, kde jde; po návratu
 * do Slova se kniha stáhne a doveze sama.
 */
@Composable
internal fun PayInChromeBar(onPay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Google Pay jde jen v Chrome. Zaplať tam a vrať se do Slova, knihu stáhnu sám.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onPay) { Text("Zaplatit v Chrome") }
    }
}

/** Výběr knihovny předem (z pruhu nad stránkou). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryPickDialog(
    libraries: List<AbsLibrary>,
    selectedId: String?,
    onPick: (AbsLibrary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kam knihu dovézt") },
        text = {
            if (libraries.isEmpty()) {
                Text("Knihovny se nenačetly. Zkontroluj přihlášení k audioknihám v Nastavení.", color = MaterialTheme.colorScheme.error)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    libraries.forEach { lib ->
                        FilterChip(selected = selectedId == lib.id, onClick = { onPick(lib) }, label = { Text(lib.name) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zavřít") } },
    )
}

@Composable
internal fun ImportStatusCard(
    importState: AudiotekaImporter.State,
    upload: AudiobookUploadManager.UploadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (text, progress, action) = when (importState) {
        is AudiotekaImporter.State.Downloading -> Triple(
            "Stahuji z Audioteky: ${importState.name} (${importState.bytes / 1_000_000} MB)",
            importState.progress ?: -1f,
            "Zrušit" to onCancel,
        )
        is AudiotekaImporter.State.Importing -> Triple(
            "Nahrávám do knihovny ${importState.target.libraryName}…",
            upload.progress.takeIf { upload.uploadId == importState.uploadId && it > 0f } ?: -1f,
            null,
        )
        is AudiotekaImporter.State.Done -> Triple(
            "Hotovo, kniha je v knihovně ${importState.libraryName}. Najdeš ji ve Slovu mezi audioknihami.",
            null,
            "OK" to onDismiss,
        )
        is AudiotekaImporter.State.Failed -> Triple(importState.message, null, "OK" to onDismiss)
        else -> return
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            when {
                progress == null -> Unit
                progress < 0f -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            action?.let { (label, fn) ->
                TextButton(onClick = fn, modifier = Modifier.align(Alignment.End)) { Text(label) }
            }
        }
    }
}

/** Volba knihovny po stažení (když ji Slovo nenavrhlo, nebo je dovoz bez ptaní vypnutý). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportDialog(
    ready: AudiotekaImporter.State.Ready,
    libraries: List<AbsLibrary>,
    onImport: (target: AudiotekaImporter.ImportTarget, title: String, author: String) -> Unit,
    onCancel: () -> Unit,
) {
    // Předvybraná jen navržená knihovna (podle kategorie); jinak nic — první knihovna bývá dětská.
    var selected by remember(libraries, ready) {
        mutableStateOf(ready.suggestion?.libraryId?.takeIf { id -> libraries.any { it.id == id } } ?: libraries.singleOrNull()?.id)
    }
    var title by remember(ready) { mutableStateOf(ready.titleHint.orEmpty()) }
    var author by remember(ready) { mutableStateOf(ready.authorHint.orEmpty()) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Dovézt do knihovny") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Staženo: ${ready.name} (${ready.file.length() / 1_000_000} MB)", style = MaterialTheme.typography.bodySmall)
                ready.note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (libraries.isEmpty()) {
                    Text("Knihovny se nenačetly. Zkontroluj přihlášení k audioknihám v Nastavení.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Do které knihovny?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        libraries.forEach { lib ->
                            FilterChip(selected = selected == lib.id, onClick = { selected = lib.id }, label = { Text(lib.name) })
                        }
                    }
                    ready.suggestion?.reason?.takeIf { selected == ready.suggestion?.libraryId }?.let {
                        Text("Navrženo podle kategorie Audioteky: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Název (prázdné = dohledá server)") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = {
                val lib = libraries.firstOrNull { it.id == selected } ?: return@TextButton
                val reason = ready.suggestion?.reason?.takeIf { lib.id == ready.suggestion?.libraryId }
                onImport(AudiotekaImporter.ImportTarget(lib.id, lib.name, reason), title, author)
            }) { Text("Dovézt") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Zahodit") } },
    )
}
