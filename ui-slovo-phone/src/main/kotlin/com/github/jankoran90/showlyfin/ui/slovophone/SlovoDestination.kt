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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Výchozí sekce při otevření appky — Domů (user 2026-08-15: „home obrazovka, co se vždy otevře"). */
    fun startSection(@Suppress("UNUSED_PARAMETER") ctx: Context): SlovoSection = SlovoSection.DOMU

    /** Pořadí sekcí v draweru skupiny „Poslech" (bez POSLECH — ten je 2. strana pageru Domů). */
    val drawerOrder: List<SlovoSection> =
        listOf(SlovoSection.DOMU, SlovoSection.OBJEVIT, SlovoSection.ZDROJE)
}
