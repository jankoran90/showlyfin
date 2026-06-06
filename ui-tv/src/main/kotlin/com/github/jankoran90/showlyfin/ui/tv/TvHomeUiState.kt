package com.github.jankoran90.showlyfin.ui.tv

import org.jellyfin.sdk.model.api.BaseItemKind

data class TvJellyfinItem(
    val id: String,
    val name: String,
    val imageUrl: String,
    val progressPct: Int?,
    val type: String,
    val backdropUrl: String? = null,
)

data class TvHomeRow(
    val title: String,
    val items: List<TvJellyfinItem>,
    // Set for per-library rows → enables "Do knihovny" end card navigation
    val libraryId: String? = null,
    val libraryName: String? = null,
    val collectionType: String? = null,
)

data class PlayMessageEvent(val itemId: String, val positionMs: Long)

data class TvLibraryRef(
    val id: String,
    val name: String,
    val collectionType: String? = null,
)

data class TvHomeUiState(
    val isLoading: Boolean = true,
    val rows: List<TvHomeRow> = emptyList(),
    val error: String? = null,
    val isNotConfigured: Boolean = false,
    val filter: BaseItemKind? = null,
    val cardSize: TvCardSize = TvCardSize.MEDIUM,
    val pinnedLibraries: List<TvLibraryRef> = emptyList(),
)
