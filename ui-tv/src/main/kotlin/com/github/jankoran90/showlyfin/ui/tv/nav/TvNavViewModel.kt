package com.github.jankoran90.showlyfin.ui.tv.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.ui.tv.TvDestination

/**
 * TENFOOT (SHW-87) — back stack TV shellu drží ViewModel (ne `remember`), aby PŘEŽIL rekreaci Activity.
 *
 * Bez toho: na TV boxu appka běžně jde na pozadí (přepnutí HDMI vstupu / launcher) a systém Activity
 * recreatuje → `remember { mutableStateListOf(Home) }` se resetoval na [TvDestination.Home] → jakékoli
 * další „Zpět" appku rovnou ukončilo / vyhodilo na domovskou obrazovku boxu místo kroku zpět (user
 * feedback 2026-07-12: „zpět má jít o jeden krok, ne úplný vyhazov domů — telefon to už má vyřešené").
 * Retained ViewModel navigační zásobník zachová → „Zpět" je vždy jeden krok po stacku.
 */
class TvNavViewModel : ViewModel() {

    /** Navigační zásobník; kořen = [TvDestination.Home]. Snapshot state → Compose se překreslí. */
    val backStack = mutableStateListOf<TvDestination>(TvDestination.Home)

    val current: TvDestination get() = backStack.last()

    /** Lze jít zpět jen když nejsme na kořeni (na kořeni BACK propadne = ukončí appku, což je správné). */
    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(dest: TvDestination) { backStack.add(dest) }

    /** Zpět o JEDEN krok (nikdy neskáče na kořen naráz). */
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
}
