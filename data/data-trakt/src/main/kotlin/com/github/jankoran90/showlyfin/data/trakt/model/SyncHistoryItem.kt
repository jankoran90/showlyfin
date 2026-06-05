package com.github.jankoran90.showlyfin.data.trakt.model

data class SyncHistoryItem(
    val id: Long,
    val type: String,
    val action: String,
    val show: Show?,
    val episode: Episode?,
    val watched_at: String?,
)
