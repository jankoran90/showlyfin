package com.github.jankoran90.showlyfin.feature.listen.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.feature.listen.UploadAudiobookViewModel
import com.github.jankoran90.showlyfin.feature.listen.detectTitleAuthor

/**
 * DROPSHIP F2 — obrazovka „Nahrát audioknihu". SAF výběr audio souborů nebo archivu (případně
 * složky), editovatelné title/author (předvyplněno z detekce), dropdown ABS knihoven, progress
 * indikátor během uploadu a výsledek s počtem stop. Respektuje AMOLED theme (vše z MaterialTheme).
 *
 * Vstup do obrazovky = karta v [SourceManagerScreen]. Když není nastavený ABS server, nabídne
 * výzvu k přihlášení v Nastavení.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadAudiobookScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UploadAudiobookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selectedUris = remember { mutableStateListOf<Uri>() }
    var firstFileName by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var autoMatch by remember { mutableStateOf(true) }
    var titleAuthorPrefilled by remember { mutableStateOf(false) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var coverName by remember { mutableStateOf<String?>(null) }

    // SAF: výběr jednoho či více souborů (audio + archivy).
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        selectedUris.clear()
        selectedUris.addAll(uris)
        val first = uris.first()
        firstFileName = queryDisplayName(context, first)
        // Předvyplň title/author jen při prvním výběru (nepřepisuj userovy úpravy).
        if (!titleAuthorPrefilled) {
            val name = firstFileName ?: ""
            if (name.isNotBlank()) {
                val (t, a) = detectTitleAuthor(name)
                title = t
                author = a ?: ""
                titleAuthorPrefilled = true
            }
        }
    }

    // SAF: výběr složky (přístup k celému stromu). Persistable permission dává appce právo číst
    // obsah i po restartu; pro soubory (OpenMultipleDocuments) to není potřeba a házelo by SecurityException.
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val children = collectFolderAudio(context, treeUri)
        if (children.isEmpty()) return@rememberLauncherForActivityResult
        selectedUris.clear()
        selectedUris.addAll(children)
        firstFileName = queryDisplayName(context, children.first())?.substringBeforeLast('/')
            ?.substringAfterLast('/') ?: treeUri.lastPathSegment
        if (!titleAuthorPrefilled) {
            val name = treeUri.lastPathSegment?.substringAfterLast('/') ?: ""
            if (name.isNotBlank()) {
                val (t, a) = detectTitleAuthor(name)
                title = t
                author = a ?: ""
                titleAuthorPrefilled = true
            }
        }
    }

    // SAF: výběr cover obrázku (image/*) — volitelný, přepíše auto-dohledaný cover.
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coverUri = uri
            coverName = queryDisplayName(context, uri)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Nahrát audioknihu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
            )
        },
    ) { padding ->
        if (state.notConfigured) {
            NotConfigured(padding)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Vyber audio soubor, archiv (ZIP/RAR/TAR/7Z) nebo celou složku. " +
                        "Backend ji rozbalí, nasbírá audio stopy a přidá do knihovny.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            filesLauncher.launch(
                                arrayOf(
                                    "audio/*",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/x-rar-compressed",
                                    "application/x-tar",
                                    "application/x-7z-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Soubor")
                    }
                    Button(
                        onClick = { treeLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Složka")
                    }
                }
            }

            if (selectedUris.isNotEmpty()) {
                item {
                    SelectionSummary(
                        fileName = firstFileName,
                        count = selectedUris.size,
                        onClear = {
                            selectedUris.clear()
                            firstFileName = null
                            title = ""
                            author = ""
                            titleAuthorPrefilled = false
                            coverUri = null
                            coverName = null
                        },
                    )
                }
            }

            item {
                LibraryDropdown(
                    libraries = state.libraries,
                    selectedId = state.selectedLibraryId,
                    onSelect = viewModel::selectLibrary,
                )
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Název knihy") },
                )
            }

            item {
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Autor (volitelné)") },
                )
            }

            item {
                FilterChip(
                    selected = autoMatch,
                    onClick = { autoMatch = !autoMatch },
                    label = { Text(if (autoMatch) "Doplnit z Audible: zapnuto" else "Doplnit z Audible: vypnuto") },
                    leadingIcon = { Icon(Icons.Rounded.AudioFile, contentDescription = null) },
                )
            }

            item {
                CoverRow(
                    coverUri = coverUri,
                    coverName = coverName,
                    onPick = { coverLauncher.launch(arrayOf("image/*")) },
                    onClear = { coverUri = null; coverName = null },
                )
            }

            if (state.isUploading) {
                item {
                    Column {
                        Text(
                            "Nahrávám… ${((state.progress).coerceIn(0f, 1f) * 100).toInt()} %",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item {
                    Button(
                        onClick = {
                            viewModel.upload(selectedUris.toList(), title.ifBlank { null }, author.ifBlank { null }, autoMatch, coverUri)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedUris.isNotEmpty() && state.selectedLibraryId != null,
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Nahrát")
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.size(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            state.result?.let { res ->
                item {
                    ResultCard(res = res, onBack = onBack, onAgain = {
                        viewModel.reset()
                        selectedUris.clear()
                        firstFileName = null
                        title = ""
                        author = ""
                        titleAuthorPrefilled = false
                        coverUri = null
                        coverName = null
                    })
                }
            }
        }
    }
}

@Composable
private fun NotConfigured(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Přihlas se k audioknihám v Nastavení, abys mohl nahrávat.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionSummary(fileName: String?, count: Int, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = fileName ?: "$count souborů",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count > 1) {
                    Text(
                        "$count souborů",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onClear) { Text("Změnit") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryDropdown(
    libraries: List<com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = libraries.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Vyber knihovnu",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text("Cílová knihovna") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (libraries.isEmpty()) {
                DropdownMenuItem(text = { Text("Žádné audioknihovní knihovny") }, onClick = { expanded = false })
            }
            libraries.forEach { lib ->
                DropdownMenuItem(text = { Text(lib.name) }, onClick = { onSelect(lib.id); expanded = false })
            }
        }
    }
}

@Composable
private fun ResultCard(
    res: com.github.jankoran90.showlyfin.data.uploader.model.AudiobookUploadResponse,
    onBack: () -> Unit,
    onAgain: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Kniha nahrána",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${res.title ?: "—"}${res.author?.let { " — $it" }.orEmpty()}" +
                    (res.folder?.let { "\nSložka: $it" }.orEmpty()) +
                    "\nStop: ${res.tracks}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (res.enrich?.matched == true) {
                Text(
                    "Metadata doplněna z Audible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAgain) { Text("Nahrát další") }
                Button(onClick = onBack) { Text("Zpět") }
            }
        }
    }
}

/** Volitelný cover: SAF image picker. User cover přepíše auto-dohledaný (Audiolibrix/Audible). */
@Composable
private fun CoverRow(coverUri: Uri?, coverName: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Obálka", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    coverName ?: "Volitelná — jinak dohledána z Audiolibrix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onPick) { Text(if (coverUri == null) "Vybrat" else "Změnit") }
            if (coverUri != null) TextButton(onClick = onClear) { Text("Odstranit") }
        }
    }
}

/** `DISPLAY_NAME` z ContentResolveru (filename vybraného souboru). */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
}.getOrNull()

/** SAF složka → seznam audio souborů uvnitř (mp3/m4b/m4a/flac). Bez DocumentFile závislosti. */
private fun collectFolderAudio(context: android.content.Context, treeUri: Uri): List<Uri> = runCatching {
    val resolver = context.contentResolver
    val out = mutableListOf<Uri>()
    // Listing přes DocumentsContract — bez extra dependency (androidx.documentfile).
    val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    resolver.query(childrenUri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
        while (c.moveToNext()) {
            val id = c.getString(0)
            val mime = c.getString(1) ?: continue
            if (mime.startsWith("audio/") || mime == "application/octet-stream") {
                out.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            }
        }
    }
    out
}.getOrDefault(emptyList())
