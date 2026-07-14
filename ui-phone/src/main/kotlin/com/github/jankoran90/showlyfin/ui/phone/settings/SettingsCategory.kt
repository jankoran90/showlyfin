package com.github.jankoran90.showlyfin.ui.phone.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Kategorie Nastavení = dlaždice rozcestníku ([SettingsHome]) → podstránka ([SettingsCategoryScreen]).
 * CHORUS Osa 1 (sourodý fleet, kánon hubme): 9 scrollovaných sekcí sjednoceno do 7 kategorií.
 * [keywords] = fulltext pro hledání v rozcestníku (matchesQuery přes normalizeForSearch —
 * nezávisle na diakritice/velikosti). Gating (isAdmin / zámky šablony) zůstává UVNITŘ podstránek.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val keywords: String,
) {
    CONNECTION(
        title = "Připojení a účty",
        subtitle = "Jellyfin, RealDebrid, Audiobookshelf",
        icon = Icons.Filled.Link,
        keywords = "jellyfin trakt realdebrid rd audiobookshelf abs server přihlášení odhlásit účet připojení heslo uploader",
    ),
    STREAMING(
        title = "Streamování",
        subtitle = "Uploader, Stremio filtry, hlasitost filmu",
        icon = Icons.Filled.Movie,
        keywords = "stremio torrent zdroje kvalita filtr streamování uploader sdílej remux drc hlasitost normalizér film realdebrid",
    ),
    CURATOR(
        title = "Kurátor",
        subtitle = "Doporučení „Pro tebe“, míra objevování",
        icon = Icons.Filled.AutoAwesome,
        keywords = "kurátor doporučení pro tebe objevování překvapení jistota nálada žánr druh mozek ai umělá inteligence recommend auteur",
    ),
    LISTEN(
        title = "Poslech",
        subtitle = "Podcasty, audioknihy, přehrávání",
        icon = Icons.Filled.Headphones,
        keywords = "podcast audiokniha poslech rss youtube čt přehrávání pořadí objevování stažené offline propojené skryté kvalita videa audiobookshelf",
    ),
    APPEARANCE(
        title = "Vzhled",
        subtitle = "Motiv, detaily zobrazení, pořadí sekcí",
        icon = Icons.Filled.Palette,
        keywords = "vzhled motiv téma barvy tmavý světlý amoled pořadí sekcí záložky podsekce detaily zobrazení",
    ),
    PROFILES(
        title = "Profily",
        subtitle = "Správa profilů rodiny",
        icon = Icons.Filled.People,
        keywords = "profil profily děti dětský šablona omezení pin admin správce avatar výchozí přidat",
    ),
    HOME_THEATER(
        title = "Domácí sestava",
        subtitle = "AVR receiver, cíl castu na TV",
        icon = Icons.Filled.Speaker,
        keywords = "avr receiver domácí sestava hlasitost zesilovač televize tv box cast na tv zenbook párování",
    ),
    SYSTEM(
        title = "Systém",
        subtitle = "Aktualizace, ladění, živý log",
        icon = Icons.Filled.Tune,
        keywords = "systém aktualizace update verze ladění debug log živý chyba diagnostika",
    ),
}
