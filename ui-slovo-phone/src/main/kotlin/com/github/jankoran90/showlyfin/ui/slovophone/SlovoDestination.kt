package com.github.jankoran90.showlyfin.ui.slovophone

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Slovo (EXCISE/SHW-103 Fáze 4) — hlavní sekce telefonní poslechové appky. Zrcadlí strukturu
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmySection], ale jen mluvené slovo: Poslech
 * (audioknihy + podcasty), Objevit (katalog podcastů), Zdroje (správce zdrojů) a Nastavení.
 * Žádný film, žádný profil/PIN (Slovo = single-user). Detailové cíle drží back-stack [SlovoDetailEntry].
 */
enum class SlovoSection(val label: String, val icon: ImageVector) {
    POSLECH("Poslech", Icons.Rounded.Headphones),
    OBJEVIT("Objevit", Icons.Rounded.Explore),
    ZDROJE("Zdroje", Icons.Rounded.RssFeed),
    NASTAVENI("Nastavení", Icons.Rounded.Settings),
}

/**
 * Lehké app-level preference shellu Slova (SharedPreferences `slovo_prefs`, bez VM) — zrcadlo
 * [com.github.jankoran90.showlyfin.ui.filmyphone.FilmyShellPrefs]. Zatím jen výchozí sekce; roste dle potřeby.
 */
object SlovoShellPrefs {
    private const val PREFS = "slovo_prefs"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Výchozí sekce při otevření appky (zatím vždy Poslech). */
    fun startSection(@Suppress("UNUSED_PARAMETER") ctx: Context): SlovoSection = SlovoSection.POSLECH

    /** Pořadí sekcí v draweru skupiny „Poslech". */
    val drawerOrder: List<SlovoSection> = listOf(SlovoSection.POSLECH, SlovoSection.OBJEVIT, SlovoSection.ZDROJE)
}
