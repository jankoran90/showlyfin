package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * CELLULOID (SHW-98) Fáze 2 — hlavní sekce telefonní appky „Filmy". Zrcadlí TV shell
 * (Domů/Pro tebe/Filmotéka/Vzácné klenoty/Knihovna/Hledat/Nastavení/Profil) — bez Poslechu
 * (ten zůstává v showlyfinu). Detailové cíle (karta filmu, přehrávání) se přidají jako lehký
 * back-stack v M2.3; M2.1 řeší jen top-level přepínání.
 */
enum class FilmySection(val label: String, val icon: ImageVector) {
    HOME("Domů", Icons.Rounded.Home),
    FOR_YOU("Pro tebe", Icons.Rounded.Recommend),
    FILMOTEKA("Filmotéka", Icons.Rounded.Movie),
    GEMS("Vzácné klenoty", Icons.Rounded.Diamond),
    LIBRARY("Knihovna", Icons.Rounded.VideoLibrary),
    SEARCH("Hledat", Icons.Rounded.Search),
    SETTINGS("Nastavení", Icons.Rounded.Settings),
    PROFILE("Profil", Icons.Rounded.AccountCircle),
}
