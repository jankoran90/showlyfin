package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.feature.discover.podcasts.PodcastPrefs

/**
 * AMFITEÁTR (FLM-05) F4 — blok Nastavení → Podcasty v telefonu (parita s TV `TvPodcastsSettingsBlock`):
 * řady sekce, počty, kvalita videa (sdílená se Slovem) a nabídka hledání na YouTube.
 */
@Composable
fun FilmyPodcastsSection() {
    val prefs = LocalContext.current.getSharedPreferences("trakt_prefs", Context.MODE_PRIVATE)
    var rows by remember { mutableStateOf(PodcastPrefs.rows(prefs)) }
    var perSource by remember { mutableStateOf(PodcastPrefs.perSource(prefs)) }
    var quality by remember { mutableStateOf(PodcastPrefs.quality(prefs)) }
    var ytOffer by remember { mutableStateOf(PodcastPrefs.searchYoutubeOffered(prefs)) }
    var layout by remember { mutableStateOf(PodcastPrefs.phoneLayout(prefs)) }
    var page by remember { mutableStateOf(PodcastPrefs.pageInFilmoteka(prefs)) }
    var nextUp by remember { mutableStateOf(PodcastPrefs.inFilmotekaNextUp(prefs)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingSectionTitle("Ve Filmotéce")
        SettingSwitchRow(
            title = "Stránka Pořady",
            subtitle = "Filmotéka · Pořady · K přehrání. Projeví se při dalším otevření Filmotéky.",
            checked = page,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.PAGE_IN_FILMOTEKA, it).apply(); page = it },
        )
        SettingChips(
            label = "Rozvržení stránky Pořady",
            subtitle = "Mřížka a seznam jdou přepnout i ikonou v liště.",
            options = PodcastPrefs.PHONE_LAYOUTS,
            selected = layout,
            labelOf = PodcastPrefs::layoutLabel,
            onSelect = { prefs.edit().putString(PodcastPrefs.PHONE_LAYOUT, it).apply(); layout = it },
        )
        SettingSwitchRow(
            title = "Pořady v „Další díly“",
            subtitle = "Rozkoukané díly pořadů i v řadě Další díly ve Filmotéce (i z hledání a z TV).",
            checked = nextUp,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.IN_FILMOTEKA_NEXTUP, it).apply(); nextUp = it },
        )
        SettingSectionTitle("Řady sekce Pořady")
        SettingSwitchRow(
            title = "Další díly",
            subtitle = "Rozkoukané díly (pozice sdílená s TV)",
            checked = rows.continueRow,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.ROW_CONTINUE, it).apply(); rows = rows.copy(continueRow = it) },
        )
        SettingSwitchRow(
            title = "Nové díly",
            subtitle = "Nejnovější díly ze všech kanálů a pořadů",
            checked = rows.newRow,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.ROW_NEW, it).apply(); rows = rows.copy(newRow = it) },
        )
        SettingChips(
            label = "Počet nových dílů",
            options = PodcastPrefs.NEW_LIMITS,
            selected = rows.newLimit,
            labelOf = { "$it" },
            onSelect = { prefs.edit().putInt(PodcastPrefs.NEW_LIMIT, it).apply(); rows = rows.copy(newLimit = it) },
        )
        SettingSwitchRow(
            title = "Kanály a pořady",
            checked = rows.channelsRow,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.ROW_CHANNELS, it).apply(); rows = rows.copy(channelsRow = it) },
        )
        SettingChips(
            label = "Dílů na kanál",
            subtitle = "Kolik posledních dílů se načte z každého kanálu (víc = pomalejší načtení)",
            options = PodcastPrefs.PER_SOURCE_OPTIONS,
            selected = perSource,
            labelOf = { "$it" },
            onSelect = { prefs.edit().putInt(PodcastPrefs.PER_SOURCE, it).apply(); perSource = it },
        )
        SettingChips(
            label = "Kvalita videa",
            subtitle = "YouTube; stejné nastavení jako ve Slovu",
            options = PodcastPrefs.QUALITIES,
            selected = quality,
            labelOf = PodcastPrefs::qualityLabel,
            onSelect = { prefs.edit().putString(PodcastPrefs.QUALITY, it).apply(); quality = it },
        )
        SettingSectionTitle("Hledání")
        SettingSwitchRow(
            title = "Nabízet hledání na YouTube",
            subtitle = "V Hledat přepínač „Hledat i na YouTube“. Pokaždé začíná vypnutý, dětský profil ho nevidí.",
            checked = ytOffer,
            onCheckedChange = { prefs.edit().putBoolean(PodcastPrefs.SEARCH_YT_OFFER, it).apply(); ytOffer = it },
        )
    }
}
