package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.core.domain.home.HomeLayoutStore
import com.github.jankoran90.showlyfin.core.domain.home.HomeRowConfig
import com.github.jankoran90.showlyfin.core.domain.home.PhoneMenuEntry
import com.github.jankoran90.showlyfin.feature.discover.home.HomeLayoutSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * PŮDORYS (SHW-112) — rozvržení domova a menu NA TELEFONU. Do vc126 uměla řady přeskládat jen TV
 * (`TvHomeRowEditor`) a telefonní menu bylo natvrdo v kódu; tenhle VM dává telefonu tytéž páky
 * nad TÝMŽ [HomeLayoutStore], takže obě zařízení sahají na jedno rozvržení.
 *
 * [homeLayoutSync] se injektuje kvůli VZNIKU mostu na synchronizovaný profil — musí běžet dřív,
 * než uživatel začne editovat, jinak by příchozí config přebil ještě neposlanou lokální změnu.
 */
@HiltViewModel
class FilmyHomeLayoutViewModel @Inject constructor(
    private val store: HomeLayoutStore,
    @Suppress("unused") private val homeLayoutSync: HomeLayoutSync,
) : ViewModel() {

    /** VŠECHNY řady (i skryté) v uživatelově pořadí — editor je musí umět vrátit zpátky. */
    val rows: StateFlow<List<HomeRowConfig>> = store.rows

    /** Uložené menu telefonu; prázdné = uživatel si ho ještě nenastavil (viz [FilmyMenuConfig.merge]). */
    val menu: StateFlow<List<PhoneMenuEntry>> = store.phoneMenu

    // ── Řady domova (pass-through na store) ───────────────────────────────────
    fun moveRow(id: String, up: Boolean) = store.move(id, up)
    fun setRowEnabled(id: String, enabled: Boolean) = store.setEnabled(id, enabled)
    fun updateRow(config: HomeRowConfig) = store.updateRow(config)
    fun addRow(config: HomeRowConfig) = store.addRow(config)
    fun removeRow(id: String) = store.removeRow(id)
    fun resetRows() = store.resetRows()

    // ── Menu telefonu ─────────────────────────────────────────────────────────

    /** Přesuň položku menu o jedno místo. Shell počítá s KOMPLETNÍM (zamergovaným) seznamem. */
    fun moveMenu(merged: List<PhoneMenuEntry>, item: String, up: Boolean) {
        val i = merged.indexOfFirst { it.item == item }
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j < 0 || j >= merged.size) return
        store.setPhoneMenu(merged.toMutableList().also { it[i] = merged[j]; it[j] = merged[i] })
    }

    fun setMenuEnabled(merged: List<PhoneMenuEntry>, item: String, enabled: Boolean) {
        store.setPhoneMenu(merged.map { if (it.item == item) it.copy(enabled = enabled) else it })
    }

    fun resetMenu() = store.resetPhoneMenu()
}
