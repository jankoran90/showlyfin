package com.github.jankoran90.showlyfin.feature.listen

/**
 * PERCH (SHW-69): cíl skoku z poslechového přehrávače na seznam dílů RODIČOVSKÉHO pořadu/knihy.
 *
 * Klik na cover v přehrávači (hraje „TUNA VERSUS – ep. 1") → otevři obrazovku „TUNA VERSUS" se všemi
 * díly, ať si lze vybrat jiný. Funguje napříč CELÝM Poslechem — typ + identifikátor rodiče se odvodí
 * z hrané epizody ([AudiobookPlayerViewModel.currentSourceTarget]):
 *  - ABS audiokniha → [Audiobook] (ABS itemId)
 *  - ABS podcast    → [Podcast]   (ABS itemId)
 *  - RSS / NaVýbornou → [Rss]     (feedUrl, např. `premium:navybornou`)
 *  - YouTube        → [Youtube]   (channel handle/id)
 */
sealed interface ListenSourceTarget {
    data class Audiobook(val itemId: String) : ListenSourceTarget
    data class Podcast(val itemId: String) : ListenSourceTarget
    /** [episodeKey] = klíč právě hrané epizody (`rss:`/`yt:`) → zvýraznění + scroll v obsahu zdroje. */
    data class Rss(val feedUrl: String, val title: String, val episodeKey: String? = null) : ListenSourceTarget
    data class Youtube(val handle: String, val title: String, val episodeKey: String? = null) : ListenSourceTarget
}
