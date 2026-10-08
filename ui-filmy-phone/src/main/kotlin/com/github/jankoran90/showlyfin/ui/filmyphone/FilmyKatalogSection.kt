package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * LABYRINT (FLM-04) — blok Nastavení → Objevování: Objevit, stránky žánrů, Pro tebe, štítky.
 * Parita s webem (Nastavení → „Objevování katalogu", „Pro tebe", „Hledání"). Hodnoty v `trakt_prefs`
 * ([KatalogPrefs]); projeví se při dalším otevření stránky.
 */
@Composable
fun FilmyKatalogSection() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Objevit, žánry a Pro tebe")
        PrepinacKatalogu("Skrýt animované a rodinné", "V katalogu žánrů (mimo jejich vlastní žánry)", KatalogPrefs.BEZ_DETSKYCH)
        PrepinacKatalogu("Štítky na kartě filmu", "Žánry, podžánry, rok, země a témata jako odkazy", KatalogPrefs.STITKY_KARTA)
        PrepinacKatalogu("Žánry a témata v hledání", null, KatalogPrefs.HLEDAT_STITKY)
        SettingSectionTitle("Pruhy na stránce žánru")
        PrepinacKatalogu("Nejlépe hodnocené", null, KatalogPrefs.PRUH_TOP)
        PrepinacKatalogu("Skryté klenoty", "Vysoké hodnocení, málo hlasů", KatalogPrefs.PRUH_KLENOTY)
        PrepinacKatalogu("Novinky", null, KatalogPrefs.PRUH_NOVINKY)
        PrepinacKatalogu("České", null, KatalogPrefs.PRUH_CESKE)
        PrepinacKatalogu("Seriály", null, KatalogPrefs.PRUH_SERIALY)
        SettingSectionTitle("Pro tebe")
        PrepinacKatalogu("Z Chci vidět — jde hned pustit", null, KatalogPrefs.PT_CHCI)
        PrepinacKatalogu("Výběr kurátora", null, KatalogPrefs.PT_KURATOR)
        var radku by remember { mutableIntStateOf(KatalogPrefs.int(ctx, KatalogPrefs.PT_RADKU, 5)) }
        SettingChips(
            label = "Řádků „Protože se ti líbil…“",
            subtitle = "Od filmů, kterým jsi dal 9–10. Zamíchat na stránce vybere jiné.",
            options = listOf(0, 3, 5, 8), selected = radku,
            labelOf = { if (it == 0) "Žádný" else it.toString() },
            onSelect = { radku = it; KatalogPrefs.setInt(ctx, KatalogPrefs.PT_RADKU, it) },
        )
        var podzanru by remember { mutableIntStateOf(KatalogPrefs.int(ctx, KatalogPrefs.PT_PODZANRU, 3)) }
        SettingChips(
            label = "Řádků z tvých podžánrů",
            subtitle = "Podžánry, které se opakují u filmů hodnocených 8 a víc.",
            options = listOf(0, 2, 3, 5), selected = podzanru,
            labelOf = { if (it == 0) "Žádný" else it.toString() },
            onSelect = { podzanru = it; KatalogPrefs.setInt(ctx, KatalogPrefs.PT_PODZANRU, it) },
        )
    }
}

@Composable
private fun PrepinacKatalogu(title: String, subtitle: String?, key: String) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(KatalogPrefs.bool(ctx, key)) }
    SettingSwitchRow(title = title, subtitle = subtitle, checked = on, onCheckedChange = {
        on = it; KatalogPrefs.setBool(ctx, key, it)
    })
}
