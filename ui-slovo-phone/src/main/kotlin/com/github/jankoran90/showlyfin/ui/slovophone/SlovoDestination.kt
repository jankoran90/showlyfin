package com.github.jankoran90.showlyfin.ui.slovophone

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Slovo (EXCISE/SHW-103 Fáze 4) — hlavní sekce telefonní poslechové appky. Zrcadlí strukturu
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmySection], ale jen mluvené slovo: Domů
 * (naposledy přehráno/pokračovat, viz [com.github.jankoran90.showlyfin.feature.listen.ui.HomeScreen]),
 * Poslech (audioknihy + podcasty), Objevit (katalog podcastů), Zdroje (správce zdrojů), Nastavení a Profil.
 * Profily (2026-08-15, user „profily jak jsme je používali v showlyfin") — 2 pevné profily
 * (Dospělý/Děti, vzor [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyProfileScreen]).
 * Detailové cíle drží back-stack [SlovoDetailEntry].
 */
enum class SlovoSection(val label: String, val icon: ImageVector) {
    DOMU("Domů", Icons.Rounded.Home),
    /** AMPHORA F1 (KO1) — místa spodní lišty: Knihovna (audioknihy), Pořady (Sledované + Nové), Hledat, Fronta. */
    KNIHOVNA("Knihovna", Icons.Rounded.LibraryBooks),
    PORADY("Pořady", Icons.Rounded.Podcasts),
    HLEDAT("Hledat", Icons.Rounded.Search),
    FRONTA("Fronta", Icons.AutoMirrored.Rounded.QueueMusic),
    /**
     * Dětský profil: sloučený poslech (KidsListenContent) — místo v dětské liště (Domů · Poslech · Stažené).
     * Dospělý ho nemá (AMPHORA F1: nahradily ho Knihovna a Pořady).
     */
    POSLECH("Poslech", Icons.Rounded.Headphones),
    OBJEVIT("Objevit", Icons.Rounded.Explore),
    ZDROJE("Zdroje", Icons.Rounded.RssFeed),
    /** User 2026-10-13 — „sekce stahování v sidebaru: fronta, staženo, stahování, rychlost a ETA". */
    STAHOVANI("Stahování", Icons.Rounded.Download),
    /** User 2026-11-03 — „napojit Audioteku do Slova jako katalog… samostatné hledání jako v páteři". */
    KATALOG("Katalog", Icons.Rounded.Storefront),
    /** AMPHORA F5 / PR2 — záložky s poznámkou ze všech knih a dílů. */
    ZALOZKY("Záložky", Icons.Rounded.Bookmarks),
    /** AMPHORA F8 / CH2 — statistiky poslechu a Slovo za rok. */
    STATISTIKY("Statistiky", Icons.Rounded.BarChart),
    NASTAVENI("Nastavení", Icons.Rounded.Settings),
    PROFIL("Profil", Icons.Rounded.AccountCircle),
}

/**
 * Lehké app-level preference shellu Slova (SharedPreferences `slovo_prefs`, bez VM) — zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyShellPrefs]. Zatím jen výchozí sekce; roste dle potřeby.
 */
object SlovoShellPrefs {
    private const val PREFS = "slovo_prefs"
    // Klíče zůstaly z doby draweru (pořadí Objevit/Zdroje/…) — dnes je to nabídka Víc pod avatarem.
    private const val KEY_DRAWER_ORDER = "drawer_order"
    private const val KEY_DRAWER_HIDDEN = "drawer_hidden"
    private const val KEY_BAR_ORDER = "bar_order"
    private const val KEY_BAR_HIDDEN = "bar_hidden"
    private const val KEY_RESELECT_TOP = "bar_reselect_top"
    private const val KEY_TRANSITIONS = "transitions"
    private const val KEY_HERO_BACKDROP = "hero_backdrop"
    private const val KEY_HERO_TINT = "hero_tint"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Výchozí sekce při otevření appky — Domů (user 2026-08-15: „home obrazovka, co se vždy otevře"). */
    fun startSection(@Suppress("UNUSED_PARAMETER") ctx: Context): SlovoSection = SlovoSection.DOMU

    // ── Spodní lišta (AMPHORA F1, KO1) ── Domů je vždy první a nejde skrýt; zbytek pořadí + viditelnost.
    // User 2026-11-23: Knihovna a Pořady jsou záložky Domů (Nastavení › Knihovna), v liště už nejsou.
    val barSections = listOf(SlovoSection.HLEDAT, SlovoSection.FRONTA)

    private fun savedBarOrder(ctx: Context): List<SlovoSection> = mergedOrder(prefs(ctx).getString(KEY_BAR_ORDER, null), barSections)
    private fun hiddenBar(ctx: Context): Set<SlovoSection> = hiddenOf(prefs(ctx).getStringSet(KEY_BAR_HIDDEN, emptySet()), barSections)

    /** Místa v liště dospělého profilu (Domů + viditelná ve zvoleném pořadí). */
    fun barOrder(ctx: Context): List<SlovoSection> {
        val hidden = hiddenBar(ctx)
        return listOf(SlovoSection.DOMU) + savedBarOrder(ctx).filter { it !in hidden }
    }

    fun barWithVisibility(ctx: Context): List<Pair<SlovoSection, Boolean>> {
        val hidden = hiddenBar(ctx)
        return savedBarOrder(ctx).map { it to (it !in hidden) }
    }

    fun setBarOrder(ctx: Context, order: List<SlovoSection>) {
        prefs(ctx).edit().putString(KEY_BAR_ORDER, order.joinToString(",") { it.name }).apply()
    }

    fun setBarHidden(ctx: Context, section: SlovoSection, hidden: Boolean) {
        val current = hiddenBar(ctx).toMutableSet()
        if (hidden) current.add(section) else current.remove(section)
        prefs(ctx).edit().putStringSet(KEY_BAR_HIDDEN, current.map { it.name }.toSet()).apply()
    }

    /** Místa skrytá z lišty — nabídka Víc je ukáže, ať zůstanou dostupná (revize F1). */
    fun hiddenBarSections(ctx: Context): List<SlovoSection> {
        val hidden = hiddenBar(ctx)
        return savedBarOrder(ctx).filter { it in hidden }
    }

    /** Ťuk na místo, které už je otevřené: vrátit ho na začátek (výchozí zapnuto). */
    fun reselectToTop(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_RESELECT_TOP, true)
    fun setReselectToTop(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_RESELECT_TOP, on).apply() }

    // ── AMPHORA F8 (DS2): přechody mezi obrazovkami a hlavička detailu ──
    enum class Transitions(val label: String) { FULL("Plné"), REDUCED("Jen prolnutí"), OFF("Vypnuté") }

    fun transitions(ctx: Context): Transitions =
        Transitions.entries.firstOrNull { it.name == prefs(ctx).getString(KEY_TRANSITIONS, null) } ?: Transitions.FULL
    fun setTransitions(ctx: Context, value: Transitions) { prefs(ctx).edit().putString(KEY_TRANSITIONS, value.name).apply() }

    /** Rozmazaný obal za hlavičkou detailu pořadu / knihy (výchozí zapnuto). */
    fun heroBackdrop(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HERO_BACKDROP, true)
    fun setHeroBackdrop(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_HERO_BACKDROP, on).apply() }

    /** Nádech barvy z obalu v hlavičce detailu (výchozí zapnuto). */
    fun heroTint(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HERO_TINT, true)
    fun setHeroTint(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_HERO_TINT, on).apply() }

    // ── Nabídka Víc pod avatarem (dřív drawer): Objevit, Zdroje, Stahování, Katalog ──
    private val reorderableSections = listOf(SlovoSection.OBJEVIT, SlovoSection.ZDROJE, SlovoSection.STAHOVANI, SlovoSection.KATALOG, SlovoSection.ZALOZKY, SlovoSection.STATISTIKY)

    private fun savedOrder(ctx: Context): List<SlovoSection> = mergedOrder(prefs(ctx).getString(KEY_DRAWER_ORDER, null), reorderableSections)
    private fun hiddenSections(ctx: Context): Set<SlovoSection> = hiddenOf(prefs(ctx).getStringSet(KEY_DRAWER_HIDDEN, emptySet()), reorderableSections)

    /** Pro editor v Nastavení — VŠECHNY položky Víc ve svém pořadí + jestli jsou skryté. */
    fun reorderableWithVisibility(ctx: Context): List<Pair<SlovoSection, Boolean>> {
        val hidden = hiddenSections(ctx)
        return savedOrder(ctx).map { it to (it !in hidden) }
    }

    fun setDrawerOrder(ctx: Context, order: List<SlovoSection>) {
        prefs(ctx).edit().putString(KEY_DRAWER_ORDER, order.joinToString(",") { it.name }).apply()
    }

    fun setSectionHidden(ctx: Context, section: SlovoSection, hidden: Boolean) {
        val current = hiddenSections(ctx).toMutableSet()
        if (hidden) current.add(section) else current.remove(section)
        prefs(ctx).edit().putStringSet(KEY_DRAWER_HIDDEN, current.map { it.name }.toSet()).apply()
    }

    /** Položky nabídky Víc (bez skrytých), pod nimi vždy Nastavení. */
    fun moreOrder(ctx: Context): List<SlovoSection> {
        val hidden = hiddenSections(ctx)
        return savedOrder(ctx).filter { it !in hidden }
    }

    /** Uložené pořadí + nové položky (přibylé v novější appce) na konec. */
    private fun mergedOrder(saved: String?, all: List<SlovoSection>): List<SlovoSection> {
        val list = saved?.split(",")?.mapNotNull { name -> all.firstOrNull { it.name == name } }?.distinct().orEmpty()
        return list + all.filter { it !in list }
    }

    private fun hiddenOf(names: Set<String>?, all: List<SlovoSection>): Set<SlovoSection> =
        names.orEmpty().mapNotNull { name -> all.firstOrNull { it.name == name } }.toSet()
}
