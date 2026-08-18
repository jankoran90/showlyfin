package com.github.jankoran90.showlyfin.ui.slovophone

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
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
    /**
     * User (2026-08-16 17:06, „Domů jedna sekce, Poslech se swipne horizontalně vedle") — Poslech
     * přestal být samostatnou sekcí draweru: je to 2. strana pageru sekce Domů. Hodnota zůstává
     * (label pro titulek lišty + redirect starých uložených stavů ve [SlovoPhoneShell]).
     */
    POSLECH("Poslech", Icons.Rounded.Headphones),
    OBJEVIT("Objevit", Icons.Rounded.Explore),
    ZDROJE("Zdroje", Icons.Rounded.RssFeed),
    NASTAVENI("Nastavení", Icons.Rounded.Settings),
    PROFIL("Profil", Icons.Rounded.AccountCircle),
}

/**
 * Lehké app-level preference shellu Slova (SharedPreferences `slovo_prefs`, bez VM) — zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyShellPrefs]. Zatím jen výchozí sekce; roste dle potřeby.
 */
object SlovoShellPrefs {
    private const val PREFS = "slovo_prefs"
    private const val KEY_DRAWER_ORDER = "drawer_order"
    private const val KEY_DRAWER_HIDDEN = "drawer_hidden"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Výchozí sekce při otevření appky — Domů (user 2026-08-15: „home obrazovka, co se vždy otevře"). */
    fun startSection(@Suppress("UNUSED_PARAMETER") ctx: Context): SlovoSection = SlovoSection.DOMU

    /**
     * User (2026-08-16 18:23–18:30, „pořadí umožníme měnit v appce a co se tam zobrazí taky, pořadí
     * si nastavim sám") — Domů je vždy první a nejde skrýt (2. strana pageru), zbytek (Objevit/Zdroje)
     * jde v Nastavení → Pořadí menu přeskládat i skrýt. Persistováno v `slovo_prefs` (bez VM, jako
     * zbytek tohohle objektu) — čte se znovu při každém překreslení draweru (viz [SlovoDrawer]), takže
     * změna v Nastavení se projeví hned při příštím otevření menu, bez restartu appky.
     */
    private val reorderableSections = listOf(SlovoSection.OBJEVIT, SlovoSection.ZDROJE)

    private fun savedOrder(ctx: Context): List<SlovoSection> {
        val saved = prefs(ctx).getString(KEY_DRAWER_ORDER, null)
            ?.split(",")
            ?.mapNotNull { name -> reorderableSections.firstOrNull { it.name == name } }
            .orEmpty()
        // Nové sekce (přibylé v novější appce), co v uloženém pořadí ještě nejsou, dopiš na konec.
        return saved + reorderableSections.filter { it !in saved }
    }

    private fun hiddenSections(ctx: Context): Set<SlovoSection> =
        prefs(ctx).getStringSet(KEY_DRAWER_HIDDEN, emptySet())
            ?.mapNotNull { name -> reorderableSections.firstOrNull { it.name == name } }
            ?.toSet()
            .orEmpty()

    /** Pro editor v Nastavení — VŠECHNY přeskládatelné sekce ve svém pořadí + jestli jsou skryté. */
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

    /** Pořadí sekcí v draweru skupiny „Poslech" (bez POSLECH — ten je 2. strana pageru Domů; bez
     * skrytých). Domů je vždy první. */
    fun drawerOrder(ctx: Context): List<SlovoSection> {
        val hidden = hiddenSections(ctx)
        return listOf(SlovoSection.DOMU) + savedOrder(ctx).filter { it !in hidden }
    }
}
