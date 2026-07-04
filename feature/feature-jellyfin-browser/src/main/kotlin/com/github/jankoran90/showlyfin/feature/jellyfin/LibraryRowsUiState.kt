package com.github.jankoran90.showlyfin.feature.jellyfin

import com.github.jankoran90.showlyfin.core.domain.MediaItem

/**
 * Jedna položka v řadě „Knihovna". Vždy nese Jellyfin data (poster, watched, progress),
 * navíc volitelně [mediaItem] = Trakt/TMDB stub pokud se podařil match (TMDB id nebo title+year).
 * Klik: mediaItem != null → bohatý Trakt/TMDB detail; jinak → proklik na Jellyfin kartu.
 */
data class LibraryRowItem(
    val jellyfinId: String,
    val name: String,
    val year: Int?,
    val type: String,
    val imageUrl: String,
    // PANORAMA (SHW-78): široký obrázek pro „Netflix" styl (backdrop → thumb; null = fallback na poster).
    val landscapeUrl: String?,
    val overview: String?,
    val watched: Boolean,
    val progressPct: Int?,
    val mediaItem: MediaItem?,
)

data class LibraryRow(
    val libraryId: String,
    val libraryName: String,
    val collectionType: String?,
    val items: List<LibraryRowItem>,
)

data class LibraryRowsUiState(
    val rows: List<LibraryRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
