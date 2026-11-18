package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager

/** Polička Audioteky (koupené knihy, odtud se stahují). */
const val AUDIOTEKA_SHELF_URL = "https://audioteka.com/cz/policka/"
const val AUDIOTEKA_LOGIN_URL = "https://audioteka.com/cz/prihlaseni/"

/**
 * Stránka Audioteky uvnitř Slova (user 2026-11-18: „chci kupovat přímo u nás z appky… a po koupi rovnou
 * integrovat nahrávku do appky"). Přihlášení i pokladna jsou jejich vlastní (WebView si přihlášení pamatuje);
 * ťuk na Stáhnout v Poličce převezme [AudiotekaImporter] a po volbě knihovny knihu nahraje do ABS.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiotekaWebScreen(
    url: String,
    titleHint: String?,
    authorHint: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudiotekaWebViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val importState by viewModel.importer.state.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    var pageTitle by remember { mutableStateOf("Audioteka") }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var hintVisible by rememberSaveable { mutableStateOf(true) }
    // Výsledek nahrávání ukazujeme jen u dovozu spuštěného tady (ne cizí upload z obrazovky Nahrát).
    var importStarted by rememberSaveable { mutableStateOf(false) }
    val titleHintState by rememberUpdatedState(titleHint)
    val authorHintState by rememberUpdatedState(authorHint)

    val webView = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false) // target=_blank (platební brána) zůstane v tomtéž okně
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleExternalScheme(ctx, request.url)

                override fun onPageFinished(view: WebView, u: String?) {
                    loading = false
                    canGoBack = view.canGoBack()
                    view.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { pageTitle = it }
                    CookieManager.getInstance().flush()
                }

                override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                    loading = true
                }
            }
            setDownloadListener { dUrl, ua, disposition, mime, _ ->
                if (AudiotekaPrefs.importDownloads(ctx)) {
                    viewModel.importer.onDownload(dUrl, ua, disposition, mime, titleHintState, authorHintState)
                } else {
                    systemDownload(ctx, dUrl, ua, disposition, mime)
                }
            }
            loadUrl(url)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView.stopLoading()
            webView.destroy()
        }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět do Slova") }
                },
                actions = {
                    TextButton(onClick = { webView.loadUrl(AUDIOTEKA_SHELF_URL) }) {
                        Icon(Icons.Rounded.LibraryBooks, contentDescription = null)
                        Text(" Polička")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (hintVisible && AudiotekaPrefs.importDownloads(ctx)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Po zaplacení otevři Poličku a u knihy ťukni na Stáhnout. Slovo ji doveze do knihovny.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { hintVisible = false }) { Icon(Icons.Rounded.Close, contentDescription = "Skrýt nápovědu") }
                }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                ImportStatusCard(
                    importState = importState,
                    upload = upload.takeIf { importStarted },
                    onCancel = { viewModel.importer.cancel() },
                    onDismiss = { viewModel.importer.dismissError(); importStarted = false },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }

    (importState as? AudiotekaImporter.State.Ready)?.let { ready ->
        ImportDialog(
            ready = ready,
            libraries = libraries,
            onImport = { lib, t, a -> importStarted = true; viewModel.importer.import(lib, t, a) },
            onCancel = { viewModel.importer.cancel() },
        )
    }
}

@Composable
private fun ImportStatusCard(
    importState: AudiotekaImporter.State,
    upload: AudiobookUploadManager.UploadState?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (text, progress, action) = when {
        importState is AudiotekaImporter.State.Downloading -> Triple(
            "Stahuji z Audioteky: ${importState.name} (${importState.bytes / 1_000_000} MB)",
            importState.progress ?: -1f,
            "Zrušit" to onCancel,
        )
        importState is AudiotekaImporter.State.Failed -> Triple(importState.message, null, "OK" to onDismiss)
        upload?.isUploading == true -> Triple("Nahrávám do knihovny…", upload.progress, null)
        upload?.result != null -> Triple("Hotovo, kniha je v knihovně. Najdeš ji ve Slovu mezi audioknihami.", null, "OK" to onDismiss)
        upload?.error != null -> Triple("Nahrání do knihovny selhalo: ${upload.error}", null, "OK" to onDismiss)
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

/** Volba knihovny u každé koupě (user 2026-11-18: „Zeptat se u každé koupě"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportDialog(
    ready: AudiotekaImporter.State.Ready,
    libraries: List<AbsLibrary>,
    onImport: (libraryId: String, title: String, author: String) -> Unit,
    onCancel: () -> Unit,
) {
    // Bez předvýběru: první knihovna bývá dětská (user 2026-11-18: kniha pro dospělé skončila v Děti).
    var selected by remember(libraries) { mutableStateOf(libraries.singleOrNull()?.id) }
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
                }
                OutlinedTextField(title, { title = it }, label = { Text("Název (prázdné = dohledá server)") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("Autor") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let { onImport(it, title, author) } }) { Text("Dovézt") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Zahodit") } },
    )
}

/** Platební brány a banky otevírají vlastní appky (intent://, bankovní schémata) → předat systému. */
private fun handleExternalScheme(ctx: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob") return false
    return try {
        val intent = if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, uri)
        intent.addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.component = null
        intent.selector = null
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") }
                .getOrNull()?.let { fb -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fb)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        true
    } catch (e: Exception) {
        true
    }
}

/** Dovoz vypnutý v Nastavení → obyčejné stažení do složky Stažené soubory (s přihlášením z WebView). */
private fun systemDownload(ctx: Context, url: String, ua: String?, disposition: String?, mime: String?) {
    if (!url.startsWith("http")) {
        Toast.makeText(ctx, "Tohle stažení Slovo neumí převzít.", Toast.LENGTH_LONG).show()
        return
    }
    val name = URLUtil.guessFileName(url, disposition, mime)
    val req = DownloadManager.Request(Uri.parse(url))
        .setMimeType(mime)
        .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
        .addRequestHeader("User-Agent", ua.orEmpty())
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    Toast.makeText(ctx, "Stahuji do složky Stažené soubory: $name", Toast.LENGTH_LONG).show()
}
