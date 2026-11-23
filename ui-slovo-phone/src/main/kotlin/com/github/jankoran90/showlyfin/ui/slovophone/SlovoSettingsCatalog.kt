package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.jankoran90.showlyfin.core.domain.normalizeForSearch
import com.github.jankoran90.showlyfin.feature.listen.ui.CastTargetSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.HiddenPodcastsSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.LinguaQuotaSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenOfflineSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.ListenOrderSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoverySettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastLinksSettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.PodcastVideoQualitySettingsSection
import com.github.jankoran90.showlyfin.feature.listen.ui.SpeakerSettingsSection

/**
 * AMPHORA F6 (NA1, kánon ui-canon „Nastavení") — kategorie rozcestníku Nastavení Slova.
 * Pořadí = pořadí dlaždic. Obsah kategorie = sekce z [slovoSettingEntries] se stejnou kategorií.
 */
internal enum class SlovoSettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    PLAYER("Přehrávač", "Rychlost, spánek, zvuk, záložky, Android Auto, statistiky", Icons.Rounded.PlayCircle),
    HOME("Domů a vzhled", "Barvy, písmo, řady na Domů, detaily a přechody, spodní lišta, hledání", Icons.Rounded.Home),
    LIBRARY("Knihovna a zdroje", "Knihovna, Audioteka, objevování a skryté pořady", Icons.AutoMirrored.Rounded.LibraryBooks),
    DOWNLOADS("Stahování", "Stažené díly a knihy v telefonu", Icons.Rounded.Download),
    DEVICES("Zařízení", "TV, Zenbook, repro (receiver), Jellyfin", Icons.Rounded.Devices),
    KIDS("Děti a profily", "Zobrazení pro Děti, dětský telefon, samo stahování", Icons.Rounded.ChildCare),
    ACCOUNT("Účet", "Přihlášení k audioknihám (Audiobookshelf)", Icons.Rounded.AccountCircle),
    ABOUT("O aplikaci", "Verze a aktualizace", Icons.Rounded.Info),
}

/**
 * Jedna sbalovací sekce Nastavení. [keywords] = slova z popisků voleb uvnitř (fulltext rozcestníku);
 * [adultOnly] = dětský profil ji nevidí (audit dětí 2026-10-12: „Nastavení jen co se netýká obsahu").
 */
internal class SlovoSettingEntry(
    val title: String,
    val icon: ImageVector,
    val category: SlovoSettingsCategory,
    val keywords: String,
    val adultOnly: Boolean = true,
    val content: @Composable ColumnScope.() -> Unit,
) {
    /** Předpočítaný text pro hledání (bez diakritiky, malými). */
    val searchText: String by lazy {
        "$title ${category.title} $keywords".normalizeForSearch()
    }
}

/** Každé slovo dotazu musí být v titulku, kategorii nebo klíčových slovech (pořadí nehraje roli). */
internal fun SlovoSettingEntry.matches(query: String): Boolean {
    val words = query.normalizeForSearch().split(' ').filter { it.isNotBlank() }
    return words.isEmpty() || words.all { searchText.contains(it) }
}

internal val slovoSettingEntries: List<SlovoSettingEntry> = listOf(
    // ── Přehrávač
    SlovoSettingEntry(
        "Přehrávání a zvuk", Icons.Rounded.PlayCircle, SlovoSettingsCategory.PLAYER,
        "rychlost výchozí pamatovat skok zpět vpřed přetočit po pauze spánek časovač ztišit zatřesení " +
            "usnul vyrovnání hlasitosti přeskakovat ticho zesílit hlas zvuk mini přehrávač barva obalu fronta swipe tah rozposlouchané",
    ) { SlovoPlayerSettingsSection() },
    SlovoSettingEntry(
        "Záložky", Icons.Rounded.Bookmarks, SlovoSettingsCategory.PLAYER,
        "záložka poznámka dvojklik sluchátka pípnout značky časová osa",
    ) { SlovoBookmarkSettingsSection() },
    SlovoSettingEntry(
        "Android Auto", Icons.Rounded.DirectionsCar, SlovoSettingsCategory.PLAYER,
        "auto auto karty pokračovat nové díly podcasty knihy knihovny samostatné karty",
    ) { SlovoAutoSettingsSection() },
    SlovoSettingEntry(
        "Statistiky poslechu", Icons.Rounded.BarChart, SlovoSettingsCategory.PLAYER,
        "statistiky poslech čas hodiny dny v řadě slovo za rok rok shrnutí počítat vypnout",
    ) { SlovoStatsSettingsSection() },
    SlovoSettingEntry(
        "Kvalita videa", Icons.Rounded.HighQuality, SlovoSettingsCategory.PLAYER,
        "video kvalita rozlišení youtube kanál počet epizod",
    ) { PodcastVideoQualitySettingsSection() },
    SlovoSettingEntry(
        "AI překlad YouTube titulků", Icons.Rounded.Translate, SlovoSettingsCategory.PLAYER,
        "titulky překlad ai youtube limit kvóta mozek",
    ) { LinguaQuotaSettingsSection() },

    // ── Domů a vzhled
    SlovoSettingEntry(
        "Záložky na Domů", Icons.Rounded.Tab, SlovoSettingsCategory.HOME,
        "záložky domů přehled knihy pořady nové díly stažené pořadí skrýt skrytá první otevřít",
    ) { SlovoHomeTabsSection() },
    SlovoSettingEntry(
        "Vzhled", Icons.Rounded.Palette, SlovoSettingsCategory.HOME,
        "barva akcent pozadí tónování světlost kontrast sytost písmo patkové newsreader velikost textu motiv amoled",
        adultOnly = false,
    ) { SlovoAppearanceSection() },
    SlovoSettingEntry(
        "Zobrazení Domů a pořadů", Icons.Rounded.ViewAgenda, SlovoSettingsCategory.HOME,
        "rozložení seznam mřížka sloupce rozposlouchané sbalit pod kartu ťuk na díl otevřít pořad rovnou přehrát " +
            "série kniha cover obal popis epizody časový údaj zbývá poslechnuto",
        adultOnly = false,
    ) { SlovoHomeLayoutSection() },
    SlovoSettingEntry(
        "Detaily a přechody", Icons.Rounded.Animation, SlovoSettingsCategory.HOME,
        "detail hlavička obal velký rozmazaný pozadí barva z obalu animace přechody posun prolnutí vypnout",
        adultOnly = false,
    ) { SlovoDetailLookSection() },
    SlovoSettingEntry(
        "Řady na Domů", Icons.Rounded.ViewDay, SlovoSettingsCategory.HOME,
        "řady nové díly další v sérii krátké díly nově v knihovně pořadí skrýt zobrazit",
    ) { SlovoHomeRowsSection() },
    SlovoSettingEntry(
        "Spodní lišta a nabídka Víc", Icons.Rounded.Menu, SlovoSettingsCategory.HOME,
        "lišta navigace záložky víc avatar pořadí ťuk na začátek nahoru",
    ) { SlovoNavOrderSection() },
    SlovoSettingEntry(
        "Hledání", Icons.Rounded.Search, SlovoSettingsCategory.HOME,
        "hledat vyhledávání audioteka nové pořady díly nedávná hledání",
    ) { SlovoSearchSettingsSection() },

    // ── Knihovna a zdroje
    SlovoSettingEntry(
        "Knihovna", Icons.AutoMirrored.Rounded.LibraryBooks, SlovoSettingsCategory.LIBRARY,
        "knihy pořady řazení filtr seznam mřížka obalů velikost řádku náhledu ukázat",
        adultOnly = false,
    ) { SlovoLibrarySection() },
    SlovoSettingEntry(
        "Řazení zdrojů", Icons.Rounded.Sort, SlovoSettingsCategory.LIBRARY,
        "pořadí knihoven audioknihy podcasty zobrazit první audiobookshelf",
    ) { ListenOrderSettingsSection() },
    SlovoSettingEntry(
        "Katalog Audioteky", Icons.Rounded.Storefront, SlovoSettingsCategory.LIBRARY,
        "audioteka nákup koupit google pay platba dovoz návrhy polička odhlásit",
    ) { SlovoAudiotekaSection() },
    SlovoSettingEntry(
        "Objevování podcastů", Icons.Rounded.Explore, SlovoSettingsCategory.LIBRARY,
        "timeline časová osa rozsah datum popis epizody řádků země režim zdroj karet objevit",
    ) { PodcastDiscoverySettingsSection() },
    SlovoSettingEntry(
        "Skryté pořady", Icons.Rounded.VisibilityOff, SlovoSettingsCategory.LIBRARY,
        "skryté schované obnovit sledované timeline",
    ) { HiddenPodcastsSettingsSection() },
    SlovoSettingEntry(
        "Propojené pořady", Icons.Rounded.Link, SlovoSettingsCategory.LIBRARY,
        "propojené audio video spojit zrušit",
    ) { PodcastLinksSettingsSection() },

    // ── Stahování
    SlovoSettingEntry(
        "Stažené / offline", Icons.Rounded.Download, SlovoSettingsCategory.DOWNLOADS,
        "stažené offline stáhnout smazat epizody nejnovější nahoře místo",
    ) { ListenOfflineSettingsSection() },

    // ── Zařízení
    SlovoSettingEntry(
        "Přehrávání na TV/Zenbooku", Icons.Rounded.Cast, SlovoSettingsCategory.DEVICES,
        "tv televize zenbook cast poslat video výchozí zařízení na tv",
    ) { CastTargetSettingsSection() },
    SlovoSettingEntry(
        "Přehrávání na repro (receiver)", Icons.Rounded.Speaker, SlovoSettingsCategory.DEVICES,
        "repro reproduktor receiver chromecast vstup zesilovač co hraje",
    ) { SpeakerSettingsSection() },
    // user 2026-09-17: appka neměla ŽÁDNOU Jellyfin login obrazovku → cast na TV z YouTube nešel.
    SlovoSettingEntry(
        "Jellyfin (videa Na výbornou, TV přes Jellyfin)", Icons.Rounded.LiveTv, SlovoSettingsCategory.DEVICES,
        "jellyfin přihlášení server heslo jméno na výbornou",
    ) { SlovoJellyfinSection() },

    // ── Děti a profily
    SlovoSettingEntry(
        "Zobrazení pro Děti", Icons.Rounded.GridView, SlovoSettingsCategory.KIDS,
        "děti dětský velikost obrázků dlaždice sloupce názvy pod obrázky otevře na poslech domů pro rodiče",
    ) { SlovoKidsDisplaySection() },
    SlovoSettingEntry(
        "Dětský telefon", Icons.Rounded.ChildCare, SlovoSettingsCategory.KIDS,
        "děti dětský režim kiosek wi-fi wifi samo stahovat odebrané smazat profil",
    ) { SlovoKioskSection() },

    // ── Účet
    SlovoSettingEntry(
        "Účet / Audioknihy", Icons.Rounded.AccountCircle, SlovoSettingsCategory.ACCOUNT,
        "účet přihlášení odhlásit server heslo jméno audiobookshelf abs",
    ) { SlovoAccountSection() },

    // ── O aplikaci
    SlovoSettingEntry(
        "O aplikaci", Icons.Rounded.Info, SlovoSettingsCategory.ABOUT,
        "verze aktualizace aktualizovat ota co je nového automatické",
        adultOnly = false,
    ) { SlovoAboutSection() },
)
