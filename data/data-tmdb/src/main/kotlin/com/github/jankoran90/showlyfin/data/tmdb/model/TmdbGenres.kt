package com.github.jankoran90.showlyfin.data.tmdb.model

/**
 * CANVAS (SHW-47) B: statická mapa TMDB žánrů id→český název. TMDB vrací v discover/search jen
 * `genre_ids` (čísla); názvy jsou pevná malá množina → mapujeme bez síťového volání (žádná zátěž
 * pro karty). Žánry filmů a seriálů mají v TMDB částečně odlišné id (Akční/Sci-Fi…).
 */
object TmdbGenres {
    private val MOVIE = mapOf(
        28 to "Akční", 12 to "Dobrodružný", 16 to "Animovaný", 35 to "Komedie",
        80 to "Krimi", 99 to "Dokument", 18 to "Drama", 10751 to "Rodinný",
        14 to "Fantasy", 36 to "Historický", 27 to "Horor", 10402 to "Hudební",
        9648 to "Mysteriózní", 10749 to "Romantický", 878 to "Sci-Fi",
        10770 to "TV film", 53 to "Thriller", 10752 to "Válečný", 37 to "Western",
    )
    private val TV = mapOf(
        10759 to "Akční", 16 to "Animovaný", 35 to "Komedie", 80 to "Krimi",
        99 to "Dokument", 18 to "Drama", 10751 to "Rodinný", 10762 to "Dětský",
        9648 to "Mysteriózní", 10763 to "Zpravodajský", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Telenovela", 10767 to "Talk show",
        10768 to "Válečný & politický", 37 to "Western",
    )

    /** Názvy žánrů pro dané genre_ids (filmy/seriály), v pořadí, bez null. */
    fun names(ids: List<Int>?, isShow: Boolean): List<String> {
        val map = if (isShow) TV else MOVIE
        return ids?.mapNotNull { map[it] }.orEmpty()
    }
}
