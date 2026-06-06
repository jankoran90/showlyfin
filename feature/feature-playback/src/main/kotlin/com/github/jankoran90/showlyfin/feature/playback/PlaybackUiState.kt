package com.github.jankoran90.showlyfin.feature.playback

data class PlaybackUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val streamUrl: String? = null,
    val positionMs: Long = 0L,
    val resumePositionMs: Long = 0L,
    val error: String? = null,
)
