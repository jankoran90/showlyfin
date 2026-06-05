package com.github.jankoran90.showlyfin.feature.jellyfin

data class JellyfinItem(
    val id: String,
    val name: String,
    val imageUrl: String,
    val year: Int?,
    val type: String,
    val isFolder: Boolean,
    val progressPct: Int?,
)

data class JellyfinLibraryItemsUiState(
    val libraryName: String = "",
    val items: List<JellyfinItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
