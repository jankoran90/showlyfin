package com.github.jankoran90.showlyfin.feature.listen.player

/** Položka fronty podcastových epizod (čeká na přehrání po dokončení aktuální). */
data class QueuedEpisode(
    val itemId: String,
    val episodeId: String,
    val title: String,
    val coverUrl: String?,
    val guest: String? = null,        // vyparsovaný host (zvýraznit, jako v detailu)
    val description: String? = null,  // popis epizody (zobrazit pod názvem)
    val podcastTitle: String? = null, // název podcastu (z kterého epizoda je)
    /**
     * LEVER (SHW-61): RSS / YouTube epizoda — přímá audio URL bez ABS session.
     * Null = klasická ABS epizoda (přehrává se přes [AbsRepository.startEpisodePlayback]).
     * Pro `direct` epizody je [episodeId] zároveň `mediaId` přehrávače ("rss:…" / "yt:…").
     */
    val direct: DirectAudio? = null,
)

/** Přímý audio zdroj (RSS enclosure nebo náš YouTube proxy) pro frontu/přehrávač bez ABS. */
data class DirectAudio(
    /** Přehratelná audio URL (RSS enclosure nebo `/api/yt/stream/{id}?kind=audio` — stabilní). */
    val url: String,
    val durationSec: Double,
    /** Autor pro metadata přehrávače (název kanálu / podcastu). */
    val author: String?,
)
