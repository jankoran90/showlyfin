package com.github.jankoran90.showlyfin.data.trakt.model

/**
 * Odpověď Traktu na zápis do „Chci vidět" (`/sync/watchlist`) a na odebrání
 * (`/sync/watchlist/remove`).
 *
 * 🔴 HTTP 200 NEZNAMENÁ, že Trakt titul opravdu přidal nebo odebral. Když zaslané id nezná, vrátí
 * 200 s nulovými počty (a titulem v `not_found`) — a appka, která se dívá jen na „nespadlo to",
 * ukáže fajfku, kterou Trakt nikdy nezapsal. Přesně vzorec „spolknutá chyba se tváří jako odpověď"
 * (audit 2026-08-24), tady na straně ZÁPISU: user 2026-08-24 *„pokud dám na kartě odebrat z chci
 * vidět, aby o tom Trakt opravdu věděl a udělal to také"*.
 *
 * Počty stačí — `not_found` je jen jejich druhá strana a nepotřebujeme z něj nic číst.
 */
data class SyncWatchlistResult(
    val added: SyncTypeCounts? = null,
    /** Titul už v seznamu byl — pro nás stejně dobré jako `added`. */
    val existing: SyncTypeCounts? = null,
    val deleted: SyncTypeCounts? = null,
)

/** Počty dotčených položek po typech (Trakt je vrací u každé sync operace). */
data class SyncTypeCounts(
    val movies: Int? = null,
    val shows: Int? = null,
    val seasons: Int? = null,
    val episodes: Int? = null,
) {
    /** Kolik se jich dotklo TOHO typu, který jsme posílali (film vs. seriál). */
    fun countFor(isMovie: Boolean): Int = (if (isMovie) movies else shows) ?: 0
}
