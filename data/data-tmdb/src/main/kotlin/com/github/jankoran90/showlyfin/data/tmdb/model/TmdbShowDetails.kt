package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbShowDetails(
    val id: Long,
    val name: String?,
    // PASSPORT (SHW-93) A1 — originální název seriálu (u ne-latinkových asijské písmo); zdroj čitelného fallbacku.
    val original_name: String? = null,
    // SEZONA (SHW-113) f2 — PŮVODNÍ JAZYK („en", „ja", „cs"). User 2026-08-01 16:44: u dospělého profilu
    // se má hrát ORIGINÁLNÍ stopa, jenže „originál" v souboru označený nebývá — u Breaking Bad byla první
    // v pořadí německá a přehrávač ji spustil, protože žádnou preferenci jazyka nedostal. Tohle je ta
    // chybějící informace: anglický seriál → „eng", japonské anime → „jpn".
    val original_language: String? = null,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Float?,
    val first_air_date: String?,
    val number_of_seasons: Int?,
    val number_of_episodes: Int?,
    val genres: List<TmdbGenre>?,
    val status: String?,
    val tagline: String?,
    // TENFOOT WS-C (SHW-87): souhrn sezón (TMDB `tv/{id}` vrací pole `seasons`).
    val seasons: List<TmdbSeasonSummary>? = null,
    // CINEMATHEQUE (SHW-90) F2 — země původu/produkce (osa Země Filmotéky).
    val origin_country: List<String>? = null,
    val production_countries: List<TmdbProductionCountry>? = null,
)
