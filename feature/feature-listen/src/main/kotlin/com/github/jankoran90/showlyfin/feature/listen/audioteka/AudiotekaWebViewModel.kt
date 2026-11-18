package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.abs.model.AbsLibrary
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AudiotekaRepository
import com.github.jankoran90.showlyfin.feature.listen.AudiobookUploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Stránka Audioteky uvnitř Slova: dovoz stažené knihy ([AudiotekaImporter]), knihovny k výběru, cílová knihovna
 * zvolená už u nákupu (user 2026-11-18 19:53: „návrh pro jaký profil … už u platby") a záznam průběhu nákupu.
 */
@HiltViewModel
class AudiotekaWebViewModel @Inject constructor(
    val importer: AudiotekaImporter,
    private val absRepo: AbsRepository,
    private val atRepo: AudiotekaRepository,
    @ApplicationContext private val context: Context,
    uploadManager: AudiobookUploadManager,
) : ViewModel() {

    val upload: StateFlow<AudiobookUploadManager.UploadState> = uploadManager.state
    val purchase = AudiotekaPurchaseFlow()

    private val _libraries = MutableStateFlow<List<AbsLibrary>>(emptyList())
    val libraries: StateFlow<List<AbsLibrary>> = _libraries.asStateFlow()

    /** Kam se po zaplacení kniha doveze (null = zeptá se po stažení). */
    private val _target = MutableStateFlow<AudiotekaImporter.ImportTarget?>(null)
    val target: StateFlow<AudiotekaImporter.ImportTarget?> = _target.asStateFlow()
    private var userPicked = false
    private var started = false
    private var suggestJob: Job? = null
    private var firstSlug: String? = null
    private var cardActive = true

    /** Kupovaná kniha: z karty, nebo podle poslední stránky knihy ve WebView (doplní server). */
    var bookId: String? = null
        private set
    var bookTitle: String? = null
        private set

    private val libsLoad = viewModelScope.launch {
        runCatching { absRepo.getAudiobookLibraries() }
            .onSuccess { _libraries.value = it }
            .onFailure { Timber.w(it, "[AUDIOTEKA] knihovny pro dovoz se nenačetly") }
    }

    /** Jednou při otevření: kniha z karty → návrh knihovny ještě před platbou. */
    fun start(cardBookId: String?, cardTitle: String?) {
        if (started) return
        started = true
        bookId = cardBookId
        bookTitle = cardTitle
        cardActive = cardTitle != null // Polička bez karty: název knihy doplní stránka knihy ve WebView
        if (cardBookId != null) suggest(stem = null, id = cardBookId)
    }

    /** Stránka knihy ve WebView (slug z adresy) → kniha se mění, návrh knihovny podle její kategorie. */
    fun onProductPage(slug: String) {
        // První stránka knihy = kniha z karty; přechod na jinou → název a autor z karty už neplatí.
        val changed = firstSlug != null && slug != firstSlug
        if (firstSlug == null) firstSlug = slug else if (changed) cardActive = false
        if (changed || !cardActive) {
            // Jiná kniha: staré údaje i návrh zahodit HNED (revize P1/P2) — kdyby dotaz na kategorii selhal,
            // nesmí se dovézt do knihovny předchozí knihy ani hledat v Poličce její název.
            bookTitle = null
            bookId = null
            if (!userPicked) _target.value = null
        }
        // Stránka knihy z karty: návrh už běží podle id knihy (spolehlivější než slug) → neprepisovat.
        if (cardActive && bookId != null) return
        suggest(stem = slug, id = null)
    }

    fun pick(lib: AbsLibrary) {
        userPicked = true
        _target.value = AudiotekaImporter.ImportTarget(lib.id, lib.name, null)
    }

    private fun suggest(stem: String?, id: String?) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            val h = atRepo.libraryHint(stem, id) ?: return@launch
            if (stem != null && !cardActive) bookTitle = h.title // kniha z WebView (jiná než z karty)
            libsLoad.join()
            if (userPicked) return@launch // ruční volba knihovny platí, identita knihy se aktualizuje dál
            pickLibrary(_libraries.value.map { it.id to it.name }, h.kids)?.let { (lid, n) ->
                _target.value = AudiotekaImporter.ImportTarget(lid, n, h.category)
            }
        }
    }

    /** Co předat importéru při stažení. Cíl platí jen pro soubor kupované knihy (importér ověří jméno). */
    fun hints(cardTitle: String?, cardAuthor: String?): AudiotekaImporter.Hints = AudiotekaImporter.Hints(
        title = if (cardActive && cardTitle != null) cardTitle else bookTitle,
        author = cardAuthor.takeIf { cardActive && cardTitle != null },
        bookId = bookId,
        slug = purchase.productSlug,
        target = _target.value,
    )

    /** Záznam stránky nákupu (host + cesta bez parametrů) do lokálního kruhového seznamu. */
    fun recordPage(url: String) {
        if (!AudiotekaPrefs.recordPurchasePages(context)) return
        val line = purchase.describe(url) ?: return
        AudiotekaPrefs.addPurchasePage(context, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + " " + line)
    }

    /** Pošle záznam průběhu nákupu do serverového logu (prefix `[audioteka-nakup]`), ať jde doladit samočinnost. */
    fun sendPurchaseLog(reason: String) {
        if (!AudiotekaPrefs.recordPurchasePages(context)) return
        val text = buildString {
            appendLine("[audioteka-nakup] $reason")
            appendLine("kniha: ${bookTitle.orEmpty()} slug=${purchase.productSlug.orEmpty()} cil=${_target.value?.libraryName.orEmpty()}")
            AudiotekaPrefs.purchasePages(context).forEach { appendLine(it) }
        }
        logScope.launch { atRepo.postLog(text) }
    }

    override fun onCleared() {
        if (purchase.checkoutSeen) sendPurchaseLog("odchod ze stránky Audioteky během nákupu")
        super.onCleared()
    }

    private companion object {
        /** Log musí odejít i po zavření obrazovky (viewModelScope už je zrušený). */
        val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
