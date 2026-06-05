package com.github.jankoran90.showlyfin.ui.tv

import org.jellyfin.sdk.model.api.BaseItemKind

sealed class TvDestination {
    object Home : TvDestination()
    data class HomeFiltered(val mediaType: BaseItemKind) : TvDestination()
    object Setup : TvDestination()
    object Settings : TvDestination()
    data class Playback(val itemId: String, val positionMs: Long = 0L) : TvDestination()
}
