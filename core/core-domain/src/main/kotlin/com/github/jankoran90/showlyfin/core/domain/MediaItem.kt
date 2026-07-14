package com.github.jankoran90.showlyfin.core.domain

data class MediaItem(
    val traktId: Long,
    val tmdbId: Long?,
    val imdbId: String?,
    val title: String,
    val year: Int?,
    val overview: String?,
    val rating: Float?,
    val genres: List<String>?,
    val type: MediaType,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val titleCz: String? = null,
    val overviewCz: String? = null,
    /**
     * COUCH (SHW-88) — číselná věková hranice (roky) z TMDB certifikace (release_dates / content_ratings,
     * preferováno CZ→DE→GB→US). null = neznámá. Plní [enrich] jen když je aktivní věkový strop profilu
     * (jinak zbytečné síťové volání). Použití: [ContentAgeGate] pro dětský profil.
     */
    val certificationAge: Int? = null,
    /**
     * CINEMATHEQUE (SHW-90) F2 — kódy zemí původu (ISO-3166-1 alpha-2, VELKÁ písmena). null = neznámé.
     * Plní [enrich] z TMDB details (u SHOW `origin_country` ∪ `production_countries`, u MOVIE
     * `production_countries`). Použití: osa Země Filmotéky (regionsOf v CinematographyRegion).
     */
    val originCountries: List<String>? = null,
    /**
     * PASSPORT (SHW-93) A1 — originální název z TMDB `original_title`/`original_name` (u ne-latinkových titulů
     * asijské písmo). Plní [enrich]. Používá se v [displayTitle] jako poslední čitelný kandidát a v hledání
     * zdrojů/titulků (A2). null = neznámý.
     */
    val originalTitle: String? = null,
) {
    fun posterUrl(size: String = "w342") = posterPath?.let { "https://image.tmdb.org/t/p/$size$it" }
    fun backdropUrl(size: String = "w780") = backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }

    /**
     * PASSPORT (SHW-93) A1 — název k ZOBRAZENÍ: první ČITELNÝ (latinkový) kandidát v pořadí
     * CZ překlad → raw (Trakt, obv. anglický) → originál. Když je vše ne-latinka (žádný latinkový kandidát
     * není), vrátí aspoň CZ/raw (lepší než prázdno). Sjednocuje dřív roztroušené `titleCz ?: title`.
     */
    val displayTitle: String
        get() {
            val cz = titleCz?.takeIf { it.isNotBlank() }
            val raw = title.takeIf { it.isNotBlank() }
            val orig = originalTitle?.takeIf { it.isNotBlank() }
            return sequenceOf(cz, raw, orig).filterNotNull().firstOrNull { !it.looksNonLatin() }
                ?: cz ?: title
        }
}
