package com.github.jankoran90.showlyfin.data.trakt.model

data class SyncExportItem(
    val ids: Ids,
    val watched_at: String?,
    val hidden_at: String?,
    /**
     * Epizody pod seriálem (`sync/history` u DÍLU): `{ids:{imdb:<seriál>}, seasons:[{number, episodes:[…]}]}`.
     * Gson null vynechá → filmová volání se nemění. Přidáno 2026-07-31, když se ukázalo, že dokoukaný díl
     * ze streamu neměl kudy do Trakt historie (hlásily se jen filmy).
     */
    val seasons: List<Season>? = null,
) {
    companion object {
        fun create(traktId: Long, watchedAt: String? = "released", hiddenAt: String? = null) =
            SyncExportItem(Ids(trakt = traktId), watchedAt, hiddenAt)

        /**
         * Plan WINNOW (SHW-41): položka z dostupných id. Pro tituly bez traktId (otevřené z pásu
         * „od stejného režiséra/studia" → nesou jen tmdbId) — Trakt `sync/watchlist` přijme i tmdb/imdb.
         * Gson nully vynechá → pošle se jen to, co máme. null = žádné použitelné id.
         */
        fun fromIds(traktId: Long?, tmdbId: Long?, imdbId: String?, watchedAt: String? = "released", hiddenAt: String? = null): SyncExportItem? {
            val ids = Ids(
                trakt = traktId?.takeIf { it != 0L },
                tmdb = tmdbId?.takeIf { it != 0L },
                imdb = imdbId?.takeIf { it.isNotBlank() },
            )
            return if (ids.trakt == null && ids.tmdb == null && ids.imdb == null) null
            else SyncExportItem(ids, watchedAt, hiddenAt)
        }
    }
    data class Ids(val trakt: Long? = null, val tmdb: Long? = null, val imdb: String? = null)

    /** Sezóna nesoucí konkrétní díly (Trakt bere `number` seriálové sezóny, ne id). */
    data class Season(val number: Int, val episodes: List<Episode>)

    /** Jeden díl v sezóně; `watched_at` může být „released" nebo ISO čas. */
    data class Episode(val number: Int, val watched_at: String? = null)
}
