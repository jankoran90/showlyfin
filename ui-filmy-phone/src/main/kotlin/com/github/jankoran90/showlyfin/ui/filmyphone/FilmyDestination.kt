package com.github.jankoran90.showlyfin.ui.filmyphone

import android.content.Context
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

/**
 * CELLULOID (SHW-98) — lehké app-level preference telefonního shellu (uloženy v `trakt_prefs`, jako ostatní
 * Filmy prefs). Zatím: „Filmotéka jako výchozí obrazovka" (user 2026-07-18, konfig jen pro dospělý účet) —
 * appka se po otevření otevře na Filmotéce a v menu je nahoře. Jednoduché SharedPreferences, bez VM.
 */
object FilmyShellPrefs {
    private const val PREFS = "trakt_prefs"
    const val KEY_DEFAULT_FILMOTEKA = "filmy_default_filmoteka"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun defaultFilmoteka(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DEFAULT_FILMOTEKA, false)
    fun setDefaultFilmoteka(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DEFAULT_FILMOTEKA, value).apply()

    /** Výchozí sekce při otevření appky. */
    fun startSection(ctx: Context): FilmySection =
        if (defaultFilmoteka(ctx)) FilmySection.FILMOTEKA else FilmySection.HOME

    /** Pořadí sekcí skupiny „Objevování" v draweru — Filmotéka nahoře, když je výchozí. */
    fun discoverOrder(filmotekaFirst: Boolean): List<FilmySection> =
        if (filmotekaFirst)
            listOf(FilmySection.FILMOTEKA, FilmySection.HOME, FilmySection.FOR_YOU, FilmySection.GEMS, FilmySection.SEARCH)
        else
            listOf(FilmySection.HOME, FilmySection.FOR_YOU, FilmySection.FILMOTEKA, FilmySection.GEMS, FilmySection.SEARCH)
}
