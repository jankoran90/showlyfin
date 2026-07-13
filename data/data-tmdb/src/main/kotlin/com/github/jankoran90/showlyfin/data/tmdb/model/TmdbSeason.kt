package com.github.jankoran90.showlyfin.data.tmdb.model

/** TENFOOT WS-C (SHW-87): souhrn sezóny ze `tv/{id}` (pole `seasons`). Season 0 = speciály. */
data class TmdbSeasonSummary(
    val season_number: Int,
    val name: String?,
    val episode_count: Int?,
    val poster_path: String?,
    val air_date: String?,
    val overview: String?,
)

/** Detail sezóny ze `tv/{id}/season/{n}` — seznam epizod. */
data class TmdbSeasonDetails(
    val season_number: Int?,
    val name: String?,
    val overview: String?,
    val air_date: String?,
    val episodes: List<TmdbEpisode>?,
)

/** Jedna epizoda sezóny. */
data class TmdbEpisode(
    val episode_number: Int,
    val season_number: Int?,
    val name: String?,
    val overview: String?,
    val still_path: String?,
    val air_date: String?,
    val runtime: Int?,
    val vote_average: Float?,
) {
    fun stillUrl(size: String = "w300") = still_path?.let { "https://image.tmdb.org/t/p/$size$it" }
}
