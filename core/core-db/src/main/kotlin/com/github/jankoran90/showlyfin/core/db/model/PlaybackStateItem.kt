package com.github.jankoran90.showlyfin.core.db.model

/**
 * SUBSTRATE (SHW-99) F3 — wire (payload) model domény `playback-state` pro delta sync. Serverový
 * `_ID_SPECS["playback-state"] = [("mediaKey",)]` odvozuje row_id z pole [mediaKey]. Gson round-trip.
 */
data class PlaybackStateItem(
    val mediaKey: String,
    val posMs: Long,
    val durMs: Long,
    val updatedAt: Long,
)
