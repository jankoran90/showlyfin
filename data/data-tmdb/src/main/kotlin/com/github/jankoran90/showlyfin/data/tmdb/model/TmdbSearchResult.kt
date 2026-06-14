package com.github.jankoran90.showlyfin.data.tmdb.model

data class TmdbSearchMovieResponse(
    val page: Int? = null,
    val results: List<TmdbSearchMovieItem> = emptyList(),
)

data class TmdbSearchMovieItem(
    val id: Long,
    val title: String? = null,
    val original_title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val vote_average: Float? = null,
    // CANVAS (SHW-47) D: TMDB vrací oblíbenost (popularity) a žánry (genre_ids) v discover/search →
    // potřeba pro řazení karet (oblíbenost) a ≤4 žánrové štítky na kartě (přes statickou mapu, bez sítě).
    val popularity: Float? = null,
    val genre_ids: List<Int>? = null,
)

data class TmdbSearchShowResponse(
    val page: Int? = null,
    val results: List<TmdbSearchShowItem> = emptyList(),
)

data class TmdbSearchShowItem(
    val id: Long,
    val name: String? = null,
    val original_name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String? = null,
    val vote_average: Float? = null,
    val popularity: Float? = null,
    val genre_ids: List<Int>? = null,
)

// COMPASS C3 (SHW-44) — univerzální hledání nad lidmi a vydavatelstvími (`/search/person`,
// `/search/company`). Doplňuje add-source pro kategorie Oblíbených Producenti/Skladatelé/Vydavatelství.
data class TmdbSearchPersonResponse(
    val page: Int? = null,
    val results: List<TmdbSearchPersonItem> = emptyList(),
)

data class TmdbSearchPersonItem(
    val id: Long,
    val name: String? = null,
    val profile_path: String? = null,
    /** Acting / Directing / Production / Sound / Writing … — určuje výchozí roli při přidání do Oblíbených. */
    val known_for_department: String? = null,
    val popularity: Float? = null,
)

data class TmdbSearchCompanyResponse(
    val page: Int? = null,
    val results: List<TmdbSearchCompanyItem> = emptyList(),
)

data class TmdbSearchCompanyItem(
    val id: Long,
    val name: String? = null,
    val logo_path: String? = null,
    val origin_country: String? = null,
)
