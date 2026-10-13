package com.github.jankoran90.showlyfin.ui.tv.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.ui.TranslationIndicatorPrefs
import com.github.jankoran90.showlyfin.ui.tv.podcasts.TvPodcastPrefs

/**
 * AMFITEÁTR (FLM-05) F3 — blok „Podcasty" v TV Nastavení: které řady sekce ukazuje, kolik dílů,
 * kvalita videa (sdílená se Slovem) a stále viditelný ukazatel AI překladů.
 */
@Composable
fun TvPodcastsSettingsBlock() {
    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE)
    var rows by remember { mutableStateOf(TvPodcastPrefs.rows(prefs)) }
    var perSource by remember { mutableStateOf(TvPodcastPrefs.perSource(prefs)) }
    var quality by remember { mutableStateOf(TvPodcastPrefs.quality(prefs)) }
    var indicator by remember { mutableStateOf(TranslationIndicatorPrefs.enabled(prefs)) }

    TvSettingsBlock(title = "Podcasty") {
        TvToggleRow(
            label = "Řada Pokračovat",
            subtitle = "Rozkoukané díly (pozice sdílená s telefonem)",
            checked = rows.continueRow,
            onCheckedChange = { prefs.edit().putBoolean(TvPodcastPrefs.ROW_CONTINUE, it).apply(); rows = rows.copy(continueRow = it) },
        )
        TvToggleRow(
            label = "Řada Nové díly",
            subtitle = "Nejnovější díly ze všech kanálů a pořadů",
            checked = rows.newRow,
            onCheckedChange = { prefs.edit().putBoolean(TvPodcastPrefs.ROW_NEW, it).apply(); rows = rows.copy(newRow = it) },
        )
        TvOptionStepperRow(
            label = "Počet nových dílů",
            options = TvPodcastPrefs.NEW_LIMITS,
            selected = rows.newLimit,
            labelOf = { "$it" },
            onSelect = { prefs.edit().putInt(TvPodcastPrefs.NEW_LIMIT, it).apply(); rows = rows.copy(newLimit = it) },
        )
        TvToggleRow(
            label = "Řada Kanály a pořady",
            checked = rows.channelsRow,
            onCheckedChange = { prefs.edit().putBoolean(TvPodcastPrefs.ROW_CHANNELS, it).apply(); rows = rows.copy(channelsRow = it) },
        )
        TvOptionStepperRow(
            label = "Dílů na kanál",
            subtitle = "Kolik posledních dílů se načte z každého kanálu (víc = pomalejší načtení)",
            options = TvPodcastPrefs.PER_SOURCE_OPTIONS,
            selected = perSource,
            labelOf = { "$it" },
            onSelect = { prefs.edit().putInt(TvPodcastPrefs.PER_SOURCE, it).apply(); perSource = it },
        )
        TvOptionStepperRow(
            label = "Kvalita videa",
            subtitle = "YouTube; stejné nastavení jako ve Slovu",
            options = TvPodcastPrefs.QUALITIES,
            selected = quality,
            labelOf = TvPodcastPrefs::qualityLabel,
            onSelect = { prefs.edit().putString(TvPodcastPrefs.QUALITY, it).apply(); quality = it },
        )
        TvToggleRow(
            label = "Ukazatel překladů",
            subtitle = "Průběh AI překladu titulků v rohu obrazovky (i v přehrávači)",
            checked = indicator,
            onCheckedChange = { TranslationIndicatorPrefs.set(prefs, it); indicator = it },
        )
    }
}
