package com.github.jankoran90.showlyfin.feature.discover.wanttosee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.data.jellyfin.ParentalControlsRepository
import com.github.jankoran90.showlyfin.data.uploader.BackfillItem
import com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktRowLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * CATALOGUE (SHW-98) — dávkové dohledání zdrojů pro CELÝ watchlist. Auto-cache se sám pouští jen při PŘIDÁNÍ
 * do „Chci vidět" ([DetailViewModel.toggleWatchlist]); starší tituly bez zdroje zůstaly prázdné. Dřív klient
 * spamoval N× `cache-one` (fire-and-forget) → po pár filmech se to zaseklo a user musel ručně restartovat, přes
 * hodiny nic nepřibývalo. TEĎ: klient JEDNOU pošle celý chybějící seznam na server (`/gems/cache-batch`), který
 * má PERSISTENTNÍ FRONTU + worker s AUTO-RETRY — dohledává na pozadí přes hodiny i po zavření appky. Klient jen
 * pollује stav (`/gems/cache-status`, kolik ještě čeká) a ukazuje živý průběh.
 *
 * Sdílený mezi telefonem (Nastavení blok + sekce „Chci vidět") a TV (sekce „Chci vidět") — parita shellů.
 */
@HiltViewModel
class WatchlistSourceCacheViewModel @Inject constructor(
    private val traktRowLoader: TraktRowLoader,
    private val workingSourceStore: WorkingSourceStore,
    private val parentalControls: ParentalControlsRepository,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        data class Done(val requested: Int, val already: Int) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        // Vstup do sekce: pokud server pořád něco dohledává (fronta neprázdná), navaž a ukazuj živý průběh.
        viewModelScope.launch {
            val remaining = workingSourceStore.cacheStatus() ?: return@launch
            if (remaining > 0) startPolling(total = remaining)
        }
    }

    /**
     * SEZONA f3b — seriály z „Chci vidět" do TÉŽE serverové fronty jako filmy (persistentní, s retry).
     *
     * 🔴 Předchozí verze (f3) posílala max 5 seriálů, jen SEZÓNU 1 a mimo frontu (fire-and-forget) —
     * takže šestý seriál a druhá sezóna tiše chyběly a nic je nikdy nedohnalo (user 2026-08-01:
     * „nemám rád mezery a zametání pod koberec"). Teď jde seriál do fronty jako `kind="show"` a SERVER
     * si ho rozloží na jednotlivé sezóny (`services/gems/seasons.py`), každou s vlastním retry.
     * Které sezóny už zdroj mají, si server ověří sám (`profile_has_working_source` s `epKey`).
     */
    private suspend fun showBackfillItems(): List<BackfillItem> {
        val shows = runCatching { traktRowLoader.watchlist("shows") }.getOrElse { return emptyList() }
        val items = shows.filter { it.imdbId != null }
            .map { BackfillItem(it.imdbId!!, it.tmdbId ?: 0L, it.title, it.year, kind = "show") }
        Timber.i("[SEZONA] backfill seriálů: %d titulů do fronty (server je rozloží na sezóny)", items.size)
        return items
    }

    fun runBackfill() {
        if (_state.value is State.Running) return
        viewModelScope.launch {
            _state.value = State.Running(0, 0)
            val watchlist = runCatching { traktRowLoader.watchlist("movies") }.getOrElse {
                _state.value = State.Error("Nepodařilo se načíst watchlist z Traktu — přihlášen?")
                return@launch
            }
            // Jen filmy s imdb, které ještě NEMAJÍ uložený zdroj.
            val missing = watchlist.filter { it.imdbId != null && workingSourceStore.get(it.imdbId, it.tmdbId) == null }
            val already = watchlist.size - missing.size
            val policy = cachePolicy()
            // SEZONA f3b: seriály jdou do TÉŽE fronty (server je rozloží na sezóny), takže jeden batch,
            // jeden ukazatel průběhu a retry i pro ně.
            val items = missing.map { BackfillItem(it.imdbId!!, it.tmdbId ?: 0L, it.title, it.year) } +
                showBackfillItems()
            if (items.isEmpty()) {
                _state.value = State.Done(requested = 0, already = already)
                return@launch
            }
            // Jeden batch → server frontu převezme a maká sám (auto-retry). Pak sleduj kolik ubývá.
            workingSourceStore.cacheBatch(items, policy)
            // Celkový počet ber ze SERVERU (rozklad seriálů na sezóny se stane až tam, takže lokální
            // počet položek by ukazatel podhodnotil a „X z Y" by lhalo).
            startPolling(total = workingSourceStore.cacheStatus() ?: items.size)
        }
    }

    /** Sleduj serverovou frontu: `done = total - zbývá`. Prázdná fronta → hotovo. Odchod z obrazovky poll zruší
     *  (viewModelScope), server běží dál — badge/„X z Y má zdroj" se dopočítá reaktivně, jak WorkingSource padají. */
    private fun startPolling(total: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val remaining = workingSourceStore.cacheStatus()
                if (remaining == null) { break }               // server nedostupný → nech stav být
                if (remaining <= 0) {
                    _state.value = State.Done(requested = total, already = 0)
                    break
                }
                _state.value = State.Running(done = (total - remaining).coerceIn(0, total), total = total)
                delay(POLL_MS)
            }
        }
    }

    private fun cachePolicy(): String = when (parentalControls.profile.value.effectiveAgeRating) {
        AgeRating.CHILDREN, AgeRating.FAMILY -> "child"
        else -> "original"
    }

    private companion object {
        const val POLL_MS = 8000L
    }
}
