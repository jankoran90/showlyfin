package com.github.jankoran90.showlyfin.core.db.model

/**
 * VLTAVA (SHW-110) — payload jednoho řádku domény `ctv-watched` (viz [CtvWatchedEntity]).
 * Server je generický: `_ID_SPECS["ctv-watched"] = [("mediaKey",)]`, timestamp bere z `watchedAt`.
 */
data class CtvWatchedItem(
    val mediaKey: String,
    val watchedAt: Long,
)
