package com.github.jankoran90.showlyfin.ui.tv.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.github.jankoran90.showlyfin.core.ui.TranslationIndicatorPrefs
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastPrefs

/**
 * AMFITEÁTR (FLM-05) F3 — blok „Podcasty" v TV Nastavení: které řady sekce ukazuje, kolik dílů,
 * kvalita videa (sdílená se Slovem) a stále viditelný ukazatel AI překladů.
 */
@Composable
fun TvPodcastsSettingsBlock() {
    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE)
    var rows by remember { mutableStateOf(PodcastPrefs.rows(prefs)) }
    var perSource by remember { mutableStateOf(PodcastPrefs.perSource(prefs)) }
    var quality by remember { mutableStateOf(PodcastPrefs.quality(prefs)) }
    var indicator by remember { mutableStateOf(TranslationIndicatorPrefs.enabled(prefs)) }
    var ytOffer by remember { mutableStateOf(PodcastPrefs.searchYoutubeOffered(prefs)) }

    TvSettingsBlock(title = "Podcasty") {
        TvToggleRow(
            label = "Řada Pokračovat",
            subtitle = "Rozkoukané díly (pozice sdílená s telefonem)",
            checked = rows.continueRow,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.ROW_CONTINUE, it).apply(); rows = rows.copy(continueRow = it) },
        )
        TvToggleRow(
            label = "Řada Nové díly",
            subtitle = "Nejnovější díly ze všech kanálů a pořadů",
            checked = rows.newRow,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.ROW_NEW, it).apply(); rows = rows.copy(newRow = it) },
        )
        TvOptionStepperRow(
            label = "Počet nových dílů",
            options = PodcastPrefs.NEW_LIMITS,
            selected = rows.newLimit,
            labelOf = { "$it" },
            onSelect = { prefs.edit().putInt(PodcastPrefs.NEW_LIMIT, it).apply(); rows = rows.copy(newLimit = it) },
        )
        TvToggleRow(
            label = "Řada Kanály a pořady",
            checked = rows.channelsRow,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.ROW_CHANNELS, it).apply(); rows = rows.copy(channelsRow = it) },
        )
        TvOptionStepperRow(
            label = "Dílů na kanál",
            subtitle = "Kolik posledních dílů se načte z každého kanálu (víc = pomalejší načtení)",
            options = PodcastPrefs.PER_SOURCE_OPTIONS,
            selected = perSource,
            labelOf = { "$it" },
            onSelect = { prefs.edit().putInt(PodcastPrefs.PER_SOURCE, it).apply(); perSource = it },
        )
        TvOptionStepperRow(
            label = "Kvalita videa",
            subtitle = "YouTube; stejné nastavení jako ve Slovu",
            options = PodcastPrefs.QUALITIES,
            selected = quality,
            labelOf = PodcastPrefs::qualityLabel,
            onSelect = { prefs.edit().putString(PodcastPrefs.QUALITY, it).apply(); quality = it },
        )
        TvToggleRow(
            label = "Ukazatel překladů",
            subtitle = "Průběh AI překladu titulků v rohu obrazovky (i v přehrávači)",
            checked = indicator,
            onCheckedChange = { TranslationIndicatorPrefs.set(prefs, it); indicator = it },
        )
        TvToggleRow(
            label = "Nabízet hledání na YouTube",
            subtitle = "V Hledat přepínač „Hledat i na YouTube“ (pokaždé začíná vypnutý, děti ho nemají)",
            checked = ytOffer,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.SEARCH_YT_OFFER, it).apply(); ytOffer = it },
        )
    }
}
