package com.github.jankoran90.showlyfin.feature.listen.audioteka

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtBook
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtRelated
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AtSeries
import com.github.jankoran90.showlyfin.data.uploader.audioteka.AudiotekaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Načítání ze serveru: [Loading] → [Ready] / [Failed] (síť, server) / [Empty] (opravdu nic). */
sealed interface AtLoad<out T> {
    data object Idle : AtLoad<Nothing>
    data object Loading : AtLoad<Nothing>
    data object Failed : AtLoad<Nothing>
    data object Empty : AtLoad<Nothing>
    data class Ready<T>(val data: T) : AtLoad<T>
}

/** Katalog Audioteky — hledání (user 2026-11-03: „samostatné hledání jako v páteři"). */
@HiltViewModel
class AudiotekaSearchViewModel @Inject constructor(
    private val repo: AudiotekaRepository,
    private val owned: AudiotekaOwnedFilter,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _result = MutableStateFlow<AtLoad<List<AtBook>>>(AtLoad.Idle)
    val result: StateFlow<AtLoad<List<AtBook>>> = _result.asStateFlow()
    private var job: Job? = null

    fun setQuery(q: String) {
        _query.value = q
        job?.cancel()
        if (q.trim().length < 2) {
            _result.value = AtLoad.Idle
            return
        }
        job = viewModelScope.launch {
            delay(450)
            run(q.trim())
        }
    }

    fun retry() {
        val q = _query.value.trim()
        if (q.length < 2) return
        job?.cancel()
        job = viewModelScope.launch { run(q) }
    }

    private suspend fun run(q: String) {
        _result.value = AtLoad.Loading
        val items = repo.search(q)?.let { owned.apply(it) }
        _result.value = when {
            items == null -> AtLoad.Failed
            items.isEmpty() -> AtLoad.Empty
            else -> AtLoad.Ready(items)
        }
    }
}

/** Detail katalogové knihy + návrhy (série, kolekce, autor, interpret, kategorie). */
@HiltViewModel
class AudiotekaBookViewModel @Inject constructor(
    private val repo: AudiotekaRepository,
    private val owned: AudiotekaOwnedFilter,
) : ViewModel() {
    private val _state = MutableStateFlow<AtLoad<AtRelated>>(AtLoad.Idle)
    val state: StateFlow<AtLoad<AtRelated>> = _state.asStateFlow()
    private var loadedId: String? = null

    fun load(bookId: String, force: Boolean = false) {
        if (!force && loadedId == bookId && _state.value is AtLoad.Ready) return
        loadedId = bookId
        _state.value = AtLoad.Loading
        viewModelScope.launch {
            val r = repo.related(bookId)?.let { owned.apply(it) }
            _state.value = if (r?.detail == null) AtLoad.Failed else AtLoad.Ready(r)
        }
    }
}

/** Série / kolekce Audioteky (výsledek hledání typu série). */
@HiltViewModel
class AudiotekaSeriesViewModel @Inject constructor(
    private val repo: AudiotekaRepository,
    private val owned: AudiotekaOwnedFilter,
) : ViewModel() {
    private val _state = MutableStateFlow<AtLoad<AtSeries>>(AtLoad.Idle)
    val state: StateFlow<AtLoad<AtSeries>> = _state.asStateFlow()
    private var loadedId: String? = null

    fun load(seriesId: String, force: Boolean = false) {
        if (!force && loadedId == seriesId && _state.value is AtLoad.Ready) return
        loadedId = seriesId
        _state.value = AtLoad.Loading
        viewModelScope.launch {
            val s = repo.series(seriesId)?.let { it.copy(items = owned.apply(it.items)) }
            _state.value = when {
                s == null -> AtLoad.Failed
                s.items.isEmpty() -> AtLoad.Empty
                else -> AtLoad.Ready(s)
            }
        }
    }
}

/**
 * Návrhy u knihy z NAŠÍ knihovny (detail audioknihy ABS): kniha → Audioteka (shoda názvu + autora)
 * → související. Nenalezená kniha = žádná sekce (raději nic než cizí kniha).
 */
@HiltViewModel
class AudiotekaSuggestionsViewModel @Inject constructor(
    private val repo: AudiotekaRepository,
    private val owned: AudiotekaOwnedFilter,
) : ViewModel() {
    private val _state = MutableStateFlow<AtLoad<AtRelated>>(AtLoad.Idle)
    val state: StateFlow<AtLoad<AtRelated>> = _state.asStateFlow()
    private var key: String? = null

    fun load(title: String, author: String?) {
        val k = "$title|$author"
        // Hotový výsledek (i „nenalezeno") se nenačítá znovu; po chybě sítě ano (revize 2026-11-03).
        if (key == k && _state.value !is AtLoad.Failed) return
        key = k
        _state.value = AtLoad.Loading
        viewModelScope.launch {
            val res = repo.match(title, author)
            val match = res?.getOrNull()
            val related = match?.let { repo.related(it.id) }?.let { owned.apply(it) }
            _state.update {
                when {
                    res == null -> AtLoad.Failed
                    match == null -> AtLoad.Empty
                    related == null -> AtLoad.Failed
                    related.sections.isEmpty() -> AtLoad.Empty
                    else -> AtLoad.Ready(related)
                }
            }
        }
    }
}

/** Nastavení katalogu (SharedPreferences `slovo_audioteka`). Sekce v menu se skrývá přes Pořadí menu. */
object AudiotekaPrefs {
    private const val PREFS = "slovo_audioteka"
    private const val KEY_SUGGESTIONS = "suggestions_in_book"
    private const val KEY_HIDE_OWNED = "hide_owned_in_suggestions"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Návrhy z Audioteky v detailu audioknihy (výchozí zapnuto). */
    fun suggestionsInBook(ctx: Context) = p(ctx).getBoolean(KEY_SUGGESTIONS, true)
    fun setSuggestionsInBook(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(KEY_SUGGESTIONS, v).apply()

    /** V návrzích „Od autora / Čte / kategorie" skrýt knihy, které už máme (série je ukazuje vždy). */
    fun hideOwned(ctx: Context) = p(ctx).getBoolean(KEY_HIDE_OWNED, false)
    fun setHideOwned(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(KEY_HIDE_OWNED, v).apply()
}
