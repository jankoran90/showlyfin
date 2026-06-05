package com.github.jankoran90.showlyfin.ui.tv

sealed class TvDestination {
    object Home : TvDestination()
    data class Playback(val itemId: String, val positionMs: Long = 0L) : TvDestination()
}
