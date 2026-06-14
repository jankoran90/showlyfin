package com.github.jankoran90.showlyfin.data.tmdb.model

/**
 * VANTAGE (SHW-48) — rolově konkrétní tvorba osoby z TMDB `/person/{id}/movie_credits`.
 * Vrací herecké role ([cast]) i štáb ([crew] s `job`/`department`), takže proklik na konkrétní
 * osobu otevře JEN tu část její tvorby, která odpovídá roli (režisér → režíroval, producent →
 * produkoval, skladatel → hudba …) — ne generický mix přes `with_people`.
 */
data class TmdbPersonMovieCredits(
    val cast: List<TmdbPersonCredit> = emptyList(),
    val crew: List<TmdbPersonCredit> = emptyList(),
)

/** Jeden záznam tvorby (film + role osoby v něm). Mapuje se na [TmdbSearchMovieItem] pro karty. */
data class TmdbPersonCredit(
    val id: Long = 0L,
    val title: String? = null,
    val original_title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val vote_average: Float? = null,
    val popularity: Float? = null,
    val genre_ids: List<Int>? = null,
    // crew-only: konkrétní práce na filmu (Director, Producer, Original Music Composer, …) + obor.
    val job: String? = null,
    val department: String? = null,
) {
    fun toMovieItem(): TmdbSearchMovieItem = TmdbSearchMovieItem(
        id = id,
        title = title,
        original_title = original_title,
        overview = overview,
        poster_path = poster_path,
        backdrop_path = backdrop_path,
        release_date = release_date,
        vote_average = vote_average,
        popularity = popularity,
        genre_ids = genre_ids,
    )
}

/**
 * Role osoby ve tvorbě = určuje, kterou část jejích TMDB kreditů ukázat. [GENERIC] = neznámá role →
 * fallback na `with_people` (cast i crew dohromady). [czLabel] = český titulek do listu tvorby.
 */
enum class PersonRole { ACTING, DIRECTING, WRITING, CINEMATOGRAPHY, PRODUCING, COMPOSING, GENERIC }

fun PersonRole.czLabel(): String = when (this) {
    PersonRole.ACTING -> "Herecká tvorba"
    PersonRole.DIRECTING -> "Režie"
    PersonRole.WRITING -> "Scénář"
    PersonRole.CINEMATOGRAPHY -> "Kamera"
    PersonRole.PRODUCING -> "Produkce"
    PersonRole.COMPOSING -> "Hudba"
    PersonRole.GENERIC -> "Tvorba"
}
