package com.github.jankoran90.showlyfin.feature.listen.player

/** Položka fronty podcastových epizod (čeká na přehrání po dokončení aktuální). */
data class QueuedEpisode(
    val itemId: String,
    val episodeId: String,
    val title: String,
    val coverUrl: String?,
)
