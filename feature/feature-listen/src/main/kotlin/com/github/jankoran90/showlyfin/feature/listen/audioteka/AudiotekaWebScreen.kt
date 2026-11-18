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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import kotlin.coroutines.resume

/** Polička Audioteky (koupené knihy, odtud se stahují). */
const val AUDIOTEKA_SHELF_URL = "https://audioteka.com/cz/policka/"
const val AUDIOTEKA_LOGIN_URL = "https://audioteka.com/cz/prihlaseni/"

/**
 * Stránka Audioteky uvnitř Slova (user 2026-11-18: „chci kupovat přímo u nás z appky… a po koupi rovnou
 * integrovat nahrávku do appky"). Přihlášení i pokladna jsou jejich vlastní (WebView si přihlášení pamatuje,
 * Google Pay přes PaymentRequest). Po zaplacení samo otevře Poličku a u koupené knihy ťukne na Stáhnout
 * ([AudiotekaPurchaseFlow]); stažení převezme [AudiotekaImporter] a doveze knihu do knihovny zvolené už u nákupu.
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
    bookId: String? = null,
    viewModel: AudiotekaWebViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val importState by viewModel.importer.state.collectAsStateWithLifecycle()
    val upload by viewModel.upload.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    var pageTitle by remember { mutableStateOf("Audioteka") }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var barVisible by rememberSaveable { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    /** Hláška nákupu v pruhu („Zaplaceno — otevírám Poličku", „ťukni na Stáhnout"). */
    var purchaseText by remember { mutableStateOf<String?>(null) }
    /** Po koupi čeká samo-ťuknutí v Poličce. */
    var autoPending by remember { mutableStateOf(false) }
    var autoJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val titleHintState by rememberUpdatedState(titleHint)
    val authorHintState by rememberUpdatedState(authorHint)
    LaunchedEffect(Unit) { viewModel.start(bookId, titleHint) }

    val webView = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false) // target=_blank (platební brána) zůstane v tomtéž okně
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // Google Pay v pokladně (user 2026-11-18 19:47). Vlastní User-Agent nenastavovat — Google Pay by zmizel.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) {
                WebSettingsCompat.setPaymentRequestEnabled(settings, true)
            }
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
                    if (u == null) return
                    viewModel.recordPage(u)
                    val auto = AudiotekaPrefs.importDownloads(ctx) && AudiotekaPrefs.autoAfterPurchase(ctx)
                    when (val ev = viewModel.purchase.onPage(u)) {
                        is AudiotekaPurchaseFlow.Event.Product -> viewModel.onProductPage(ev.slug)
                        AudiotekaPurchaseFlow.Event.CheckoutStarted -> viewModel.sendPurchaseLog("pokladna")
                        AudiotekaPurchaseFlow.Event.PurchaseFinished -> {
                            viewModel.sendPurchaseLog("konec nákupu (stránka poděkování)")
                            if (auto) {
                                autoPending = true
                                purchaseText = "Zaplaceno — otevírám Poličku a stahuji knihu…"
                                barVisible = true
                                view.postDelayed({ view.loadUrl(AUDIOTEKA_SHELF_URL) }, 1500)
                            }
                        }
                        AudiotekaPurchaseFlow.Event.None -> Unit
                    }
                    // Polička po koupi (i když stránku poděkování nepoznáme a user ji otevře sám) → samo-ťuknutí.
                    val shelf = u.contains("/policka")
                    if (shelf && auto && (autoPending || viewModel.purchase.checkoutSeen) && autoJob?.isActive != true) {
                        autoPending = false
                        viewModel.purchase.consumeCheckout()
                        autoJob = scope.launch {
                            val ok = autoDownload(view, viewModel) { viewModel.importer.state.value }
                            purchaseText = if (ok) null else "Kniha je koupená — ťukni u ní na Stáhnout, dovoz proběhne sám."
                        }
                    }
                }

                override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                    loading = true
                }
            }
            setDownloadListener { dUrl, ua, disposition, mime, _ ->
                if (AudiotekaPrefs.importDownloads(ctx)) {
                    viewModel.importer.onDownload(dUrl, ua, disposition, mime, viewModel.hints(titleHintState, authorHintState))
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
            if (barVisible && AudiotekaPrefs.importDownloads(ctx)) {
                ImportTargetBar(
                    target = target,
                    auto = AudiotekaPrefs.autoImport(ctx) && AudiotekaPrefs.autoAfterPurchase(ctx),
                    text = purchaseText,
                    onChange = { picking = true },
                    onClose = { barVisible = false },
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                ImportStatusCard(
                    importState = importState,
                    upload = upload,
                    onCancel = { viewModel.importer.cancel() },
                    onDismiss = { viewModel.importer.dismissError() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }

    if (picking) {
        LibraryPickDialog(
            libraries = libraries,
            selectedId = target?.libraryId,
            onPick = { viewModel.pick(it); picking = false },
            onDismiss = { picking = false },
        )
    }
    (importState as? AudiotekaImporter.State.Ready)?.let { ready ->
        ImportDialog(
            ready = ready,
            libraries = libraries,
            onImport = { t, title, author -> viewModel.importer.import(t, title, author) },
            onCancel = { viewModel.importer.cancel() },
        )
    }
}

/**
 * Po koupi v Poličce: ťukne na Stáhnout u koupené knihy (stránka se dočítá → víc pokusů), pak v okně formátu na
 * MP3. Hotovo, jakmile importér začne stahovat. false = nenašel (diagnostika jde do serverového logu).
 */
private suspend fun autoDownload(
    view: WebView,
    vm: AudiotekaWebViewModel,
    state: () -> AudiotekaImporter.State,
): Boolean {
    fun started() = state().let { it is AudiotekaImporter.State.Downloading || it is AudiotekaImporter.State.Importing }
    var last = ""
    repeat(10) {
        delay(1500)
        if (started()) return true
        val raw = view.evalJs(AudiotekaPurchaseFlow.clickDownloadJs(vm.purchase.productSlug, vm.hints(null, null).title))
        last = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault(raw)
        if (last.contains("\"ok\":true")) {
            repeat(8) {
                delay(1000)
                if (started()) return true
                view.evalJs(AudiotekaPurchaseFlow.CLICK_MP3_JS)
            }
            if (started()) return true
            vm.sendPurchaseLog("samo-ťuknutí: Stáhnout ťuknuto, stažení nezačalo ($last)")
            return false
        }
    }
    vm.sendPurchaseLog("samo-ťuknutí: kniha v Poličce nenalezena ($last)")
    return false
}

private suspend fun WebView.evalJs(js: String): String = suspendCancellableCoroutine { c ->
    evaluateJavascript(js) { r -> if (c.isActive) c.resume(r.orEmpty()) }
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
