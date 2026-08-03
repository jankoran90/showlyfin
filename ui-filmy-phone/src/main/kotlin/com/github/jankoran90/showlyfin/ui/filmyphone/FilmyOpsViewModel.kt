package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.OpsPrefs
import com.github.jankoran90.showlyfin.data.uploader.OpsRepository
import com.github.jankoran90.showlyfin.data.uploader.model.OpsHistoryResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsOverviewResponse
import com.github.jankoran90.showlyfin.data.uploader.model.OpsSourcesResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PROVOZ (SHW-114) — stav sekce „Provoz" na telefonu.
 *
 * Obnovuje se sama, dokud je obrazovka vidět ([startAutoRefresh]/[stopAutoRefresh]) — „co právě hraje"
 * bez obnovování nemá smysl. Tep na serveru vyprší po 90 s, tak se ptáme po 5 s: divák pozná i to,
 * že přehrávání skončilo.
 */
@HiltViewModel
class FilmyOpsViewModel @Inject constructor(
    private val ops: OpsRepository,
    private val profileRepository: ProfileRepository,
    @javax.inject.Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val overview: OpsOverviewResponse? = null,
        val sources: OpsSourcesResponse? = null,
        val history: OpsHistoryResponse? = null,
        val busy: String? = null,      // probíhající akce (blokuje tlačítka)
        val message: String? = null,   // výsledek akce pro uživatele
        val unavailable: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Profilový klíč, pod kterým server vede zdroje i seznamy (po srovnání klíčů 2026-08-03 jeden). */
    private fun profileKey(): String =
        profileRepository.activeProfile.value?.let {
            it.jellyfinUserId.ifBlank { it.profileUuid }
        }.orEmpty()

    fun refresh() {
        viewModelScope.launch {
            val key = profileKey()
            if (key.isBlank()) {
                _state.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            val overview = ops.overview(key)
            val sources = ops.sources(key)
            val history = ops.history(key, OpsPrefs.historyDays(prefs))
            _state.update {
                it.copy(
                    loading = false,
                    overview = overview ?: it.overview,
                    sources = sources ?: it.sources,
                    history = history ?: it.history,
                    // „Nedostupné" jen když nemáme VŮBEC nic — jedno vynechané kolo obrazovku nevyprázdní.
                    unavailable = overview == null && it.overview == null,
                )
            }
        }
    }

    /**
     * Obnovování, dokud je obrazovka vidět. Interval je v Nastavení ([OpsPrefs.KEY_REFRESH_SEC]);
     * 0 = neobnovovat samo — pak se načte jen jednou při otevření (pro toho, kdo nechce, aby mu
     * telefon každých pár vteřin ťukal na server).
     */
    fun startAutoRefresh() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            val every = OpsPrefs.refreshSec(prefs)
            refresh()
            if (every <= 0) return@launch
            while (isActive) {
                delay(every * 1000L)
                refresh()
            }
        }
    }

    fun stopAutoRefresh() {
        pollJob?.cancel()
        pollJob = null
    }

    /** „Dohledat chybějící" — server zařadí tituly bez zdroje do fronty. */
    fun sweepMissing() = action("sweep") {
        val res = ops.sweep(profileKey())
        when {
            res == null -> "Dohledání se nepodařilo spustit."
            res.missing == 0 -> "Všechny tituly mají zdroj — není co dohledávat."
            else -> "Hledám zdroj pro ${res.missing} " +
                "${plural(res.missing, "titul", "tituly", "titulů")} (${res.queued} ve frontě)."
        }
    }

    /** „Ověřit zdraví" — server projede uložené zdroje a mrtvé nahradí. */
    fun verifyHealth() = action("verify") {
        if (ops.verifyHealth(profileKey())) "Kontrola zdrojů běží na serveru, výsledek se tu projeví."
        else "Kontrolu se nepodařilo spustit."
    }

    fun removeSource(tmdb: Long, imdb: String, title: String) = action("remove-$tmdb") {
        if (ops.removeSource(profileKey(), tmdb, imdb)) "Zdroj u „$title“ odebrán."
        else "Zdroj se nepodařilo odebrat."
    }

    /** Jak má automat hledat: `child` = česky a přes sdilej napřed, `original` = původní znění. */
    fun setPolicy(policy: String) = action("policy") {
        if (ops.setPolicy(profileKey(), policy)) {
            if (policy == "child") "Automat bude hledat české verze (sdilej napřed)."
            else "Automat bude hledat v původním znění."
        } else "Nastavení se nepodařilo uložit."
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun action(tag: String, block: suspend () -> String) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = tag, message = null) }
            val msg = runCatching { block() }.getOrElse { "Akce selhala: ${it.message ?: "neznámá chyba"}" }
            _state.update { it.copy(busy = null, message = msg) }
            refresh()
        }
    }

    private fun plural(n: Int, one: String, few: String, many: String) = when {
        n == 1 -> one
        n in 2..4 -> few
        else -> many
    }

    override fun onCleared() {
        stopAutoRefresh()
        super.onCleared()
    }
}
