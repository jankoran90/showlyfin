package com.github.jankoran90.showlyfin.ui.tv

import com.github.jankoran90.showlyfin.core.domain.MediaItem
import org.jellyfin.sdk.model.api.BaseItemKind

sealed class TvDestination {
    object Home : TvDestination()
    data class HomeFiltered(val mediaType: BaseItemKind) : TvDestination()
    object Setup : TvDestination()
    object Settings : TvDestination()
    object Discover : TvDestination()
    object Watchlist : TvDestination()
    data class Detail(val item: MediaItem) : TvDestination()
    data class Playback(val itemId: String, val positionMs: Long = 0L) : TvDestination()
}
