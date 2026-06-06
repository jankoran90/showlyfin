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
    object JellyfinBrowse : TvDestination()
    data class JellyfinLibrary(
        val libraryId: String,
        val libraryName: String,
        val collectionType: String? = null,
        val parentItemType: String? = null,
        val parent: TvDestination = JellyfinBrowse,
    ) : TvDestination()
    data class Detail(val item: MediaItem) : TvDestination()
    data class JellyfinDetail(
        val itemId: String,
        val parent: TvDestination = JellyfinBrowse,
    ) : TvDestination()
    data class Playback(
        val itemId: String = "",
        val positionMs: Long = 0L,
        val externalUrl: String? = null,
        val title: String = "",
    ) : TvDestination()
    data class SmartDetect(val item: MediaItem) : TvDestination()
}
