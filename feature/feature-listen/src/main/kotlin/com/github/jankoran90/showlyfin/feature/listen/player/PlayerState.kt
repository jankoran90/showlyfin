package com.github.jankoran90.showlyfin.feature.listen.player

data class PlayerState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val title: String = "",
    val author: String? = null,
    val coverUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val currentChapterTitle: String? = null,
    val currentChapterIndex: Int? = null,
    val sleepMinutesLeft: Int? = null,
)
