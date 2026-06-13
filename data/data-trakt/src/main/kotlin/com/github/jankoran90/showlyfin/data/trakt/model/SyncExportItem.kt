package com.github.jankoran90.showlyfin.data.trakt.model

data class SyncExportItem(
    val ids: Ids,
    val watched_at: String?,
    val hidden_at: String?,
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
}
