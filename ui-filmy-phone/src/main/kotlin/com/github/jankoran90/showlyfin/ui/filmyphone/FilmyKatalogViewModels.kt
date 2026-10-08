package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.KatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LABYRINT (FLM-04) — nastavení Objevit / Pro tebe v appce (parita s webem: Nastavení → „Objevování
 * katalogu" a „Pro tebe"). Lokální prefy (`trakt_prefs`) jako ostatní Filmy volby shellu.
 */
object KatalogPrefs {
    private const val PREFS = "trakt_prefs"
    const val BEZ_DETSKYCH = "katalog_bez_detskych"
    const val PRUH_TOP = "katalog_pruh_top"
    const val PRUH_KLENOTY = "katalog_pruh_klenoty"
    const val PRUH_NOVINKY = "katalog_pruh_novinky"
    const val PRUH_CESKE = "katalog_pruh_ceske"
    const val PRUH_SERIALY = "katalog_pruh_serialy"
    const val PT_RADKU = "protebe_radku"
    const val PT_PODZANRU = "protebe_podzanru"
    const val PT_CHCI = "protebe_chci"
    const val PT_KURATOR = "protebe_kurator"
    const val STITKY_KARTA = "katalog_stitky_karta"
    const val HLEDAT_STITKY = "katalog_hledat_stitky"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun bool(ctx: Context, key: String, def: Boolean = true) = p(ctx).getBoolean(key, def)
    fun setBool(ctx: Context, key: String, v: Boolean) = p(ctx).edit().putBoolean(key, v).apply()
    fun int(ctx: Context, key: String, def: Int) = p(ctx).getInt(key, def)
    fun setInt(ctx: Context, key: String, v: Int) = p(ctx).edit().putInt(key, v).apply()
}

/** Objevit: mapa žánrů a nálad. */
@HiltViewModel
class ObjevitViewModel @Inject constructor(
    private val repo: KatalogRepository,
) : ViewModel() {
    data class State(val mapa: KatalogRepository.Mapa? = null, val loading: Boolean = true, val chyba: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = State(loading = true)
            val m = repo.mapa()
            _state.value = State(mapa = m, loading = false, chyba = m == null)
        }
    }
}

/** Stránka žánru / podžánru / tématu / roku / země: Máš k dispozici, pruhy, Procházet vše. */
@HiltViewModel
class KatalogStrankaViewModel @Inject constructor(
    private val repo: KatalogRepository,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    data class Pruh(val nazev: String, val razeni: String?, val tituly: List<KatalogRepository.Titul>)

    data class State(
        val nadpis: String = "",
        val rodic: Pair<String, String>? = null,          // (id, label) nadřazeného žánru
        val sourozenci: List<KatalogRepository.Stitek> = emptyList(),
        val vlastni: List<KatalogRepository.Titul> = emptyList(),
        /** user 2026-10-08: „switch, když obsahuje českou stopu" — filtr Máš k dispozici. */
        val jenCz: Boolean = false,
        val pruhy: List<Pruh> = emptyList(),
        val vse: List<KatalogRepository.Titul> = emptyList(),
        val razeni: String = "hodnoceni",
        val serialy: Boolean = false,
        val ceske: Boolean = false,
        val dekada: Int? = null,                            // 0 = starší než 1960
        val stranka: Int = 0,
        val stranek: Int = 1,
        val nacitam: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var druh = ""
    private var arg = ""
    private var vseJob: Job? = null

    fun open(druh: String, arg: String) {
        if (this.druh == druh && this.arg == arg) return
        this.druh = druh; this.arg = arg
        _state.value = State()
        viewModelScope.launch { hlavicka() }
        viewModelScope.launch { _state.update { it.copy(vlastni = repo.vlastni(druh, arg).orEmpty()) } }
        viewModelScope.launch { pruhy() }
        resetVse()
    }

    private suspend fun hlavicka() {
        val mapa = repo.mapa()
        var nadpis = arg
        var rodic: Pair<String, String>? = null
        var sourozenci = emptyList<KatalogRepository.Stitek>()
        when (druh) {
            "zanr" -> {
                nadpis = mapa?.hlavni?.firstOrNull { it.id == arg }?.label ?: arg
                sourozenci = mapa?.podzanry.orEmpty().filter { it.parent == arg }
                    .map { KatalogRepository.Stitek("podzanr", it.id, it.label) }
            }
            "podzanr" -> {
                val p = mapa?.podzanry?.firstOrNull { it.id == arg }
                nadpis = p?.label ?: arg
                val parent = p?.parent
                if (parent != null) {
                    rodic = parent to (mapa?.hlavni?.firstOrNull { it.id == parent }?.label ?: parent)
                    sourozenci = mapa?.podzanry.orEmpty().filter { it.parent == parent && it.id != arg }
                        .map { KatalogRepository.Stitek("podzanr", it.id, it.label) }
                } else {
                    sourozenci = mapa?.podzanry.orEmpty().filter { it.nalada && it.id != arg }
                        .map { KatalogRepository.Stitek("podzanr", it.id, it.label) }
                }
            }
            "rok" -> {
                nadpis = "Rok $arg"
                val r = arg.toIntOrNull() ?: 0
                val letos = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                sourozenci = listOf(-3, -2, -1, 1, 2, 3).map { r + it }.filter { it in 1900..letos }
                    .map { KatalogRepository.Stitek("rok", it.toString(), it.toString()) }
            }
            "zeme" -> nadpis = java.util.Locale("", arg).getDisplayCountry(java.util.Locale("cs")).ifBlank { arg }
            else -> nadpis = repo.nazevTematu(arg)?.let { "# $it" } ?: "Téma"
        }
        _state.update { it.copy(nadpis = nadpis, rodic = rodic, sourozenci = sourozenci) }
    }

    private suspend fun pruhy() {
        val bez = KatalogPrefs.bool(ctx, KatalogPrefs.BEZ_DETSKYCH)
        val definice = buildList {
            if (KatalogPrefs.bool(ctx, KatalogPrefs.PRUH_TOP)) add(Triple("Nejlépe hodnocené", "hodnoceni", false to false))
            if (KatalogPrefs.bool(ctx, KatalogPrefs.PRUH_KLENOTY) && druh != "rok") add(Triple("Skryté klenoty", "klenoty", false to false))
            if (KatalogPrefs.bool(ctx, KatalogPrefs.PRUH_NOVINKY) && druh != "rok") add(Triple("Novinky", "novinky", false to false))
            if (KatalogPrefs.bool(ctx, KatalogPrefs.PRUH_CESKE) && druh != "zeme") add(Triple("České", "hodnoceni", false to true))
            if (KatalogPrefs.bool(ctx, KatalogPrefs.PRUH_SERIALY)) add(Triple("Seriály", "hodnoceni", true to false))
        }
        val vysledky = definice.map { (nazev, razeni, typ) ->
            viewModelScope.launch {
                val s = repo.objevuj(druh, arg, razeni, 1, serialy = typ.first, jenCeske = typ.second, bezDetskych = bez)
                val tituly = s?.tituly.orEmpty()
                if (tituly.isEmpty()) return@launch
                // pruhy dorazí v libovolném pořadí — drž pořadí z definice
                _state.update { st ->
                    val nove = (st.pruhy + Pruh(nazev, if (typ.first || typ.second) null else razeni, tituly))
                        .sortedBy { p -> definice.indexOfFirst { it.first == p.nazev } }
                    st.copy(pruhy = nove)
                }
            }
        }
        vysledky.forEach { it.join() }
    }

    fun prepniCz() { _state.update { it.copy(jenCz = !it.jenCz) } }

    fun setRazeni(r: String) { _state.update { it.copy(razeni = r) }; resetVse() }
    fun setSerialy(v: Boolean) { _state.update { it.copy(serialy = v) }; resetVse() }
    fun setCeske(v: Boolean) { _state.update { it.copy(ceske = v) }; resetVse() }
    fun setDekada(d: Int?) { _state.update { it.copy(dekada = d) }; resetVse() }

    /** „vše ›" u pruhu: spodní výpis přepne na stejné řazení (filmy). */
    fun vseJako(razeni: String) {
        _state.update { it.copy(razeni = razeni, serialy = false) }
        resetVse()
    }

    private fun resetVse() {
        vseJob?.cancel()
        _state.update { it.copy(vse = emptyList(), stranka = 0, stranek = 1, nacitam = false) }
        nactiDalsi()
    }

    fun nactiDalsi() {
        val s = _state.value
        if (s.nacitam || s.stranka >= s.stranek || druh.isBlank()) return
        _state.update { it.copy(nacitam = true) }
        vseJob = viewModelScope.launch {
            val (od, doRoku) = when (val d = s.dekada) {
                null -> null to null
                0 -> null to 1959
                2020 -> 2020 to null
                else -> d to d + 9
            }
            val r = repo.objevuj(
                druh, arg, s.razeni, s.stranka + 1, serialy = s.serialy, jenCeske = s.ceske,
                rokOd = od, rokDo = doRoku, bezDetskych = KatalogPrefs.bool(ctx, KatalogPrefs.BEZ_DETSKYCH),
            )
            _state.update {
                if (r == null) it.copy(nacitam = false)
                else it.copy(vse = it.vse + r.tituly, stranka = r.stranka, stranek = r.stranek, nacitam = false)
            }
        }
    }
}

/** Pro tebe (serverové řádky, parita s webem). */
@HiltViewModel
class ProTebeKatalogViewModel @Inject constructor(
    private val repo: KatalogRepository,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {
    data class State(
        val radky: List<KatalogRepository.Radek> = emptyList(),
        val loading: Boolean = true,
        val chyba: Boolean = false,
        val jenHned: Boolean = false,
        val zamichat: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, chyba = false) }
            val r = repo.proTebe(
                zamichat = _state.value.zamichat,
                radku = KatalogPrefs.int(ctx, KatalogPrefs.PT_RADKU, 5),
                podzanru = KatalogPrefs.int(ctx, KatalogPrefs.PT_PODZANRU, 3),
                chci = KatalogPrefs.bool(ctx, KatalogPrefs.PT_CHCI),
                kurator = KatalogPrefs.bool(ctx, KatalogPrefs.PT_KURATOR),
            )
            _state.update { it.copy(radky = r.orEmpty(), loading = false, chyba = r == null) }
        }
    }

    fun zamichat() { _state.update { it.copy(zamichat = it.zamichat + 1) }; load() }
    fun prepniJenHned() { _state.update { it.copy(jenHned = !it.jenHned) } }
}

/** Štítky na kartě filmu (žánry, podžánry, rok, země, témata) z profilu titulu. */
@HiltViewModel
class KatalogStitkyViewModel @Inject constructor(
    private val repo: KatalogRepository,
) : ViewModel() {
    private val _stitky = MutableStateFlow<Map<String, List<KatalogRepository.Stitek>>>(emptyMap())
    val stitky: StateFlow<Map<String, List<KatalogRepository.Stitek>>> = _stitky.asStateFlow()

    fun nacti(klic: String) {
        if (_stitky.value.containsKey(klic)) return
        viewModelScope.launch {
            val p = repo.profily(listOf(klic))[klic] ?: return@launch
            val mapa = repo.mapa()
            val label: (String) -> String = { id -> mapa?.hlavni?.firstOrNull { it.id == id }?.label ?: id }
            val labelPod: (String) -> String = { id -> mapa?.podzanry?.firstOrNull { it.id == id }?.label ?: id }
            val cs = java.util.Locale("cs")
            val out = buildList {
                p.rok?.let { add(KatalogRepository.Stitek("rok", it.toString(), it.toString())) }
                p.zeme.take(3).forEach {
                    add(KatalogRepository.Stitek("zeme", it, java.util.Locale("", it).getDisplayCountry(cs).ifBlank { it }))
                }
                p.kanon.forEach { add(KatalogRepository.Stitek("zanr", it, label(it))) }
                p.podzanry.forEach { add(KatalogRepository.Stitek("podzanr", it, labelPod(it))) }
                p.temata.take(8).forEach { (id, n) -> add(KatalogRepository.Stitek("tema", id.toString(), "# $n")) }
            }
            _stitky.update { it + (klic to out) }
        }
    }
}

/** LABYRINT — stránka „Česky" v liště: celá Filmotéka s českou/slovenskou stopou po hlavních žánrech
 *  (user 2026-10-08: „nebo možná rovnou sekci v liště s českým dabingem"). */
@HiltViewModel
class CeskyViewModel @Inject constructor(
    private val repo: KatalogRepository,
) : ViewModel() {
    data class State(
        val radky: List<Pair<String, List<KatalogRepository.Titul>>> = emptyList(),
        val celkem: Int = 0,
        val loading: Boolean = true,
        val chyba: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val t = repo.vlastni("vse", "", jenCz = true)
            if (t == null) { _state.update { it.copy(loading = false, chyba = true) }; return@launch }
            val radky = t.groupBy { it.hlavni ?: "Ostatní" }.toList()
                .sortedWith(compareBy<Pair<String, List<KatalogRepository.Titul>>> { it.first == "Ostatní" }.thenByDescending { it.second.size })
            _state.value = State(radky = radky, celkem = t.size, loading = false)
        }
    }
}

/** Hledat: žánry, podžánry a témata k dotazu (tituly a lidi hledá appka sama). Debounce 400 ms. */
@HiltViewModel
class KatalogHledatViewModel @Inject constructor(
    private val repo: KatalogRepository,
) : ViewModel() {
    private val _stitky = MutableStateFlow<List<KatalogRepository.Stitek>>(emptyList())
    val stitky: StateFlow<List<KatalogRepository.Stitek>> = _stitky.asStateFlow()
    private var job: Job? = null
    private var posledni = ""

    fun dotaz(q: String) {
        val t = q.trim()
        if (t == posledni) return
        posledni = t
        job?.cancel()
        if (t.length < 2) { _stitky.value = emptyList(); return }
        job = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _stitky.value = repo.hledatStitky(t).orEmpty()
        }
    }
}
